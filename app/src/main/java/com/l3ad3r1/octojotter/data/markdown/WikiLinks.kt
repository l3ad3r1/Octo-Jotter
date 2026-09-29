package com.l3ad3r1.octojotter.data.markdown

/**
 * One Obsidian-style link: `[[Target]]`, `[[Target|Alias]]`, `[[Target#Heading]]`,
 * `[[Target#Heading|Alias]]`, or an embed (`![[Target]]`).
 *
 * [target] is the note title to resolve; [display] is what a reader should see.
 */
data class WikiLink(
    val target: String,
    val heading: String?,
    val alias: String?,
    val isEmbed: Boolean = false,
) {
    val display: String
        get() = alias ?: if (heading != null) "$target > $heading" else target
}

object WikiLinks {
    private val LINK = Regex("""(!?)\[\[([^\[\]\n]+)]]""")

    /** Parse the text between `[[` and `]]`. Null when there is no target. */
    fun parseInner(inner: String, isEmbed: Boolean = false): WikiLink? {
        val pipe = inner.indexOf('|')
        val left = if (pipe >= 0) inner.substring(0, pipe) else inner
        val alias = if (pipe >= 0) inner.substring(pipe + 1).trim().ifEmpty { null } else null
        val hash = left.indexOf('#')
        val target = (if (hash >= 0) left.substring(0, hash) else left).trim()
        val heading = if (hash >= 0) left.substring(hash + 1).trim().ifEmpty { null } else null
        if (target.isEmpty()) return null
        return WikiLink(target, heading, alias, isEmbed)
    }

    fun extract(text: String): List<WikiLink> =
        LINK.findAll(text).mapNotNull { parseInner(it.groupValues[2], it.groupValues[1].isNotEmpty()) }.toList()

    /** Distinct link targets in [text], in order of first appearance. */
    fun targets(text: String): List<String> = extract(text).map { it.target }.distinct()

    /**
     * If [text] ends inside an unclosed `[[`, the partial query typed after it
     * (used to drive the editor's link suggestions); otherwise null.
     */
    fun openQuery(textBeforeCursor: String): String? {
        val open = textBeforeCursor.lastIndexOf("[[")
        if (open < 0) return null
        val query = textBeforeCursor.substring(open + 2)
        if ('\n' in query || ']' in query || '[' in query) return null
        return query
    }
}
