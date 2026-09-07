package com.l3ad3r1.octojotter.reminders

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Schedules (or cancels) the one-shot [ReminderWorker] for a note's reminder.
 * Uses `enqueueUniqueWork` keyed by note id, so setting a new time — or
 * clearing it — always replaces whatever was scheduled before rather than
 * stacking duplicate notifications.
 */
object ReminderScheduler {
    const val EXTRA_NOTE_ID = "noteId"
    const val EXTRA_NOTE_TITLE = "noteTitle"

    private fun uniqueWorkName(noteId: Int) = "reminder_note_$noteId"

    /** Pass null [reminderAt] to cancel any reminder scheduled for this note. */
    fun schedule(context: Context, noteId: Int, title: String, reminderAt: Long?) {
        val workManager = WorkManager.getInstance(context)
        if (reminderAt == null) {
            workManager.cancelUniqueWork(uniqueWorkName(noteId))
            return
        }
        val delayMs = (reminderAt - System.currentTimeMillis()).coerceAtLeast(0L)
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setInputData(
                Data.Builder()
                    .putInt(EXTRA_NOTE_ID, noteId)
                    .putString(EXTRA_NOTE_TITLE, title)
                    .build()
            )
            .build()
        workManager.enqueueUniqueWork(uniqueWorkName(noteId), ExistingWorkPolicy.REPLACE, request)
    }
}
