package com.arst.diabetesassistant.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.arst.diabetesassistant.R
import com.arst.diabetesassistant.data.AppData
import com.arst.diabetesassistant.data.GlucoseEntry
import com.arst.diabetesassistant.data.GlucoseTag
import com.arst.diabetesassistant.data.GlucoseUnit
import com.arst.diabetesassistant.data.Store
import com.arst.diabetesassistant.util.dateLabel
import com.arst.diabetesassistant.util.glucoseValue
import com.arst.diabetesassistant.util.parseNumber
import com.arst.diabetesassistant.util.timeOf
import com.arst.diabetesassistant.util.toMgdl
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

@Composable
fun unitLabel(u: GlucoseUnit): String = stringResource(if (u == GlucoseUnit.MGDL) R.string.unit_mgdl else R.string.unit_mmol)

@Composable
fun tagLabel(t: GlucoseTag): String = stringResource(
    when (t) {
        GlucoseTag.FASTING -> R.string.tag_fasting
        GlucoseTag.BEFORE_MEAL -> R.string.tag_before_meal
        GlucoseTag.AFTER_MEAL -> R.string.tag_after_meal
        GlucoseTag.BEDTIME -> R.string.tag_bedtime
        GlucoseTag.OTHER -> R.string.tag_other
    }
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GlucoseScreen(data: AppData, modifier: Modifier) {
    val ctx = LocalContext.current
    val s = data.settings
    var input by remember { mutableStateOf("") }
    var tag by remember { mutableStateOf(GlucoseTag.FASTING) }
    var time by remember { mutableStateOf<Long?>(null) } // null = now
    var alert by remember { mutableStateOf<Int?>(null) }  // string res of warning
    var toDelete by remember { mutableStateOf<GlucoseEntry?>(null) }

    val value = parseNumber(input)
    val mgdl = value?.let { toMgdl(it, s.unit) }
    val plausible = mgdl != null && mgdl in 10.0..700.0

    LazyColumn(modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.new_reading), style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = input, onValueChange = { input = it.take(6) },
                        label = { Text(stringResource(R.string.glucose_value) + " (" + unitLabel(s.unit) + ")") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true, isError = input.isNotBlank() && !plausible,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        GlucoseTag.entries.forEach { t ->
                            FilterChip(selected = tag == t, onClick = { tag = t }, label = { Text(tagLabel(t)) })
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.reading_time) + " " + (time?.let { timeOf(it) } ?: stringResource(R.string.now)))
                        TextButton(onClick = {
                            val n = LocalTime.now()
                            TimePickerDialog(ctx, { _, h, m ->
                                var t = ZonedDateTime.of(LocalDate.now(), LocalTime.of(h, m), ZoneId.systemDefault())
                                if (t.toInstant().toEpochMilli() > System.currentTimeMillis()) t = t.minusDays(1)
                                time = t.toInstant().toEpochMilli()
                            }, n.hour, n.minute, true).show()
                        }) { Text(stringResource(R.string.change)) }
                    }
                    Button(
                        enabled = plausible,
                        onClick = {
                            val v = mgdl ?: return@Button
                            val e = GlucoseEntry(Store.newId(), time ?: System.currentTimeMillis(), v, tag)
                            Store.update { d -> d.copy(glucose = (d.glucose + e).sortedByDescending { it.time }) }
                            input = ""; time = null
                            alert = when {
                                v < s.lowMgdl -> R.string.alert_low
                                v > s.highMgdl -> R.string.alert_high
                                else -> null
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.save)) }
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.last_7_days), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.padding(4.dp))
                    GlucoseChart(data.glucose, s.lowMgdl, s.highMgdl)
                    val week = data.glucose.filter { it.time > System.currentTimeMillis() - 7L * 86400000 }
                    if (week.isNotEmpty()) {
                        val avg = week.map { it.mgdl }.average()
                        val inRange = week.count { it.mgdl in s.lowMgdl..s.highMgdl } * 100 / week.size
                        Spacer(Modifier.padding(4.dp))
                        Text(stringResource(R.string.week_stats, glucoseValue(avg, s.unit), unitLabel(s.unit),
                            com.arst.diabetesassistant.util.num(inRange)))
                    }
                }
            }
        }

        items(data.glucose.take(100), key = { it.id }) { e ->
            val color = when {
                e.mgdl < s.lowMgdl -> ColorLow
                e.mgdl > s.highMgdl -> ColorHigh
                else -> ColorOk
            }
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(glucoseValue(e.mgdl, s.unit), color = color, fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(tagLabel(e.tag))
                        Text(dateLabel(e.time) + "  " + timeOf(e.time), style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = { toDelete = e }) { Icon(Icons.Filled.Delete, stringResource(R.string.delete)) }
                }
            }
        }
    }

    alert?.let { res ->
        AlertDialog(
            onDismissRequest = { alert = null },
            title = { Text(stringResource(if (res == R.string.alert_low) R.string.alert_low_title else R.string.alert_high_title)) },
            text = { Text(stringResource(res)) },
            confirmButton = { TextButton(onClick = { alert = null }) { Text(stringResource(R.string.ok)) } },
        )
    }
    toDelete?.let { e ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            text = { Text(stringResource(R.string.confirm_delete_reading)) },
            confirmButton = {
                TextButton(onClick = {
                    Store.update { d -> d.copy(glucose = d.glucose.filter { it.id != e.id }) }
                    toDelete = null
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
