package com.l3ad3r1.octojotter

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.l3ad3r1.octojotter.ui.NoteApp
import com.l3ad3r1.octojotter.ui.NoteViewModel
import com.l3ad3r1.octojotter.ui.theme.MyApplicationTheme
import androidx.fragment.app.FragmentActivity

class MainActivity : FragmentActivity() {
  private val viewModel: NoteViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    // Must be called before super.onCreate() so the system splash is handed off cleanly.
    installSplashScreen()
    super.onCreate(savedInstanceState)
    if (savedInstanceState == null) {
      handleSharedText(intent)
    }
    handleOpenNoteExtra(intent)
    enableEdgeToEdge()
    setContent {
      val themeMode by viewModel.themeMode.collectAsState()
      val pluginTheme by viewModel.activePluginTheme.collectAsState()
      val appFontId by viewModel.appFontId.collectAsState()
      val appFontScale by viewModel.appFontScale.collectAsState()
      // An enabled theme plugin overrides both the palette and dark/light.
      val darkTheme = pluginTheme?.dark ?: when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
      }
      val appFont = if (appFontId == com.l3ad3r1.octojotter.data.local.ThemePreferences.DEFAULT_FONT_FAMILY) {
        null
      } else {
        com.l3ad3r1.octojotter.ui.theme.AppFontOption.fromId(appFontId).fontFamily
      }
      MyApplicationTheme(
        darkTheme = darkTheme,
        overrideColorScheme = pluginTheme?.colorScheme,
        fontFamily = appFont,
        fontScale = appFontScale,
      ) {
        NoteApp(viewModel = viewModel)
      }
    }
  }

  /**
   * Re-arm the app lock whenever the app leaves the foreground.
   *
   * The ViewModel outlives a stop/start, so without this the lock only ever
   * applied to a cold start: unlock once, background the app, and it stayed
   * unlocked until the process was killed — including when handing the phone
   * to someone else. `onStop` rather than `onPause` so the biometric prompt
   * itself (which pauses the activity) doesn't immediately re-lock behind it.
   */
  override fun onStop() {
    super.onStop()
    viewModel.lockApp()
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleSharedText(intent)
    handleOpenNoteExtra(intent)
  }

  /** Tapping a Task Reminders notification opens straight to that note. */
  private fun handleOpenNoteExtra(intent: Intent?) {
    val noteId = intent?.getIntExtra(EXTRA_OPEN_NOTE_ID, -1) ?: -1
    if (noteId >= 0) viewModel.requestOpenNote(noteId)
  }

  private fun handleSharedText(intent: Intent?) {
    if (intent?.action != Intent.ACTION_SEND) return
    if (!intent.type.orEmpty().startsWith("text/")) return
    val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
    if (sharedText.isBlank()) return
    val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)?.trim().orEmpty()
    viewModel.createNoteFromShare(
      title = subject.ifBlank { sharedText.lineSequence().firstOrNull()?.take(60).orEmpty() },
      sharedText = sharedText
    )
  }

  companion object {
    /** Int extra: opens straight to this note id, used by [com.l3ad3r1.octojotter.reminders.ReminderWorker]'s tap action. */
    const val EXTRA_OPEN_NOTE_ID = "open_note_id"
  }
}
