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
    enableEdgeToEdge()
    setContent {
      val themeMode by viewModel.themeMode.collectAsState()
      val pluginTheme by viewModel.activePluginTheme.collectAsState()
      // An enabled theme plugin overrides both the palette and dark/light.
      val darkTheme = pluginTheme?.dark ?: when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
      }
      MyApplicationTheme(
        darkTheme = darkTheme,
        overrideColorScheme = pluginTheme?.colorScheme
      ) {
        NoteApp(viewModel = viewModel)
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleSharedText(intent)
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
}
