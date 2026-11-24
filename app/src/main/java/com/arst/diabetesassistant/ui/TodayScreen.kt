package com.arst.diabetesassistant.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.arst.diabetesassistant.R
import com.arst.diabetesassistant.alarm.DoseActions
import com.arst.diabetesassistant.alarm.Notifier
import com.arst.diabetesassistant.alarm.Scheduler
import com.arst.diabetesassistant.data.AppData
import com.arst.diabetesassistant.data.DoseStatus
import com.arst.diabetesassistant.data.MedType
import com.arst.diabetesassistant.data.ScheduledDose
import com.arst.diabetesassistant.data.dosesOn
import com.arst.diabetesassistant.data.isDone
import com.arst.diabetesassistant.util.dateLabel
import com.arst.diabetesassistant.util.glucoseValue
import com.arst.diabetesassistant.util.minuteToHHmm
import com.arst.diabetesassistant.util.num
import com.arst.diabetesassistant.util.timeOf

@Composable
fun TodayScreen(data: AppData, modifier: Modifier, onGoToMeds: () -> Unit, onGoToGlucose: () -> Unit) {
    val ctx = LocalContext.current
    val doses = dosesOn(data)
    val now = System.currentTimeMillis()
    val done = doses.count { it.status == DoseStatus.TAKEN }
    val s = data.settings

    LazyColumn(modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {

        item {
            Text(dateLabel(now), style = MaterialTheme.typography.titleMedium)
        }

        if (!Notifier.canPost(ctx) || !Scheduler.canExact(ctx)) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(stringResource(R.string.warn_permissions), Modifier.padding(16.dp))
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.today_progress, num(done), num(doses.size)),
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    val last = data.glucose.firstOrNull()
                    Spacer(Modifier.padding(4.dp))
                    if (last != null) {
                        val color = when {
                            last.mgdl < s.lowMgdl -> ColorLow
                            last.mgdl > s.highMgdl -> ColorHigh
                            else -> ColorOk
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.last_glucose) + " ")
                            Text(glucoseValue(last.mgdl, s.unit) + " " + unitLabel(s.unit),
                                color = color, fontWeight = FontWeight.Bold)
                            Text("  (" + dateLabel(last.time) + " " + timeOf(last.time) + ")",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    TextButton(onClick = onGoToGlucose) { Text(stringResource(R.string.add_glucose)) }
                }
            }
        }

        if (doses.isEmpty()) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.no_meds_yet))
                        Spacer(Modifier.padding(4.dp))
                        Button(onClick = onGoToMeds) { Text(stringResource(R.string.add_med)) }
                    }
                }
            }
        }

        items(doses, key = { "${it.med.id}-${it.minute}" }) { d ->
            DoseRow(d, now, onTaken = { DoseActions.mark(ctx, d.med.id, d.at, DoseStatus.TAKEN) },
                onSkip = { DoseActions.mark(ctx, d.med.id, d.at, DoseStatus.SKIPPED) })
        }
    }
}

@Composable
private fun DoseRow(d: ScheduledDose, now: Long, onTaken: () -> Unit, onSkip: () -> Unit) {
    val overdue = !isDone(d.status) && d.at < now
    val container = when {
        d.status == DoseStatus.TAKEN -> Color(0x332E7D32)
        d.status == DoseStatus.MISSED || overdue -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = container)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(minuteToHHmm(d.minute), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text((if (d.med.type == MedType.INSULIN) "💉 " else "💊 ") + d.med.name,
                        style = MaterialTheme.typography.titleMedium)
                    if (d.med.dose.isNotBlank()) Text(d.med.dose, style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    stringResource(
                        when (d.status) {
                            DoseStatus.TAKEN -> R.string.status_taken
                            DoseStatus.SKIPPED -> R.string.status_skipped
                            DoseStatus.MISSED -> R.string.status_missed
                            DoseStatus.PENDING -> R.string.status_due
                            null -> if (overdue) R.string.status_due else R.string.status_upcoming
                        }
                    ),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            if (d.status != DoseStatus.TAKEN && d.status != DoseStatus.SKIPPED) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onTaken, modifier = Modifier.weight(1f)) {
                        Text(stringResource(if (d.status == DoseStatus.MISSED) R.string.btn_taken_late else R.string.btn_taken))
                    }
                    OutlinedButton(onClick = onSkip, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.btn_skip)) }
                }
            }
        }
    }
}
