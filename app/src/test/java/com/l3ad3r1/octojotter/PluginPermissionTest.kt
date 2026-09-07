package com.l3ad3r1.octojotter

import com.l3ad3r1.octojotter.plugin.PluginPermissions
import com.l3ad3r1.octojotter.plugin.grantedPermissions
import com.l3ad3r1.octojotter.plugin.undeclaredPermissions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Consent is collected against a plugin's *registry listing*; capability used
 * to be granted from its *manifest*, a different file fetched from a URL the
 * listing names. Nothing reconciled the two, so a plugin whose listing declared
 * no permissions installed with no consent dialog at all and still received
 * whatever its manifest asked for. These tests pin the reconciliation.
 */
class PluginPermissionTest {

    private val read = PluginPermissions.NOTES_READ
    private val write = PluginPermissions.NOTES_WRITE

    @Test
    fun `a silent listing cannot smuggle permissions through its manifest`() {
        // The escalation, exactly: empty listing means no consent dialog is
        // shown at all, so nothing here may be granted.
        val consented = emptyList<String>()
        val requested = listOf(read, write)

        assertEquals(listOf(read, write), undeclaredPermissions(consented, requested))
        assertEquals(emptyList<String>(), grantedPermissions(consented, requested))
    }

    @Test
    fun `a manifest matching its listing is granted in full`() {
        val consented = listOf(read, write)
        assertTrue(undeclaredPermissions(consented, listOf(read, write)).isEmpty())
        assertEquals(listOf(read, write), grantedPermissions(consented, listOf(read, write)))
    }

    @Test
    fun `widening beyond the listing is caught even when partly overlapping`() {
        // The subtle case: the user consented to read, so a dialog *was* shown.
        // The manifest quietly adds write on top.
        val consented = listOf(read)
        val requested = listOf(read, write)

        assertEquals(listOf(write), undeclaredPermissions(consented, requested))
        assertEquals(listOf(read), grantedPermissions(consented, requested))
    }

    @Test
    fun `asking for less than was consented to grants only what was asked`() {
        val consented = listOf(read, write)
        assertTrue(undeclaredPermissions(consented, listOf(read)).isEmpty())
        assertEquals(listOf(read), grantedPermissions(consented, listOf(read)))
    }

    @Test
    fun `an unknown permission string is still gated by the listing`() {
        // Forward compatibility must not become a bypass: a permission this
        // build doesn't recognise is treated like any other.
        val consented = listOf(read)
        assertEquals(listOf("notes:delete"), undeclaredPermissions(consented, listOf("notes:delete")))
        assertEquals(emptyList<String>(), grantedPermissions(consented, listOf("notes:delete")))
    }

    @Test
    fun `duplicates in a manifest are collapsed`() {
        val consented = listOf(read)
        assertEquals(listOf(read), grantedPermissions(consented, listOf(read, read, read)))
    }
}
