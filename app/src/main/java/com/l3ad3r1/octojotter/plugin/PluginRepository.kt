package com.l3ad3r1.octojotter.plugin

import com.l3ad3r1.octojotter.data.local.PluginDao
import com.l3ad3r1.octojotter.data.local.PluginEntity
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Installs, enables and removes community plugins. Registry + manifests are
 * plain JSON fetched over HTTPS from public GitHub raw URLs (no token needed).
 * Phase 1 stores the full manifest so plugins keep working offline.
 */
class PluginRepository(private val pluginDao: PluginDao) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val registryAdapter = moshi.adapter(RegistryIndex::class.java)
    private val manifestAdapter = moshi.adapter(PluginManifest::class.java)

    val installedPlugins: Flow<List<PluginEntity>> = pluginDao.getAllPluginsFlow()

    // The single enabled theme plugin, if any (drives the app's active theme).
    val enabledThemePlugin: Flow<PluginEntity?> =
        pluginDao.getEnabledByTypeFlow(PluginTypes.THEME).map { it.firstOrNull() }

    // All enabled script plugins (each may register editor commands).
    val enabledScriptPlugins: Flow<List<PluginEntity>> =
        pluginDao.getEnabledByTypeFlow(PluginTypes.SCRIPT)

    // All enabled snippet plugins (each contributes insertable templates).
    val enabledSnippetPlugins: Flow<List<PluginEntity>> =
        pluginDao.getEnabledByTypeFlow(PluginTypes.SNIPPET)

    /** Fetch the community registry index. */
    suspend fun fetchRegistry(url: String = DEFAULT_REGISTRY_URL): Result<List<RegistryEntry>> =
        withContext(Dispatchers.IO) {
            try {
                val body = httpGet(url)
                val index = registryAdapter.fromJson(body)
                    ?: return@withContext Result.failure(IOException("Malformed plugin registry."))
                Result.success(index.plugins)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /** Download a plugin's manifest and store it locally (disabled until enabled). */
    suspend fun install(entry: RegistryEntry, appVersion: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val manifestJson = httpGet(entry.manifestUrl)
            val manifest = manifestAdapter.fromJson(manifestJson)
                ?: return@withContext Result.failure(IOException("Malformed plugin manifest."))
            if (!meetsMinVersion(manifest.minAppVersion, appVersion)) {
                return@withContext Result.failure(
                    IOException("${manifest.name} needs Octo Jotter ${manifest.minAppVersion}+ (you have $appVersion). Please update the app.")
                )
            }
            pluginDao.upsert(
                PluginEntity(
                    id = manifest.id,
                    name = manifest.name,
                    version = manifest.version,
                    type = manifest.type,
                    author = manifest.author,
                    description = manifest.description,
                    enabled = false,
                    sourceUrl = entry.manifestUrl,
                    payloadJson = manifestJson,
                    permissions = manifest.permissions
                )
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setEnabled(plugin: PluginEntity, enabled: Boolean) {
        pluginDao.setEnabled(plugin.id, enabled)
        // Only one theme can be active at a time.
        if (enabled && plugin.type == PluginTypes.THEME) {
            pluginDao.disableOthersOfType(PluginTypes.THEME, plugin.id)
        }
    }

    suspend fun uninstall(plugin: PluginEntity) {
        pluginDao.deleteById(plugin.id)
    }

    fun parseManifest(plugin: PluginEntity): PluginManifest? =
        try { manifestAdapter.fromJson(plugin.payloadJson) } catch (e: Exception) { null }

    // True if [appVersion] >= [min] (dotted numeric compare); null/blank min = no floor.
    private fun meetsMinVersion(min: String?, appVersion: String): Boolean {
        if (min.isNullOrBlank()) return true
        fun parts(v: String) = v.split(".", "-").map { it.toIntOrNull() ?: 0 }
        val a = parts(appVersion)
        val m = parts(min)
        for (i in 0 until maxOf(a.size, m.size)) {
            val av = a.getOrElse(i) { 0 }
            val mv = m.getOrElse(i) { 0 }
            if (av != mv) return av > mv
        }
        return true
    }

    private fun httpGet(url: String): String {
        val request = Request.Builder().url(url).header("Accept", "application/json").build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code} fetching $url")
            return resp.body?.string() ?: throw IOException("Empty response from $url")
        }
    }

    companion object {
        const val DEFAULT_REGISTRY_URL =
            "https://raw.githubusercontent.com/l3ad3r1/Octo-Jotter/main/plugins/registry.json"
    }
}
