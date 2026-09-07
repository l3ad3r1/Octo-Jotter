package com.l3ad3r1.octojotter.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Picks a date + time for a note's Task Reminders reminder, using the
 * platform's own [DatePickerDialog]/[TimePickerDialog] — Compose Material3
 * has no ready-made combined date+time picker, and these are the pickers
 * users already know from every other Android app.
 */
@Composable
fun ReminderPickerDialog(
    initialMillis: Long?,
    onDismiss: () -> Unit,
    onConfirm: (millis: Long?) -> Unit
) {
    val context = LocalContext.current
    val calendar = remember {
        Calendar.getInstance().apply {
            timeInMillis = initialMillis ?: (System.currentTimeMillis() + 60 * 60 * 1000L)
        }
    }
    var selectedMillis by remember { mutableStateOf(initialMillis) }
    val displayFormat = remember { SimpleDateFormat("EEE, MMM d 'at' h:mm a", Locale.getDefault()) }

    fun pickDateThenTime() {
        val cal = Calendar.getInstance().apply { timeInMillis = calendar.timeInMillis }
        DatePickerDialog(
            context,
            { _, year, month, day ->
                cal.set(Calendar.YEAR, year)
                cal.set(Calendar.MONTH, month)
                cal.set(Calendar.DAY_OF_MONTH, day)
                TimePickerDialog(
                    context,
                    { _, hour, minute ->
                        cal.set(Calendar.HOUR_OF_DAY, hour)
                        cal.set(Calendar.MINUTE, minute)
                        cal.set(Calendar.SECOND, 0)
                        calendar.timeInMillis = cal.timeInMillis
                        selectedMillis = cal.timeInMillis
                    },
                    cal.get(Calendar.HOUR_OF_DAY),
                    cal.get(Calendar.MINUTE),
                    false
                ).show()
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set reminder") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    selectedMillis?.let { displayFormat.format(it) } ?: "No reminder set",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = { pickDateThenTime() },
                    modifier = Modifier.fillMaxWidth().testTag("pick_reminder_datetime_button")
                ) {
                    Text(if (selectedMillis != null) "Change date & time" else "Pick date & time")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selectedMillis) },
                modifier = Modifier.testTag("confirm_reminder_button")
            ) { Text("Save") }
        },
        dismissButton = {
            if (initialMillis != null) {
                TextButton(onClick = { onConfirm(null) }) {
                    Text("Remove reminder", color = MaterialTheme.colorScheme.error)
                }
            } else {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}
