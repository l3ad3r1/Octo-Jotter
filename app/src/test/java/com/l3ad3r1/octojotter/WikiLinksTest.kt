package com.l3ad3r1.octojotter

import com.l3ad3r1.octojotter.data.markdown.WikiLink
import com.l3ad3r1.octojotter.data.markdown.WikiLinks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WikiLinksTest {
    @Test fun plainLink() {
        assertEquals(WikiLink("Note", null, null), WikiLinks.parseInner("Note"))
    }

    @Test fun aliasHeadingAndBoth() {
        assertEquals("Shown", WikiLinks.parseInner("Note|Shown")!!.display)
        assertEquals("Note > Intro", WikiLinks.parseInner("Note#Intro")!!.display)
        val both = WikiLinks.parseInner("Note#Intro|Shown")!!
        assertEquals("Note", both.target)
        assertEquals("Intro", both.heading)
        assertEquals("Shown", both.display)
    }

    @Test fun emptyTargetIsNotALink() {
        assertNull(WikiLinks.parseInner("#Heading"))
        assertNull(WikiLinks.parseInner("|alias"))
    }

    @Test fun extractFindsLinksAndEmbeds() {
        val links = WikiLinks.extract("See [[A]], [[B|bee]] and ![[C#x]].\n[[unclosed")
        assertEquals(listOf("A", "B", "C"), links.map { it.target })
        assertEquals(listOf(false, false, true), links.map { it.isEmbed })
    }

    @Test fun targetsAreDistinct() {
        assertEquals(listOf("A", "B"), WikiLinks.targets("[[A]] [[A|x]] [[B#h]]"))
    }

    @Test fun openQuery() {
        assertEquals("", WikiLinks.openQuery("text [["))
        assertEquals("Pro", WikiLinks.openQuery("a [[Done]] b [[Pro"))
        assertNull(WikiLinks.openQuery("a [[Done]] b"))
        assertNull(WikiLinks.openQuery("a [[Pro\nx"))
    }
}
