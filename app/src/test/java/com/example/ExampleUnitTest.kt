package com.example

import com.example.data.local.NoteEntity
import org.junit.Assert.*
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun noteEntity_folderPathParsing_isCorrect() {
    // Test simple title with no delimiters
    val note1 = NoteEntity(title = "Simple Note", content = "Content")
    assertTrue(note1.folderPath.isEmpty())
    assertEquals("Simple Note", note1.displayTitle)

    // Test title with single folder delimiter
    val note2 = NoteEntity(title = "Personal__My Note", content = "Content")
    assertEquals(listOf("Personal"), note2.folderPath)
    assertEquals("My Note", note2.displayTitle)

    // Test title with nested folder delimiters
    val note3 = NoteEntity(title = "Work__Projects__Jetpack Compose__Task Tracker", content = "Content")
    assertEquals(listOf("Work", "Projects", "Jetpack Compose"), note3.folderPath)
    assertEquals("Task Tracker", note3.displayTitle)

    // Test title with consecutive delimiters or trailing/leading ones
    val note4 = NoteEntity(title = "__Folder__Note", content = "Content")
    assertEquals(listOf("", "Folder"), note4.folderPath)
    assertEquals("Note", note4.displayTitle)
  }
}
