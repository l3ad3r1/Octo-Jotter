package com.l3ad3r1.octojotter

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.l3ad3r1.octojotter.data.local.NoteEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Octojot", appName)
  }

  @Test
  fun `verify note entity pinned default and copy`() {
    val note = NoteEntity(
        title = "Test Note",
        content = "Test Content"
    )
    assertFalse(note.pinned)

    val pinnedNote = note.copy(pinned = true)
    assertTrue(pinnedNote.pinned)
  }

  @Test
  fun `verify draft entity properties`() {
    val draft = com.l3ad3r1.octojotter.data.local.DraftEntity(
        noteId = 42,
        title = "Draft Title",
        content = "Draft Content"
    )
    assertEquals(42, draft.noteId)
    assertEquals("Draft Title", draft.title)
    assertEquals("Draft Content", draft.content)
  }

  @Test
  fun `verify note entity tags property and default`() {
    val note = NoteEntity(title = "Tag Note", content = "Tag Content")
    assertTrue(note.tags.isEmpty())

    val noteWithTags = note.copy(tags = listOf("work", "important"))
    assertEquals(2, noteWithTags.tags.size)
    assertEquals("work", noteWithTags.tags[0])
    assertEquals("important", noteWithTags.tags[1])
  }

  @Test
  fun `verify room type converters serialization and deserialization`() {
    val converters = com.l3ad3r1.octojotter.data.local.Converters()
    val originalTags = listOf("personal", "ideas", "coding")

    // Serialize
    val jsonString = converters.fromList(originalTags)
    assertTrue(jsonString.contains("personal"))
    assertTrue(jsonString.contains("ideas"))
    assertTrue(jsonString.contains("coding"))

    // Deserialize
    val deserializedTags = converters.fromString(jsonString)
    assertEquals(3, deserializedTags.size)
    assertEquals("personal", deserializedTags[0])
    assertEquals("ideas", deserializedTags[1])
    assertEquals("coding", deserializedTags[2])
  }

  @Test
  fun `verify room type converters empty and null handling`() {
    val converters = com.l3ad3r1.octojotter.data.local.Converters()
    
    // Null and empty serialization
    assertEquals("[]", converters.fromList(null))
    assertEquals("[]", converters.fromList(emptyList()))

    // Null and empty deserialization
    assertTrue(converters.fromString(null).isEmpty())
    assertTrue(converters.fromString("").isEmpty())
    assertTrue(converters.fromString("[]").isEmpty())
    assertTrue(converters.fromString("[invalid-json]").isEmpty())
  }

  @Test
  fun `verify DatabaseBackup model properties`() {
    val backup = com.l3ad3r1.octojotter.data.local.DatabaseBackup(
        exportTime = 123456789L,
        notes = listOf(NoteEntity(id = 1, title = "A", content = "B", tags = listOf("t1"))),
        drafts = listOf(com.l3ad3r1.octojotter.data.local.DraftEntity(noteId = 1, title = "Draft", content = "Draft Content"))
    )
    assertEquals(123456789L, backup.exportTime)
    assertEquals(1, backup.notes.size)
    assertEquals("A", backup.notes[0].title)
    assertEquals("t1", backup.notes[0].tags[0])
    assertEquals(1, backup.drafts.size)
    assertEquals("Draft", backup.drafts[0].title)
  }

  @Test
  fun `verify autoLinkUrls with no URLs`() {
    val input = androidx.compose.ui.text.buildAnnotatedString { append("Hello world with no links!") }
    val result = com.l3ad3r1.octojotter.ui.autoLinkUrls(input, androidx.compose.ui.graphics.Color.Blue)
    assertEquals(input.text, result.text)
    assertTrue(result.getStringAnnotations("URL", 0, result.length).isEmpty())
  }

  @Test
  fun `verify autoLinkUrls with single URL`() {
    val input = androidx.compose.ui.text.buildAnnotatedString { append("Go to https://google.com for info.") }
    val linkColor = androidx.compose.ui.graphics.Color.Blue
    val result = com.l3ad3r1.octojotter.ui.autoLinkUrls(input, linkColor)
    
    assertEquals(input.text, result.text)
    
    val annotations = result.getStringAnnotations("URL", 0, result.length)
    assertEquals(1, annotations.size)
    assertEquals("https://google.com", annotations[0].item)
    assertEquals(6, annotations[0].start)
    assertEquals(24, annotations[0].end)
  }

  @Test
  fun `verify autoLinkUrls with multiple URLs`() {
    val input = androidx.compose.ui.text.buildAnnotatedString { append("Check https://a.com and http://b.org now.") }
    val linkColor = androidx.compose.ui.graphics.Color.Blue
    val result = com.l3ad3r1.octojotter.ui.autoLinkUrls(input, linkColor)
    
    val annotations = result.getStringAnnotations("URL", 0, result.length)
    assertEquals(2, annotations.size)
    assertEquals("https://a.com", annotations[0].item)
    assertEquals("http://b.org", annotations[1].item)
  }

  @Test
  fun `verify theme preferences datastore persistence`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val themePrefs = com.l3ad3r1.octojotter.data.local.ThemePreferences(context)
    
    // Default should be system
    var mode = themePrefs.themeMode.first()
    assertEquals(com.l3ad3r1.octojotter.data.local.ThemePreferences.THEME_SYSTEM, mode)
    
    // Set to dark
    themePrefs.setThemeMode(com.l3ad3r1.octojotter.data.local.ThemePreferences.THEME_DARK)
    mode = themePrefs.themeMode.first()
    assertEquals(com.l3ad3r1.octojotter.data.local.ThemePreferences.THEME_DARK, mode)

    // Set to light
    themePrefs.setThemeMode(com.l3ad3r1.octojotter.data.local.ThemePreferences.THEME_LIGHT)
    mode = themePrefs.themeMode.first()
    assertEquals(com.l3ad3r1.octojotter.data.local.ThemePreferences.THEME_LIGHT, mode)
  }
}
