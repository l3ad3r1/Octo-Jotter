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
            val manifestJson = httpGet(entry.manifestUrl)
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
    suspend fun migrateExistingUsageToInstalled(githubInUse: Boolean, aiInUse: Boolean) =
        withContext(Dispatchers.IO) {
            if (githubInUse) installBuiltinFeature(FeaturePluginIds.GITHUB_SYNC)
            if (aiInUse) installBuiltinFeature(FeaturePluginIds.ON_DEVICE_AI)
        }

    /** Local browse entries for the two built-in feature plugins — never
     *  fetched over the network, filtered out once installed the same way a
     *  community registry entry is (see installedById in the Browse list). */
    fun builtinFeatureEntries(): List<RegistryEntry> = listOf(
        RegistryEntry(
            id = FeaturePluginIds.GITHUB_SYNC,
            name = "GitHub Sync",
            author = "Octo Jotter",
            description = "Sync notes to a private Gist or a whole GitHub repository.",
            type = PluginTypes.FEATURE,
            version = "1.0",
            manifestUrl = "",
        ),
        RegistryEntry(
            id = FeaturePluginIds.ON_DEVICE_AI,
            name = "On-device AI",
            author = "Octo Jotter",
            description = "Chat and semantic search over your notes, running entirely on your phone.",
            type = PluginTypes.FEATURE,
            version = "1.0",
            manifestUrl = "",
        ),
        RegistryEntry(
            id = FeaturePluginIds.DAILY_NOTES,
            name = "Daily Notes",
            author = "Octo Jotter",
            description = "One tap opens (or creates) today's note.",
            type = PluginTypes.FEATURE,
            version = "1.0",
            manifestUrl = "",
        ),
        RegistryEntry(
            id = FeaturePluginIds.TEMPLATES,
            name = "Templates",
            author = "Octo Jotter",
            description = "Reusable note templates with {{date}}, {{time}}, and {{title}} variables.",
            type = PluginTypes.FEATURE,
            version = "1.0",
            manifestUrl = "",
        ),
        RegistryEntry(
            id = FeaturePluginIds.TASK_REMINDERS,
            name = "Task Reminders",
            author = "Octo Jotter",
            description = "Set a reminder on any note and get a notification when it's due.",
            type = PluginTypes.FEATURE,
            version = "1.0",
            manifestUrl = "",
        ),
        RegistryEntry(
            id = FeaturePluginIds.GRAPH_VIEW,
            name = "Graph View",
            author = "Octo Jotter",
            description = "Visualize how your notes connect through [[wikilinks]].",
            type = PluginTypes.FEATURE,
            version = "1.0",
            manifestUrl = "",
        ),
        RegistryEntry(
            id = FeaturePluginIds.OCR_SCAN,
            name = "Scan Text (OCR)",
            author = "Octo Jotter",
            description = "Capture a photo and pull its text into a note — runs on-device.",
            type = PluginTypes.FEATURE,
            version = "1.0",
            manifestUrl = "",
        ),
        RegistryEntry(
            id = FeaturePluginIds.COMMAND_PALETTE,
            name = "Command Palette",
            author = "Octo Jotter",
            description = "A quick-action search for jumping to notes and running commands.",
            type = PluginTypes.FEATURE,
            version = "1.0",
            manifestUrl = "",
        ),
    )

    /** Install a built-in feature plugin by id — no network, no manifest fetch. */
    suspend fun installBuiltinFeature(id: String) = withContext(Dispatchers.IO) {
        val entry = builtinFeatureEntries().firstOrNull { it.id == id } ?: return@withContext
        pluginDao.upsert(
            PluginEntity(
                id = entry.id,
                name = entry.name,
                version = entry.version ?: "1.0",
                type = PluginTypes.FEATURE,
                author = entry.author,
                description = entry.description,
                enabled = true,
                sourceUrl = null,
                payloadJson = "{}",
                permissions = emptyList()
            )
        )
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
