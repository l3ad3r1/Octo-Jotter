package com.l3ad3r1.octojotter.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.l3ad3r1.octojotter.ui.theme.OctoSpacing

/**
 * The Library landing header: the "Notes" title and note count, with the
 * sort/view controls on the same bar as the title instead of a separate row
 * underneath. Search and Ask live in the TopAppBar and bottom NavigationBar,
 * and note creation is the FAB — this header used to repeat all three, which
 * meant every one of them was a decision the app asked the user to make twice.
 */
@Composable
fun LibraryHeader(
    noteCount: Int,
    modifier: Modifier = Modifier,
    trailingControls: @Composable RowScope.() -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            // Less top padding than bottom: with no wordmark above it in the
            // TopAppBar any more, "Notes" is the screen's title now, so it
            // sits close under the bar instead of floating mid-gap.
            .padding(start = OctoSpacing.lg, end = OctoSpacing.lg, top = OctoSpacing.xs, bottom = OctoSpacing.md),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Notes",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Row(verticalAlignment = Alignment.CenterVertically, content = trailingControls)
        }
        Spacer(modifier = Modifier.height(OctoSpacing.xs))
        Text(
            text = when {
                noteCount == 0 -> "A quiet place for your next idea"
                noteCount == 1 -> "1 note, ready when you are"
                else -> "$noteCount notes, ready when you are"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
