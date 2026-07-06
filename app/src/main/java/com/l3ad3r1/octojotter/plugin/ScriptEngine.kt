package com.l3ad3r1.octojotter.plugin

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Context
import org.mozilla.javascript.ContextFactory
import org.mozilla.javascript.Function
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined

/**
 * Sandboxed JavaScript runtime for `script` community plugins, backed by Mozilla
 * Rhino in interpreted mode (Android-safe, no native code).
 *
 * Safety:
 *  - Interpreted mode (`optimizationLevel = -1`) so nothing is compiled to
 *    bytecode (required on Android, and avoids classloading tricks).
 *  - A [Context.setClassShutter] denies access to every Java class, so plugins
 *    can't reach `java.*`, reflection, IO, or the network.
 *  - A *sealed safe* standard scope (no `Packages`, `getClass`, etc.).
 *  - An instruction budget via [ContextFactory.observeInstructionCount] aborts
 *    runaway/infinite-loop scripts.
 *
 * A plugin's only capability is the injected `octo` API — currently
 * `octo.registerCommand(id, name, fn)`, where `fn(text)` returns transformed
 * text. All Rhino access is confined to a single dispatcher via [mutex], and a
 * plugin's top-level scope is kept alive so its command functions stay callable.
 */
class ScriptEngine {

    data class CommandDescriptor(val pluginId: String, val id: String, val name: String)

    private class Command(val descriptor: CommandDescriptor, val fn: Function)
    private class LoadedPlugin(val scope: Scriptable, val commands: MutableList<Command> = mutableListOf())

    private val mutex = Mutex()
    private val plugins = LinkedHashMap<String, LoadedPlugin>()
    private val factory = SandboxContextFactory()

    /** Replace all loaded plugins with [scripts] (pluginId -> JS source). */
    suspend fun reload(scripts: Map<String, String>) = withContext(Dispatchers.Default) {
        mutex.withLock {
            plugins.clear()
            for ((pluginId, source) in scripts) {
                try {
                    val cx = factory.enterContext()
                    try {
                        val scope = cx.initSafeStandardObjects(null, true)
                        val loaded = LoadedPlugin(scope)
                        installApi(cx, scope, pluginId, loaded)
                        cx.evaluateString(scope, source, pluginId, 1, null)
                        plugins[pluginId] = loaded
                    } finally {
                        Context.exit()
                    }
                } catch (_: Throwable) {
                    // A broken plugin is skipped rather than crashing the app.
                }
            }
        }
    }

    /** Command descriptors registered by all currently-loaded plugins. */
    suspend fun commands(): List<CommandDescriptor> = mutex.withLock {
        plugins.values.flatMap { plugin -> plugin.commands.map { it.descriptor } }
    }

    /** Run a command's function against [input], returning the transformed text. */
    suspend fun run(pluginId: String, commandId: String, input: String): Result<String> =
        withContext(Dispatchers.Default) {
            mutex.withLock {
                val plugin = plugins[pluginId]
                    ?: return@withLock Result.failure(IllegalStateException("Plugin not loaded"))
                val command = plugin.commands.firstOrNull { it.descriptor.id == commandId }
                    ?: return@withLock Result.failure(IllegalStateException("Command not found"))
                try {
                    val cx = factory.enterContext()
                    try {
                        val result = command.fn.call(cx, plugin.scope, plugin.scope, arrayOf<Any>(input))
                        Result.success(Context.toString(result))
                    } finally {
                        Context.exit()
                    }
                } catch (e: Throwable) {
                    Result.failure(Exception(e.message ?: "Plugin command failed"))
                }
            }
        }

    private fun installApi(cx: Context, scope: Scriptable, pluginId: String, loaded: LoadedPlugin) {
        val octo = cx.newObject(scope)
        val registerCommand = object : BaseFunction() {
            override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable?, args: Array<out Any>?): Any {
                val id = args?.getOrNull(0)?.let { Context.toString(it) } ?: return Undefined.instance
                val name = args.getOrNull(1)?.let { Context.toString(it) } ?: id
                val fn = args.getOrNull(2) as? Function ?: return Undefined.instance
                loaded.commands.add(Command(CommandDescriptor(pluginId, id, name), fn))
                return Undefined.instance
            }
        }
        ScriptableObject.putProperty(octo, "registerCommand", registerCommand)
        ScriptableObject.putProperty(scope, "octo", octo)
    }

    /** Denies Java-class access and enforces the per-run instruction budget. */
    private class SandboxContextFactory : ContextFactory() {
        override fun makeContext(): Context {
            val cx = super.makeContext()
            cx.optimizationLevel = -1
            cx.instructionObserverThreshold = 10_000
            cx.setClassShutter { false }
            return cx
        }

        override fun observeInstructionCount(cx: Context?, instructionCount: Int) {
            if (instructionCount > INSTRUCTION_BUDGET) {
                throw Error("Plugin exceeded its instruction budget")
            }
        }
    }

    companion object {
        private const val INSTRUCTION_BUDGET = 5_000_000
    }
}
