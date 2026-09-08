package com.l3ad3r1.octojotter

import com.l3ad3r1.octojotter.plugin.PluginManifest
import com.l3ad3r1.octojotter.plugin.PluginPermissions
import com.l3ad3r1.octojotter.plugin.PluginTypes
import com.l3ad3r1.octojotter.plugin.RegistryIndex
import com.l3ad3r1.octojotter.plugin.ScriptEngine
import com.l3ad3r1.octojotter.plugin.undeclaredPermissions
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * The community plugins published from `plugins/` in this repo.
 *
 * A broken plugin fails *silently*: `ScriptEngine.reload` skips anything that
 * throws, so a syntax error ships as "the command just isn't there" with no
 * error anywhere. And a registry entry whose permissions disagree with its
 * manifest is refused outright by `install()`. Neither shows up until a user
 * installs it, by which point the manifest is already on `main` and being
 * served to everyone.
 *
 * So this reads the actual files, checks what `install()` will check, and then
 * **executes every script plugin in the real sandbox** to prove its commands
 * register and run.
 */
class CommunityPluginRegistryTest {

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val registryAdapter = moshi.adapter(RegistryIndex::class.java)
    private val manifestAdapter = moshi.adapter(PluginManifest::class.java)

    /** Unit tests run with the module dir (`app/`) as CWD; `plugins/` is beside it. */
    private val pluginsDir = File("../plugins").let {
        if (it.isDirectory) it else File("plugins")
    }

    private fun registry(): List<com.l3ad3r1.octojotter.plugin.RegistryEntry> {
        val json = File(pluginsDir, "registry.json").readText(Charsets.UTF_8)
        return requireNotNull(registryAdapter.fromJson(json)) {
            "plugins/registry.json is not parseable as a RegistryIndex"
        }.plugins
    }

    private fun manifestOf(id: String): PluginManifest {
        val f = File(pluginsDir, "$id/manifest.json")
        assertTrue("plugins/$id/manifest.json is missing", f.isFile)
        return requireNotNull(manifestAdapter.fromJson(f.readText(Charsets.UTF_8))) {
            "plugins/$id/manifest.json is not parseable as a PluginManifest"
        }
    }

    /** Run [block] on a throwaway daemon thread; null if it doesn't finish in time. */
    private fun <T> bounded(block: suspend () -> T): T? {
        val done = ArrayBlockingQueue<Result<T>>(1)
        Thread({ done.offer(runCatching { runBlocking { block() } }) }, "community-plugin-probe")
            .apply { isDaemon = true }.start()
        return done.poll(45_000, TimeUnit.MILLISECONDS)?.getOrThrow()
    }

    @Test
    fun `the registry parses and every id is unique`() {
        val ids = registry().map { it.id }
        assertEquals("duplicate plugin id in registry.json", ids.distinct().size, ids.size)
        assertTrue("registry is empty", ids.isNotEmpty())
    }

    @Test
    fun `every entry has a manifest that exists, parses and agrees with it`() {
        registry().forEach { entry ->
            val m = manifestOf(entry.id)
            assertEquals("id disagrees for ${entry.id}", entry.id, m.id)
            assertEquals("type disagrees for ${entry.id}", entry.type, m.type)
            assertEquals("version disagrees for ${entry.id}", entry.version, m.version)
        }
    }

    @Test
    fun `no plugin asks for more permission than its listing shows the user`() {
        // Exactly the check install() runs. A mismatch is not cosmetic: the
        // install is refused.
        registry().forEach { entry ->
            assertEquals(
                "${entry.id} would be refused by install()",
                emptyList<String>(),
                undeclaredPermissions(entry.permissions, manifestOf(entry.id).permissions),
            )
        }
    }

    @Test
    fun `declared permissions are ones the app understands`() {
        val known = setOf(PluginPermissions.NOTES_READ, PluginPermissions.NOTES_WRITE)
        registry().forEach { entry ->
            assertEquals(
                "${entry.id} declares a permission the consent dialog can't describe",
                emptyList<String>(),
                entry.permissions.filterNot { it in known },
            )
        }
    }

    @Test
    fun `manifestUrl points at the plugin's own manifest over https`() {
        registry().forEach { entry ->
            assertTrue(
                "${entry.id} must be served over HTTPS, got ${entry.manifestUrl}",
                entry.manifestUrl.startsWith("https://"),
            )
            assertTrue(
                "${entry.id}'s manifestUrl does not point at plugins/${entry.id}/manifest.json",
                entry.manifestUrl.endsWith("/plugins/${entry.id}/manifest.json"),
            )
        }
    }

    @Test
    fun `every script plugin loads and registers at least one command`() {
        // The one that matters most: ScriptEngine.reload swallows a throwing
        // plugin, so a syntax error is invisible until someone installs it and
        // wonders where the command went.
        val scripts = registry().filter { it.type == PluginTypes.SCRIPT }
        assertTrue("expected some script plugins", scripts.isNotEmpty())

        scripts.forEach { entry ->
            val m = manifestOf(entry.id)
            val source = requireNotNull(m.main) { "${entry.id} is type=script but has no `main`" }
            val engine = ScriptEngine()
            val loaded = bounded {
                engine.reload(
                    listOf(
                        ScriptEngine.PluginSpec(
                            id = entry.id,
                            source = source,
                            permissions = m.permissions.toSet(),
                        )
                    )
                )
            }
            assertNotNull("${entry.id} did not finish loading", loaded)
            val commands = bounded { engine.commands() }
            assertTrue(
                "${entry.id} registered no commands — it probably threw while evaluating",
                !commands.isNullOrEmpty(),
            )
        }
    }

    @Test
    fun `permission-free script commands run and return text`() {
        // Commands needing notes:read/write require a PluginHost, which is the
        // app. The ones that only transform their input can be executed here,
        // which catches a runtime error inside the command body — not just a
        // parse error at load.
        val sample = listOf(
            "# Title",
            "",
            "Some prose with a #tag and a [[Wikilink]].",
            "",
            "| a | b |",
            "| --- | ---: |",
            "| 1 | 2 |",
            "",
            "## Section",
            "- [ ] todo",
        ).joinToString("\n")

        registry()
            .filter { it.type == PluginTypes.SCRIPT && it.permissions.isEmpty() }
            .forEach { entry ->
                val m = manifestOf(entry.id)
                val engine = ScriptEngine()
                bounded {
                    engine.reload(
                        listOf(
                            ScriptEngine.PluginSpec(entry.id, m.main!!, emptySet())
                        )
                    )
                }
                val commands = bounded { engine.commands() }.orEmpty()
                commands.forEach { cmd ->
                    val result = bounded { engine.run(entry.id, cmd.id, sample) }
                    assertNotNull("${entry.id}/${cmd.id} never returned", result)
                    assertTrue(
                        "${entry.id}/${cmd.id} failed: ${result!!.exceptionOrNull()?.message}",
                        result.isSuccess,
                    )
                    assertTrue(
                        "${entry.id}/${cmd.id} returned nothing",
                        !result.getOrNull().isNullOrBlank(),
                    )
                }
            }
    }

    @Test
    fun `theme plugins declare colours and snippet plugins declare snippets`() {
        registry().forEach { entry ->
            val m = manifestOf(entry.id)
            when (entry.type) {
                PluginTypes.THEME -> assertTrue(
                    "${entry.id} is type=theme but declares no colours",
                    (m.theme?.colors?.size ?: 0) > 0,
                )
                PluginTypes.SNIPPET -> assertTrue(
                    "${entry.id} is type=snippet but declares no snippets",
                    !m.snippets.isNullOrEmpty(),
                )
            }
        }
    }

    @Test
    fun `theme colours are valid hex`() {
        val hex = Regex("^#([0-9a-fA-F]{6}|[0-9a-fA-F]{8})$")
        registry().filter { it.type == PluginTypes.THEME }.forEach { entry ->
            manifestOf(entry.id).theme?.colors?.forEach { (slot, value) ->
                assertTrue(
                    "${entry.id}.$slot = \"$value\" is not #RRGGBB or #AARRGGBB",
                    hex.matches(value),
                )
            }
        }
    }
}
