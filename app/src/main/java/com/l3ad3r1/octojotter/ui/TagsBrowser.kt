package com.l3ad3r1.octojotter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.l3ad3r1.octojotter.ui.theme.OctoShapes
import com.l3ad3r1.octojotter.ui.theme.OctoSpacing

/**
 * The library's Tags destination: every tag the app found across your notes
 * (frontmatter `tags:` lists and inline #hashtags alike — see
 * [com.l3ad3r1.octojotter.data.repository.NoteRepository.scanAndExtractTags]),
 * tap one to drop back into the library filtered to it.
 */
@Composable
fun TagsBrowser(
    tags: List<String>,
    selectedTag: String?,
    onSelectTag: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (tags.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize().padding(OctoSpacing.xxl),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Label,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(40.dp),
                )
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(OctoSpacing.md))
                Text(
                    text = "No tags yet",
                    style = MaterialTheme.typography.titleMedium,
                )
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(OctoSpacing.xs))
                Text(
                    text = "Add a tags: list to a note's properties, or write a #hashtag in its body.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = OctoSpacing.lg,
            vertical = OctoSpacing.md,
        ),
        verticalArrangement = Arrangement.spacedBy(OctoSpacing.sm),
    ) {
        item {
            TagRow(
                label = "All notes",
                selected = selectedTag == null,
                onClick = { onSelectTag(null) },
                testTag = "tags_browser_all",
            )
        }
        items(tags) { tag ->
            TagRow(
                label = tag,
                selected = selectedTag == tag,
                onClick = { onSelectTag(tag) },
                testTag = "tags_browser_$tag",
            )
        }
    }
}

@Composable
private fun TagRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
) {
    val background = if (selected) MaterialTheme.colorScheme.secondaryContainer
    else MaterialTheme.colorScheme.surfaceContainerHigh
    val content = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
    else MaterialTheme.colorScheme.onSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background, OctoShapes.compactCard)
            .clickable(onClick = onClick)
            .padding(horizontal = OctoSpacing.lg, vertical = OctoSpacing.md)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OctoSpacing.md),
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Label,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = content,
        )
    }
}
