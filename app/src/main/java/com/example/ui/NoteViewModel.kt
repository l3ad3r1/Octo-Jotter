package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.data.local.AppDatabase
import com.example.data.local.NoteEntity
import com.example.data.local.DraftEntity
import com.example.data.remote.RetrofitClient
import com.example.data.remote.TokenManager
import com.example.data.repository.NoteRepository
import com.example.sync.SyncWorker
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

class NoteViewModel(application: Application) : AndroidViewModel(application) {

    private val noteDao = AppDatabase.getDatabase(application).noteDao()
    private val githubApiService = RetrofitClient.githubApiService
    private val tokenManager = TokenManager(application)
    private val repository = NoteRepository(noteDao, githubApiService, tokenManager)

    // Manual Theme preferences
    private val themePreferences = com.example.data.local.ThemePreferences(application)
    val themeMode: StateFlow<String> = themePreferences.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.example.data.local.ThemePreferences.THEME_SYSTEM)

    fun setThemeMode(mode: String) {
        viewModelScope.launch {
            themePreferences.setThemeMode(mode)
        }
    }

    // Folder preferences
    private val folderPreferences = com.example.data.local.FolderPreferences(application)
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

    // DB Backup export preferences/status
    private val _exportStatus = MutableStateFlow<String?>(null)
    val exportStatus: StateFlow<String?> = _exportStatus.asStateFlow()

    fun exportDatabase() {
        viewModelScope.launch {
            _exportStatus.value = "Exporting..."
            try {
                val notes = repository.getAllNotes()
                val drafts = repository.getAllDrafts()
                val backup = com.example.data.local.DatabaseBackup(
                    exportTime = System.currentTimeMillis(),
                    notes = notes,
                    drafts = drafts
                )
                
                val moshi = com.squareup.moshi.Moshi.Builder()
                    .add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
                    .build()
                val adapter = moshi.adapter(com.example.data.local.DatabaseBackup::class.java)
                val jsonString = adapter.toJson(backup)
                
                val filename = "gist_notes_backup_${System.currentTimeMillis()}.json"
                val file = java.io.File(getApplication<Application>().filesDir, filename)
                file.writeText(jsonString)
                
                _exportStatus.value = "Database exported successfully to: ${file.absolutePath}"
            } catch (e: Exception) {
                _exportStatus.value = "Failed to export database: ${e.message}"
            }
        }
    }

    fun clearExportStatus() {
        _exportStatus.value = null
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

    // Filter notes by folder
    val filteredNotes: StateFlow<List<NoteEntity>> = combine(notesWithTagFiltering, _selectedFolder) { notes, selectedFolder ->
        when (selectedFolder) {
            null -> notes
            "Uncategorized" -> notes.filter { it.folderPath.isEmpty() }
            else -> notes.filter { it.folderPath.contains(selectedFolder) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    // Manual sync triggered from settings
    fun syncNow() {
        viewModelScope.launch {
            _isSyncing.value = true
            _syncMessage.value = "Starting Sync..."

            val pushResult = repository.pushToGithub()
            if (pushResult.isFailure) {
                _isSyncing.value = false
                _syncMessage.value = "Push failed: ${pushResult.exceptionOrNull()?.message}"
                return@launch
            }

            val pullResult = repository.pullFromGithub()
            _isSyncing.value = false
            if (pullResult.isSuccess) {
                _syncMessage.value = "Sync successful!"
            } else {
                _syncMessage.value = "Pull failed: ${pullResult.exceptionOrNull()?.message}"
            }
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
