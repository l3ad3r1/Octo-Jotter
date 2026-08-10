package com.l3ad3r1.octojotter.ai.index

/**
 * Splits a note's markdown body into embedding-sized chunks.
 *
 * Pure and deterministic (unit-tested). Strategy: split on blank lines into
 * blocks (paragraphs, headings, list groups, fenced code), then greedily pack
 * blocks into chunks up to [maxChars]. A block larger than [maxChars] on its own
 * is hard-split on whitespace. Consecutive chunks share a small trailing overlap
 * ([overlapChars]) so a sentence spanning a boundary is still retrievable from
 * both sides.
 *
 * Character budgets approximate token budgets (~4 chars/token); the default
 * ~1600 chars ≈ 400 tokens, comfortably under all-MiniLM's 256-token window once
 * short notes dominate, and a safe ceiling for larger models.
 */
class NoteChunker(
    private val maxChars: Int = 1600,
    private val overlapChars: Int = 160,
) {
    init {
        require(maxChars > 0) { "maxChars must be > 0" }
        require(overlapChars in 0 until maxChars) { "overlapChars must be in [0, maxChars)" }
    }

    fun chunk(content: String): List<String> {
        val text = content.trim()
        if (text.isEmpty()) return emptyList()

        val blocks = splitIntoBlocks(text)
        val chunks = mutableListOf<String>()
        val current = StringBuilder()

        fun flush() {
            val chunk = current.toString().trim()
            if (chunk.isNotEmpty()) chunks += chunk
            current.setLength(0)
        }

        for (block in blocks) {
            for (piece in hardSplit(block)) {
                val addition = if (current.isEmpty()) piece else "\n\n$piece"
                if (current.length + addition.length > maxChars && current.isNotEmpty()) {
                    val tail = overlapTail(current.toString())
                    flush()
                    if (tail.isNotEmpty()) current.append(tail).append("\n\n")
                    current.append(piece)
                } else {
                    current.append(addition)
                }
            }
        }
        flush()
        return chunks
    }

    private fun splitIntoBlocks(text: String): List<String> =
        text.split(Regex("\\n[ \\t]*\\n"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    /** Split a block that alone exceeds [maxChars] on whitespace boundaries. */
    private fun hardSplit(block: String): List<String> {
        if (block.length <= maxChars) return listOf(block)
        val out = mutableListOf<String>()
        val words = block.split(Regex("\\s+"))
        val sb = StringBuilder()
        for (word in words) {
            val addition = if (sb.isEmpty()) word else " $word"
            if (sb.length + addition.length > maxChars && sb.isNotEmpty()) {
                out += sb.toString()
                sb.setLength(0)
                sb.append(word)
            } else {
                sb.append(addition)
            }
        }
        if (sb.isNotEmpty()) out += sb.toString()
        return out
    }

    /** Last [overlapChars] characters of [chunk], snapped to a word boundary. */
    private fun overlapTail(chunk: String): String {
        if (overlapChars == 0 || chunk.length <= overlapChars) return ""
        val slice = chunk.substring(chunk.length - overlapChars)
        val cut = slice.indexOf(' ')
        return if (cut in 0 until slice.length - 1) slice.substring(cut + 1).trim() else slice.trim()
    }
}
