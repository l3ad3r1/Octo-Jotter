package com.l3ad3r1.octojotter

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.l3ad3r1.octojotter.ui.editor.BlockFormat
import com.l3ad3r1.octojotter.ui.editor.InlineFormat
import com.l3ad3r1.octojotter.ui.editor.MarkdownSnippets
import com.l3ad3r1.octojotter.ui.editor.activeBlockFormat
import com.l3ad3r1.octojotter.ui.editor.activeInlineFormats
import com.l3ad3r1.octojotter.ui.editor.indent
import com.l3ad3r1.octojotter.ui.editor.insertBlockSnippet
import com.l3ad3r1.octojotter.ui.editor.insertLink
import com.l3ad3r1.octojotter.ui.editor.outdent
import com.l3ad3r1.octojotter.ui.editor.toggleBlock
import com.l3ad3r1.octojotter.ui.editor.toggleInline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Selection and caret rules for the editor toolbar's Markdown transforms. */
class MarkdownEditingTest {

    private fun value(text: String, start: Int = text.length, end: Int = start) =
        TextFieldValue(text, TextRange(start, end))

    // --- inline ------------------------------------------------------------

    @Test
    fun `bold wraps the selection and keeps it selected`() {
        val result = toggleInline(value("hello world", 0, 5), InlineFormat.Bold)
        assertEquals("**hello** world", result.text)
        assertEquals("hello", result.text.substring(result.selection.min, result.selection.max))
    }

    @Test
    fun `bold twice removes the markers`() {
        val once = toggleInline(value("hello world", 0, 5), InlineFormat.Bold)
        val twice = toggleInline(once, InlineFormat.Bold)
        assertEquals("hello world", twice.text)
    }

    @Test
    fun `bold with no selection wraps the word under the caret`() {
        val result = toggleInline(value("hello world", 3), InlineFormat.Bold)
        assertEquals("**hello** world", result.text)
    }

    @Test
    fun `bold on empty text leaves the caret between the markers`() {
        val result = toggleInline(value("", 0), InlineFormat.Bold)
        assertEquals("****", result.text)
        assertEquals(2, result.selection.start)
    }

    @Test
    fun `unwrapping works when the markers sit outside the selection`() {
        // "bold" selected inside "**bold**"
        val result = toggleInline(value("**bold**", 2, 6), InlineFormat.Bold)
        assertEquals("bold", result.text)
    }

    @Test
    fun `superscript and highlight use their own tokens`() {
        assertEquals("x^2^", toggleInline(value("x2", 1, 2), InlineFormat.Superscript).text)
        assertEquals("==key==", toggleInline(value("key", 0, 3), InlineFormat.Highlight).text)
    }

    // --- blocks ------------------------------------------------------------

    @Test
    fun `bullet applies to every line the selection touches`() {
        val result = toggleBlock(value("one\ntwo\nthree", 0, 9), BlockFormat.Bullet)
        assertEquals("- one\n- two\n- three", result.text)
    }

    @Test
    fun `bullet toggles back off`() {
        val on = toggleBlock(value("one\ntwo", 0, 7), BlockFormat.Bullet)
        val off = toggleBlock(on, BlockFormat.Bullet)
        assertEquals("one\ntwo", off.text)
    }

    @Test
    fun `ordered list numbers the lines sequentially`() {
        val result = toggleBlock(value("one\ntwo\nthree", 0, 13), BlockFormat.Ordered)
        assertEquals("1. one\n2. two\n3. three", result.text)
    }

    @Test
    fun `list markers replace each other instead of stacking`() {
        val bullets = toggleBlock(value("milk\neggs", 0, 9), BlockFormat.Bullet)
        val tasks = toggleBlock(bullets, BlockFormat.Task)
        assertEquals("- [ ] milk\n- [ ] eggs", tasks.text)
    }

    @Test
    fun `headings replace each other`() {
        val h1 = toggleBlock(value("Title", 0), BlockFormat.H1)
        assertEquals("# Title", h1.text)
        val h2 = toggleBlock(h1, BlockFormat.H2)
        assertEquals("## Title", h2.text)
    }

    @Test
    fun `blank lines are left alone`() {
        val result = toggleBlock(value("one\n\ntwo", 0, 8), BlockFormat.Bullet)
        assertEquals("- one\n\n- two", result.text)
    }

    @Test
    fun `indent and outdent round-trip`() {
        val indented = indent(value("one\ntwo", 0, 7))
        assertEquals("  one\n  two", indented.text)
        assertEquals("one\ntwo", outdent(indented).text)
    }

    // --- snippets ----------------------------------------------------------

    @Test
    fun `table snippet lands on its own line with the caret in the first cell`() {
        val result = insertBlockSnippet(value("intro", 5), MarkdownSnippets.TABLE)
        assertTrue(result.text.startsWith("intro\n| Column | Column |"))
        assertTrue("$0 placeholder must be stripped", !result.text.contains("$0"))
        assertEquals("| ", result.text.substring(result.selection.start - 2, result.selection.start))
    }

    @Test
    fun `link keeps the selection as the label and selects the url`() {
        val result = insertLink(value("Anthropic", 0, 9))
        assertEquals("[Anthropic](url)", result.text)
        assertEquals("url", result.text.substring(result.selection.min, result.selection.max))
    }

    // --- active state ------------------------------------------------------

    @Test
    fun `caret inside bold reports bold and not italic`() {
        val active = activeInlineFormats(value("**bold**", 4))
        assertTrue(InlineFormat.Bold in active)
        assertTrue("'*' must not double-match inside '**'", InlineFormat.Italic !in active)
    }

    @Test
    fun `active block format reads the caret's line`() {
        assertEquals(BlockFormat.Task, activeBlockFormat(value("- [ ] buy milk", 8)))
        assertEquals(BlockFormat.H2, activeBlockFormat(value("## Heading", 5)))
        assertEquals(BlockFormat.Ordered, activeBlockFormat(value("1. first", 5)))
        assertEquals(null, activeBlockFormat(value("plain text", 5)))
    }

    @Test
    fun `bullet is not reported for a task line`() {
        assertEquals(BlockFormat.Task, activeBlockFormat(value("- [x] done", 8)))
    }
}
