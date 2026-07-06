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
import com.l3ad3r1.octojotter.data.remote.PutContentRequest
import com.l3ad3r1.octojotter.data.remote.DeleteContentRequest
import com.l3ad3r1.octojotter.data.remote.TokenManager
import android.util.Base64
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
        // Repo-backed note: remove the file from its repository instead of a Gist.
        if (!note.repository.isNullOrEmpty()) {
            return deleteNoteFromRepository(note)
        }
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

    // ---------------------------------------------------------------------
    // Repository (folder-based) two-way sync — GitHub Contents/Git Data API
    // ---------------------------------------------------------------------

    // Split "owner/repo" into a validated pair, or fail with a friendly message.
    private fun parseRepoPath(repoPath: String): Result<Pair<String, String>> {
        val parts = repoPath.trim().trim('/').split("/")
        val owner = parts.getOrNull(0)?.takeIf { it.isNotBlank() }
        val repo = parts.getOrNull(1)?.takeIf { it.isNotBlank() }
        return if (owner != null && repo != null && parts.size == 2) {
            Result.success(owner to repo)
        } else {
            Result.failure(Exception("Invalid repository. Use the format owner/repo."))
        }
    }

    // Percent-encode each path segment while preserving "/" separators, so
    // paths with spaces or unicode (e.g. "01 - Projects/Idea — draft.md") work.
    private fun encodeRepoPath(path: String): String =
        path.split("/").joinToString("/") { segment ->
            java.net.URLEncoder.encode(segment, "UTF-8")
                .replace("+", "%20")
                .replace("%2F", "/")
        }

    /**
     * Pull every Markdown file from [repoPath] into the local DB. Notes are
     * matched by (repository, path); locally-dirty notes (needsSync) are left
     * untouched so unsynced edits are never clobbered by a pull.
     */
    suspend fun pullFromRepository(repoPath: String): Result<Unit> {
        val token = tokenManager.getToken()
            ?: return Result.failure(Exception("No GitHub token saved. Please add one in Settings."))
        val formattedToken = "Bearer $token"
        val (owner, repo) = parseRepoPath(repoPath).getOrElse { return Result.failure(it) }

        return try {
            // Default branch varies (main vs master); try main, then master.
            var response = githubApiService.getGitTree(formattedToken, owner, repo, "main")
            if (!response.isSuccessful) {
                response = githubApiService.getGitTree(formattedToken, owner, repo, "master")
            }
            if (!response.isSuccessful) {
                val code = response.code()
                val hint = when (code) {
                    401 -> "Unauthorized — check your token."
                    403 -> "Forbidden — the token needs `repo` scope for private repos."
                    404 -> "Repository or branch not found."
                    else -> response.message()
                }
                return Result.failure(IOException("Failed to fetch repository tree ($code): $hint"))
            }

            val entries = response.body()?.tree ?: emptyList()
            val mdEntries = entries.filter { it.type == "blob" && it.path.endsWith(".md", ignoreCase = true) }

            for (entry in mdEntries) {
                val blobResponse = githubApiService.getGitBlob(formattedToken, owner, repo, entry.sha)
                if (!blobResponse.isSuccessful) continue
                val encoded = blobResponse.body()?.content ?: continue
                val decoded = String(Base64.decode(encoded, Base64.DEFAULT), Charsets.UTF_8)
                // Title mirrors the app's PARA folder convention (path "/" -> "__").
                val title = entry.path.removeSuffix(".md").replace("/", "__")

                val existing = noteDao.getNoteByRepoAndPath(repoPath, entry.path)
                if (existing == null) {
                    noteDao.insert(
                        NoteEntity(
                            title = title,
                            content = decoded,
                            repository = repoPath,
                            path = entry.path,
                            sha = entry.sha,
                            needsSync = false,
                            lastModifiedLocally = System.currentTimeMillis()
                        )
                    )
                } else if (!existing.needsSync) {
                    noteDao.update(
                        existing.copy(
                            title = title,
                            content = decoded,
                            sha = entry.sha,
                            needsSync = false,
                            lastModifiedLocally = System.currentTimeMillis()
                        )
                    )
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Push all locally-dirty notes belonging to [repoPath] back to the repo,
     * creating or updating each file (conflict-safe via the stored blob sha).
     */
    suspend fun pushToRepository(repoPath: String): Result<Unit> {
        val token = tokenManager.getToken()
            ?: return Result.failure(Exception("No GitHub token saved. Please add one in Settings."))
        val formattedToken = "Bearer $token"
        val (owner, repo) = parseRepoPath(repoPath).getOrElse { return Result.failure(it) }

        return try {
            val notesToSync = noteDao.getNotesToSyncForRepository(repoPath)
            for (note in notesToSync) {
                // Derive a file path from the title's PARA convention if unset.
                val rawPath = note.path ?: (note.title.replace("__", "/").ifBlank { "Untitled" } + ".md")
                val encodedPath = encodeRepoPath(rawPath)
                val base64Content = Base64.encodeToString(note.content.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

                suspend fun put(sha: String?) = githubApiService.createOrUpdateFile(
                    formattedToken, owner, repo, encodedPath,
                    PutContentRequest(
                        message = "Update ${note.title} via Octo-Jotter",
                        content = base64Content,
                        sha = sha
                    )
                )

                var response = put(note.sha)
                // 409/422 = stale sha; retry as a fresh create/update without sha.
                if (response.code() == 409 || response.code() == 422) {
                    response = put(null)
                }

                if (response.isSuccessful) {
                    val newSha = response.body()?.content?.sha
                    noteDao.update(note.copy(path = rawPath, sha = newSha, needsSync = false))
                } else {
                    return Result.failure(IOException("Failed to upload ${note.title} (${response.code()})"))
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Delete a repo-backed note's file from GitHub (best-effort). */
    suspend fun deleteNoteFromRepository(note: NoteEntity): Result<Unit> {
        val repoPath = note.repository
        val path = note.path
        val sha = note.sha
        if (repoPath.isNullOrEmpty() || path.isNullOrEmpty() || sha.isNullOrEmpty()) {
            return Result.success(Unit)  // never synced remotely; nothing to delete
        }
        val token = tokenManager.getToken() ?: return Result.success(Unit)
        val formattedToken = "Bearer $token"
        val (owner, repo) = parseRepoPath(repoPath).getOrElse { return Result.success(Unit) }

        return try {
            val response = githubApiService.deleteRepoFile(
                formattedToken, owner, repo, encodeRepoPath(path),
                DeleteContentRequest(message = "Delete ${note.title} via Octo-Jotter", sha = sha)
            )
            if (response.isSuccessful || response.code() == 404) {
                Result.success(Unit)
            } else {
                Result.failure(IOException("Failed to delete file from repo (${response.code()})"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
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
