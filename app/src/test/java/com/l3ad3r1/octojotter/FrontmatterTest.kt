package com.l3ad3r1.octojotter

import com.l3ad3r1.octojotter.data.markdown.Frontmatter
import com.l3ad3r1.octojotter.data.markdown.Frontmatter.PropertyValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Front-matter parsing, and above all round-trip fidelity: notes sync to a real
 * Git repository, so reading a note and writing it back must not perturb a
 * single byte the user didn't ask to change.
 */
class FrontmatterTest {

    // --- detection ---------------------------------------------------------

    @Test
    fun `parses a block at the head of the note`() {
        val fm = Frontmatter.parse(
            """
            ---
            title: Weekly review
            ---
            # Body
            """.trimIndent()
        )
        assertNotNull(fm)
        assertEquals(PropertyValue.Text("Weekly review"), fm!!.fields["title"])
    }

    @Test
    fun `a thematic break mid-note is not front matter`() {
        val content = "# Heading\n\nSome text\n\n---\n\nMore text\n"
        assertNull(Frontmatter.parse(content))
        assertEquals(content, Frontmatter.bodyOf(content))
    }

    @Test
    fun `an unclosed block is not front matter`() {
        assertNull(Frontmatter.parse("---\ntitle: x\n\nbody without a closing delimiter\n"))
    }

    @Test
    fun `body starts after the closing delimiter`() {
        val content = "---\ntitle: x\n---\n# Body\n\ntext\n"
        assertEquals("# Body\n\ntext\n", Frontmatter.bodyOf(content))
    }

    @Test
    fun `an empty block parses with no fields`() {
        val fm = Frontmatter.parse("---\n---\nbody\n")
        assertNotNull(fm)
        assertEquals(0, fm!!.fields.size)
        assertEquals("body\n", Frontmatter.bodyOf("---\n---\nbody\n"))
    }

    // --- value types -------------------------------------------------------

    @Test
    fun `reads every supported scalar type`() {
        val fm = Frontmatter.parse(
            """
            ---
            title: Weekly review
            count: 42
            ratio: 1.5
            done: true
            missed: false
            created: 2026-07-28
            starts: 2026-07-28T09:30
            ---
            """.trimIndent()
        )!!
        assertEquals(PropertyValue.Text("Weekly review"), fm.fields["title"])
        assertEquals(PropertyValue.Numeric(42.0), fm.fields["count"])
        assertEquals(PropertyValue.Numeric(1.5), fm.fields["ratio"])
        assertEquals(PropertyValue.Checkbox(true), fm.fields["done"])
        assertEquals(PropertyValue.Checkbox(false), fm.fields["missed"])
        assertEquals(PropertyValue.DateStamp("2026-07-28"), fm.fields["created"])
        assertEquals(PropertyValue.DateStamp("2026-07-28T09:30"), fm.fields["starts"])
    }

    @Test
    fun `reads block and inline lists`() {
        val fm = Frontmatter.parse(
            """
            ---
            tags:
              - work
              - review
            aliases: [Review, Weekly]
            ---
            """.trimIndent()
        )!!
        assertEquals(PropertyValue.Items(listOf("work", "review")), fm.fields["tags"])
        assertEquals(PropertyValue.Items(listOf("Review", "Weekly")), fm.fields["aliases"])
    }

    @Test
    fun `version-like and zero-padded values stay text`() {
        val fm = Frontmatter.parse("---\nversion: 1.2.3\ncode: 0123\n---\n")!!
        assertEquals(PropertyValue.Text("1.2.3"), fm.fields["version"])
        assertEquals(PropertyValue.Text("0123"), fm.fields["code"])
    }

    @Test
    fun `nested maps are kept unparsed rather than mangled`() {
        val fm = Frontmatter.parse(
            """
            ---
            title: x
            meta:
              author: jo
              level: 2
            ---
            """.trimIndent()
        )!!
        assertTrue(fm.fields["meta"] is PropertyValue.Unparsed)
        assertEquals(PropertyValue.Text("x"), fm.fields["title"])
    }

    @Test
    fun `tags and aliases helpers strip hashes and merge singular keys`() {
        val fm = Frontmatter.parse(
            """
            ---
            tags:
              - "#work"
              - review
            alias: Weekly
            ---
            """.trimIndent()
        )!!
        assertEquals(listOf("work", "review"), fm.tags())
        assertEquals(listOf("Weekly"), fm.aliases())
    }

    @Test
    fun `comma separated text reads as a list of tags`() {
        val fm = Frontmatter.parse("---\ntags: work, review\n---\n")!!
        assertEquals(listOf("work", "review"), fm.tags())
    }

    // --- round trip: the invariant that protects the user's vault ----------

    private val corpus = listOf(
        "simple" to "---\ntitle: Note\n---\nbody\n",
        "no trailing newline" to "---\ntitle: Note\n---\nbody",
        "crlf" to "---\r\ntitle: Note\r\ntags:\r\n  - a\r\n---\r\nbody\r\n",
        "block list" to "---\ntags:\n  - work\n  - review\n---\n# Body\n",
        "inline list" to "---\naliases: [A, B]\n---\nbody\n",
        "nested map" to "---\nmeta:\n  author: jo\n  level: 2\n---\nbody\n",
        "comments and blanks" to "---\n# a comment\n\ntitle: x\n\n# another\n---\nbody\n",
        "duplicate keys" to "---\ntitle: one\ntitle: two\n---\nbody\n",
        "unicode" to "---\ntitle: Café — 日本語\ntags:\n  - naïve\n---\nbody\n",
        "quoted values" to "---\ntitle: \"Has: a colon\"\nother: 'single'\n---\nbody\n",
        "empty value" to "---\ntitle:\n---\nbody\n",
        "empty block" to "---\n---\nbody\n",
        "code fence with dashes" to "---\ntitle: x\n---\n```\n---\n```\n",
        "body only" to "# Just a note\n\ntext\n",
        "thematic break" to "text\n\n---\n\nmore\n"
    )

    @Test
    fun `reading and writing back an unchanged property is byte-identical`() {
        for ((name, content) in corpus) {
            val fm = Frontmatter.parse(content)
            if (fm == null) {
                // Nothing to write back; the body accessor must be a no-op.
                assertEquals(name, content, Frontmatter.bodyOf(content))
                continue
            }
            for ((key, value) in fm.fields) {
                assertEquals(
                    "$name: rewriting '$key' with its own value changed the file",
                    content,
                    Frontmatter.replaceField(content, key, value)
                )
            }
        }
    }

    /**
     * The invariant that actually matters, and which the no-op short-circuit
     * above cannot satisfy on its own: changing one property to a genuinely
     * new value must leave every other line — and the whole body — untouched.
     */
    @Test
    fun `changing one property leaves every other line untouched`() {
        for ((name, content) in corpus) {
            val fm = Frontmatter.parse(content) ?: continue
            for ((key, _) in fm.fields) {
                val out = Frontmatter.replaceField(content, key, PropertyValue.Text("CHANGED"))
                assertEquals("$name/$key: body was modified", Frontmatter.bodyOf(content), Frontmatter.bodyOf(out))

                val range = fm.lineRanges.getValue(key)
                val before = fm.rawBlock.split("\n").toMutableList()
                val after = Frontmatter.blockOf(out).split("\n").toMutableList()
                // Drop the edited key's lines from each side; the rest must match.
                repeat(range.last - range.first + 1) { before.removeAt(range.first) }
                after.removeAt(range.first)
                assertEquals("$name/$key: neighbouring lines changed", before, after)
            }
        }
    }

    @Test
    fun `block plus body always reconstructs the original`() {
        for ((name, content) in corpus) {
            assertEquals(name, content, Frontmatter.blockOf(content) + Frontmatter.bodyOf(content))
        }
    }

    // --- writing -----------------------------------------------------------

    @Test
    fun `replacing a value touches only that line`() {
        val content = "---\ntitle: old\ntags:\n  - a\ncount: 1\n---\nbody\n"
        val out = Frontmatter.replaceField(content, "title", PropertyValue.Text("new"))
        assertEquals("---\ntitle: new\ntags:\n  - a\ncount: 1\n---\nbody\n", out)
    }

    @Test
    fun `replacing a block list rewrites the whole list`() {
        val content = "---\ntags:\n  - a\n  - b\ntitle: x\n---\nbody\n"
        val out = Frontmatter.replaceField(content, "tags", PropertyValue.Items(listOf("c")))
        assertEquals("---\ntags:\n  - c\ntitle: x\n---\nbody\n", out)
    }

    @Test
    fun `a new key is appended before the closing delimiter`() {
        val content = "---\ntitle: x\n---\nbody\n"
        val out = Frontmatter.upsertField(content, "count", PropertyValue.Numeric(3.0))
        assertEquals("---\ntitle: x\ncount: 3\n---\nbody\n", out)
    }

    @Test
    fun `a note without front matter gains a block`() {
        val out = Frontmatter.upsertField("# Body\n", "title", PropertyValue.Text("x"))
        assertEquals("---\ntitle: x\n---\n# Body\n", out)
    }

    @Test
    fun `removing a key removes exactly its lines`() {
        val content = "---\ntitle: x\ntags:\n  - a\n  - b\ncount: 1\n---\nbody\n"
        val out = Frontmatter.removeField(content, "tags")
        assertEquals("---\ntitle: x\ncount: 1\n---\nbody\n", out)
    }

    @Test
    fun `writes preserve CRLF line endings`() {
        val content = "---\r\ntitle: old\r\n---\r\nbody\r\n"
        val out = Frontmatter.replaceField(content, "title", PropertyValue.Text("new"))
        assertEquals("---\r\ntitle: new\r\n---\r\nbody\r\n", out)
    }

    @Test
    fun `values needing quotes get them`() {
        val out = Frontmatter.upsertField("---\n---\nbody\n", "title", PropertyValue.Text("Has: a colon"))
        assertEquals("---\ntitle: \"Has: a colon\"\n---\nbody\n", out)
    }

    @Test
    fun `replacing an absent key is a no-op but upsert adds it`() {
        val content = "---\ntitle: x\n---\nbody\n"
        assertEquals(content, Frontmatter.replaceField(content, "nope", PropertyValue.Text("y")))
        assertTrue(Frontmatter.upsertField(content, "nope", PropertyValue.Text("y")).contains("nope: y"))
    }

    @Test
    fun `an unparsed nested map survives a rewrite of its neighbour`() {
        val content = "---\nmeta:\n  author: jo\n  level: 2\ntitle: x\n---\nbody\n"
        val out = Frontmatter.replaceField(content, "title", PropertyValue.Text("y"))
        assertEquals("---\nmeta:\n  author: jo\n  level: 2\ntitle: y\n---\nbody\n", out)
    }
}
