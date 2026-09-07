package com.l3ad3r1.octojotter.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.l3ad3r1.octojotter.MainActivity
import com.l3ad3r1.octojotter.R
import com.l3ad3r1.octojotter.data.local.AppDatabase
import com.l3ad3r1.octojotter.plugin.FeaturePluginIds

/**
 * Fires the local notification for one note's Task Reminders due date.
 * Reads [ReminderScheduler.EXTRA_NOTE_ID]/[ReminderScheduler.EXTRA_NOTE_TITLE]
 * from its input data rather than re-reading the note, so a reminder still
 * fires with the title it was set against even if the note was renamed since.
 */
class ReminderWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val remindersEnabled = AppDatabase.getDatabase(applicationContext)
            .pluginDao().getPluginById(FeaturePluginIds.TASK_REMINDERS)?.enabled ?: false
        if (!remindersEnabled) return Result.success()

        val noteId = inputData.getInt(ReminderScheduler.EXTRA_NOTE_ID, -1)
        if (noteId < 0) return Result.success()
        val title = inputData.getString(ReminderScheduler.EXTRA_NOTE_TITLE) ?: "Reminder"

        // The reminder may have been cleared or rescheduled after this work was
        // enqueued but before it ran; re-check the note is still due right now.
        val note = AppDatabase.getDatabase(applicationContext).noteDao().getNoteById(noteId)
        val dueAt = note?.reminderAt
        if (note == null || dueAt == null || dueAt > System.currentTimeMillis() + GRACE_MS) {
            return Result.success()
        }

        postNotification(noteId, title)
        return Result.success()
    }

    private fun postNotification(noteId: Int, title: String) {
        val context = applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) {
            return // declined the permission; nothing to post
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Note reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Reminders you set on individual notes." }
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_NOTE_ID, noteId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            noteId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Reminder: $title")
            .setContentText("Tap to open this note.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        androidx.core.app.NotificationManagerCompat.from(context).notify(noteId, notification)
    }

    companion object {
        const val CHANNEL_ID = "note_reminders"
        private const val GRACE_MS = 60_000L // tolerate up to a minute of scheduling jitter
    }
}
