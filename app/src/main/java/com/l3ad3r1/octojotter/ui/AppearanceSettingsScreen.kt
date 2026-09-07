package com.l3ad3r1.octojotter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.l3ad3r1.octojotter.data.local.PluginEntity
import com.l3ad3r1.octojotter.data.local.ThemePreferences
import com.l3ad3r1.octojotter.plugin.PluginTypes
import com.l3ad3r1.octojotter.ui.theme.AppFontOption
import com.l3ad3r1.octojotter.ui.theme.OctoShapes
import com.l3ad3r1.octojotter.ui.theme.OctoSpacing

/**
 * Settings → Appearance, nested behind its own screen instead of a single
 * inline toggle: a live preview, the installed theme plugins (once any are
 * downloaded from Community Plugins), dark mode, and the app-wide font.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSettingsScreen(
    viewModel: NoteViewModel,
    onNavigateBack: () -> Unit = {},
) {
    val themeMode by viewModel.themeMode.collectAsState()
    val installedPlugins by viewModel.installedPlugins.collectAsState()
    val appFontId by viewModel.appFontId.collectAsState()
    val appFontScale by viewModel.appFontScale.collectAsState()
    val installedThemes = remember(installedPlugins) { installedPlugins.filter { it.type == PluginTypes.THEME } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Appearance", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("appearance_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ThemePreviewCard()

            // Only once a theme plugin is actually downloaded — a "Themes"
            // section with nothing installed under it would just be empty
            // shelf space above Dark mode.
            if (installedThemes.isNotEmpty()) {
                AppearanceSection(title = "Themes") {
                    installedThemes.forEachIndexed { index, plugin ->
                        if (index > 0) Spacer(modifier = Modifier.height(4.dp))
                        ThemeRow(
                            plugin = plugin,
                            onSelect = { viewModel.setPluginEnabled(plugin, true) },
                        )
                    }
                }
            }

            AppearanceSection(title = "Dark mode") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Dark mode",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Switch(
                        checked = themeMode == ThemePreferences.THEME_DARK,
                        onCheckedChange = { isDark ->
                            viewModel.setThemeMode(if (isDark) ThemePreferences.THEME_DARK else ThemePreferences.THEME_LIGHT)
                        },
                        modifier = Modifier.testTag("theme_toggle")
                    )
                }
            }

            AppearanceSection(title = "Font") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FontRow(
                        label = "Default",
                        selected = appFontId == ThemePreferences.DEFAULT_FONT_FAMILY,
                        onSelect = { viewModel.setAppFont(ThemePreferences.DEFAULT_FONT_FAMILY) },
                        testTag = "font_option_default",
                    )
                    AppFontOption.entries.forEach { option ->
                        FontRow(
                            label = option.label,
                            fontFamily = option.fontFamily,
                            selected = appFontId == option.id,
                            onSelect = { viewModel.setAppFont(option.id) },
                            testTag = "font_option_${option.id}",
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Font size",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${(appFontScale * 100).toInt()}%",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Slider(
                        value = appFontScale,
                        onValueChange = { viewModel.setAppFontScale(it) },
                        valueRange = 0.85f..1.3f,
                        steps = 8,
                        modifier = Modifier.fillMaxWidth().testTag("font_size_slider")
                    )
                    if (appFontScale != ThemePreferences.DEFAULT_FONT_SCALE) {
                        TextButton(onClick = { viewModel.setAppFontScale(ThemePreferences.DEFAULT_FONT_SCALE) }) {
                            Text("Reset to default")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

/** A live sample of the current theme — colors and type update the moment
 *  dark mode, a theme plugin, or the font changes, since it just reads
 *  MaterialTheme like everything else on screen. */
@Composable
private fun ThemePreviewCard() {
    Card(
        shape = OctoShapes.card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth().testTag("theme_preview_card")
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "Notes",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Card(
                shape = OctoShapes.compactCard,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(OctoSpacing.lg)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Weekly Review",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Icon(
                            Icons.Default.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "This is what your notes look like — same colors, same type, live.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = {}, enabled = false) { Text("Save") }
                IconButton(onClick = {}, enabled = false) {
                    Icon(Icons.Default.Add, contentDescription = null)
                }
            }
        }
    }
}

@Composable
private fun AppearanceSection(title: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(
        shape = OctoShapes.card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            content()
        }
    }
}

@Composable
private fun ThemeRow(plugin: PluginEntity, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .testTag("theme_option_${plugin.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(selected = plugin.enabled, onClick = onSelect)
        Column(modifier = Modifier.weight(1f)) {
            Text(plugin.name, style = MaterialTheme.typography.bodyLarge)
            plugin.author?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun FontRow(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
    testTag: String,
    fontFamily: androidx.compose.ui.text.font.FontFamily? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge.let {
                if (fontFamily != null) it.copy(fontFamily = fontFamily) else it
            },
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
