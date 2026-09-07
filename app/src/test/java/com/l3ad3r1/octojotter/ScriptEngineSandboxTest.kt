package com.l3ad3r1.octojotter

import com.l3ad3r1.octojotter.plugin.ScriptEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * The plugin sandbox's containment guarantees.
 *
 * The instruction budget was inert: Rhino resets its own counter after every
 * call into `observeInstructionCount`, so the value passed never rose much
 * above the 10,000 observer threshold and could never exceed the multi-million
 * budget it was compared against. `while(true){}` in a community plugin ran
 * forever, holding the engine lock. The budget now accumulates, with a
 * wall-clock deadline behind it.
 *
 * Every engine call goes through [bounded], which gives up after
 * [TEST_TIMEOUT_MS] and returns null. A regression therefore *fails* the suite
 * within a minute instead of wedging it — cancellation cannot interrupt a
 * running Rhino loop, so waiting on it directly would hang forever. The engine
 * thread is a daemon, so a stuck one never keeps the test JVM alive.
 */
class ScriptEngineSandboxTest {

    private companion object {
        /** Comfortably above the engine's own 2s budget, well under CI patience. */
        const val TEST_TIMEOUT_MS = 45_000L
    }

    /**
     * Run [block] on a throwaway daemon thread, returning null if it hasn't
     * finished within [TEST_TIMEOUT_MS].
     *
     * Deliberately *not* structured concurrency. Cancelling a coroutine cannot
     * interrupt a running Rhino loop, so `coroutineScope`/`withTimeout` would
     * still wait on the stuck child and the suite would hang — the exact
     * failure mode this helper exists to avoid. An orphaned daemon thread costs
     * a core until the test JVM exits, which is the right trade for a
     * regression that reports itself instead of wedging CI.
     */
    private fun <T> bounded(block: suspend () -> T): T? {
        val done = ArrayBlockingQueue<Result<T>>(1)
        Thread({ done.offer(runCatching { runBlocking { block() } }) }, "sandbox-test-probe")
            .apply { isDaemon = true }
            .start()
        // getOrThrow so a real exception surfaces as a failure, while a timeout
        // (null) is distinguishable by the caller.
        return done.poll(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS)?.getOrThrow()
    }

    private fun plugin(id: String, source: String, permissions: Set<String> = emptySet()) =
        ScriptEngine.PluginSpec(id = id, source = source, permissions = permissions)

    @Test
    fun `an infinite loop in a command is aborted rather than hanging`() {
        val engine = ScriptEngine()
        val loaded = bounded {
            engine.reload(
                listOf(
                    plugin(
                        "runaway",
                        """octo.registerCommand('spin', 'Spin', function (text) { while (true) {} });"""
                    )
                )
            )
        }
        assertNotNull("plugin load did not finish", loaded)

        val result = bounded { engine.run("runaway", "spin", "input") }
        assertNotNull("a non-terminating command was never stopped", result)
        assertTrue("a non-terminating command must fail, not return", result!!.isFailure)
    }

    @Test
    fun `an infinite loop at plugin load level does not block other plugins`() {
        val engine = ScriptEngine()
        val loaded = bounded {
            engine.reload(
                listOf(
                    // Never finishes evaluating; must be abandoned, not awaited.
                    plugin("runaway", "while (true) {}"),
                    plugin(
                        "wellbehaved",
                        """octo.registerCommand('shout', 'Shout', function (t) { return t.toUpperCase(); });"""
                    ),
                )
            )
        }
        assertNotNull("a runaway plugin blocked the whole reload", loaded)

        val commands = bounded { engine.commands() }
        assertEquals(listOf("shout"), commands?.map { it.id })
    }

    @Test
    fun `a well-behaved command still runs normally`() {
        // Guards against "fixing" the budget by making it so tight that
        // legitimate plugins are killed too.
        val engine = ScriptEngine()
        bounded {
            engine.reload(
                listOf(
                    plugin(
                        "shouty",
                        """octo.registerCommand('shout', 'Shout', function (t) { return t.toUpperCase(); });"""
                    )
                )
            )
        }

        val result = bounded { engine.run("shouty", "shout", "hello") }
        assertEquals("HELLO", result?.getOrNull())
    }

    @Test
    fun `a loop of realistic size is not mistaken for a runaway`() {
        val engine = ScriptEngine()
        bounded {
            engine.reload(
                listOf(
                    plugin(
                        "counter",
                        """
                        octo.registerCommand('count', 'Count', function (t) {
                          var n = 0;
                          for (var i = 0; i < 20000; i++) { n += i; }
                          return String(n);
                        });
                        """.trimIndent()
                    )
                )
            )
        }

        val result = bounded { engine.run("counter", "count", "") }
        assertEquals("199990000", result?.getOrNull())
    }

    @Test
    fun `the engine still works after a runaway plugin was stopped`() {
        // The engine serialises on one lock and one thread. A killed plugin
        // must release both, or the first bad plugin bricks the feature for
        // every other one.
        val engine = ScriptEngine()
        bounded {
            engine.reload(
                listOf(
                    plugin("runaway", """octo.registerCommand('spin', 'Spin', function (t) { while (true) {} });"""),
                    plugin("echo", """octo.registerCommand('echo', 'Echo', function (t) { return t; });"""),
                )
            )
        }

        val spun = bounded { engine.run("runaway", "spin", "x") }
        assertNotNull("the runaway was never stopped", spun)
        assertTrue(spun!!.isFailure)

        val echoed = bounded { engine.run("echo", "echo", "still alive") }
        assertEquals("still alive", echoed?.getOrNull())
    }

    @Test
    fun `scripts cannot reach java classes`() {
        val engine = ScriptEngine()
        bounded {
            engine.reload(
                listOf(
                    plugin(
                        "escapee",
                        """
                        octo.registerCommand('escape', 'Escape', function (t) {
                          return String(java.lang.System.getProperty('user.home'));
                        });
                        """.trimIndent()
                    )
                )
            )
        }

        val result = bounded { engine.run("escapee", "escape", "") }
        assertNotNull(result)
        assertTrue("the class shutter must deny java.* access", result!!.isFailure)
    }

    @Test
    fun `an ungranted permission is refused at the api boundary`() {
        val engine = ScriptEngine()
        bounded {
            engine.reload(
                listOf(
                    plugin(
                        "nosy",
                        """
                        octo.registerCommand('peek', 'Peek', function (t) {
                          return octo.notes.titles().join(',');
                        });
                        """.trimIndent(),
                        permissions = emptySet(),
                    )
                )
            )
        }

        val result = bounded { engine.run("nosy", "peek", "") }
        assertNotNull(result)
        assertTrue("notes:read was never granted", result!!.isFailure)
    }
}
