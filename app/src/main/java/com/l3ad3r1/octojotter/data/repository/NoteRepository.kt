package com.l3ad3r1.octojotter.data.repository

import com.l3ad3r1.octojotter.data.local.NoteDao
import com.l3ad3r1.octojotter.data.local.NoteEntity
import com.l3ad3r1.octojotter.data.local.DraftEntity
import com.l3ad3r1.octojotter.data.local.TagEntity
import com.l3ad3r1.octojotter.data.local.NoteTagCrossRef
import com.l3ad3r1.octojotter.data.local.NoteWithTags
import com.l3ad3r1.octojotter.data.remote.GithubApiService
import com.l3ad3r1.octojotter.data.remote.GistFileRequest
import com.l3ad3r1.octojotter.data.remote.GistRequest
import com.l3ad3r1.octojotter.data.remote.TokenManager
import kotlinx.coroutines.flow.Flow
import java.io.IOException

class NoteRepository(
    private val noteDao: NoteDao,
    private val githubApiService: GithubApiService,
    private val tokenManager: TokenManager
) {
    val allNotes: Flow<List<NoteEntity>> = noteDao.getAllNotesFlow()
    val allTagsFlow: Flow<List<TagEntity>> = noteDao.getAllTagsFlow()

    fun searchNotes(query: String): Flow<List<NoteEntity>> {
        return noteDao.searchNotesFlow("%$query%")
    }

    fun getNotesFilteredAndSorted(query: String, sortBy: String): Flow<List<NoteEntity>> {
        val searchPattern = "%$query%"
        return noteDao.getNotesFilteredAndSorted(searchPattern, sortBy)
    }

    fun getNoteByIdFlow(id: Int): Flow<NoteEntity?> {
        return noteDao.getNoteByIdFlow(id)
    }

    suspend fun getNoteById(id: Int): NoteEntity? {
        return noteDao.getNoteById(id)
    }

    suspend fun insertNote(note: NoteEntity): Long {
        val id = noteDao.insert(note)
        scanAndExtractTags(id.toInt(), note.content)
        return id
    }

    suspend fun updateNote(note: NoteEntity) {
        noteDao.update(note)
        scanAndExtractTags(note.id, note.content)
    }

    fun getNotesByTag(tagName: String): Flow<List<NoteEntity>> {
        return noteDao.getNotesByTag(tagName)
    }

    fun getBacklinks(targetTitle: String, currentNoteId: Int): Flow<List<NoteEntity>> {
        return noteDao.getBacklinks(targetTitle, currentNoteId)
    }

    suspend fun getNoteByTitle(title: String): NoteEntity? {
        return noteDao.getNoteByTitle(title)
    }

    private suspend fun scanAndExtractTags(noteId: Int, content: String) {
        val regex = Regex("(?<=\\s|^)#([a-zA-Z0-9_-]+)")
        val tags = regex.findAll(content).map { it.groupValues[1] }.toList()
        noteDao.updateTagsForNote(noteId, tags)
    }

    suspend fun deleteNote(note: NoteEntity) {
        noteDao.delete(note)
    }

    suspend fun getDraftByNoteId(noteId: Int): DraftEntity? {
        return noteDao.getDraftByNoteId(noteId)
    }

    suspend fun insertDraft(draft: DraftEntity) {
        noteDao.insertDraft(draft)
    }

    suspend fun deleteDraftByNoteId(noteId: Int) {
        noteDao.deleteDraftByNoteId(noteId)
    }

    suspend fun getAllNotes(): List<NoteEntity> {
        return noteDao.getAllNotes()
    }

    suspend fun getAllDrafts(): List<DraftEntity> {
        return noteDao.getAllDrafts()
    }

    suspend fun deleteNoteAndGist(note: NoteEntity): Result<Unit> {
        noteDao.delete(note)
        val gistId = note.gistId
        if (!gistId.isNullOrEmpty()) {
            val token = tokenManager.getToken()
            if (token != null) {
                val formattedToken = "Bearer $token"
                return try {
                    val response = githubApiService.deleteGist(formattedToken, gistId)
                    if (response.isSuccessful || response.code() == 404) {
                        Result.success(Unit)
                    } else {
                        Result.failure(IOException("Failed to delete Gist: ${response.code()} ${response.message()}"))
                    }
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        }
        return Result.success(Unit)
    }

    suspend fun deleteNoteById(id: Int) {
        noteDao.deleteById(id)
    }

    suspend fun pullFromGithub(): Result<Unit> {
        val token = tokenManager.getToken() ?: return Result.failure(Exception("No GitHub token saved. Please add one in Settings."))
        val formattedToken = "Bearer $token"

        return try {
            val response = githubApiService.getGists(formattedToken)
            if (!response.isSuccessful) {
                return Result.failure(IOException("GitHub API Error: ${response.code()} ${response.message()}"))
            }

            val gists = response.body() ?: emptyList()
            for (gist in gists) {
                val mdFileEntry = gist.files?.values?.firstOrNull { file ->
                    file.filename?.endsWith(".md", ignoreCase = true) == true
                }

                if (mdFileEntry != null) {
                    val fullGistResponse = githubApiService.getGist(formattedToken, gist.id)
                    if (fullGistResponse.isSuccessful) {
                        val fullGist = fullGistResponse.body()
                        val fullMdFile = fullGist?.files?.get(mdFileEntry.filename)
                        val content = fullMdFile?.content ?: ""
                        val title = mdFileEntry.filename?.removeSuffix(".md") ?: "Untitled"

                        val existingNote = noteDao.getNoteByGistId(gist.id)
                        if (existingNote == null) {
                            noteDao.insert(
                                NoteEntity(
                                    gistId = gist.id,
                                    title = title,
                                    content = content,
                                    lastModifiedLocally = System.currentTimeMillis(),
                                    needsSync = false
                                )
                            )
                        } else if (!existingNote.needsSync) {
                            noteDao.update(
                                existingNote.copy(
                                    title = title,
                                    content = content,
                                    lastModifiedLocally = System.currentTimeMillis(),
                                    needsSync = false
                                )
                            )
                        }
                    }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun pushToGithub(): Result<Unit> {
        val token = tokenManager.getToken() ?: return Result.failure(Exception("No GitHub token saved. Please add one in Settings."))
        val formattedToken = "Bearer $token"

        return try {
            val notesToSync = noteDao.getNotesToSync()
            for (note in notesToSync) {
                val filename = "${note.title.ifBlank { "Untitled" }}.md"
                val fileRequest = GistFileRequest(content = note.content)
                val filesMap = mapOf(filename to fileRequest)
                val gistRequest = GistRequest(
                    description = "Gist Note: ${note.title}",
                    public = false,
                    files = filesMap
                )

                if (note.gistId.isNullOrEmpty()) {
                    val response = githubApiService.createGist(formattedToken, gistRequest)
                    if (response.isSuccessful) {
                        val createdGist = response.body()
                        if (createdGist != null) {
                            noteDao.update(
                                note.copy(
                                    gistId = createdGist.id,
                                    needsSync = false
                                )
                            )
                        }
                    } else {
                        return Result.failure(IOException("Failed to create Gist: ${response.code()} ${response.message()}"))
                    }
                } else {
                    val response = githubApiService.updateGist(formattedToken, note.gistId, gistRequest)
                    if (response.isSuccessful) {
                        noteDao.update(
                            note.copy(
                                needsSync = false
                            )
                        )
                    } else if (response.code() == 404) {
                        val responseCreate = githubApiService.createGist(formattedToken, gistRequest)
                        if (responseCreate.isSuccessful) {
                            val createdGist = responseCreate.body()
                            if (createdGist != null) {
                                noteDao.update(
                                    note.copy(
                                        gistId = createdGist.id,
                                        needsSync = false
                                    )
                                )
                            }
                        }
                    } else {
                        return Result.failure(IOException("Failed to update Gist: ${response.code()} ${response.message()}"))
                    }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
