package com.l3ad3r1.octojotter.ui

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.l3ad3r1.octojotter.data.local.PluginEntity
import com.l3ad3r1.octojotter.plugin.PluginTypes
import com.l3ad3r1.octojotter.plugin.RegistryEntry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityPluginsScreen(viewModel: NoteViewModel, onNavigateBack: () -> Unit = {}) {
    val installed by viewModel.installedPlugins.collectAsState()
    val registry by viewModel.registryPlugins.collectAsState()
    val isLoading by viewModel.isLoadingPlugins.collectAsState()
    val message by viewModel.pluginMessage.collectAsState()

    // Load the community registry the first time the screen opens.
    LaunchedEffect(Unit) {
        if (registry.isEmpty()) viewModel.refreshPluginRegistry()
    }

    val installedById = installed.associateBy { it.id }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Community Plugins", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("plugins_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.refreshPluginRegistry() },
                        modifier = Modifier.testTag("plugins_refresh_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh registry")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Extend Octo Jotter with themes and packs shared by the community. Install one, then toggle it on. Only trusted, declarative plugins for now — no third-party code runs on your device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            message?.let { msg ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (msg.contains("fail", ignoreCase = true))
                            MaterialTheme.colorScheme.errorContainer
                        else MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(msg, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        IconButton(onClick = { viewModel.clearPluginMessage() }) {
                            Icon(Icons.Default.Check, contentDescription = "Dismiss", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            // ---- Installed ----
            if (installed.isNotEmpty()) {
                SectionHeader("Installed")
                installed.forEach { plugin ->
                    InstalledPluginRow(
                        plugin = plugin,
                        onToggle = { viewModel.setPluginEnabled(plugin, it) },
                        onRemove = { viewModel.uninstallPlugin(plugin) }
                    )
                }
            }

            // ---- Browse ----
            SectionHeader("Browse")
            if (isLoading && registry.isEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Loading plugins…", style = MaterialTheme.typography.bodyMedium)
                }
            } else if (registry.isEmpty()) {
                Text(
                    "No plugins found. Pull to refresh, or check your connection.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                registry.forEach { entry ->
                    RegistryPluginRow(
                        entry = entry,
                        installed = installedById[entry.id] != null,
                        onInstall = { viewModel.installPlugin(entry) }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun pluginIcon(type: String) =
    if (type == PluginTypes.THEME) Icons.Default.Palette else Icons.Default.Extension

@Composable
private fun InstalledPluginRow(
    plugin: PluginEntity,
    onToggle: (Boolean) -> Unit,
    onRemove: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth().testTag("installed_plugin_${plugin.id}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(pluginIcon(plugin.type), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(plugin.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${plugin.type} · v${plugin.version}" + (plugin.author?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Switch(
                checked = plugin.enabled,
                onCheckedChange = onToggle,
                modifier = Modifier.testTag("plugin_toggle_${plugin.id}")
            )
            IconButton(onClick = onRemove, modifier = Modifier.testTag("plugin_remove_${plugin.id}")) {
                Icon(Icons.Default.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun RegistryPluginRow(
    entry: RegistryEntry,
    installed: Boolean,
    onInstall: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth().testTag("registry_plugin_${entry.id}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(pluginIcon(entry.type), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                entry.description?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    "${entry.type}" + (entry.author?.let { " · $it" } ?: "") + (entry.version?.let { " · v$it" } ?: ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            if (installed) {
                Icon(Icons.Default.Check, contentDescription = "Installed", tint = MaterialTheme.colorScheme.primary)
            } else {
                Button(onClick = onInstall, modifier = Modifier.testTag("plugin_install_${entry.id}")) {
                    Text("Install")
                }
            }
        }
    }
}
