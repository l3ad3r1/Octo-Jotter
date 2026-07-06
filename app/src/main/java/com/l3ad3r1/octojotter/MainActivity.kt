package com.l3ad3r1.octojotter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.l3ad3r1.octojotter.ui.NoteApp
import com.l3ad3r1.octojotter.ui.NoteViewModel
import com.l3ad3r1.octojotter.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  private val viewModel: NoteViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      val themeMode by viewModel.themeMode.collectAsState()
      val darkTheme = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
      }
      MyApplicationTheme(darkTheme = darkTheme) {
        NoteApp(viewModel = viewModel)
      }
    }
  }
}
