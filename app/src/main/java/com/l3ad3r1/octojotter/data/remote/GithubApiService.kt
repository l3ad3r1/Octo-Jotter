package com.l3ad3r1.octojotter.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
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
    @Json(name = "files") val files: Map<String, GistFile>?
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
}
