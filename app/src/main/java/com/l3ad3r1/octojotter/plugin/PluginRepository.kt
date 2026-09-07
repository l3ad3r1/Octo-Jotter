package com.l3ad3r1.octojotter.plugin

import android.content.res.AssetManager
import android.util.Log
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
 * Permissions a plugin's manifest asks for that its registry listing never
 * declared — and that the user was therefore never shown.
 *
 * Consent is collected against the registry entry; capability is granted from
 * the manifest, a separate file fetched from a URL the entry names. Nothing
 * kept the two in agreement, so a plugin listing no permissions installed with
 * no consent dialog at all and still received whatever its manifest asked for.
 */
internal fun undeclaredPermissions(consented: List<String>, requested: List<String>): List<String> =
    requested.distinct().filterNot { it in consented }

/** The permissions a plugin actually gets: never more than the user agreed to. */
internal fun grantedPermissions(consented: List<String>, requested: List<String>): List<String> =
    requested.distinct().filter { it in consented }

/** Marks a manifest bundled in the APK rather than fetched. See [PluginRepository.fetch]. */
internal const val ASSET_URL_PREFIX = "asset:"

/**
 * Installs, enables and removes plugins — community and built-in alike.
 *
 * Registry and manifests are plain JSON. A community plugin's are fetched over
 * HTTPS from public GitHub raw URLs (no token needed); a built-in's are bundled
 * in the APK under `assets/plugins/` and addressed with the [ASSET_URL_PREFIX]
 * scheme, because a compiled-in capability must not stop working offline or
 * when GitHub is unreachable. That is the *only* difference between the two —
 * everything downstream of [fetch] is one code path.
 *
 * The full manifest is stored on install so plugins keep working offline.
 */
class PluginRepository(
    private val pluginDao: PluginDao,
    private val assets: AssetManager,
) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val registryAdapter = moshi.adapter(RegistryIndex::class.java)
    private val manifestAdapter = moshi.adapter(PluginManifest::class.java)

    /** Bundled registry, parsed once — it is a file in the APK. */
    @Volatile private var builtinCache: List<RegistryEntry>? = null

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

    /** Fetch a registry index — the community one over HTTPS, or a bundled one. */
    suspend fun fetchRegistry(url: String = DEFAULT_REGISTRY_URL): Result<List<RegistryEntry>> =
        withContext(Dispatchers.IO) {
            try {
                val body = fetch(url)
                val index = registryAdapter.fromJson(body)
                    ?: return@withContext Result.failure(IOException("Malformed plugin registry."))
                Result.success(index.plugins)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Download a plugin's manifest and store it locally (disabled until enabled).
     *
     * The permissions the user is shown before installing come from the
     * registry entry; the permissions a plugin can actually use come from its
     * manifest, which is a *different file* fetched from a URL the registry
     * names. Granting the manifest's list unchecked meant a plugin whose
     * registry entry declared no permissions — and so installed with no consent
     * dialog at all — could still hand itself `notes:read`/`notes:write`. The
     * install is refused unless the manifest asks for no more than the entry
     * the user agreed to.
     */
    suspend fun install(entry: RegistryEntry, appVersion: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val manifestJson = fetch(entry.manifestUrl)
            val manifest = manifestAdapter.fromJson(manifestJson)
                ?: return@withContext Result.failure(IOException("Malformed plugin manifest."))
            if (!meetsMinVersion(manifest.minAppVersion, appVersion)) {
                return@withContext Result.failure(
                    IOException("${manifest.name} needs Octo Jotter ${manifest.minAppVersion}+ (you have $appVersion). Please update the app.")
                )
            }
            val undeclared = undeclaredPermissions(entry.permissions, manifest.permissions)
            if (undeclared.isNotEmpty()) {
                return@withContext Result.failure(
                    IOException(
                        "${manifest.name} asks for permissions its listing didn't declare " +
                            "(${undeclared.joinToString()}). Install refused."
                    )
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
                    // Intersect rather than trust: the granted set can only ever
                    // be what the consent dialog actually listed.
                    permissions = grantedPermissions(entry.permissions, manifest.permissions)
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

    /**
     * GitHub Sync and On-device AI are downloadable feature plugins — they
     * start in Browse, not pre-installed, same as a community theme. This
     * only exists for the upgrade path: a device that already has a GitHub
     * token saved, or an AI model already on disk, was using these features
     * before the plugin system existed to gate them. Silently uninstalling
     * something already in active use on upgrade would look like sync or AI
     * broke, so this installs (and enables) it once, matching the state the
     * device was already in. A fresh install where neither is true never
     * calls this with `true` — those stay in Browse until the user installs.
     */
    suspend fun migrateExistingUsageToInstalled(githubInUse: Boolean, aiInUse: Boolean, appVersion: String) =
        withContext(Dispatchers.IO) {
            if (githubInUse) installAndEnableBuiltin(FeaturePluginIds.GITHUB_SYNC, appVersion)
            if (aiInUse) installAndEnableBuiltin(FeaturePluginIds.ON_DEVICE_AI, appVersion)
        }

    /**
     * Browse entries for the plugins the app ships compiled in.
     *
     * These used to be a hardcoded `List<RegistryEntry>` right here, and
     * installing one took its own path that skipped the manifest, the version
     * check and the consent dialog — so a "feature plugin" was a plugin in name
     * only. They are now described by `assets/plugins/registry.json` in exactly
     * the schema the community registry uses, and install through
     * [install] like anything else.
     *
     * Read once and cached: it is a bundled file, it cannot change at runtime,
     * and Browse asks for it on every open.
     */
    suspend fun builtinFeatureEntries(): List<RegistryEntry> {
        builtinCache?.let { return it }
        return fetchRegistry(BUILTIN_REGISTRY_URL)
            .getOrElse {
                // A missing or malformed bundled registry is a packaging bug, not
                // a runtime condition. Surfacing it as an empty Browse list is
                // more useful than crashing on a screen the user just opened.
                Log.e(TAG, "Bundled plugin registry unreadable", it)
                emptyList()
            }
            .also { builtinCache = it }
    }

    /**
     * Install a built-in and switch it on, for the upgrade path only.
     *
     * Ordinary installs land disabled, like every other plugin — the user
     * enables them. This exists solely so a device already using GitHub sync or
     * on-device AI before the plugin system gated them keeps working; see
     * [migrateExistingUsageToInstalled].
     */
    private suspend fun installAndEnableBuiltin(id: String, appVersion: String) {
        val entry = builtinFeatureEntries().firstOrNull { it.id == id } ?: return
        install(entry, appVersion).onSuccess {
            pluginDao.setEnabled(id, true)
        }
    }

    /**
     * Whether the built-in feature plugin [id] is installed and enabled.
     * False (not just "not enabled") when it was never installed at all —
     * unlike an always-on capability, a feature plugin that's still sitting
     * in Browse should read as off, not on-until-proven-otherwise.
     */
    fun isFeatureEnabled(id: String): Flow<Boolean> =
        installedPlugins.map { list -> list.firstOrNull { it.id == id }?.enabled ?: false }

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

    /**
     * Read a registry or manifest, wherever it lives.
     *
     * This is the single seam between a built-in and a community plugin. An
     * `asset:` URL resolves inside the APK, so a compiled-in capability
     * installs with no network at all; anything else goes over HTTPS. Both
     * return the same JSON to the same parser, which is what lets one install
     * path serve both.
     */
    private fun fetch(url: String): String =
        if (url.startsWith(ASSET_URL_PREFIX)) {
            val path = url.removePrefix(ASSET_URL_PREFIX)
            try {
                assets.open(path).use { it.readBytes().toString(Charsets.UTF_8) }
            } catch (e: IOException) {
                throw IOException("Bundled plugin asset missing: $path", e)
            }
        } else {
            httpGet(url)
        }

    private fun httpGet(url: String): String {
        val request = Request.Builder().url(url).header("Accept", "application/json").build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code} fetching $url")
            return resp.body?.string() ?: throw IOException("Empty response from $url")
        }
    }

    companion object {
        private const val TAG = "PluginRepository"

        /** The registry of compiled-in plugins, bundled in the APK. */
        const val BUILTIN_REGISTRY_URL = ASSET_URL_PREFIX + "plugins/registry.json"

        const val DEFAULT_REGISTRY_URL =
            "https://raw.githubusercontent.com/l3ad3r1/Octo-Jotter/main/plugins/registry.json"
    }
}
