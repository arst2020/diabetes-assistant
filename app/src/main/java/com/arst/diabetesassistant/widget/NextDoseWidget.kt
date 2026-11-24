package com.arst.diabetesassistant.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.arst.diabetesassistant.R
import com.arst.diabetesassistant.data.DoseStatus
import com.arst.diabetesassistant.data.MedType
import com.arst.diabetesassistant.data.Store
import com.arst.diabetesassistant.data.dosesOn
import com.arst.diabetesassistant.data.nextDose
import com.arst.diabetesassistant.ui.MainActivity
import com.arst.diabetesassistant.util.localized
import com.arst.diabetesassistant.util.minuteToHHmm
import com.arst.diabetesassistant.util.num

/** Home-screen widget: next dose + today's progress. */
class NextDoseWidget : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        Store.init(ctx.applicationContext)
        ids.forEach { mgr.updateAppWidget(it, build(ctx)) }
    }

    companion object {
        fun updateAll(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx) ?: return
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, NextDoseWidget::class.java))
            if (ids.isEmpty()) return
            val views = build(ctx)
            ids.forEach { mgr.updateAppWidget(it, views) }
        }

        private fun build(ctx: Context): RemoteViews {
            val c = ctx.localized()
            val data = Store.current
            val today = dosesOn(data)
            val taken = today.count { it.status == DoseStatus.TAKEN }
            val next = nextDose(data)
            val v = RemoteViews(ctx.packageName, R.layout.widget_next_dose)
            v.setTextViewText(R.id.w_title, c.getString(R.string.widget_next))
            if (next == null) {
                v.setTextViewText(R.id.w_next, c.getString(R.string.widget_none))
            } else {
                val icon = if (next.med.type == MedType.INSULIN) "💉" else "💊"
                val due = if (next.status == DoseStatus.PENDING) " ⚠️" else ""
                v.setTextViewText(R.id.w_next, "$icon ${minuteToHHmm(next.minute)}  ${next.med.name} ${next.med.dose}$due")
            }
            v.setTextViewText(R.id.w_progress, c.getString(R.string.today_progress, num(taken), num(today.size)))
            val open = PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            v.setOnClickPendingIntent(R.id.w_root, open)
            return v
        }
    }
}
