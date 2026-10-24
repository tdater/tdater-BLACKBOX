package com.tdater.blackbox.system

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.tdater.blackbox.BlackboxApp
import com.tdater.blackbox.R
import com.tdater.blackbox.core.model.Investigation
import com.tdater.blackbox.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Local follow-up reminders. Inexact alarms (no exact-alarm permission), one notification per investigation.
 * Nothing leaves the phone; reminders are rebuilt from the database after a reboot.
 */
class Reminders(private val context: Context) {
    private val alarms get() = context.getSystemService(AlarmManager::class.java)

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, "Follow-up reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Reminders you set to come back to a problem"
                })
        }
    }

    fun canNotify(): Boolean = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun schedule(inv: Investigation) {
        val at = inv.followUpAt ?: return cancel(inv.id)
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(inv))
    }

    fun cancel(iid: String) {
        alarms.cancel(PendingIntent.getBroadcast(context, iid.hashCode(), Intent(context, ReminderReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        NotificationManagerCompat.from(context).cancel(iid.hashCode())
    }

    private fun pending(inv: Investigation) = PendingIntent.getBroadcast(
        context, inv.id.hashCode(),
        Intent(context, ReminderReceiver::class.java)
            .putExtra(EXTRA_PID, inv.projectId).putExtra(EXTRA_IID, inv.id)
            .putExtra(EXTRA_TITLE, inv.title).putExtra(EXTRA_NOTE, inv.followUpNote),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun show(pid: String, iid: String, title: String, note: String) {
        if (!canNotify()) return
        ensureChannel()
        val open = PendingIntent.getActivity(context, iid.hashCode(),
            Intent(context, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(EXTRA_PID, pid).putExtra(EXTRA_IID, iid),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_blackbox)
            .setContentTitle("Follow up: $title")
            .setContentText(note.ifBlank { "You asked BLACKBOX to remind you to come back to this." })
            .setStyle(NotificationCompat.BigTextStyle().bigText(note.ifBlank { "You asked BLACKBOX to remind you to come back to this." }))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try { NotificationManagerCompat.from(context).notify(iid.hashCode(), n) } catch (_: SecurityException) { }
    }

    companion object {
        const val CHANNEL = "followups"
        const val EXTRA_PID = "com.tdater.blackbox.PID"
        const val EXTRA_IID = "com.tdater.blackbox.IID"
        const val EXTRA_TITLE = "com.tdater.blackbox.TITLE"
        const val EXTRA_NOTE = "com.tdater.blackbox.NOTE"
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pid = intent.getStringExtra(Reminders.EXTRA_PID) ?: return
        val iid = intent.getStringExtra(Reminders.EXTRA_IID) ?: return
        Reminders(context).show(pid, iid, intent.getStringExtra(Reminders.EXTRA_TITLE).orEmpty(),
            intent.getStringExtra(Reminders.EXTRA_NOTE).orEmpty())
    }
}

/** Alarms don't survive a reboot; reschedule every future follow-up from the database. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val app = context.applicationContext as BlackboxApp
        val done = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val r = Reminders(context)
                val now = System.currentTimeMillis()
                app.repository.followUps().forEach { inv ->
                    if ((inv.followUpAt ?: 0) > now) r.schedule(inv)
                }
            } catch (_: Exception) {
                // Locked/encrypted storage before first unlock, etc. Reminders return on the next app start.
            } finally {
                done.finish()
            }
        }
    }
}
