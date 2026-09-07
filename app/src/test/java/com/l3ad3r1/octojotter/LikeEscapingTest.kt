package com.l3ad3r1.octojotter

import com.l3ad3r1.octojotter.data.repository.escapeLike
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Search and backlink queries interpolate user text into a SQL `LIKE` pattern.
 * Unescaped, `%` and `_` from a note title or a search box act as wildcards:
 * searching "TODO_2" also matched "TODO-2", and a wikilink to `[[draft_v1]]`
 * pulled in backlinks meant for `[[draft-v1]]`.
 */
class LikeEscapingTest {

    @Test
    fun `ordinary text is untouched`() {
        assertEquals("meeting notes", escapeLike("meeting notes"))
    }

    @Test
    fun `underscore is escaped so it matches itself`() {
        assertEquals("draft\\_v1", escapeLike("draft_v1"))
    }

    @Test
    fun `percent is escaped so it does not match everything`() {
        assertEquals("100\\% done", escapeLike("100% done"))
    }

    @Test
    fun `the escape character itself is escaped first`() {
        // Order matters: escaping the backslash after the wildcards would
        // double-escape the ones just inserted and break the pattern.
        assertEquals("path\\\\to", escapeLike("path\\to"))
    }

    @Test
    fun `a mix of all three is escaped consistently`() {
        assertEquals("a\\\\b\\%c\\_d", escapeLike("a\\b%c_d"))
    }

    @Test
    fun `empty input stays empty`() {
        assertEquals("", escapeLike(""))
    }
}
