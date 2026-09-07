package com.l3ad3r1.octojotter.data.repository

import com.l3ad3r1.octojotter.data.markdown.Frontmatter
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
import java.security.MessageDigest

private const val MAX_REPO_PAGES = 20
private const val GISTS_PER_PAGE = 100
private const val MAX_GIST_PAGES = 20
private const val CONFLICT_STATE = "CONFLICT"

data class NoteRevision(
    val id: String,
    val label: String,
    val committedAt: String?,
    val summary: String?
)

class NoteRepository(
    private val noteDao: NoteDao,
    private val githubApiService: GithubApiService,
    private val tokenManager: TokenManager
) {
    val allNotes: Flow<List<NoteEntity>> = noteDao.getAllNotesFlow()
    val trashNotes: Flow<List<NoteEntity>> = noteDao.getTrashNotesFlow()
    val allTagsFlow: Flow<List<TagEntity>> = noteDao.getAllTagsFlow()
    val trashCount: Flow<Int> = noteDao.getTrashCountFlow()
    val pendingSyncCount: Flow<Int> = noteDao.getPendingSyncCountFlow()
    val conflictCount: Flow<Int> = noteDao.getConflictCountFlow()
    val conflictedNotes: Flow<List<NoteEntity>> = noteDao.getConflictedNotesFlow()

    fun searchNotes(query: String): Flow<List<NoteEntity>> {
        return noteDao.searchNotesFlow("%${escapeLike(query)}%")
    }

    fun getNotesFilteredAndSorted(query: String, sortBy: String): Flow<List<NoteEntity>> {
        return noteDao.getNotesFilteredAndSorted("%${escapeLike(query)}%", sortBy)
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

    /**
     * Save only the title and body the editor owns, leaving every sync column
     * (`gistId`, `sha`, `remoteFilename`, hashes, conflict state) to whatever
     * the last sync wrote. See [NoteDao.updateNoteText] for why writing the
     * editor's whole-row snapshot back was creating duplicate Gists.
     */
    suspend fun updateNoteText(id: Int, title: String, content: String) {
        noteDao.updateNoteText(id, title, content, System.currentTimeMillis())
        scanAndExtractTags(id, content)
    }


    fun getNotesByTag(tagName: String): Flow<List<NoteEntity>> {
        return noteDao.getNotesByTag(tagName)
    }

    fun getBacklinks(targetTitle: String, currentNoteId: Int): Flow<List<NoteEntity>> {
        return noteDao.getBacklinks(escapeLike(targetTitle), currentNoteId)
    }

    suspend fun getNoteByTitle(title: String): NoteEntity? {
        return noteDao.getNoteByTitle(title)
    }

    suspend fun setNoteColor(id: Int, color: String?) {
        noteDao.setColor(id, color)
    }

    suspend fun setNoteReminder(id: Int, reminderAt: Long?) {
        noteDao.setReminderAt(id, reminderAt)
    }

    suspend fun getDailyNoteByTitle(title: String): NoteEntity? {
        return noteDao.getDailyNoteByTitle(title)
    }

    suspend fun getNotesWithReminders(): List<NoteEntity> {
        return noteDao.getNotesWithReminders()
    }

    // Tags come from two places in a note: inline #hashtags anywhere in the body,
    // and a `tags:` (or `tag:`) list in YAML frontmatter — the convention this
    // app's own Second Brain templates use, and the one Obsidian vaults use in
    // general. Missing the frontmatter half meant every PARA/Zettelkasten note
    // synced in from a real vault had no tags in this table at all, so "Filter
    // by tag" (and the Tags nav destination) had nothing to show.
    private suspend fun scanAndExtractTags(noteId: Int, content: String) {
        val inlineRegex = Regex("(?<=\\s|^)#([a-zA-Z0-9_-]+)")
        val inlineTags = inlineRegex.findAll(content)
            .map { it.groupValues[1] }
            // Obsidian's own tag rule: a tag must contain at least one
            // non-numeric character. Without this, "PR #21" or "issue #3" in
            // a daily note reads as tag "21" or "3" — a reference, not a tag.
            .filter { tag -> tag.any { ch -> !ch.isDigit() } }
            .toList()
        val frontmatterTags = Frontmatter.parse(content)?.tags().orEmpty()
        val tags = (frontmatterTags + inlineTags).distinct()
        noteDao.updateTagsForNote(noteId, tags)
    }

    /**
     * Re-run tag extraction over every note already in the database. Sync
     * (both the per-repo pull and the individual-Gist pull) writes through
     * [NoteDao] directly rather than [insertNote]/[updateNote], so every note
     * that arrived before this fix — which, for a synced vault, is all of
     * them — has an empty entry in the tags table no matter what its
     * frontmatter says. Cheap enough to call unconditionally at startup:
     * [NoteDao.updateTagsForNote] is a clean delete-and-reinsert, so running
     * it again over unchanged notes is a no-op past the first launch.
     */
    suspend fun backfillTagsForAllNotes() {
        noteDao.getAllNotes().forEach { note -> scanAndExtractTags(note.id, note.content) }
    }

    suspend fun moveNoteToTrash(note: NoteEntity) {
        // Local and reversible: the remote copy is untouched until the trash is
        // emptied, so Restore never has to re-upload anything.
        noteDao.moveToTrash(id = note.id, deletedAt = System.currentTimeMillis())
    }

    suspend fun restoreNoteFromTrash(note: NoteEntity) {
        noteDao.restoreFromTrash(note.id)
    }

    /**
     * Permanently delete everything in the Trash, remote copies included.
     *
     * Returns how many notes are still waiting on GitHub — offline, or a failed
     * request. Those rows deliberately stay in the Trash: dropping them locally
     * while the Gist still existed is what used to make deleted notes reappear
     * on the next pull. [processPendingRemoteDeletes] retries them on every
     * sync and removes each row as soon as its remote copy is confirmed gone.
     */
    suspend fun emptyTrash(): Int {
        noteDao.queueTrashForPurge()
        processPendingRemoteDeletes()
        noteDao.purgeDeletableTrash()
        return noteDao.countPendingRemoteDeletes()
    }

    /**
     * Drain the "deleted, remote copy still out there" queue. Each note whose
     * Gist or repository file is confirmed gone (deleted now, or already a 404)
     * is hard-deleted locally. Failures are left queued for the next attempt.
     */
    suspend fun processPendingRemoteDeletes(): Result<Unit> {
        val pending = noteDao.getNotesPendingRemoteDelete()
        if (pending.isEmpty()) return Result.success(Unit)
        var lastFailure: Throwable? = null
        for (note in pending) {
            deleteRemoteCopy(note)
                .onSuccess { noteDao.deleteById(note.id) }
                .onFailure { lastFailure = it }
        }
        return lastFailure?.let { Result.failure(it) } ?: Result.success(Unit)
    }

    /**
     * Delete this note's remote copy — Gist or repository file — treating "no
     * remote copy" and "already gone (404)" as success, since either way there
     * is nothing left to resurrect the note on the next pull.
     */
    private suspend fun deleteRemoteCopy(note: NoteEntity): Result<Unit> =
        when (decidePurge(note)) {
            PurgeDecision.DropRow -> Result.success(Unit)

            PurgeDecision.DeleteRepoFile -> deleteNoteFromRepository(note)

            PurgeDecision.DeleteGist -> {
                val token = tokenManager.getToken()
                if (token == null) {
                    Result.failure(IOException("No GitHub token saved."))
                } else {
                    try {
                        val response = githubApiService.deleteGist("Bearer $token", note.gistId.orEmpty())
                        if (response.isSuccessful || response.code() == 404) {
                            Result.success(Unit)
                        } else {
                            Result.failure(
                                IOException("Failed to delete Gist: ${response.code()} ${response.message()}")
                            )
                        }
                    } catch (e: Exception) {
                        Result.failure(e)
                    }
                }
            }
        }

    suspend fun setNoteLocked(note: NoteEntity, locked: Boolean) {
        noteDao.setLocked(note.id, locked)
    }

    suspend fun resolveConflictKeepLocal(note: NoteEntity) {
        noteDao.update(
            note.copy(
                needsSync = true,
                conflictState = null,
                conflictedRemoteContent = null,
                conflictedRemoteModifiedAt = null,
                lastModifiedLocally = System.currentTimeMillis()
            )
        )
    }

    suspend fun resolveConflictUseRemote(note: NoteEntity) {
        val remote = note.conflictedRemoteContent ?: return
        noteDao.update(
            note.copy(
                content = remote,
                needsSync = false,
                conflictState = null,
                conflictedRemoteContent = null,
                conflictedRemoteModifiedAt = null,
                lastSyncedContentHash = hashContent(remote),
                lastModifiedLocally = System.currentTimeMillis()
            )
        )
        scanAndExtractTags(note.id, remote)
    }

    suspend fun resolveConflictSaveBoth(note: NoteEntity): Long {
        val remote = note.conflictedRemoteContent ?: ""
        val copyId = noteDao.insert(
            note.copy(
                id = 0,
                gistId = null,
                sha = null,
                // Clear the remote address as well as the identity. Keeping
                // `path` meant the "remote copy" pushed straight back over the
                // file it was supposed to sit beside; with it null the push
                // derives a fresh path from the copy's own title.
                path = null,
                remoteFilename = null,
                deletedAt = null,
                pendingRemoteDelete = false,
                remoteUpdatedAt = null,
                lastSyncedContentHash = null,
                title = "${note.title.ifBlank { "Untitled" }} (remote copy)",
                content = remote,
                needsSync = true,
                conflictState = null,
                conflictedRemoteContent = null,
                conflictedRemoteModifiedAt = null,
                lastModifiedLocally = System.currentTimeMillis()
            )
        )
        resolveConflictKeepLocal(note)
        scanAndExtractTags(copyId.toInt(), remote)
        return copyId
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

    suspend fun getNoteHistory(note: NoteEntity): Result<List<NoteRevision>> {
        val token = tokenManager.getToken()
            ?: return Result.failure(Exception("No GitHub token saved. Please add one in Settings."))
        val formattedToken = "Bearer $token"
        return try {
            if (!note.repository.isNullOrBlank() && !note.path.isNullOrBlank()) {
                val (owner, repo) = parseRepoPath(note.repository).getOrElse { return Result.failure(it) }
                val response = githubApiService.getRepoCommitsForPath(
                    formattedToken,
                    owner,
                    repo,
                    note.path
                )
                if (!response.isSuccessful) {
                    return Result.failure(IOException("Failed to load history (${response.code()})"))
                }
                Result.success(
                    response.body().orEmpty().map { commit ->
                        NoteRevision(
                            id = commit.sha,
                            label = commit.sha.take(7),
                            committedAt = commit.commit?.author?.date,
                            summary = commit.commit?.message
                        )
                    }
                )
            } else {
                val gistId = note.gistId
                    ?: return Result.failure(Exception("This note has not been synced yet."))
                val response = githubApiService.getGist(formattedToken, gistId)
                if (!response.isSuccessful) {
                    return Result.failure(IOException("Failed to load history (${response.code()})"))
                }
                Result.success(
                    response.body()?.history.orEmpty().mapNotNull { entry ->
                        val version = entry.version ?: return@mapNotNull null
                        NoteRevision(
                            id = version,
                            label = version.take(7),
                            committedAt = entry.committedAt,
                            summary = entry.changeStatus?.let {
                                "+${it.additions ?: 0} -${it.deletions ?: 0}"
                            }
                        )
                    }
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getRevisionContent(note: NoteEntity, revisionId: String): Result<String> {
        val token = tokenManager.getToken()
            ?: return Result.failure(Exception("No GitHub token saved. Please add one in Settings."))
        val formattedToken = "Bearer $token"
        return try {
            if (!note.repository.isNullOrBlank() && !note.path.isNullOrBlank()) {
                val (owner, repo) = parseRepoPath(note.repository).getOrElse { return Result.failure(it) }
                val response = githubApiService.getRepoFileContent(
                    formattedToken,
                    owner,
                    repo,
                    encodeRepoPath(note.path),
                    revisionId
                )
                if (!response.isSuccessful) {
                    return Result.failure(IOException("Failed to load revision (${response.code()})"))
                }
                val encoded = response.body()?.content
                    ?: return Result.failure(IOException("Revision has no content."))
                Result.success(decodeBase64Text(encoded))
            } else {
                val gistId = note.gistId
                    ?: return Result.failure(Exception("This note has not been synced yet."))
                val response = githubApiService.getGistRevision(formattedToken, gistId, revisionId)
                if (!response.isSuccessful) {
                    return Result.failure(IOException("Failed to load revision (${response.code()})"))
                }
                val file = response.body()?.files?.values?.firstOrNull {
                    it.filename?.endsWith(".md", ignoreCase = true) == true
                }
                Result.success(file?.content.orEmpty())
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun restoreRevision(note: NoteEntity, revisionId: String): Result<Unit> {
        return getRevisionContent(note, revisionId).mapCatching { content ->
            val updated = note.copy(
                content = content,
                needsSync = true,
                conflictState = null,
                conflictedRemoteContent = null,
                conflictedRemoteModifiedAt = null,
                lastModifiedLocally = System.currentTimeMillis()
            )
            updateNote(updated)
        }
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

    private fun hashContent(content: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(content.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private suspend fun markConflict(
        note: NoteEntity,
        remoteContent: String,
        remoteModifiedAt: Long = System.currentTimeMillis(),
        remoteSha: String? = null
    ) {
        noteDao.markConflict(
            id = note.id,
            sha = remoteSha ?: note.sha,
            remoteContent = remoteContent,
            remoteModifiedAt = remoteModifiedAt
        )
    }

    private fun decodeBase64Text(encoded: String): String =
        String(Base64.decode(encoded.replace("\n", ""), Base64.DEFAULT), Charsets.UTF_8)

    /** List "owner/repo" for every repository the token can access, across all pages. */
    suspend fun listAccessibleRepositories(): Result<List<String>> {
        val token = tokenManager.getToken()
            ?: return Result.failure(Exception("No GitHub token saved. Please add one in Settings."))
        val formattedToken = "Bearer $token"
        return try {
            val names = mutableListOf<String>()
            var page = 1
            while (page <= MAX_REPO_PAGES) {
                val response = githubApiService.getUserRepos(formattedToken, page = page)
                if (!response.isSuccessful) {
                    if (page == 1) {
                        val hint = if (response.code() == 403) "token needs `repo` scope" else response.message()
                        return Result.failure(IOException("Couldn't list repositories (${response.code()}): $hint"))
                    }
                    break  // partial results are still useful
                }
                val batch = response.body().orEmpty()
                names += batch.map { it.fullName }
                if (batch.size < 100) break  // last page
                page++
            }
            Result.success(names.distinct().sorted())
        } catch (e: Exception) {
            Result.failure(e)
        }
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

            val body = response.body()
            // GitHub caps a tree response and sets `truncated`. Carrying on
            // would look like a successful sync that had quietly dropped part
            // of the vault — and, worse, the missing files look locally-deleted.
            if (body?.truncated == true) {
                return Result.failure(
                    IOException("This repository is too large for a single sync (GitHub truncated the file list). Split the vault across repositories.")
                )
            }
            val entries = body?.tree ?: emptyList()
            val mdEntries = entries.filter { it.type == "blob" && it.path.endsWith(".md", ignoreCase = true) }

            for (entry in mdEntries) {
                val existing = noteDao.getNoteByRepoAndPath(repoPath, entry.path)

                // A trashed note stays trashed; re-syncing it would put content
                // back into a note the user has already thrown away.
                if (existing?.deletedAt != null) continue

                // The blob sha *is* the content hash, so an unchanged file with
                // no local edits needs no download at all. This is what keeps a
                // few-hundred-note vault from spending a few-hundred API calls
                // on every sync.
                if (existing != null && !existing.needsSync &&
                    existing.conflictState == null && existing.sha == entry.sha
                ) continue

                val blobResponse = githubApiService.getGitBlob(formattedToken, owner, repo, entry.sha)
                if (!blobResponse.isSuccessful) continue
                val encoded = blobResponse.body()?.content ?: continue
                val decoded = decodeBase64Text(encoded)
                // Title mirrors the app's PARA folder convention (path "/" -> "__").
                val title = entry.path.removeSuffix(".md").replace("/", "__")

                when (decidePull(existing, decoded)) {
                    // Sync writes through the DAO directly (it needs the exact
                    // insert/update semantics above), so it has to re-run tag
                    // extraction itself — insertNote()/updateNote() only cover
                    // notes created or edited from inside the app.
                    PullDecision.Insert -> {
                        val newId = noteDao.insert(
                            NoteEntity(
                                title = title,
                                content = decoded,
                                repository = repoPath,
                                path = entry.path,
                                sha = entry.sha,
                                needsSync = false,
                                lastSyncedContentHash = hashContent(decoded),
                                lastModifiedLocally = System.currentTimeMillis()
                            )
                        )
                        scanAndExtractTags(newId.toInt(), decoded)
                    }

                    PullDecision.Conflict ->
                        markConflict(existing!!, decoded, remoteSha = entry.sha)

                    PullDecision.AcceptRemote -> {
                        val applied = noteDao.applyRemoteContent(
                            id = existing!!.id,
                            title = title,
                            content = decoded,
                            sha = entry.sha,
                            remoteFilename = existing.remoteFilename,
                            remoteUpdatedAt = existing.remoteUpdatedAt,
                            contentHash = hashContent(decoded),
                            modifiedAt = System.currentTimeMillis(),
                        )
                        // 0 rows means the user started editing mid-pull; their
                        // text wins and the next push sends it.
                        if (applied > 0) scanAndExtractTags(existing.id, decoded)
                    }

                    // Locally edited, but the edit happens to match what the
                    // remote already has. Leave needsSync set so the push side
                    // clears it; touching the row here would discard the flag.
                    PullDecision.Skip -> Unit
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

                val response = put(note.sha)
                if (response.code() == 409 || response.code() == 422) {
                    val remote = githubApiService.getRepoFileContent(
                        formattedToken, owner, repo, encodedPath, null
                    )
                    val remoteBody = remote.body()
                    val remoteContent = remoteBody?.content?.let { decodeBase64Text(it) }
                    if (remoteContent != null) {
                        markConflict(note, remoteContent, remoteSha = remoteBody.sha)
                    }
                    return Result.failure(IOException("Conflict detected for ${note.title}. Resolve it in Sync Health."))
                }

                if (response.isSuccessful) {
                    val newSha = response.body()?.content?.sha
                    // Identity first and unconditionally — losing the new sha
                    // would make the next push a blind write.
                    noteDao.setRepoIdentity(note.id, rawPath, newSha)
                    noteDao.markSynced(
                        id = note.id,
                        remoteUpdatedAt = note.remoteUpdatedAt,
                        contentHash = hashContent(note.content),
                        unchangedSince = note.lastModifiedLocally,
                    )
                } else {
                    return Result.failure(IOException("Failed to upload ${note.title} (${response.code()})"))
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Delete a repo-backed note's file from GitHub.
     *
     * Success here authorises a permanent local delete, so it must mean "there
     * is definitely nothing left on GitHub" — not "we didn't manage to try".
     * Reporting success on a missing token or an unparseable repo name would
     * drop the row while the file was still in the repository, and the next
     * pull would bring the note straight back.
     */
    suspend fun deleteNoteFromRepository(note: NoteEntity): Result<Unit> {
        val repoPath = note.repository
        val path = note.path
        val sha = note.sha
        if (repoPath.isNullOrEmpty() || path.isNullOrEmpty() || sha.isNullOrEmpty()) {
            return Result.success(Unit)  // never synced remotely; nothing to delete
        }
        val token = tokenManager.getToken()
            ?: return Result.failure(IOException("No GitHub token saved."))
        val formattedToken = "Bearer $token"
        val (owner, repo) = parseRepoPath(repoPath).getOrElse { return Result.failure(it) }

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
            // Page through the whole list — everything past the first 100 Gists
            // used to be invisible to the app.
            val gists = mutableListOf<com.l3ad3r1.octojotter.data.remote.GistResponse>()
            var page = 1
            while (page <= MAX_GIST_PAGES) {
                val response = githubApiService.getGists(formattedToken, perPage = GISTS_PER_PAGE, page = page)
                if (!response.isSuccessful) {
                    if (page == 1) {
                        return Result.failure(IOException("GitHub API Error: ${response.code()} ${response.message()}"))
                    }
                    break  // partial results still beat none
                }
                val batch = response.body().orEmpty()
                gists += batch
                if (batch.size < GISTS_PER_PAGE) break
                page++
            }

            for (gist in gists) {
                val mdFileEntry = gist.files?.values?.firstOrNull { file ->
                    file.filename?.endsWith(".md", ignoreCase = true) == true
                } ?: continue

                val existingNote = noteDao.getNoteByGistId(gist.id)

                // Trashed stays trashed — don't pull content back into a note
                // the user has thrown away (and don't spend a request on it).
                if (existingNote?.deletedAt != null) continue

                // The list endpoint already tells us when the Gist last
                // changed, so an untouched Gist with no local edits needs no
                // detail fetch. This turns a sync over N Gists from N+1
                // requests into 1 in the common case.
                if (existingNote != null && !existingNote.needsSync &&
                    existingNote.conflictState == null &&
                    existingNote.remoteUpdatedAt != null &&
                    existingNote.remoteUpdatedAt == gist.updatedAt
                ) continue

                val fullGistResponse = githubApiService.getGist(formattedToken, gist.id)
                if (!fullGistResponse.isSuccessful) continue
                val fullGist = fullGistResponse.body()
                val fullMdFile = fullGist?.files?.get(mdFileEntry.filename)
                val content = fullMdFile?.content ?: ""
                val remoteFilename = mdFileEntry.filename
                val title = remoteFilename?.removeSuffix(".md") ?: "Untitled"
                val remoteUpdatedAt = fullGist?.updatedAt ?: gist.updatedAt

                if (existingNote == null) {
                    val newId = noteDao.insert(
                        NoteEntity(
                            gistId = gist.id,
                            title = title,
                            content = content,
                            remoteFilename = remoteFilename,
                            remoteUpdatedAt = remoteUpdatedAt,
                            lastSyncedContentHash = hashContent(content),
                            lastModifiedLocally = System.currentTimeMillis(),
                            needsSync = false
                        )
                    )
                    scanAndExtractTags(newId.toInt(), content)
                } else if (existingNote.needsSync && existingNote.content != content) {
                    markConflict(existingNote, content)
                } else if (!existingNote.needsSync) {
                    val applied = noteDao.applyRemoteContent(
                        id = existingNote.id,
                        title = title,
                        content = content,
                        sha = existingNote.sha,
                        remoteFilename = remoteFilename,
                        remoteUpdatedAt = remoteUpdatedAt,
                        contentHash = hashContent(content),
                        modifiedAt = System.currentTimeMillis(),
                    )
                    if (applied > 0) scanAndExtractTags(existingNote.id, content)
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
                if (note.conflictState == CONFLICT_STATE) continue
                val newFilename = "${note.title.ifBlank { "Untitled" }}.md"
                val contentHash = hashContent(note.content)

                // Creating a Gist: one file, named after the title.
                val createRequest = GistRequest(
                    description = "Gist Note: ${note.title}",
                    public = false,
                    files = mapOf(newFilename to GistFileRequest(content = note.content))
                )

                suspend fun storeCreated(created: com.l3ad3r1.octojotter.data.remote.GistResponse) {
                    noteDao.setGistIdentity(note.id, created.id, newFilename)
                    noteDao.markSynced(note.id, created.updatedAt, contentHash, note.lastModifiedLocally)
                }

                if (note.gistId.isNullOrEmpty()) {
                    val response = githubApiService.createGist(formattedToken, createRequest)
                    if (response.isSuccessful) {
                        response.body()?.let { storeCreated(it) }
                    } else {
                        return Result.failure(IOException("Failed to create Gist: ${response.code()} ${response.message()}"))
                    }
                } else {
                    val updateRequest = GistRequest(
                        description = "Gist Note: ${note.title}",
                        public = false,
                        files = gistFilesForPush(note.remoteFilename, newFilename, note.content)
                    )

                    val response = githubApiService.updateGist(formattedToken, note.gistId, updateRequest)
                    if (response.isSuccessful) {
                        noteDao.setGistIdentity(note.id, note.gistId, newFilename)
                        noteDao.markSynced(note.id, response.body()?.updatedAt, contentHash, note.lastModifiedLocally)
                    } else if (response.code() == 404) {
                        // The Gist was deleted on GitHub — recreate it.
                        val responseCreate = githubApiService.createGist(formattedToken, createRequest)
                        if (responseCreate.isSuccessful) {
                            responseCreate.body()?.let { storeCreated(it) }
                        } else {
                            return Result.failure(
                                IOException("Failed to recreate Gist: ${responseCreate.code()} ${responseCreate.message()}")
                            )
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

/**
 * What permanently deleting one trashed note requires.
 *
 * Split out of [NoteRepository.processPendingRemoteDeletes] for the same reason
 * as [decidePull]: it is the decision that can lose — or resurrect — a note,
 * and it should be testable without a network or a keystore. Only [DropRow]
 * authorises deleting the local row outright; the other two must confirm the
 * remote copy is gone first, because a row removed while its Gist or repo file
 * still exists is re-created by the very next pull.
 */
internal sealed interface PurgeDecision {
    /** Never synced anywhere. Nothing to delete remotely. */
    data object DropRow : PurgeDecision

    /** Backed by a Gist that has to go first. */
    data object DeleteGist : PurgeDecision

    /** Backed by a file in a GitHub repository that has to go first. */
    data object DeleteRepoFile : PurgeDecision
}

/** Pure rule for permanently deleting one note. See [PurgeDecision]. */
internal fun decidePurge(note: NoteEntity): PurgeDecision = when {
    // Repository wins: a repo-backed note is addressed by path, not Gist id.
    !note.repository.isNullOrEmpty() -> PurgeDecision.DeleteRepoFile
    !note.gistId.isNullOrEmpty() -> PurgeDecision.DeleteGist
    else -> PurgeDecision.DropRow
}

/**
 * Escape the LIKE metacharacters so a query is matched literally. Pairs with
 * the `ESCAPE '\'` clause on every LIKE in `NoteDao`; without it, searching for
 * "TODO_2" also matched "TODO-2" and "TODOx2", and a wikilink target containing
 * `_` linked back from every similarly-named note.
 */
internal fun escapeLike(raw: String): String =
    raw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

/**
 * The `files` map for a Gist update.
 *
 * The Gist API renames a file only when the entry is keyed by its *current*
 * remote filename and carries the new name in `filename`. Keying by the new
 * name instead leaves the old file in place and adds a second one — after
 * which a pull can pick the stale file and overwrite the note with its
 * pre-rename title and text. [oldFilename] is null for a note that has never
 * been pushed, where there is nothing to rename.
 */
internal fun gistFilesForPush(
    oldFilename: String?,
    newFilename: String,
    content: String,
): Map<String, GistFileRequest> {
    val currentName = oldFilename ?: newFilename
    return if (currentName == newFilename) {
        mapOf(newFilename to GistFileRequest(content = content))
    } else {
        mapOf(currentName to GistFileRequest(content = content, filename = newFilename))
    }
}

/**
 * What a repository pull should do with one remote file.
 *
 * Split out of [NoteRepository.pullFromRepository] so the decision can be
 * tested without a database, a network, or a keystore. The branch order is
 * load-bearing: a locally-edited note must be checked for conflict *before*
 * the clean-note case, or a pull would silently overwrite unsynced work.
 */
internal sealed interface PullDecision {
    /** No local row for this path yet — take the remote file as a new note. */
    data object Insert : PullDecision

    /** Edited on both sides. Keep both versions and let the user choose. */
    data object Conflict : PullDecision

    /** No local edits pending, so the remote copy is authoritative. */
    data object AcceptRemote : PullDecision

    /** Local edits pending, but identical to the remote. Nothing to do. */
    data object Skip : PullDecision
}

/** Pure reconciliation rule for a single file. See [PullDecision]. */
internal fun decidePull(existing: NoteEntity?, remoteContent: String): PullDecision = when {
    existing == null -> PullDecision.Insert
    existing.needsSync && existing.content != remoteContent -> PullDecision.Conflict
    !existing.needsSync -> PullDecision.AcceptRemote
    else -> PullDecision.Skip
}
