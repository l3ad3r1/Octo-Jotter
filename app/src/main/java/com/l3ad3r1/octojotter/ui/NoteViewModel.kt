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
import com.l3ad3r1.octojotter.sync.SyncWorker
import android.content.Context
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

class NoteViewModel(application: Application) : AndroidViewModel(application) {

    private val noteDao = AppDatabase.getDatabase(application).noteDao()
    private val githubApiService = RetrofitClient.githubApiService
    private val tokenManager = TokenManager(application)
    private val repository = NoteRepository(noteDao, githubApiService, tokenManager)

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

    // ---- Script plugins (phase 2): sandboxed JS commands ----
    private val scriptEngine = com.l3ad3r1.octojotter.plugin.ScriptEngine()
    private val _pluginCommands =
        MutableStateFlow<List<com.l3ad3r1.octojotter.plugin.ScriptEngine.CommandDescriptor>>(emptyList())
    val pluginCommands: StateFlow<List<com.l3ad3r1.octojotter.plugin.ScriptEngine.CommandDescriptor>> =
        _pluginCommands.asStateFlow()

    init {
        // Reload the sandbox whenever the set of enabled script plugins changes.
        viewModelScope.launch {
            pluginRepository.enabledScriptPlugins.collect { list ->
                val scripts = list.mapNotNull { entity ->
                    pluginRepository.parseManifest(entity)?.main?.let { entity.id to it }
                }.toMap()
                scriptEngine.reload(scripts)
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
            try {
                val resp = githubApiService.getLatestRelease("l3ad3r1", "Octo-Jotter")
                if (!resp.isSuccessful) {
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
                _updateStatus.value = UpdateStatus.Error(e.message ?: "Update check failed.")
            }
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
                "Uncategorized" -> visible.filter { it.folderPath.isEmpty() }
                else -> visible.filter { it.folderPath.contains(selectedFolder) }
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
            val result = repository.deleteNoteAndGist(note)
            if (result.isFailure) {
                _syncMessage.value = "Failed to delete Gist on GitHub: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    // Single manual sync: pushes & pulls both Gist notes and the selected
    // repository, aggregating any failures into one message.
    fun syncNow() {
        viewModelScope.launch {
            _isSyncing.value = true
            _syncMessage.value = "Syncing..."
            val errors = mutableListOf<String>()

            // Gist notes
            repository.pushToGithub().onFailure { errors.add("Gist push: ${it.message}") }
            repository.pullFromGithub().onFailure { errors.add("Gist pull: ${it.message}") }

            // Selected repository (if any)
            val repoPath = selectedRepository.value
            if (!repoPath.isNullOrBlank()) {
                repository.pushToRepository(repoPath).onFailure { errors.add("Repo push: ${it.message}") }
                repository.pullFromRepository(repoPath).onFailure { errors.add("Repo pull: ${it.message}") }
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
