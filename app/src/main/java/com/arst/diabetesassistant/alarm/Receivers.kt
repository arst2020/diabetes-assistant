package com.arst.diabetesassistant.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.arst.diabetesassistant.data.DoseEvent
import com.arst.diabetesassistant.data.DoseStatus
import com.arst.diabetesassistant.data.Store
import com.arst.diabetesassistant.widget.NextDoseWidget

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Store.init(ctx.applicationContext)
        val medId = intent.getLongExtra(Scheduler.EXTRA_MED, -1)
        when (intent.action) {
            Scheduler.ACTION_MAIN -> onMain(ctx, medId, intent)
            Scheduler.ACTION_NAG -> onNag(ctx, medId, intent)
        }
        NextDoseWidget.updateAll(ctx)
    }

    private fun onMain(ctx: Context, medId: Long, intent: Intent) {
        val minute = intent.getIntExtra(Scheduler.EXTRA_MINUTE, -1)
        val at = intent.getLongExtra(Scheduler.EXTRA_AT, System.currentTimeMillis())
        val med = Store.med(medId)
        if (med == null || !med.active || minute !in med.times) return
        // arm tomorrow's alarm first
        Scheduler.scheduleMain(ctx, medId, minute)

        val existing = Store.dose(medId, at)
        if (existing != null && existing.status != DoseStatus.PENDING) return // already taken/skipped early
        // ignore stale alarms (e.g. delivered hours late after the phone was off)
        if (System.currentTimeMillis() - at > 3L * 3600 * 1000) return
        if (existing == null) Store.setDose(DoseEvent(medId, at, DoseStatus.PENDING))

        Notifier.showDose(ctx, med, at, 0)
        val s = Store.current.settings
        Scheduler.scheduleNag(ctx, medId, at, 1, System.currentTimeMillis() + s.nagMinutes * 60_000L)
    }

    private fun onNag(ctx: Context, medId: Long, intent: Intent) {
        val at = intent.getLongExtra(Scheduler.EXTRA_AT, 0)
        val count = intent.getIntExtra(Scheduler.EXTRA_COUNT, 1)
        val med = Store.med(medId) ?: return
        val ev = Store.dose(medId, at) ?: return
        if (ev.status != DoseStatus.PENDING) return
        val s = Store.current.settings
        if (count > s.maxNags) {
            Store.setDose(ev.copy(status = DoseStatus.MISSED))
            AlarmActivityBridge.close(medId, at)
            Notifier.showMissed(ctx, med, at)
            return
        }
        Notifier.cancel(ctx, medId, at) // re-post so it alerts again
        Notifier.showDose(ctx, med, at, count)
        Scheduler.scheduleNag(ctx, medId, at, count + 1, System.currentTimeMillis() + s.nagMinutes * 60_000L)
    }
}

/** Indirection so the receiver file does not depend on UI details. */
object AlarmActivityBridge {
    fun close(medId: Long, at: Long) = com.arst.diabetesassistant.ui.AlarmActivity.finishFor(medId, at)
}

class ActionReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_TAKEN = "com.arst.diabetesassistant.TAKEN"
        const val ACTION_SKIP = "com.arst.diabetesassistant.SKIP"
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        Store.init(ctx.applicationContext)
        val medId = intent.getLongExtra(Scheduler.EXTRA_MED, -1)
        val at = intent.getLongExtra(Scheduler.EXTRA_AT, 0)
        val status = when (intent.action) {
            ACTION_TAKEN -> DoseStatus.TAKEN
            ACTION_SKIP -> DoseStatus.SKIPPED
            else -> return
        }
        DoseActions.mark(ctx, medId, at, status)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Store.init(ctx.applicationContext)
        Scheduler.rescheduleAll(ctx)
        NextDoseWidget.updateAll(ctx)
    }
}
