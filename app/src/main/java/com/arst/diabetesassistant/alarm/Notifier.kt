package com.arst.diabetesassistant.alarm

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.arst.diabetesassistant.R
import com.arst.diabetesassistant.data.DoseStatus
import com.arst.diabetesassistant.data.MedType
import com.arst.diabetesassistant.data.Medication
import com.arst.diabetesassistant.ui.AlarmActivity
import com.arst.diabetesassistant.util.localized
import com.arst.diabetesassistant.util.timeOf

object Notifier {
    const val CH_ALARM = "dose_alarm_v1"
    const val CH_INFO = "dose_info_v1"

    fun createChannels(ctx: Context) {
        val c = ctx.localized()
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val alarm = NotificationChannel(CH_ALARM, c.getString(R.string.channel_alarm), NotificationManager.IMPORTANCE_HIGH).apply {
            description = c.getString(R.string.channel_alarm_desc)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 600, 400, 600, 400, 600)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            setBypassDnd(true)
        }
        val info = NotificationChannel(CH_INFO, c.getString(R.string.channel_info), NotificationManager.IMPORTANCE_DEFAULT)
        nm.createNotificationChannel(alarm)
        nm.createNotificationChannel(info)
    }

    fun notifId(medId: Long, at: Long): Int = "$medId@$at".hashCode()

    fun canPost(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun actionPi(ctx: Context, action: String, med: Medication, at: Long): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, 0,
            Intent(ctx, ActionReceiver::class.java).apply {
                this.action = action
                data = Uri.parse("dose://act/$action/${med.id}/$at")
                putExtra(Scheduler.EXTRA_MED, med.id)
                putExtra(Scheduler.EXTRA_AT, at)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    fun showDose(ctx: Context, med: Medication, at: Long, repeat: Int) {
        if (!canPost(ctx)) return
        val c = ctx.localized()
        val full = PendingIntent.getActivity(
            ctx, 0,
            AlarmActivity.intent(ctx, med.id, at),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val title = c.getString(
            if (med.type == MedType.INSULIN) R.string.notif_title_insulin else R.string.notif_title_pill,
            med.name
        )
        var text = c.getString(R.string.notif_text, med.dose, timeOf(at))
        if (repeat > 0) text = c.getString(R.string.notif_repeat, com.arst.diabetesassistant.util.num(repeat)) + " — " + text

        val n = NotificationCompat.Builder(ctx, CH_ALARM)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(false)
            .setContentIntent(full)
            .setFullScreenIntent(full, true)
            .addAction(0, c.getString(R.string.btn_taken), actionPi(ctx, ActionReceiver.ACTION_TAKEN, med, at))
            .addAction(0, c.getString(R.string.btn_skip), actionPi(ctx, ActionReceiver.ACTION_SKIP, med, at))
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(notifId(med.id, at), n)
        } catch (_: SecurityException) {
        }
    }

    fun showMissed(ctx: Context, med: Medication, at: Long) {
        if (!canPost(ctx)) return
        val c = ctx.localized()
        val open = PendingIntent.getActivity(
            ctx, 0, AlarmActivity.intent(ctx, med.id, at),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(ctx, CH_INFO)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(c.getString(R.string.notif_missed_title, med.name))
            .setContentText(c.getString(R.string.notif_missed_text, timeOf(at)))
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(0, c.getString(R.string.btn_taken_late), actionPi(ctx, ActionReceiver.ACTION_TAKEN, med, at))
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(notifId(med.id, at), n)
        } catch (_: SecurityException) {
        }
    }

    fun cancel(ctx: Context, medId: Long, at: Long) {
        NotificationManagerCompat.from(ctx).cancel(notifId(medId, at))
    }
}

/** Single place that records a user decision about a dose. */
object DoseActions {
    fun mark(ctx: Context, medId: Long, at: Long, status: DoseStatus) {
        com.arst.diabetesassistant.data.Store.setDose(
            com.arst.diabetesassistant.data.DoseEvent(medId, at, status, System.currentTimeMillis())
        )
        Scheduler.cancelNag(ctx, medId, at)
        Notifier.cancel(ctx, medId, at)
        AlarmActivity.finishFor(medId, at)
        com.arst.diabetesassistant.widget.NextDoseWidget.updateAll(ctx)
    }
}
