package com.arst.diabetesassistant.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import com.arst.diabetesassistant.data.Medication
import com.arst.diabetesassistant.data.Store
import com.arst.diabetesassistant.ui.MainActivity
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Two kinds of alarms:
 *  - MAIN: one per (medication, time-of-day); fires daily, re-arms itself for the next day.
 *  - NAG : repeat reminder for a specific dose until it is confirmed / skipped.
 */
object Scheduler {
    private const val TAG = "Scheduler"
    const val ACTION_MAIN = "com.arst.diabetesassistant.MAIN"
    const val ACTION_NAG = "com.arst.diabetesassistant.NAG"
    const val EXTRA_MED = "med"
    const val EXTRA_MINUTE = "minute"
    const val EXTRA_AT = "at"
    const val EXTRA_COUNT = "count"

    fun atToday(minute: Int, date: LocalDate = LocalDate.now()): Long =
        ZonedDateTime.of(date, LocalTime.of(minute / 60, minute % 60), ZoneId.systemDefault())
            .toInstant().toEpochMilli()

    /** Next occurrence strictly after [after]. */
    fun nextOccurrence(minute: Int, after: Long = System.currentTimeMillis()): Long {
        var date = LocalDate.now()
        var t = atToday(minute, date)
        while (t <= after) {
            date = date.plusDays(1); t = atToday(minute, date)
        }
        return t
    }

    private fun mainIntent(ctx: Context, medId: Long, minute: Int, at: Long = 0) =
        Intent(ctx, AlarmReceiver::class.java).apply {
            action = ACTION_MAIN
            data = Uri.parse("dose://main/$medId/$minute")
            putExtra(EXTRA_MED, medId)
            putExtra(EXTRA_MINUTE, minute)
            putExtra(EXTRA_AT, at)
        }

    private fun nagIntent(ctx: Context, medId: Long, at: Long, count: Int = 0) =
        Intent(ctx, AlarmReceiver::class.java).apply {
            action = ACTION_NAG
            data = Uri.parse("dose://nag/$medId/$at")
            putExtra(EXTRA_MED, medId)
            putExtra(EXTRA_AT, at)
            putExtra(EXTRA_COUNT, count)
        }

    private fun pi(ctx: Context, intent: Intent, flags: Int = PendingIntent.FLAG_UPDATE_CURRENT): PendingIntent? =
        PendingIntent.getBroadcast(ctx, 0, intent, flags or PendingIntent.FLAG_IMMUTABLE)

    fun canExact(ctx: Context): Boolean {
        val am = ctx.getSystemService(AlarmManager::class.java)
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
    }

    private fun setExact(ctx: Context, at: Long, op: PendingIntent) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        try {
            if (canExact(ctx)) {
                // setAlarmClock = most reliable (survives Doze), shows alarm icon in status bar
                val show = PendingIntent.getActivity(
                    ctx, 1, Intent(ctx, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), op)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, op)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "exact alarm not allowed, falling back", e)
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, op)
        }
    }

    fun scheduleMain(ctx: Context, medId: Long, minute: Int) {
        val at = nextOccurrence(minute)
        val op = pi(ctx, mainIntent(ctx, medId, minute, at)) ?: return
        setExact(ctx, at, op)
    }

    fun cancelMain(ctx: Context, medId: Long, minute: Int) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        pi(ctx, mainIntent(ctx, medId, minute), PendingIntent.FLAG_NO_CREATE)?.let { am.cancel(it); it.cancel() }
    }

    fun scheduleNag(ctx: Context, medId: Long, scheduledAt: Long, count: Int, fireAt: Long) {
        val op = pi(ctx, nagIntent(ctx, medId, scheduledAt, count)) ?: return
        setExact(ctx, fireAt, op)
    }

    fun cancelNag(ctx: Context, medId: Long, scheduledAt: Long) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        pi(ctx, nagIntent(ctx, medId, scheduledAt), PendingIntent.FLAG_NO_CREATE)?.let { am.cancel(it); it.cancel() }
    }

    fun scheduleMed(ctx: Context, med: Medication) {
        if (!med.active) return
        med.times.forEach { scheduleMain(ctx, med.id, it) }
    }

    fun cancelMed(ctx: Context, med: Medication) {
        med.times.forEach { cancelMain(ctx, med.id, it) }
    }

    /** Re-arm everything (app start, boot, time change, permission granted). */
    fun rescheduleAll(ctx: Context) {
        Store.current.meds.forEach { if (it.active) scheduleMed(ctx, it) }
    }
}
