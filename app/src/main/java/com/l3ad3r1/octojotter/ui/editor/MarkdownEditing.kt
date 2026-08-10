package com.l3ad3r1.octojotter.ui.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * Pure Markdown editing transforms for the editor toolbar.
 *
 * Everything here is a plain function over [TextFieldValue] — no Compose state,
 * no Android dependencies — so the selection/caret rules can be unit tested
 * (see `MarkdownEditingTest`) rather than only exercised by tapping the UI.
 *
 * Two families of action:
 *  - **inline** ([InlineFormat]) wrap the selection in a symmetric token and
 *    toggle back off when the selection is already wrapped;
 *  - **block** ([BlockFormat]) rewrite the line prefix of every line the
 *    selection touches, and are mutually exclusive within a family (turning a
 *    bullet into a task list replaces the marker rather than stacking it).
 */

/** Symmetric inline tokens. [Superscript]/[Subscript]/[Highlight] follow the Pandoc/Obsidian spelling. */
enum class InlineFormat(val token: String) {
    Bold("**"),
    Italic("*"),
    Strikethrough("~~"),
    Code("`"),
    Highlight("=="),
    Superscript("^"),
    Subscript("~")
}

/** Line-prefix formats. Headings replace each other; list markers replace each other. */
enum class BlockFormat(val prefix: String) {
    H1("# "),
    H2("## "),
    H3("### "),
    Bullet("- "),
    Ordered("1. "),
    Task("- [ ] "),
    Quote("> ");

    val isHeading: Boolean get() = this == H1 || this == H2 || this == H3
}

/** Cursor placeholder inside a snippet — removed on insert, with the caret left in its place. */
const val CURSOR = "$0"

private val HEADING_PREFIX = Regex("""^\s{0,3}#{1,6} """)
private val LIST_PREFIX = Regex("""^\s*(?:[-*+] \[[ xX]] |[-*+] |\d+\. )""")
private val QUOTE_PREFIX = Regex("""^\s*> ?""")
private val INDENT_UNIT = "  "

// ---------------------------------------------------------------------------
// Inline formatting
// ---------------------------------------------------------------------------

/**
 * Toggle [format] around the selection.
 *
 * With no selection the word under the caret is wrapped; if the caret isn't in
 * a word, an empty pair is inserted with the caret between the tokens. An
 * already-wrapped span (tokens inside *or* just outside the selection) is
 * unwrapped instead, so tapping Bold twice is a no-op on the text.
 */
fun toggleInline(value: TextFieldValue, format: InlineFormat): TextFieldValue {
    val token = format.token
    val text = value.text
    var start = value.selection.min
    var end = value.selection.max

    if (start == end) {
        val word = wordBoundsAt(text, start)
        if (word != null) {
            start = word.first
            end = word.second
        }
    }

    val selected = text.substring(start, end)

    // Case 1: the tokens are inside the selection — "**bold**" selected.
    if (selected.length >= 2 * token.length &&
        selected.startsWith(token) &&
        selected.endsWith(token)
    ) {
        val inner = selected.substring(token.length, selected.length - token.length)
        return TextFieldValue(
            text = text.substring(0, start) + inner + text.substring(end),
            selection = TextRange(start, start + inner.length)
        )
    }

    // Case 2: the tokens sit just outside the selection — "bold" selected within "**bold**".
    if (start >= token.length &&
        end + token.length <= text.length &&
        text.regionMatches(start - token.length, token, 0, token.length) &&
        text.regionMatches(end, token, 0, token.length)
    ) {
        return TextFieldValue(
            text = text.substring(0, start - token.length) + selected + text.substring(end + token.length),
            selection = TextRange(start - token.length, start - token.length + selected.length)
        )
    }

    // Case 3: not formatted yet — wrap it.
    val wrapped = token + selected + token
    val caret = if (selected.isEmpty()) start + token.length else start + token.length + selected.length
    return TextFieldValue(
        text = text.substring(0, start) + wrapped + text.substring(end),
        selection = if (selected.isEmpty()) {
            TextRange(caret)
        } else {
            TextRange(start + token.length, caret)
        }
    )
}

// ---------------------------------------------------------------------------
// Block formatting
// ---------------------------------------------------------------------------

/**
 * Toggle [format] on every line the selection touches.
 *
 * Applying a format a second time removes it. Applying a different format of
 * the same family replaces it (H2 over H1, task list over bullets). [BlockFormat.Ordered]
 * renumbers the affected lines from 1.
 */
fun toggleBlock(value: TextFieldValue, format: BlockFormat): TextFieldValue {
    val text = value.text
    val (blockStart, blockEnd) = lineBlockBounds(text, value.selection.min, value.selection.max)
    val block = text.substring(blockStart, blockEnd)
    val lines = block.split("\n")

    val allHaveIt = lines.all { it.isBlank() || hasBlockFormat(it, format) }
    val rewritten = lines.mapIndexed { index, line ->
        if (line.isBlank()) return@mapIndexed line
        val bare = stripBlockPrefixes(line, format)
        if (allHaveIt) {
            bare
        } else {
            val indent = line.takeWhile { it == ' ' }
            val prefix = if (format == BlockFormat.Ordered) "${index + 1}. " else format.prefix
            indent + prefix + bare.trimStart()
        }
    }

    val newBlock = rewritten.joinToString("\n")
    val delta = newBlock.length - block.length
    return TextFieldValue(
        text = text.substring(0, blockStart) + newBlock + text.substring(blockEnd),
        selection = TextRange(
            value.selection.min.coerceAtMost(blockStart + newBlock.length),
            (value.selection.max + delta).coerceIn(0, text.length + delta)
        )
    )
}

/** Add one indent level to every line the selection touches. */
fun indent(value: TextFieldValue): TextFieldValue = reindent(value) { INDENT_UNIT + it }

/** Remove one indent level from every line the selection touches. */
fun outdent(value: TextFieldValue): TextFieldValue = reindent(value) { line ->
    when {
        line.startsWith(INDENT_UNIT) -> line.removePrefix(INDENT_UNIT)
        line.startsWith("\t") -> line.removePrefix("\t")
        line.startsWith(" ") -> line.removePrefix(" ")
        else -> line
    }
}

private fun reindent(value: TextFieldValue, transform: (String) -> String): TextFieldValue {
    val text = value.text
    val (blockStart, blockEnd) = lineBlockBounds(text, value.selection.min, value.selection.max)
    val block = text.substring(blockStart, blockEnd)
    val newBlock = block.split("\n").joinToString("\n") { if (it.isBlank()) it else transform(it) }
    val delta = newBlock.length - block.length
    return TextFieldValue(
        text = text.substring(0, blockStart) + newBlock + text.substring(blockEnd),
        selection = TextRange(
            (value.selection.min + if (value.selection.min > blockStart) delta.coerceAtMost(0) else 0)
                .coerceIn(blockStart, blockStart + newBlock.length),
            (value.selection.max + delta).coerceIn(blockStart, blockStart + newBlock.length)
        )
    )
}

// ---------------------------------------------------------------------------
// Snippet insertion
// ---------------------------------------------------------------------------

/**
 * Insert a multi-line [snippet] as its own block, padding with blank lines so it
 * never glues onto surrounding text. A [CURSOR] marker in the snippet decides
 * where the caret lands; without one the caret goes to the end of the snippet.
 */
fun insertBlockSnippet(value: TextFieldValue, snippet: String): TextFieldValue {
    val text = value.text
    val start = value.selection.min
    val end = value.selection.max

    val needsLeading = start > 0 && !text.substring(0, start).endsWith("\n")
    val needsTrailing = end < text.length && !text.substring(end).startsWith("\n")
    val body = (if (needsLeading) "\n" else "") + snippet + (if (needsTrailing) "\n" else "")

    val markerIndex = body.indexOf(CURSOR)
    val clean = body.replace(CURSOR, "")
    val caret = start + if (markerIndex >= 0) markerIndex else clean.length

    return TextFieldValue(
        text = text.substring(0, start) + clean + text.substring(end),
        selection = TextRange(caret)
    )
}

/** Insert [snippet] inline at the caret, replacing the selection. Honours [CURSOR]. */
fun insertInline(value: TextFieldValue, snippet: String): TextFieldValue {
    val text = value.text
    val start = value.selection.min
    val end = value.selection.max
    val markerIndex = snippet.indexOf(CURSOR)
    val clean = snippet.replace(CURSOR, "")
    val caret = start + if (markerIndex >= 0) markerIndex else clean.length
    return TextFieldValue(
        text = text.substring(0, start) + clean + text.substring(end),
        selection = TextRange(caret)
    )
}

/**
 * Wrap the selection as a link. Selected text becomes the label with the caret
 * on the placeholder URL; with no selection the caret lands on the label.
 */
fun insertLink(value: TextFieldValue): TextFieldValue {
    val selected = value.text.substring(value.selection.min, value.selection.max)
    return if (selected.isEmpty()) {
        insertInline(value, "[$CURSOR](url)")
    } else {
        val text = value.text
        val start = value.selection.min
        val end = value.selection.max
        val replacement = "[$selected](url)"
        val urlStart = start + replacement.length - 4 // start of "url"
        TextFieldValue(
            text = text.substring(0, start) + replacement + text.substring(end),
            selection = TextRange(urlStart, urlStart + 3)
        )
    }
}

/** Snippets for the toolbar's block-insert actions. */
object MarkdownSnippets {
    const val TABLE = "| Column | Column |\n| --- | --- |\n| $CURSOR |  |\n"
    const val CODE_BLOCK = "```\n$CURSOR\n```\n"
    const val HORIZONTAL_RULE = "\n---\n"
    const val DETAILS = "> [!NOTE]\n> $CURSOR\n"
    const val FOOTNOTE = "[^1]$CURSOR\n\n[^1]: "

    fun callout(kind: String) = "> [!$kind]\n> $CURSOR\n"
}

// ---------------------------------------------------------------------------
// Active-state detection (drives the toolbar's highlighted buttons)
// ---------------------------------------------------------------------------

/** The inline formats wrapping the caret / selection right now. */
fun activeInlineFormats(value: TextFieldValue): Set<InlineFormat> {
    val text = value.text
    val start = value.selection.min
    val end = value.selection.max
    // Longest tokens first so "~~" wins over "~" and "**" over "*".
    return InlineFormat.entries
        .sortedByDescending { it.token.length }
        .filterTo(LinkedHashSet()) { format ->
            val token = format.token
            val inside = end - start >= 2 * token.length &&
                text.regionMatches(start, token, 0, token.length) &&
                text.regionMatches(end - token.length, token, 0, token.length)
            val outside = start >= token.length &&
                end + token.length <= text.length &&
                text.regionMatches(start - token.length, token, 0, token.length) &&
                text.regionMatches(end, token, 0, token.length)
            inside || outside || enclosedByTokenOnLine(text, start, end, token)
        }
        .let { active ->
            // "**bold**" also matches the "*" italic test; drop the shorter
            // token when a longer one starting with it is already active.
            active.filterNotTo(LinkedHashSet()) { candidate ->
                active.any { it != candidate && it.token.startsWith(candidate.token) }
            }
        }
}

/** The block format applied to the line the caret sits on, if any. */
fun activeBlockFormat(value: TextFieldValue): BlockFormat? {
    val line = currentLine(value.text, value.selection.min)
    // Task before Bullet: "- [ ] " also starts with "- ".
    return listOf(
        BlockFormat.H3, BlockFormat.H2, BlockFormat.H1,
        BlockFormat.Task, BlockFormat.Quote, BlockFormat.Bullet, BlockFormat.Ordered
    ).firstOrNull { hasBlockFormat(line, it) }
}

// ---------------------------------------------------------------------------
// Internals
// ---------------------------------------------------------------------------

private fun hasBlockFormat(line: String, format: BlockFormat): Boolean {
    val trimmed = line.trimStart()
    return when (format) {
        BlockFormat.Ordered -> Regex("""^\d+\. """).containsMatchIn(trimmed)
        BlockFormat.Task -> Regex("""^[-*+] \[[ xX]] """).containsMatchIn(trimmed)
        BlockFormat.Bullet -> Regex("""^[-*+] """).containsMatchIn(trimmed) &&
            !Regex("""^[-*+] \[[ xX]] """).containsMatchIn(trimmed)
        else -> trimmed.startsWith(format.prefix)
    }
}

/**
 * Strip the prefixes that [format] conflicts with. Headings and list markers
 * are separate families, so adding a heading to a bullet keeps the bullet.
 */
private fun stripBlockPrefixes(line: String, format: BlockFormat): String {
    var result = line
    when {
        format.isHeading -> result = HEADING_PREFIX.replace(result, "")
        format == BlockFormat.Quote -> result = QUOTE_PREFIX.replace(result, "")
        else -> result = LIST_PREFIX.replace(result, "")
    }
    return result
}

private fun currentLine(text: String, caret: Int): String {
    val start = text.lastIndexOf('\n', (caret - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
    val end = text.indexOf('\n', caret).let { if (it < 0) text.length else it }
    return if (start > end) "" else text.substring(start, end)
}

/** Expand [start]..[end] to whole lines. */
private fun lineBlockBounds(text: String, start: Int, end: Int): Pair<Int, Int> {
    val blockStart = text.lastIndexOf('\n', (start - 1).coerceAtLeast(0))
        .let { if (it < 0 || start == 0) 0 else it + 1 }
    val blockEnd = text.indexOf('\n', end).let { if (it < 0) text.length else it }
    return blockStart to blockEnd.coerceAtLeast(blockStart)
}

private fun wordBoundsAt(text: String, caret: Int): Pair<Int, Int>? {
    fun isWord(c: Char) = c.isLetterOrDigit() || c == '_'
    var start = caret
    var end = caret
    while (start > 0 && isWord(text[start - 1])) start--
    while (end < text.length && isWord(text[end])) end++
    return if (start == end) null else start to end
}

/**
 * True when the caret sits between a matching pair of [token]s on its own line —
 * catches the "caret inside **bold**" case that the selection tests miss.
 */
private fun enclosedByTokenOnLine(text: String, start: Int, end: Int, token: String): Boolean {
    if (start != end) return false
    val lineStart = text.lastIndexOf('\n', (start - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
    val lineEnd = text.indexOf('\n', start).let { if (it < 0) text.length else it }
    if (start !in lineStart..lineEnd) return false

    val before = text.lastIndexOf(token, (start - token.length).coerceAtLeast(0))
    if (before < lineStart) return false
    val after = text.indexOf(token, start)
    return after in 0 until lineEnd
}
