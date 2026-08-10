package com.l3ad3r1.octojotter.data.markdown

/**
 * YAML front matter ("Properties", in Obsidian's vocabulary) at the head of a
 * Markdown note.
 *
 * Front matter exists **only** when the file's very first line is `---` and a
 * closing `---` line follows. That is Obsidian's rule, and it is what keeps a
 * mid-note `---` thematic break from being mistaken for a property block.
 *
 * ### Round-trip safety
 *
 * Notes sync to the user's real Git repository, and [NoteEntity.content] is
 * hashed byte-for-byte to decide what needs pushing. So this file never
 * re-serialises a block it merely read: [parse] records the line range each key
 * occupies, and the writers ([replaceField], [removeField], [upsertField]) edit
 * only those lines. Anything this parser does not understand — nested maps,
 * comments, unusual spacing, duplicate keys — is carried through verbatim as
 * [PropertyValue.Unparsed] rather than rewritten or dropped.
 *
 * The invariant the tests enforce is: reading a note and writing it back
 * without changing a property returns the original string, byte for byte,
 * including CRLF line endings and a missing trailing newline.
 *
 * Only the property subset Obsidian actually emits is interpreted:
 *
 * ```yaml
 * ---
 * title: Weekly review          # text
 * count: 42                     # number
 * done: true                    # checkbox
 * created: 2026-07-28           # date
 * tags:                         # list (block form)
 *   - work
 *   - review
 * aliases: [Review, Weekly]     # list (inline form)
 * ---
 * ```
 */
data class Frontmatter(
    /** Parsed properties, in the order they appear. Later duplicates win. */
    val fields: Map<String, PropertyValue>,
    /** The block verbatim, including both `---` delimiters and the trailing newline. */
    val rawBlock: String,
    /** Index into the note content where the body starts (just past the closing `---`). */
    val bodyOffset: Int,
    /** Key -> the lines of [rawBlock] it occupies, 0 being the opening `---`. */
    val lineRanges: Map<String, IntRange>,
    /** True when the block uses CRLF, so edits keep the file's line endings. */
    val crlf: Boolean,
    /**
     * Keys whose list was written inline (`aliases: [A, B]`) rather than as a
     * block list. Rewriting one of these keeps the author's style instead of
     * reflowing it, which would otherwise dirty every note in a synced vault.
     */
    val inlineLists: Set<String> = emptySet()
) {

    /** Property values Obsidian round-trips. Anything else stays [Unparsed]. */
    sealed class PropertyValue {
        data class Text(val value: String) : PropertyValue()
        data class Numeric(val value: Double) : PropertyValue()
        data class Checkbox(val value: Boolean) : PropertyValue()
        /** A date or date-time, kept as written so formatting survives a round trip. */
        data class DateStamp(val value: String) : PropertyValue()
        data class Items(val values: List<String>) : PropertyValue()
        /** Something this parser does not model (a nested map, say) — never rewritten. */
        data class Unparsed(val raw: String) : PropertyValue()
    }

    /** `tags:` / `tag:`, with any leading `#` stripped, matching Obsidian. */
    fun tags(): List<String> = (stringsAt("tags") + stringsAt("tag"))
        .map { it.removePrefix("#").trim() }
        .filter { it.isNotEmpty() }
        .distinct()

    /** `aliases:` / `alias:` — the other titles a `[[wikilink]]` may resolve through. */
    fun aliases(): List<String> = (stringsAt("aliases") + stringsAt("alias"))
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()

    /** Read a key as a list of strings, accepting list, comma-separated text, or a scalar. */
    fun stringsAt(key: String): List<String> = when (val value = fields[key]) {
        is PropertyValue.Items -> value.values
        is PropertyValue.Text -> value.value.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        is PropertyValue.Numeric -> listOf(formatNumber(value.value))
        is PropertyValue.Checkbox -> listOf(value.value.toString())
        is PropertyValue.DateStamp -> listOf(value.value)
        is PropertyValue.Unparsed, null -> emptyList()
    }

    companion object {
        const val DELIMITER = "---"

        private val DATE = Regex("""^\d{4}-\d{2}-\d{2}([T ]\d{2}:\d{2}(:\d{2})?)?$""")
        private val KEY = Regex("""^[A-Za-z0-9_][A-Za-z0-9_\-. ]*$""")

        /** Parse the block at the head of [content], or null when there isn't one. */
        fun parse(content: String): Frontmatter? {
            val firstBreak = content.indexOf('\n')
            if (firstBreak < 0) return null
            if (content.substring(0, firstBreak).trimEnd('\r', ' ', '\t') != DELIMITER) return null
            val crlf = firstBreak > 0 && content[firstBreak - 1] == '\r'

            // Locate the closing delimiter line.
            var cursor = firstBreak + 1
            var blockEnd = -1
            while (cursor <= content.length) {
                val newline = content.indexOf('\n', cursor)
                val lineEnd = if (newline < 0) content.length else newline
                if (content.substring(cursor, lineEnd).trimEnd('\r', ' ', '\t') == DELIMITER) {
                    blockEnd = if (newline < 0) content.length else newline + 1
                    break
                }
                if (newline < 0) break
                cursor = newline + 1
            }
            if (blockEnd < 0) return null

            val rawBlock = content.substring(0, blockEnd)
            val lines = rawBlock.split("\n")
            // The closing delimiter, as an index into `lines`.
            val closing = lines.indices.drop(1)
                .firstOrNull { lines[it].trimEnd('\r', ' ', '\t') == DELIMITER }
                ?: return null

            val fields = LinkedHashMap<String, PropertyValue>()
            val ranges = LinkedHashMap<String, IntRange>()
            val inlineLists = LinkedHashSet<String>()

            var i = 1
            while (i < closing) {
                val line = lines[i].trimEnd('\r')
                val trimmed = line.trim()
                // Blank lines, comments and stray continuation lines carry through untouched.
                if (trimmed.isEmpty() || trimmed.startsWith("#") || line.first().isWhitespace()) {
                    i++
                    continue
                }
                val colon = line.indexOf(':')
                if (colon <= 0) {
                    i++
                    continue
                }
                val key = line.substring(0, colon).trim()
                if (!KEY.matches(key)) {
                    i++
                    continue
                }
                val inline = line.substring(colon + 1).trim()

                if (inline.isNotEmpty()) {
                    val value = scalar(inline)
                    fields[key] = value
                    ranges[key] = i..i
                    if (value is PropertyValue.Items) inlineLists += key
                    i++
                    continue
                }

                // `key:` with nothing after it — the value is on the following
                // indented lines, either a block list or something we don't model.
                var last = i
                val items = mutableListOf<String>()
                var listOnly = true
                var j = i + 1
                while (j < closing) {
                    val next = lines[j].trimEnd('\r')
                    if (next.isBlank() || !next.first().isWhitespace()) break
                    val item = next.trim()
                    if (item.startsWith("- ")) {
                        items += unquote(item.removePrefix("- ").trim())
                    } else if (item == "-") {
                        items += ""
                    } else {
                        listOnly = false
                    }
                    last = j
                    j++
                }

                fields[key] = when {
                    last == i -> PropertyValue.Text("")
                    listOnly -> PropertyValue.Items(items)
                    // A nested map (or anything else): keep the raw lines so a
                    // later write puts them back exactly as they were.
                    else -> PropertyValue.Unparsed(
                        lines.subList(i, last + 1).joinToString("\n") { it.trimEnd('\r') }
                    )
                }
                ranges[key] = i..last
                i = last + 1
            }

            return Frontmatter(fields, rawBlock, blockEnd, ranges, crlf, inlineLists)
        }

        /** The note body — [content] with any front matter removed. */
        fun bodyOf(content: String): String =
            parse(content)?.let { content.substring(it.bodyOffset) } ?: content

        /** The raw front-matter block, or an empty string when there is none. */
        fun blockOf(content: String): String = parse(content)?.rawBlock.orEmpty()

        /**
         * Set [key] to [value], rewriting only the lines that key occupies. A key
         * that isn't present is appended just before the closing `---`; a note
         * without front matter gains a minimal block.
         */
        fun upsertField(content: String, key: String, value: PropertyValue): String =
            write(content, key, value)

        /** Replace [key] only if it already exists; otherwise return [content] unchanged. */
        fun replaceField(content: String, key: String, value: PropertyValue): String {
            val existing = parse(content) ?: return content
            if (!existing.lineRanges.containsKey(key)) return content
            return write(content, key, value)
        }

        /** Remove [key], leaving every other line untouched. */
        fun removeField(content: String, key: String): String = write(content, key, null)

        private fun write(content: String, key: String, value: PropertyValue?): String {
            val existing = parse(content)

            // Writing a value that is already there changes nothing — return the
            // note untouched rather than re-rendering the line. This is what
            // preserves the author's own formatting (quote style, spacing,
            // `yes` vs `true`) through a read-modify-write of some *other* key.
            if (existing != null && value != null && existing.fields[key] == value) return content

            if (existing == null) {
                if (value == null) return content
                val block = buildString {
                    append(DELIMITER).append('\n')
                    render(key, value, inline = false).forEach { append(it).append('\n') }
                    append(DELIMITER).append('\n')
                }
                return block + content
            }

            val lines = existing.rawBlock.split("\n").toMutableList()
            // `split` leaves a trailing "" when the block ends with a newline.
            val hadTrailingBreak = lines.lastOrNull() == ""
            if (hadTrailingBreak) lines.removeAt(lines.lastIndex)

            val rendered = value
                ?.let { render(key, it, inline = key in existing.inlineLists) }
                ?.map { line -> line.withBreak(existing.crlf) }
            val range = existing.lineRanges[key]

            when {
                range != null && rendered != null -> {
                    repeat(range.last - range.first + 1) { lines.removeAt(range.first) }
                    lines.addAll(range.first, rendered)
                }
                range != null -> repeat(range.last - range.first + 1) { lines.removeAt(range.first) }
                rendered != null -> {
                    // Insert before the closing delimiter.
                    val closing = lines.indices.drop(1)
                        .first { lines[it].trimEnd('\r', ' ', '\t') == DELIMITER }
                    lines.addAll(closing, rendered)
                }
                else -> return content
            }

            val rebuilt = lines.joinToString("\n") + if (hadTrailingBreak) "\n" else ""
            return rebuilt + content.substring(existing.bodyOffset)
        }

        // --- value <-> text ------------------------------------------------

        private fun scalar(raw: String): PropertyValue {
            if (raw.startsWith("[") && raw.endsWith("]")) {
                return PropertyValue.Items(splitInline(raw.substring(1, raw.length - 1)))
            }
            if (raw.equals("true", ignoreCase = true)) return PropertyValue.Checkbox(true)
            if (raw.equals("false", ignoreCase = true)) return PropertyValue.Checkbox(false)
            if (DATE.matches(raw)) return PropertyValue.DateStamp(raw)
            // Guard against "1.2.3" and "0123" being read as numbers we'd reformat.
            raw.toDoubleOrNull()?.let { number ->
                if (formatNumber(number) == raw) return PropertyValue.Numeric(number)
            }
            return PropertyValue.Text(unquote(raw))
        }

        private fun render(key: String, value: PropertyValue, inline: Boolean): List<String> = when (value) {
            is PropertyValue.Items -> when {
                inline -> listOf("$key: [" + value.values.joinToString(", ") { quoteIfNeeded(it) } + "]")
                value.values.isEmpty() -> listOf("$key:")
                else -> listOf("$key:") + value.values.map { "  - ${quoteIfNeeded(it)}" }
            }
            is PropertyValue.Unparsed -> value.raw.split("\n")
            is PropertyValue.Text -> listOf("$key: ${quoteIfNeeded(value.value)}")
            is PropertyValue.Numeric -> listOf("$key: ${formatNumber(value.value)}")
            is PropertyValue.Checkbox -> listOf("$key: ${value.value}")
            is PropertyValue.DateStamp -> listOf("$key: ${value.value}")
        }

        private fun formatNumber(value: Double): String =
            if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

        private fun splitInline(raw: String): List<String> {
            val out = mutableListOf<String>()
            val current = StringBuilder()
            var quote: Char? = null
            for (ch in raw) {
                when {
                    quote != null && ch == quote -> quote = null
                    quote == null && (ch == '"' || ch == '\'') -> quote = ch
                    quote == null && ch == ',' -> {
                        out += current.toString().trim()
                        current.clear()
                    }
                    else -> current.append(ch)
                }
            }
            out += current.toString().trim()
            return out.map { unquote(it) }.filter { it.isNotEmpty() }
        }

        private fun unquote(raw: String): String {
            if (raw.length >= 2) {
                val first = raw.first()
                if ((first == '"' || first == '\'') && raw.last() == first) {
                    return raw.substring(1, raw.length - 1)
                }
            }
            return raw
        }

        // Quote only where YAML would otherwise misread the value.
        private fun quoteIfNeeded(value: String): String {
            if (value.isEmpty()) return "\"\""
            val needsQuotes = value.contains(": ") ||
                value.endsWith(":") ||
                value.trim() != value ||
                value.first() in "[]{}&*#?|-<>=!%@`,\"'"
            return if (needsQuotes) "\"" + value.replace("\"", "\\\"") + "\"" else value
        }

        private fun String.withBreak(crlf: Boolean): String = if (crlf) this + "\r" else this
    }
}
