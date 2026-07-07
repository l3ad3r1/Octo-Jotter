package com.l3ad3r1.octojotter.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.l3ad3r1.octojotter.data.local.AppDatabase
import com.l3ad3r1.octojotter.data.local.NoteEntity
import com.l3ad3r1.octojotter.data.local.DraftEntity
import com.l3ad3r1.octojotter.data.remote.RetrofitClient
import com.l3ad3r1.octojotter.data.remote.TokenManager
import com.l3ad3r1.octojotter.data.repository.NoteRepository
import com.l3ad3r1.octojotter.data.repository.NoteRevision
import com.l3ad3r1.octojotter.plugin.PluginNote
import com.l3ad3r1.octojotter.plugin.PluginTask
import com.l3ad3r1.octojotter.sync.SyncWorker
import android.content.Context
import android.net.Uri
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class SaveStatus {
    Idle,
    Saving,
    Saved
}

enum class SyncState {
    Synced,
    Syncing,
    Offline
}

data class SyncHealth(
    val activeNotes: Int = 0,
    val pendingSync: Int = 0,
    val conflicts: Int = 0,
    val trash: Int = 0,
    val lastMessage: String? = null
)

sealed class HistoryState {
    object Idle : HistoryState()
    object Loading : HistoryState()
    data class Loaded(val revisions: List<NoteRevision>) : HistoryState()
    data class Error(val message: String) : HistoryState()
}

sealed class UpdateStatus {
    object Idle : UpdateStatus()
    object Checking : UpdateStatus()
    object UpToDate : UpdateStatus()
    data class Available(
        val latestVersion: String,
        val releaseUrl: String,
        val apkUrl: String?,
        val notes: String
    ) : UpdateStatus()
    data class Error(val message: String) : UpdateStatus()
}

sealed class DownloadStatus {
    object Idle : DownloadStatus()
    data class Downloading(val percent: Int) : DownloadStatus()
    object Installing : DownloadStatus()
    data class Failed(val message: String) : DownloadStatus()
}

class NoteViewModel(application: Application) : AndroidViewModel(application) {
    private val logTag = "OctoJotter"

    private val noteDao = AppDatabase.getDatabase(application).noteDao()
    private val githubApiService = RetrofitClient.githubApiService
    private val tokenManager = TokenManager(application)
    private val repository = NoteRepository(noteDao, githubApiService, tokenManager)
    private val appLockPreferences = com.l3ad3r1.octojotter.data.local.AppLockPreferences(application)

    val appLockEnabled: StateFlow<Boolean> = appLockPreferences.appLockEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _appUnlocked = MutableStateFlow(false)
    val appUnlocked: StateFlow<Boolean> = _appUnlocked.asStateFlow()

    fun setAppLockEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appLockPreferences.setAppLockEnabled(enabled)
            _appUnlocked.value = !enabled
        }
    }

    fun markAppUnlocked() {
        _appUnlocked.value = true
    }

    fun lockApp() {
        if (appLockEnabled.value) {
            _appUnlocked.value = false
        }
    }

    // Manual Theme preferences
    private val themePreferences = com.l3ad3r1.octojotter.data.local.ThemePreferences(application)
    val themeMode: StateFlow<String> = themePreferences.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.l3ad3r1.octojotter.data.local.ThemePreferences.THEME_SYSTEM)

    fun setThemeMode(mode: String) {
        viewModelScope.launch {
            themePreferences.setThemeMode(mode)
        }
    }

    // Folder preferences
    private val folderPreferences = com.l3ad3r1.octojotter.data.local.FolderPreferences(application)
    val customFolders: StateFlow<Set<String>> = folderPreferences.customFolders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    fun addFolder(folder: String) {
        viewModelScope.launch {
            folderPreferences.addFolder(folder)
        }
    }

    fun deleteFolder(folder: String) {
        viewModelScope.launch {
            folderPreferences.deleteFolder(folder)
        }
    }

    // Repository sync preferences (list of "owner/repo" + selected one)
    private val repoPreferences = com.l3ad3r1.octojotter.data.local.RepoPreferences(application)
    val repositories: StateFlow<List<String>> = repoPreferences.repositories
        .map { it.sorted() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val selectedRepository: StateFlow<String?> = repoPreferences.selectedRepository
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun addRepository(repo: String) {
        viewModelScope.launch { repoPreferences.addRepository(repo) }
    }

    fun deleteRepository(repo: String) {
        viewModelScope.launch { repoPreferences.deleteRepository(repo) }
    }

    fun selectRepository(repo: String?) {
        viewModelScope.launch { repoPreferences.setSelectedRepository(repo) }
    }

    // Discovered repositories from the GitHub account (for tap-to-add in Settings).
    private val _availableRepos = MutableStateFlow<List<String>>(emptyList())
    val availableRepos: StateFlow<List<String>> = _availableRepos.asStateFlow()
    private val _isLoadingRepos = MutableStateFlow(false)
    val isLoadingRepos: StateFlow<Boolean> = _isLoadingRepos.asStateFlow()

    fun fetchAvailableRepos() {
        viewModelScope.launch {
            _isLoadingRepos.value = true
            val result = repository.listAccessibleRepositories()
            _isLoadingRepos.value = false
            result
                .onSuccess { repos ->
                    _availableRepos.value = repos
                    if (repos.isEmpty()) _syncMessage.value = "No repositories found for this account."
                }
                .onFailure { _syncMessage.value = it.message }
        }
    }

    // ---- Community plugins ----
    private val pluginRepository =
        com.l3ad3r1.octojotter.plugin.PluginRepository(AppDatabase.getDatabase(application).pluginDao())

    val installedPlugins: StateFlow<List<com.l3ad3r1.octojotter.data.local.PluginEntity>> =
        pluginRepository.installedPlugins
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _registryPlugins = MutableStateFlow<List<com.l3ad3r1.octojotter.plugin.RegistryEntry>>(emptyList())
    val registryPlugins: StateFlow<List<com.l3ad3r1.octojotter.plugin.RegistryEntry>> = _registryPlugins.asStateFlow()

    private val _isLoadingPlugins = MutableStateFlow(false)
    val isLoadingPlugins: StateFlow<Boolean> = _isLoadingPlugins.asStateFlow()

    private val _pluginMessage = MutableStateFlow<String?>(null)
    val pluginMessage: StateFlow<String?> = _pluginMessage.asStateFlow()

    // Active theme supplied by an enabled theme plugin (overrides built-in palette).
    val activePluginTheme: StateFlow<com.l3ad3r1.octojotter.ui.theme.PluginThemeState?> =
        pluginRepository.enabledThemePlugin.map { entity ->
            val spec = entity?.let { pluginRepository.parseManifest(it) }?.theme
            spec?.let {
                com.l3ad3r1.octojotter.ui.theme.PluginThemeState(
                    dark = it.dark,
                    colorScheme = com.l3ad3r1.octojotter.ui.theme.buildPluginColorScheme(it)
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun refreshPluginRegistry() {
        viewModelScope.launch {
            _isLoadingPlugins.value = true
            val result = pluginRepository.fetchRegistry()
            _isLoadingPlugins.value = false
            result
                .onSuccess { _registryPlugins.value = it }
                .onFailure { _pluginMessage.value = "Couldn't load plugins: ${it.message}" }
        }
    }

    fun installPlugin(entry: com.l3ad3r1.octojotter.plugin.RegistryEntry) {
        viewModelScope.launch {
            pluginRepository.install(entry, currentVersionName)
                .onSuccess { _pluginMessage.value = "Installed ${entry.name}" }
                .onFailure { _pluginMessage.value = "Install failed: ${it.message}" }
        }
    }

    fun setPluginEnabled(plugin: com.l3ad3r1.octojotter.data.local.PluginEntity, enabled: Boolean) {
        viewModelScope.launch { pluginRepository.setEnabled(plugin, enabled) }
    }

    fun uninstallPlugin(plugin: com.l3ad3r1.octojotter.data.local.PluginEntity) {
        viewModelScope.launch {
            pluginRepository.uninstall(plugin)
            _pluginMessage.value = "Removed ${plugin.name}"
        }
    }

    fun clearPluginMessage() { _pluginMessage.value = null }

    // ---- Script plugins (phase 2/4): sandboxed JS commands + gated APIs ----
    // Bridge the sandbox uses to affect the app; every capability is permission-gated
    // by ScriptEngine before it calls through here.
    private val pluginHost = object : com.l3ad3r1.octojotter.plugin.PluginHost {
        override fun createNote(title: String, content: String) {
            kotlinx.coroutines.runBlocking {
                repository.insertNote(
                    com.l3ad3r1.octojotter.data.local.NoteEntity(
                        title = title.ifBlank { "Untitled" },
                        content = content,
                        needsSync = true,
                        lastModifiedLocally = System.currentTimeMillis()
                    )
                )
            }
            _pluginMessage.value = "Plugin created note: ${title.ifBlank { "Untitled" }}"
        }

        override fun listNoteTitles(): List<String> =
            readablePluginNotes().map { it.title }

        override fun listNotes(): List<PluginNote> =
            readablePluginNotes()

        override fun searchNotes(query: String): List<PluginNote> {
            val normalized = query.trim()
            if (normalized.isEmpty()) return readablePluginNotes()
            return readablePluginNotes().filter { note ->
                note.title.contains(normalized, ignoreCase = true) ||
                    note.displayTitle.contains(normalized, ignoreCase = true) ||
                    note.content.contains(normalized, ignoreCase = true) ||
                    note.tags.any { it.contains(normalized, ignoreCase = true) } ||
                    note.folder.orEmpty().contains(normalized, ignoreCase = true)
            }
        }

        override fun notesWithTag(tag: String): List<PluginNote> {
            val normalized = tag.trim().removePrefix("#")
            if (normalized.isEmpty()) return emptyList()
            return readablePluginNotes().filter { note ->
                note.tags.any { it.equals(normalized, ignoreCase = true) }
            }
        }

        override fun openTasks(): List<PluginTask> =
            readablePluginNotes()
                .filterNot { it.locked }
                .flatMap { note ->
                    note.content.lines().mapIndexedNotNull { index, line ->
                        val match = OPEN_TASK_REGEX.matchEntire(line) ?: return@mapIndexedNotNull null
                        PluginTask(
                            noteId = note.id,
                            noteTitle = note.displayTitle.ifBlank { note.title.ifBlank { "Untitled Note" } },
                            text = match.groupValues[1].trim(),
                            line = line,
                            lineNumber = index + 1,
                            tags = note.tags,
                            folder = note.folder,
                            lastModifiedLocally = note.lastModifiedLocally
                        )
                    }
                }

        override fun log(pluginId: String, message: String) {
            android.util.Log.i("OctoPlugin/$pluginId", message)
        }
    }

    private fun readablePluginNotes(): List<PluginNote> =
        kotlinx.coroutines.runBlocking {
            repository.getAllNotes()
                .filter { it.deletedAt == null }
                .map { note ->
                    PluginNote(
                        id = note.id,
                        title = note.title,
                        displayTitle = note.displayTitle,
                        content = if (note.locked) "" else note.content,
                        tags = note.tags,
                        folder = note.folder ?: note.folderPath.joinToString("/").ifBlank { null },
                        path = note.path,
                        lastModifiedLocally = note.lastModifiedLocally,
                        locked = note.locked
                    )
                }
        }

    private companion object {
        val OPEN_TASK_REGEX = Regex("""^\s*[-*]\s+\[\s]\s+(.*)$""")
    }

    private val scriptEngine = com.l3ad3r1.octojotter.plugin.ScriptEngine(pluginHost)
    private val _pluginCommands =
        MutableStateFlow<List<com.l3ad3r1.octojotter.plugin.ScriptEngine.CommandDescriptor>>(emptyList())
    val pluginCommands: StateFlow<List<com.l3ad3r1.octojotter.plugin.ScriptEngine.CommandDescriptor>> =
        _pluginCommands.asStateFlow()

    init {
        // Reload the sandbox whenever the set of enabled script plugins changes.
        viewModelScope.launch {
            pluginRepository.enabledScriptPlugins.collect { list ->
                val specs = list.mapNotNull { entity ->
                    pluginRepository.parseManifest(entity)?.main?.let { src ->
                        com.l3ad3r1.octojotter.plugin.ScriptEngine.PluginSpec(
                            id = entity.id,
                            source = src,
                            permissions = entity.permissions.toSet()
                        )
                    }
                }
                scriptEngine.reload(specs)
                _pluginCommands.value = scriptEngine.commands()
            }
        }
    }

    // Insertable snippets contributed by enabled snippet plugins.
    val pluginSnippets: StateFlow<List<com.l3ad3r1.octojotter.plugin.SnippetSpec>> =
        pluginRepository.enabledSnippetPlugins.map { list ->
            list.flatMap { entity -> pluginRepository.parseManifest(entity)?.snippets ?: emptyList() }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Run a plugin command on [input], returning transformed text (or null on error). */
    suspend fun runPluginCommand(
        descriptor: com.l3ad3r1.octojotter.plugin.ScriptEngine.CommandDescriptor,
        input: String
    ): String? {
        val result = scriptEngine.run(descriptor.pluginId, descriptor.id, input)
        return result.getOrElse {
            _pluginMessage.value = "Plugin command failed: ${it.message}"
            null
        }
    }

    // DB Backup export preferences/status
    private val _exportStatus = MutableStateFlow<String?>(null)
    val exportStatus: StateFlow<String?> = _exportStatus.asStateFlow()

    // Absolute path of the most recent export, so the UI can offer a share sheet.
    private val _lastExportedPath = MutableStateFlow<String?>(null)
    val lastExportedPath: StateFlow<String?> = _lastExportedPath.asStateFlow()

    fun exportDatabase() {
        viewModelScope.launch {
            _exportStatus.value = "Exporting..."
            try {
                val notes = repository.getAllNotes()
                val drafts = repository.getAllDrafts()
                val backup = com.l3ad3r1.octojotter.data.local.DatabaseBackup(
                    exportTime = System.currentTimeMillis(),
                    notes = notes,
                    drafts = drafts
                )
                
                val moshi = com.squareup.moshi.Moshi.Builder()
                    .add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
                    .build()
                val adapter = moshi.adapter(com.l3ad3r1.octojotter.data.local.DatabaseBackup::class.java)
                val jsonString = adapter.toJson(backup)
                
                // Write to a shareable subdirectory exposed via FileProvider.
                val exportsDir = java.io.File(getApplication<Application>().filesDir, "exports").apply { mkdirs() }
                val filename = "octojotter_backup_${System.currentTimeMillis()}.json"
                val file = java.io.File(exportsDir, filename)
                file.writeText(jsonString)

                _lastExportedPath.value = file.absolutePath
                _exportStatus.value = "Backup ready: $filename. Tap Share to save it anywhere."
            } catch (e: Exception) {
                _lastExportedPath.value = null
                _exportStatus.value = "Failed to export database: ${e.message}"
            }
        }
    }

    // ---- In-app updater (GitHub Releases channel) ----
    val currentVersionName: String = com.l3ad3r1.octojotter.BuildConfig.VERSION_NAME

    private val _updateStatus = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)
    val updateStatus: StateFlow<UpdateStatus> = _updateStatus.asStateFlow()

    fun checkForUpdate() {
        viewModelScope.launch {
            _updateStatus.value = UpdateStatus.Checking
            android.util.Log.i(logTag, "Update check started (current v$currentVersionName)")
            try {
                val resp = githubApiService.getLatestRelease("l3ad3r1", "Octo-Jotter")
                if (!resp.isSuccessful) {
                    android.util.Log.e(logTag, "Update check HTTP ${resp.code()}")
                    _updateStatus.value = UpdateStatus.Error("Couldn't reach GitHub (HTTP ${resp.code()})")
                    return@launch
                }
                val release = resp.body()
                if (release == null) {
                    _updateStatus.value = UpdateStatus.Error("No published release found.")
                    return@launch
                }
                val latestTag = release.tagName?.removePrefix("v")?.trim().orEmpty()
                if (latestTag.isEmpty()) {
                    _updateStatus.value = UpdateStatus.Error("No published release found.")
                    return@launch
                }
                if (isNewerVersion(latestTag, currentVersionName)) {
                    val apk = release.assets
                        ?.firstOrNull { it.name?.endsWith(".apk", ignoreCase = true) == true }
                        ?.downloadUrl
                    android.util.Log.i(logTag, "Update available: v$latestTag apk=$apk")
                    _updateStatus.value = UpdateStatus.Available(
                        latestVersion = latestTag,
                        releaseUrl = release.htmlUrl ?: "https://github.com/l3ad3r1/Octo-Jotter/releases/latest",
                        apkUrl = apk,
                        notes = release.body?.trim()?.take(400).orEmpty()
                    )
                } else {
                    _updateStatus.value = UpdateStatus.UpToDate
                }
            } catch (e: Exception) {
                android.util.Log.e(logTag, "Update check failed", e)
                _updateStatus.value = UpdateStatus.Error(e.message ?: "Update check failed.")
            }
        }
    }

    // ---- In-app APK download + install (replaces the old open-in-browser flow,
    // which stalled on GitHub asset redirects and was invisible to logs) ----
    private val _downloadStatus = MutableStateFlow<DownloadStatus>(DownloadStatus.Idle)
    val downloadStatus: StateFlow<DownloadStatus> = _downloadStatus.asStateFlow()

    fun downloadAndInstallUpdate(apkUrl: String, version: String) {
        viewModelScope.launch {
            _downloadStatus.value = DownloadStatus.Downloading(0)
            android.util.Log.i(logTag, "Update download started: v$version from $apkUrl")
            val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val dir = java.io.File(getApplication<Application>().filesDir, "updates").apply { mkdirs() }
                    dir.listFiles()?.forEach { it.delete() }
                    val file = java.io.File(dir, "OctoJotter-v$version.apk")
                    val client = okhttp3.OkHttpClient.Builder()
                        .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                        .build()
                    val request = okhttp3.Request.Builder().url(apkUrl).build()
                    client.newCall(request).execute().use { resp ->
                        if (!resp.isSuccessful) throw java.io.IOException("HTTP ${resp.code} downloading APK")
                        val body = resp.body ?: throw java.io.IOException("Empty download body")
                        val total = body.contentLength()
                        body.byteStream().use { input ->
                            file.outputStream().use { output ->
                                val buffer = ByteArray(8192)
                                var downloaded = 0L
                                var read = input.read(buffer)
                                while (read != -1) {
                                    output.write(buffer, 0, read)
                                    downloaded += read
                                    if (total > 0) {
                                        _downloadStatus.value = DownloadStatus.Downloading(((downloaded * 100) / total).toInt())
                                    }
                                    read = input.read(buffer)
                                }
                            }
                        }
                    }
                    android.util.Log.i(logTag, "Update downloaded: ${file.length()} bytes -> ${file.absolutePath}")
                    Result.success(file)
                } catch (e: Exception) {
                    android.util.Log.e(logTag, "Update download failed", e)
                    Result.failure(e)
                }
            }
            result.onSuccess { file ->
                _downloadStatus.value = DownloadStatus.Installing
                launchInstaller(file)
                _downloadStatus.value = DownloadStatus.Idle
            }.onFailure {
                _downloadStatus.value = DownloadStatus.Failed(it.message ?: "Download failed")
            }
        }
    }

    private fun launchInstaller(file: java.io.File) {
        val context = getApplication<Application>()
        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            android.util.Log.i(logTag, "Installer launched for ${file.name}")
        } catch (e: Exception) {
            android.util.Log.e(logTag, "Failed to launch installer", e)
            _downloadStatus.value = DownloadStatus.Failed("Couldn't open installer: ${e.message}")
        }
    }

    fun clearDownloadStatus() { _downloadStatus.value = DownloadStatus.Idle }

    /** Read the app's own recent logcat output (for the hidden debug screen). */
    suspend fun readDebugLogs(): String = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-v", "time", "-t", "800"))
            process.inputStream.bufferedReader().use { it.readText() }.ifBlank { "(no logs captured)" }
        } catch (e: Exception) {
            "Failed to read logs: ${e.message}"
        }
    }

    // Compare dotted numeric versions; true when [latest] is strictly greater than [current].
    private fun isNewerVersion(latest: String, current: String): Boolean {
        fun parts(v: String) = v.split(".", "-").map { it.toIntOrNull() ?: 0 }
        val l = parts(latest)
        val c = parts(current)
        for (i in 0 until maxOf(l.size, c.size)) {
            val a = l.getOrElse(i) { 0 }
            val b = c.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    fun clearExportStatus() {
        _exportStatus.value = null
        _lastExportedPath.value = null
    }

    // Sync States
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    // Search query state
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Sort order state ("LAST_MODIFIED" or "TITLE")
    private val _sortBy = MutableStateFlow("LAST_MODIFIED")
    val sortBy: StateFlow<String> = _sortBy.asStateFlow()

    // Selected tag filter state
    private val _selectedTag = MutableStateFlow<String?>(null)
    val selectedTag: StateFlow<String?> = _selectedTag.asStateFlow()

    // Selected folder filter state
    private val _selectedFolder = MutableStateFlow<String?>(null)
    val selectedFolder: StateFlow<String?> = _selectedFolder.asStateFlow()

    fun selectFolder(folder: String?) {
        _selectedFolder.value = folder
    }

    // Folder tree view state (PARA double-underscore folders)
    private val _isFolderTreeView = MutableStateFlow(false)
    val isFolderTreeView: StateFlow<Boolean> = _isFolderTreeView.asStateFlow()

    fun toggleFolderTreeView() {
        _isFolderTreeView.value = !_isFolderTreeView.value
    }

    // Notes list filtered/searched/sorted reactively in Room
    @OptIn(ExperimentalCoroutinesApi::class)
    val allNotes: StateFlow<List<NoteEntity>> = combine(_searchQuery, _sortBy) { query, sort ->
        Pair(query, sort)
    }.flatMapLatest { (query, sort) ->
        repository.getNotesFilteredAndSorted(query, sort)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Unfiltered notes for building the nested folder tree in the drawer
    // (independent of search / folder / tag selection).
    val allNotesForFolders: StateFlow<List<NoteEntity>> =
        repository.allNotes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val trashNotes: StateFlow<List<NoteEntity>> =
        repository.trashNotes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val conflictedNotes: StateFlow<List<NoteEntity>> =
        repository.conflictedNotes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Combine custom folders and actual note folders
    val allFolders: StateFlow<List<String>> = combine(customFolders, allNotes) { custom, notes ->
        val used = notes.flatMap { it.folderPath }.filter { it.isNotBlank() }
        (custom + used).distinct().sorted()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())


    // Filter notes by query, sort, and tag from the DB (Single Source of Truth)
    @OptIn(ExperimentalCoroutinesApi::class)
    val notesWithTagFiltering: StateFlow<List<NoteEntity>> = combine(_searchQuery, _sortBy, _selectedTag) { query, sort, tag ->
        Triple(query, sort, tag)
    }.flatMapLatest { (query, sort, tag) ->
        if (tag == null) {
            repository.getNotesFilteredAndSorted(query, sort)
        } else {
            repository.getNotesByTag(tag)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Notes hidden from the list while a "deleted — Undo" Snackbar is showing.
    // The actual delete is only committed once the Snackbar is dismissed without Undo.
    private val _pendingDeletionIds = MutableStateFlow<Set<Int>>(emptySet())

    // Filter notes by folder, excluding any pending (optimistically deleted) notes.
    val filteredNotes: StateFlow<List<NoteEntity>> =
        combine(notesWithTagFiltering, _selectedFolder, _pendingDeletionIds) { notes, selectedFolder, pending ->
            val visible = if (pending.isEmpty()) notes else notes.filter { it.id !in pending }
            when (selectedFolder) {
                null -> visible
                "Uncategorized" -> visible.filter { it.locationPath.isEmpty() }
                // selectedFolder is now a full path ("Repo/Folder/Sub"); match that
                // folder and everything nested beneath it.
                else -> visible.filter {
                    val p = it.locationPath.joinToString("/")
                    p == selectedFolder || p.startsWith("$selectedFolder/")
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Hide a note optimistically (swipe-to-delete) before the Undo window elapses.
    fun markPendingDeletion(noteId: Int) {
        _pendingDeletionIds.value = _pendingDeletionIds.value + noteId
    }

    // Restore an optimistically hidden note when the user taps Undo.
    fun undoPendingDeletion(noteId: Int) {
        _pendingDeletionIds.value = _pendingDeletionIds.value - noteId
    }

    // Commit a pending swipe-delete once the Undo window has elapsed.
    fun commitPendingDeletion(note: NoteEntity) {
        deleteNote(note)
        _pendingDeletionIds.value = _pendingDeletionIds.value - note.id
    }

    // Database-backed tags
    val dbTags: StateFlow<List<String>> = repository.allTagsFlow.map { tags ->
        tags.map { it.name }.distinct().sorted()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val availableTags: StateFlow<List<String>> = dbTags

    fun getBacklinksForNote(title: String, noteId: Int): Flow<List<NoteEntity>> {
        return repository.getBacklinks(title, noteId)
    }

    fun exportMarkdownArchive() {
        viewModelScope.launch {
            _exportStatus.value = "Exporting Markdown..."
            try {
                val notes = repository.getAllNotes()
                    .filter { it.deletedAt == null }
                val exportsDir = java.io.File(getApplication<Application>().filesDir, "exports").apply { mkdirs() }
                val filename = "octojotter_markdown_${System.currentTimeMillis()}.zip"
                val file = java.io.File(exportsDir, filename)
                java.util.zip.ZipOutputStream(file.outputStream()).use { zip ->
                    val usedNames = mutableSetOf<String>()
                    notes.forEach { note ->
                        val baseName = note.title.ifBlank { "Untitled" }
                            .replace("__", "/")
                            .split("/")
                            .joinToString("/") { segment ->
                                segment.replace(Regex("""[\\:*?"<>|]"""), "-").ifBlank { "Untitled" }
                            }
                        var entryName = "$baseName.md"
                        var suffix = 2
                        while (!usedNames.add(entryName.lowercase())) {
                            entryName = "${baseName} ($suffix).md"
                            suffix++
                        }
                        zip.putNextEntry(java.util.zip.ZipEntry(entryName))
                        zip.write(note.content.toByteArray(Charsets.UTF_8))
                        zip.closeEntry()
                    }
                }
                _lastExportedPath.value = file.absolutePath
                _exportStatus.value = "Markdown archive ready: $filename. Tap Share to save it anywhere."
            } catch (e: Exception) {
                _lastExportedPath.value = null
                _exportStatus.value = "Failed to export Markdown: ${e.message}"
            }
        }
    }

    fun importMarkdownFile(uri: Uri) {
        viewModelScope.launch {
            try {
                val resolver = getApplication<Application>().contentResolver
                val title = resolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && nameIndex >= 0) cursor.getString(nameIndex) else null
                }?.removeSuffix(".md")?.removeSuffix(".markdown") ?: "Imported note"
                val content = resolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                    ?: throw java.io.IOException("Couldn't read selected file.")
                val note = NoteEntity(
                    title = title.ifBlank { "Imported note" },
                    content = content,
                    folder = "Imported",
                    lastModifiedLocally = System.currentTimeMillis(),
                    needsSync = true
                )
                repository.insertNote(note)
                _exportStatus.value = "Imported Markdown note: ${note.title}"
                triggerBackgroundSync()
            } catch (e: Exception) {
                _exportStatus.value = "Failed to import Markdown: ${e.message}"
            }
        }
    }

    fun selectTag(tag: String?) {
        _selectedTag.value = tag
    }

    fun setNoteFolder(note: NoteEntity, folder: String?) {
        viewModelScope.launch {
            val updated = note.copy(
                folder = if (folder == "Uncategorized" || folder.isNullOrBlank()) null else folder,
                lastModifiedLocally = System.currentTimeMillis(),
                needsSync = true
            )
            repository.updateNote(updated)
            if (_editingNote.value?.id == note.id) {
                _editingNote.value = updated
            }
            triggerBackgroundSync()
        }
    }

    fun duplicateNote(note: NoteEntity) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val duplicate = note.copy(
                id = 0,
                gistId = null,
                repository = null,
                path = null,
                sha = null,
                title = "${note.title.ifBlank { "Untitled" }} (copy)",
                lastModifiedLocally = now,
                needsSync = true,
                deletedAt = null,
                pendingRemoteDelete = false,
                remoteUpdatedAt = null,
                lastSyncedContentHash = null,
                conflictState = null,
                conflictedRemoteContent = null,
                conflictedRemoteModifiedAt = null
            )
            repository.insertNote(duplicate)
            triggerBackgroundSync()
        }
    }

    fun renameNote(note: NoteEntity, newDisplayTitle: String) {
        val cleanedTitle = newDisplayTitle.trim().ifBlank { "Untitled Note" }
        viewModelScope.launch {
            val renamedTitle = if (note.title.contains("__")) {
                note.title.substringBeforeLast("__") + "__" + cleanedTitle
            } else {
                cleanedTitle
            }
            val updated = note.copy(
                title = renamedTitle,
                lastModifiedLocally = System.currentTimeMillis(),
                needsSync = true
            )
            repository.updateNote(updated)
            if (_editingNote.value?.id == note.id) {
                _editingNote.value = updated
                _editorTitle.value = updated.displayTitle
            }
            triggerBackgroundSync()
        }
    }

    fun setTaskChecked(noteId: Int, lineNumber: Int, checked: Boolean) {
        viewModelScope.launch {
            val note = repository.getNoteById(noteId) ?: return@launch
            if (note.locked || lineNumber < 1) return@launch
            val lines = note.content.lines().toMutableList()
            val index = lineNumber - 1
            val currentLine = lines.getOrNull(index) ?: return@launch
            val updatedLine = when {
                checked -> currentLine.replaceFirst(Regex("""^(\s*[-*]\s+)\[\s]"""), "$1[x]")
                else -> currentLine.replaceFirst(Regex("""^(\s*[-*]\s+)\[[xX]]"""), "$1[ ]")
            }
            if (updatedLine == currentLine) return@launch
            val updated = note.copy(
                content = lines.also { it[index] = updatedLine }.joinToString("\n"),
                lastModifiedLocally = System.currentTimeMillis(),
                needsSync = true
            )
            repository.updateNote(updated)
            if (_editingNote.value?.id == note.id) {
                _editingNote.value = updated
                _editorContent.value = updated.content
            }
            triggerBackgroundSync()
        }
    }

    // Network availability reactive flow
    private val networkAvailableFlow: Flow<Boolean> = callbackFlow {
        val connectivityManager = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true)
            }
            override fun onLost(network: Network) {
                trySend(false)
            }
        }
        
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        val initialValue = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        trySend(initialValue)
        
        try {
            connectivityManager.registerNetworkCallback(
                NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build(),
                callback
            )
        } catch (e: Exception) {
            trySend(true)
        }
        
        awaitClose {
            try {
                connectivityManager.unregisterNetworkCallback(callback)
            } catch (e: Exception) {}
        }
    }

    // Combined SyncState Flow observing WorkManager task status, manual sync status, and network status
    val syncState: StateFlow<SyncState> by lazy {
        combine(
            _isSyncing,
            WorkManager.getInstance(getApplication()).getWorkInfosForUniqueWorkFlow("GistNotesSync"),
            networkAvailableFlow
        ) { manualSyncing, workInfos, isOnline ->
            if (!isOnline) {
                SyncState.Offline
            } else if (manualSyncing || workInfos.any { it.state == androidx.work.WorkInfo.State.RUNNING }) {
                SyncState.Syncing
            } else {
                SyncState.Synced
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SyncState.Synced)
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateSortBy(sort: String) {
        _sortBy.value = sort
    }

    // GitHub PAT
    val githubToken: StateFlow<String> = tokenManager.tokenFlow

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    val syncHealth: StateFlow<SyncHealth> = combine(
        repository.allNotes,
        repository.pendingSyncCount,
        repository.conflictCount,
        repository.trashCount,
        _syncMessage
    ) { notes, pending, conflicts, trash, message ->
        SyncHealth(
            activeNotes = notes.size,
            pendingSync = pending,
            conflicts = conflicts,
            trash = trash,
            lastMessage = message
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SyncHealth())

    // Editor States
    private val _editingNote = MutableStateFlow<NoteEntity?>(null)
    val editingNote: StateFlow<NoteEntity?> = _editingNote.asStateFlow()

    private val _editorTitle = MutableStateFlow("")
    val editorTitle: StateFlow<String> = _editorTitle.asStateFlow()

    private val _editorContent = MutableStateFlow("")
    val editorContent: StateFlow<String> = _editorContent.asStateFlow()

    private val _saveStatus = MutableStateFlow(SaveStatus.Idle)
    val saveStatus: StateFlow<SaveStatus> = _saveStatus.asStateFlow()

    private val _pendingDraft = MutableStateFlow<DraftEntity?>(null)
    val pendingDraft: StateFlow<DraftEntity?> = _pendingDraft.asStateFlow()

    private val _historyState = MutableStateFlow<HistoryState>(HistoryState.Idle)
    val historyState: StateFlow<HistoryState> = _historyState.asStateFlow()

    private val _revisionPreview = MutableStateFlow<String?>(null)
    val revisionPreview: StateFlow<String?> = _revisionPreview.asStateFlow()

    private var autoSaveJob: Job? = null
    private var draftSaveJob: Job? = null

    fun saveToken(token: String) {
        tokenManager.saveToken(token)
    }

    fun clearToken() {
        tokenManager.clearToken()
    }

    // Load note for editing
    fun loadNote(noteId: Int) {
        viewModelScope.launch {
            val note = repository.getNoteById(noteId)
            _editingNote.value = note
            if (note != null) {
                _editorTitle.value = note.title
                _editorContent.value = note.content
                _saveStatus.value = SaveStatus.Idle
                
                // Check if an unsaved draft exists for this note
                val draft = repository.getDraftByNoteId(noteId)
                if (draft != null && (draft.title != note.title || draft.content != note.content)) {
                    _pendingDraft.value = draft
                } else {
                    _pendingDraft.value = null
                }
            }
        }
    }

    // Handle Title/Content text changes with 1.5s debounce auto-save
    fun onNoteTextChanged(newTitle: String, newContent: String) {
        _editorTitle.value = newTitle
        _editorContent.value = newContent
        
        val note = _editingNote.value ?: return
        _saveStatus.value = SaveStatus.Saving

        // Auto-save draft state to 'drafts' table (1.0s debounce for fast temporary crash recovery)
        draftSaveJob?.cancel()
        draftSaveJob = viewModelScope.launch {
            delay(1000)
            repository.insertDraft(
                DraftEntity(
                    noteId = note.id,
                    title = newTitle,
                    content = newContent,
                    lastSaved = System.currentTimeMillis()
                )
            )
        }

        // Main note database update (1.5s debounce)
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            delay(1500) // 1.5s debounce
            val updated = note.copy(
                title = newTitle,
                content = newContent,
                lastModifiedLocally = System.currentTimeMillis(),
                needsSync = true
            )
            repository.updateNote(updated)
            _editingNote.value = updated
            _saveStatus.value = SaveStatus.Saved
            
            // Delete draft since the main note is now fully saved
            repository.deleteDraftByNoteId(note.id)
            
            // Trigger background WorkManager sync immediately
            triggerBackgroundSync()
        }
    }

    // Recover draft and apply its title and content
    fun recoverDraft(draft: DraftEntity) {
        _editorTitle.value = draft.title
        _editorContent.value = draft.content
        _pendingDraft.value = null
        onNoteTextChanged(draft.title, draft.content)
        viewModelScope.launch {
            repository.deleteDraftByNoteId(draft.noteId)
        }
    }

    // Discard draft and remove it from DB
    fun discardDraft(draft: DraftEntity) {
        _pendingDraft.value = null
        viewModelScope.launch {
            repository.deleteDraftByNoteId(draft.noteId)
        }
    }

    // Clean up draft on clean exit
    fun clearDraftForCurrentNote() {
        val note = _editingNote.value ?: return
        viewModelScope.launch {
            repository.deleteDraftByNoteId(note.id)
        }
    }

    // Toggle pin/unpin status of a note
    fun togglePinNote(note: NoteEntity) {
        viewModelScope.launch {
            val updated = note.copy(
                pinned = !note.pinned,
                lastModifiedLocally = System.currentTimeMillis(),
                needsSync = true
            )
            repository.updateNote(updated)
            if (_editingNote.value?.id == note.id) {
                _editingNote.value = updated
            }
            // Trigger background WorkManager sync immediately
            triggerBackgroundSync()
        }
    }

    // Create a new note and return its ID
    fun createNewNote(onNoteCreated: (Int) -> Unit) {
        viewModelScope.launch {
            val defaultFolder = if (_selectedFolder.value == "Uncategorized") null else _selectedFolder.value
            val newNote = NoteEntity(
                title = "",
                content = "",
                folder = defaultFolder,
                lastModifiedLocally = System.currentTimeMillis(),
                needsSync = false
            )
            val id = repository.insertNote(newNote).toInt()
            onNoteCreated(id)
        }
    }

    fun createNewNoteWithTitle(title: String, onNoteCreated: (Int) -> Unit) {
        viewModelScope.launch {
            val defaultFolder = if (_selectedFolder.value == "Uncategorized") null else _selectedFolder.value
            val newNote = NoteEntity(
                title = title,
                content = "",
                folder = defaultFolder,
                lastModifiedLocally = System.currentTimeMillis(),
                needsSync = false
            )
            val id = repository.insertNote(newNote).toInt()
            onNoteCreated(id)
        }
    }

    suspend fun getNoteByTitle(title: String): NoteEntity? {
        return repository.getNoteByTitle(title)
    }

    // Update tags for current note
    fun updateTags(newTags: List<String>) {
        val note = _editingNote.value ?: return
        _saveStatus.value = SaveStatus.Saving
        viewModelScope.launch {
            val updated = note.copy(
                tags = newTags,
                lastModifiedLocally = System.currentTimeMillis(),
                needsSync = true
            )
            repository.updateNote(updated)
            _editingNote.value = updated
            _saveStatus.value = SaveStatus.Saved
            triggerBackgroundSync()
        }
    }

    // Delete a note
    fun deleteNote(note: NoteEntity) {
        viewModelScope.launch {
            repository.moveNoteToTrash(note)
            _syncMessage.value = "Moved \"${note.displayTitle.ifBlank { "Untitled Note" }}\" to Trash."
        }
    }

    fun createNoteFromShare(title: String, sharedText: String) {
        viewModelScope.launch {
            val clippedAt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date())
            val content = buildString {
                append(sharedText)
                append("\n\n---\n")
                append("Captured with Octo Jotter on ")
                append(clippedAt)
            }
            val newNote = NoteEntity(
                title = title.ifBlank { "Shared note" },
                content = content,
                folder = "Inbox",
                lastModifiedLocally = System.currentTimeMillis(),
                needsSync = true
            )
            repository.insertNote(newNote)
            _syncMessage.value = "Captured shared text to Inbox."
            triggerBackgroundSync()
        }
    }

    fun restoreNote(note: NoteEntity) {
        viewModelScope.launch {
            repository.restoreNoteFromTrash(note)
            _syncMessage.value = "Restored \"${note.displayTitle.ifBlank { "Untitled Note" }}\"."
            triggerBackgroundSync()
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            repository.emptyTrash()
            _syncMessage.value = "Trash emptied."
        }
    }

    fun toggleLockNote(note: NoteEntity) {
        viewModelScope.launch {
            repository.setNoteLocked(note, !note.locked)
            val updated = note.copy(locked = !note.locked)
            if (_editingNote.value?.id == note.id) {
                _editingNote.value = updated
            }
        }
    }

    fun loadNoteHistory(noteId: Int) {
        viewModelScope.launch {
            _historyState.value = HistoryState.Loading
            _revisionPreview.value = null
            val note = repository.getNoteById(noteId)
            if (note == null) {
                _historyState.value = HistoryState.Error("Note not found.")
                return@launch
            }
            repository.getNoteHistory(note)
                .onSuccess { _historyState.value = HistoryState.Loaded(it) }
                .onFailure { _historyState.value = HistoryState.Error(it.message ?: "Couldn't load history.") }
        }
    }

    fun previewRevision(noteId: Int, revisionId: String) {
        viewModelScope.launch {
            val note = repository.getNoteById(noteId) ?: return@launch
            repository.getRevisionContent(note, revisionId)
                .onSuccess { _revisionPreview.value = it }
                .onFailure { _syncMessage.value = "Couldn't load revision: ${it.message}" }
        }
    }

    fun restoreRevision(noteId: Int, revisionId: String) {
        viewModelScope.launch {
            val note = repository.getNoteById(noteId) ?: return@launch
            repository.restoreRevision(note, revisionId)
                .onSuccess {
                    _syncMessage.value = "Revision restored. It will sync on the next push."
                    loadNote(noteId)
                    triggerBackgroundSync()
                }
                .onFailure { _syncMessage.value = "Couldn't restore revision: ${it.message}" }
        }
    }

    fun resolveConflictKeepLocal(note: NoteEntity) {
        viewModelScope.launch {
            repository.resolveConflictKeepLocal(note)
            _syncMessage.value = "Keeping local copy for ${note.displayTitle.ifBlank { "Untitled Note" }}."
            triggerBackgroundSync()
        }
    }

    fun resolveConflictUseRemote(note: NoteEntity) {
        viewModelScope.launch {
            repository.resolveConflictUseRemote(note)
            _syncMessage.value = "Using remote copy for ${note.displayTitle.ifBlank { "Untitled Note" }}."
        }
    }

    fun resolveConflictSaveBoth(note: NoteEntity) {
        viewModelScope.launch {
            repository.resolveConflictSaveBoth(note)
            _syncMessage.value = "Saved both copies for ${note.displayTitle.ifBlank { "Untitled Note" }}."
            triggerBackgroundSync()
        }
    }

    // Single manual sync: pushes & pulls both Gist notes and the selected
    // repository, aggregating any failures into one message.
    fun syncNow() {
        viewModelScope.launch {
            _isSyncing.value = true
            _syncMessage.value = "Syncing..."
            val errors = mutableListOf<String>()

            repository.pullFromGithub().onFailure { errors.add("Gist pull: ${it.message}") }
            repository.pushToGithub().onFailure { errors.add("Gist push: ${it.message}") }

            // Selected repository (if any)
            val repoPath = selectedRepository.value
            if (!repoPath.isNullOrBlank()) {
                repository.pullFromRepository(repoPath).onFailure { errors.add("Repo pull: ${it.message}") }
                repository.pushToRepository(repoPath).onFailure { errors.add("Repo push: ${it.message}") }
            }

            _isSyncing.value = false
            _syncMessage.value = if (errors.isEmpty()) "Sync successful!" else errors.joinToString("\n")
        }
    }

    fun clearSyncMessage() {
        _syncMessage.value = null
    }

    // Background sync via WorkManager
    fun triggerBackgroundSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(getApplication())
            .enqueueUniqueWork("GistNotesSync", ExistingWorkPolicy.REPLACE, syncRequest)
    }
}
