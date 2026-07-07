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
class ScriptEngine(private val host: PluginHost? = null) {

    data class CommandDescriptor(val pluginId: String, val id: String, val name: String)

    /** A plugin to load: its id, JS source, and the permissions the user granted. */
    data class PluginSpec(val id: String, val source: String, val permissions: Set<String>)

    private class Command(val descriptor: CommandDescriptor, val fn: Function)
    private class LoadedPlugin(val scope: Scriptable, val commands: MutableList<Command> = mutableListOf())

    private val mutex = Mutex()
    private val plugins = LinkedHashMap<String, LoadedPlugin>()
    private val factory = SandboxContextFactory()

    /** Replace all loaded plugins with [specs]. */
    suspend fun reload(specs: List<PluginSpec>) = withContext(Dispatchers.Default) {
        mutex.withLock {
            plugins.clear()
            for (spec in specs) {
                try {
                    val cx = factory.enterContext()
                    try {
                        val scope = cx.initSafeStandardObjects(null, true)
                        val loaded = LoadedPlugin(scope)
                        installApi(cx, scope, spec.id, spec.permissions, loaded)
                        cx.evaluateString(scope, spec.source, spec.id, 1, null)
                        plugins[spec.id] = loaded
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

    private fun installApi(
        cx: Context,
        scope: Scriptable,
        pluginId: String,
        permissions: Set<String>,
        loaded: LoadedPlugin
    ) {
        val octo = cx.newObject(scope)

        // octo.registerCommand(id, name, fn) — no permission needed.
        ScriptableObject.putProperty(octo, "registerCommand", object : BaseFunction() {
            override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable?, args: Array<out Any>?): Any {
                val id = args?.getOrNull(0)?.let { Context.toString(it) } ?: return Undefined.instance
                val name = args.getOrNull(1)?.let { Context.toString(it) } ?: id
                val fn = args.getOrNull(2) as? Function ?: return Undefined.instance
                loaded.commands.add(Command(CommandDescriptor(pluginId, id, name), fn))
                return Undefined.instance
            }
        })

        // octo.log(message) — no permission needed.
        ScriptableObject.putProperty(octo, "log", object : BaseFunction() {
            override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable?, args: Array<out Any>?): Any {
                host?.log(pluginId, args?.getOrNull(0)?.let { Context.toString(it) } ?: "")
                return Undefined.instance
            }
        })

        // octo.notes.{create,list} — permission-gated.
        val notes = cx.newObject(scope)
        ScriptableObject.putProperty(notes, "create", object : BaseFunction() {
            override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable?, args: Array<out Any>?): Any {
                requirePermission(permissions, PluginPermissions.NOTES_WRITE)
                val title = args?.getOrNull(0)?.let { Context.toString(it) } ?: ""
                val content = args?.getOrNull(1)?.let { Context.toString(it) } ?: ""
                host?.createNote(title, content)
                return Undefined.instance
            }
        })
        ScriptableObject.putProperty(notes, "list", object : BaseFunction() {
            override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable?, args: Array<out Any>?): Any {
                requirePermission(permissions, PluginPermissions.NOTES_READ)
                val noteSnapshots = host?.listNotes() ?: emptyList()
                return cx.newArray(scope, noteSnapshots.map { it.toJsObject(cx, scope) }.toTypedArray())
            }
        })
        ScriptableObject.putProperty(notes, "titles", object : BaseFunction() {
            override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable?, args: Array<out Any>?): Any {
                requirePermission(permissions, PluginPermissions.NOTES_READ)
                val titles = host?.listNoteTitles() ?: emptyList()
                return cx.newArray(scope, titles.toTypedArray())
            }
        })
        ScriptableObject.putProperty(notes, "search", object : BaseFunction() {
            override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable?, args: Array<out Any>?): Any {
                requirePermission(permissions, PluginPermissions.NOTES_READ)
                val query = args?.getOrNull(0)?.let { Context.toString(it) } ?: ""
                val noteSnapshots = host?.searchNotes(query) ?: emptyList()
                return cx.newArray(scope, noteSnapshots.map { it.toJsObject(cx, scope) }.toTypedArray())
            }
        })
        ScriptableObject.putProperty(notes, "withTag", object : BaseFunction() {
            override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable?, args: Array<out Any>?): Any {
                requirePermission(permissions, PluginPermissions.NOTES_READ)
                val tag = args?.getOrNull(0)?.let { Context.toString(it) } ?: ""
                val noteSnapshots = host?.notesWithTag(tag) ?: emptyList()
                return cx.newArray(scope, noteSnapshots.map { it.toJsObject(cx, scope) }.toTypedArray())
            }
        })
        ScriptableObject.putProperty(notes, "openTasks", object : BaseFunction() {
            override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable?, args: Array<out Any>?): Any {
                requirePermission(permissions, PluginPermissions.NOTES_READ)
                val tasks = host?.openTasks() ?: emptyList()
                return cx.newArray(scope, tasks.map { it.toJsObject(cx, scope) }.toTypedArray())
            }
        })
        ScriptableObject.putProperty(octo, "notes", notes)

        ScriptableObject.putProperty(scope, "octo", octo)
    }

    // Throws a JS-catchable error (surfaced to the user) when a plugin calls an
    // API it wasn't granted permission for.
    private fun requirePermission(granted: Set<String>, permission: String) {
        if (permission !in granted) {
            throw IllegalStateException("Permission denied: '$permission' was not granted to this plugin.")
        }
    }

    private fun PluginNote.toJsObject(cx: Context, scope: Scriptable): Scriptable {
        val obj = cx.newObject(scope)
        ScriptableObject.putProperty(obj, "id", id)
        ScriptableObject.putProperty(obj, "title", title)
        ScriptableObject.putProperty(obj, "displayTitle", displayTitle)
        ScriptableObject.putProperty(obj, "content", content)
        ScriptableObject.putProperty(obj, "tags", cx.newArray(scope, tags.toTypedArray()))
        ScriptableObject.putProperty(obj, "folder", folder ?: "")
        ScriptableObject.putProperty(obj, "path", path ?: "")
        ScriptableObject.putProperty(obj, "lastModifiedLocally", lastModifiedLocally.toDouble())
        ScriptableObject.putProperty(obj, "locked", locked)
        return obj
    }

    private fun PluginTask.toJsObject(cx: Context, scope: Scriptable): Scriptable {
        val obj = cx.newObject(scope)
        ScriptableObject.putProperty(obj, "noteId", noteId)
        ScriptableObject.putProperty(obj, "noteTitle", noteTitle)
        ScriptableObject.putProperty(obj, "text", text)
        ScriptableObject.putProperty(obj, "line", line)
        ScriptableObject.putProperty(obj, "lineNumber", lineNumber)
        ScriptableObject.putProperty(obj, "tags", cx.newArray(scope, tags.toTypedArray()))
        ScriptableObject.putProperty(obj, "folder", folder ?: "")
        ScriptableObject.putProperty(obj, "lastModifiedLocally", lastModifiedLocally.toDouble())
        return obj
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
