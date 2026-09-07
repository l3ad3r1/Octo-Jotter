package com.l3ad3r1.octojotter

import androidx.test.core.app.ApplicationProvider
import com.l3ad3r1.octojotter.plugin.ASSET_URL_PREFIX
import com.l3ad3r1.octojotter.plugin.FeaturePluginIds
import com.l3ad3r1.octojotter.plugin.PluginManifest
import com.l3ad3r1.octojotter.plugin.PluginPermissions
import com.l3ad3r1.octojotter.plugin.PluginTypes
import com.l3ad3r1.octojotter.plugin.RegistryIndex
import com.l3ad3r1.octojotter.plugin.undeclaredPermissions
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The plugins the app ships compiled in are described by
 * `assets/plugins/registry.json` plus one manifest each, in the same schema the
 * community registry uses, and they install through the same code path.
 *
 * They used to be a hardcoded `List<RegistryEntry>` in `PluginRepository` whose
 * install skipped the manifest, the version check and the consent dialog
 * entirely — a "feature plugin" was a plugin in name only. Because they now go
 * through `install()`, a mistake in these JSON files is no longer cosmetic: it
 * makes the feature fail to install, on a screen with no network to blame. This
 * suite reads the real bundled assets and checks them the way `install()` will.
 */
@RunWith(RobolectricTestRunner::class)
class BuiltinPluginRegistryTest {

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val registryAdapter = moshi.adapter(RegistryIndex::class.java)
    private val manifestAdapter = moshi.adapter(PluginManifest::class.java)

    private fun asset(path: String): String =
        ApplicationProvider.getApplicationContext<android.app.Application>()
            .assets.open(path).use { it.readBytes().toString(Charsets.UTF_8) }

    private fun registry() = requireNotNull(registryAdapter.fromJson(asset("plugins/registry.json"))) {
        "assets/plugins/registry.json is not parseable as a RegistryIndex"
    }.plugins

    @Test
    fun `the bundled registry parses and covers every declared feature id`() {
        val ids = registry().map { it.id }.toSet()
        val declared = setOf(
            FeaturePluginIds.GITHUB_SYNC, FeaturePluginIds.ON_DEVICE_AI,
            FeaturePluginIds.DAILY_NOTES, FeaturePluginIds.TEMPLATES,
            FeaturePluginIds.TASK_REMINDERS, FeaturePluginIds.GRAPH_VIEW,
            FeaturePluginIds.OCR_SCAN, FeaturePluginIds.COMMAND_PALETTE,
        )
        // A FeaturePluginIds constant with no registry entry is a feature the
        // UI gates on but that can never be installed.
        assertEquals(declared, ids)
    }

    @Test
    fun `every entry points at a bundled manifest that exists and parses`() {
        registry().forEach { entry ->
            assertTrue(
                "${entry.id} must use the asset: scheme — a built-in cannot depend on the network",
                entry.manifestUrl.startsWith(ASSET_URL_PREFIX),
            )
            val path = entry.manifestUrl.removePrefix(ASSET_URL_PREFIX)
            val manifest = manifestAdapter.fromJson(asset(path))
            assertNotNull("$path is not parseable as a PluginManifest", manifest)
            assertEquals("id disagrees between registry and manifest", entry.id, manifest!!.id)
            assertEquals(PluginTypes.FEATURE, manifest.type)
        }
    }

    @Test
    fun `no built-in asks for more permission than its listing shows the user`() {
        // The exact check install() runs. If this fails, the feature does not
        // install at all — the consent fix refuses it.
        registry().forEach { entry ->
            val manifest = manifestAdapter.fromJson(
                asset(entry.manifestUrl.removePrefix(ASSET_URL_PREFIX))
            )!!
            assertEquals(
                "${entry.id} would be refused by install(): manifest asks for more than the listing declares",
                emptyList<String>(),
                undeclaredPermissions(entry.permissions, manifest.permissions),
            )
        }
    }

    @Test
    fun `declared permissions are ones the app actually understands`() {
        val known = setOf(PluginPermissions.NOTES_READ, PluginPermissions.NOTES_WRITE)
        registry().forEach { entry ->
            val unknown = entry.permissions.filterNot { it in known }
            assertEquals(
                "${entry.id} declares a permission with no meaning, so the consent dialog " +
                    "would show the user a raw string",
                emptyList<String>(),
                unknown,
            )
        }
    }

    @Test
    fun `on-device AI discloses that it reads every note`() {
        // It builds an embedding index over the full text of every unlocked
        // note. That was previously silent — it installed with no dialog at
        // all, because built-ins skipped consent.
        val ai = registry().single { it.id == FeaturePluginIds.ON_DEVICE_AI }
        assertTrue(
            "On-device AI must disclose notes:read",
            PluginPermissions.NOTES_READ in ai.permissions,
        )
    }

    @Test
    fun `no built-in sets a minAppVersion floor above the current build`() {
        // A built-in shipping inside the APK that refuses to install on that
        // same APK would be unreachable.
        registry().forEach { entry ->
            val manifest = manifestAdapter.fromJson(
                asset(entry.manifestUrl.removePrefix(ASSET_URL_PREFIX))
            )!!
            assertTrue(
                "${entry.id} declares minAppVersion=${manifest.minAppVersion}; a bundled " +
                    "plugin should not gate itself on a future release",
                manifest.minAppVersion.isNullOrBlank(),
            )
        }
    }
}
