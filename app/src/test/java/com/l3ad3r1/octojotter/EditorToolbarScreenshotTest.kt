package com.l3ad3r1.octojotter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.l3ad3r1.octojotter.ui.EditorInputs
import com.l3ad3r1.octojotter.ui.MarkdownPreview
import com.l3ad3r1.octojotter.ui.editor.EditorToolbar
import com.l3ad3r1.octojotter.ui.editor.PluginAction
import com.l3ad3r1.octojotter.ui.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the redesigned editor chrome to PNGs under `src/test/screenshots/`.
 * No emulator required — Robolectric + Roborazzi draw the real Compose tree.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class EditorToolbarScreenshotTest {

    @get:Rule val composeTestRule = createComposeRule()

    private val sample = TextFieldValue(
        text = "**Design notes** for the editor rebuild",
        selection = TextRange(0, 16) // selects "**Design notes**" → Bold shows active
    )

    @Test
    fun toolbar_dark() {
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = true) {
                Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
                    Toolbar(sample)
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/editor_toolbar_dark.png")
    }

    @Test
    fun toolbar_light() {
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = false) {
                Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
                    Toolbar(sample)
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/editor_toolbar_light.png")
    }

    /** The editing surface: title, tags, folder, markdown body, toolbar. */
    @Test
    fun editor_surface_dark() {
        val body = TextFieldValue(
            text = "## Sync design\n\n" +
                "- [ ] reconcile ==conflicts== before push\n" +
                "- [x] hash content with SHA-256\n\n" +
                "> [!NOTE]\n> Repo mode uses the Contents API.\n",
            selection = TextRange(20)
        )
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = true) {
                androidx.compose.material3.Scaffold(
                    bottomBar = { Toolbar(body) }
                ) { padding ->
                    Column(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.background)
                            .padding(padding)
                    ) {
                        EditorInputs(
                            title = "Sync design",
                            textFieldValue = body,
                            onTitleChanged = {},
                            onContentChanged = {},
                            fontSize = 16,
                            monospace = true
                        )
                    }
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/editor_surface_dark.png")
    }

    /** Front matter renders as a Properties card, never as body text. */
    @Test
    fun preview_properties_dark() {
        val markdown = """
            ---
            title: Weekly review
            created: 2026-07-28
            done: true
            tags:
              - work
              - review
            aliases: [Review, Weekly]
            meta:
              author: jo
              level: 2
            ---
            # Week 31

            Body text after the properties block.
        """.trimIndent()

        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = true) {
                MarkdownPreview(
                    markdown = markdown,
                    modifier = Modifier.background(MaterialTheme.colorScheme.background)
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/preview_properties_dark.png")
    }

    /** The preview renderer, exercising every block the new toolbar can emit. */
    @Test
    fun preview_blocks_dark() {
        val markdown = """
            # Release checklist

            Ship ==v2.7== with H2O^2^ and CO~2~ notes.

            > [!WARNING]
            > The upload keystore password is still in PROGRESS.md.

            1. Cut the tag
            2. Build the APK

            - [x] Toolbar rebuilt
            - [ ] Icon regenerated

            | Item | State |
            | --- | --- |
            | Toolbar | done |
            | Icon | pending |

            ```kotlin
            fun main() = println("octo")
            ```

            ---
        """.trimIndent()

        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = true) {
                MarkdownPreview(
                    markdown = markdown,
                    modifier = Modifier.background(MaterialTheme.colorScheme.background)
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/editor_preview_dark.png")
    }

    @androidx.compose.runtime.Composable
    private fun Toolbar(value: TextFieldValue) {
        EditorToolbar(
            value = value,
            onValueChange = {},
            onPickImage = {},
            onDraw = {},
            fontSize = 16,
            onFontSizeChange = {},
            monospace = true,
            onMonospaceChange = {},
            canUndo = true,
            onUndo = {},
            canRedo = false,
            onRedo = {},
            pluginActions = listOf(PluginAction("cmd_text-tools_upper", "UPPERCASE", true)),
            onPluginAction = {}
        )
    }
}
