package com.l3ad3r1.octojotter.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.HTTP
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

@JsonClass(generateAdapter = true)
data class GistFile(
    @Json(name = "filename") val filename: String?,
    @Json(name = "type") val type: String?,
    @Json(name = "language") val language: String?,
    @Json(name = "raw_url") val rawUrl: String?,
    @Json(name = "size") val size: Int?,
    @Json(name = "content") val content: String?
)

@JsonClass(generateAdapter = true)
data class GistResponse(
    @Json(name = "id") val id: String,
    @Json(name = "description") val description: String?,
    @Json(name = "updated_at") val updatedAt: String?,
    @Json(name = "files") val files: Map<String, GistFile>?,
    @Json(name = "history") val history: List<GistHistoryEntry>? = null
)

@JsonClass(generateAdapter = true)
data class GistHistoryEntry(
    @Json(name = "version") val version: String?,
    @Json(name = "committed_at") val committedAt: String?,
    @Json(name = "change_status") val changeStatus: GistChangeStatus?
)

@JsonClass(generateAdapter = true)
data class GistChangeStatus(
    @Json(name = "total") val total: Int?,
    @Json(name = "additions") val additions: Int?,
    @Json(name = "deletions") val deletions: Int?
)

@JsonClass(generateAdapter = true)
data class GistRequest(
    @Json(name = "description") val description: String?,
    @Json(name = "public") val public: Boolean = false,
    @Json(name = "files") val files: Map<String, GistFileRequest>
)

@JsonClass(generateAdapter = true)
data class GistFileRequest(
    @Json(name = "content") val content: String,
    @Json(name = "filename") val filename: String? = null
)

@JsonClass(generateAdapter = true)
data class ReleaseAsset(
    @Json(name = "name") val name: String?,
    @Json(name = "content_type") val contentType: String?,
    @Json(name = "browser_download_url") val downloadUrl: String?
)

@JsonClass(generateAdapter = true)
data class ReleaseResponse(
    @Json(name = "tag_name") val tagName: String?,
    @Json(name = "name") val name: String?,
    @Json(name = "html_url") val htmlUrl: String?,
    @Json(name = "body") val body: String?,
    @Json(name = "assets") val assets: List<ReleaseAsset>?
)

interface GithubApiService {
    @GET("gists")
    suspend fun getGists(
        @Header("Authorization") token: String,
        @Query("per_page") perPage: Int = 100
    ): Response<List<GistResponse>>

    @GET("gists/{gist_id}")
    suspend fun getGist(
        @Header("Authorization") token: String,
        @Path("gist_id") gistId: String
    ): Response<GistResponse>

    @GET("gists/{gist_id}/{sha}")
    suspend fun getGistRevision(
        @Header("Authorization") token: String,
        @Path("gist_id") gistId: String,
        @Path("sha") sha: String
    ): Response<GistResponse>

    @POST("gists")
    suspend fun createGist(
        @Header("Authorization") token: String,
        @Body request: GistRequest
    ): Response<GistResponse>

    @PATCH("gists/{gist_id}")
    suspend fun updateGist(
        @Header("Authorization") token: String,
        @Path("gist_id") gistId: String,
        @Body request: GistRequest
    ): Response<GistResponse>

    @DELETE("gists/{gist_id}")
    suspend fun deleteGist(
        @Header("Authorization") token: String,
        @Path("gist_id") gistId: String
    ): Response<Unit>

    // Latest published GitHub Release — used by the in-app updater. No auth
    // needed for a public repo.
    @GET("repos/{owner}/{repo}/releases/latest")
    suspend fun getLatestRelease(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): Response<ReleaseResponse>

    // List repositories the authenticated user owns (includes private repos
    // when the token has the `repo` scope). Used for repo discovery in Settings.
    @GET("user/repos")
    suspend fun getUserRepos(
        @Header("Authorization") token: String,
        @Query("per_page") perPage: Int = 100,
        @Query("page") page: Int = 1,
        // Include repos you own, collaborate on, or can access via an org — the
        // old "owner"-only default hid collaborator/org repos.
        @Query("affiliation") affiliation: String = "owner,collaborator,organization_member",
        @Query("sort") sort: String = "full_name"
    ): Response<List<RepoSummary>>

    // --- Repository (Contents / Git Data) API for two-way repo sync ---

    // Recursive git tree for a branch; lists every blob (file) in the repo.
    @GET("repos/{owner}/{repo}/git/trees/{branch}")
    suspend fun getGitTree(
        @Header("Authorization") token: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("branch") branch: String,
        @Query("recursive") recursive: Int = 1
    ): Response<GitTreeResponse>

    // Fetch a single blob by SHA (content is base64-encoded).
    @GET("repos/{owner}/{repo}/git/blobs/{sha}")
    suspend fun getGitBlob(
        @Header("Authorization") token: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("sha") sha: String
    ): Response<GitBlobResponse>

    // Create or update a file. Omit `sha` to create; include it to update.
    @PUT("repos/{owner}/{repo}/contents/{path}")
    suspend fun createOrUpdateFile(
        @Header("Authorization") token: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path(value = "path", encoded = true) path: String,
        @Body request: PutContentRequest
    ): Response<PutContentResponse>

    // Delete a file (DELETE with a body carrying the message + sha).
    @HTTP(method = "DELETE", path = "repos/{owner}/{repo}/contents/{path}", hasBody = true)
    suspend fun deleteRepoFile(
        @Header("Authorization") token: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path(value = "path", encoded = true) path: String,
        @Body request: DeleteContentRequest
    ): Response<Unit>

    @GET("repos/{owner}/{repo}/commits")
    suspend fun getRepoCommitsForPath(
        @Header("Authorization") token: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("path") path: String,
        @Query("per_page") perPage: Int = 30
    ): Response<List<RepoCommitResponse>>

    @GET("repos/{owner}/{repo}/contents/{path}")
    suspend fun getRepoFileContent(
        @Header("Authorization") token: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path(value = "path", encoded = true) path: String,
        @Query("ref") ref: String? = null
    ): Response<RepoContentResponse>
}

// --- Repository API models ---

@JsonClass(generateAdapter = true)
data class RepoSummary(
    @Json(name = "full_name") val fullName: String,
    @Json(name = "private") val private: Boolean?,
    @Json(name = "default_branch") val defaultBranch: String?
)

@JsonClass(generateAdapter = true)
data class RepoCommitResponse(
    @Json(name = "sha") val sha: String,
    @Json(name = "commit") val commit: RepoCommitInfo?
)

@JsonClass(generateAdapter = true)
data class RepoCommitInfo(
    @Json(name = "message") val message: String?,
    @Json(name = "author") val author: RepoCommitAuthor?
)

@JsonClass(generateAdapter = true)
data class RepoCommitAuthor(
    @Json(name = "date") val date: String?
)

@JsonClass(generateAdapter = true)
data class RepoContentResponse(
    @Json(name = "name") val name: String?,
    @Json(name = "path") val path: String?,
    @Json(name = "sha") val sha: String?,
    @Json(name = "content") val content: String?,
    @Json(name = "encoding") val encoding: String?
)

@JsonClass(generateAdapter = true)
data class GitTreeResponse(
    @Json(name = "sha") val sha: String?,
    @Json(name = "tree") val tree: List<GitTreeEntry>?,
    @Json(name = "truncated") val truncated: Boolean?
)

@JsonClass(generateAdapter = true)
data class GitTreeEntry(
    @Json(name = "path") val path: String,
    @Json(name = "mode") val mode: String?,
    @Json(name = "type") val type: String,   // "blob" or "tree"
    @Json(name = "sha") val sha: String,
    @Json(name = "size") val size: Int?
)

@JsonClass(generateAdapter = true)
data class GitBlobResponse(
    @Json(name = "sha") val sha: String?,
    @Json(name = "size") val size: Int?,
    @Json(name = "content") val content: String?,   // base64
    @Json(name = "encoding") val encoding: String?
)

@JsonClass(generateAdapter = true)
data class PutContentRequest(
    @Json(name = "message") val message: String,
    @Json(name = "content") val content: String,     // base64
    @Json(name = "sha") val sha: String? = null,
    @Json(name = "branch") val branch: String? = null
)

@JsonClass(generateAdapter = true)
data class PutContentResponse(
    @Json(name = "content") val content: ContentInfo?,
    @Json(name = "commit") val commit: CommitInfo?
)

@JsonClass(generateAdapter = true)
data class ContentInfo(
    @Json(name = "name") val name: String?,
    @Json(name = "path") val path: String?,
    @Json(name = "sha") val sha: String?
)

@JsonClass(generateAdapter = true)
data class CommitInfo(
    @Json(name = "sha") val sha: String?,
    @Json(name = "message") val message: String?
)

@JsonClass(generateAdapter = true)
data class DeleteContentRequest(
    @Json(name = "message") val message: String,
    @Json(name = "sha") val sha: String,
    @Json(name = "branch") val branch: String? = null
)
