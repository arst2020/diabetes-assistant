package com.arst.diabetesassistant.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.arst.diabetesassistant.R
import com.arst.diabetesassistant.alarm.Notifier
import com.arst.diabetesassistant.alarm.Scheduler
import com.arst.diabetesassistant.data.AppData
import com.arst.diabetesassistant.data.GlucoseUnit
import com.arst.diabetesassistant.data.Store
import com.arst.diabetesassistant.util.glucoseValue
import com.arst.diabetesassistant.util.num
import com.arst.diabetesassistant.util.parseNumber
import com.arst.diabetesassistant.util.toMgdl

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(data: AppData, modifier: Modifier, onLanguageChanged: () -> Unit) {
    val ctx = LocalContext.current
    val s = data.settings

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) runCatching { Store.exportTo(ctx, uri) }
            .onSuccess { Toast.makeText(ctx, R.string.backup_done, Toast.LENGTH_SHORT).show() }
            .onFailure { Toast.makeText(ctx, R.string.backup_failed, Toast.LENGTH_LONG).show() }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            Store.current.meds.forEach { Scheduler.cancelMed(ctx, it) }
            Store.importFrom(ctx, uri)
            Scheduler.rescheduleAll(ctx)
        }.onSuccess { Toast.makeText(ctx, R.string.restore_done, Toast.LENGTH_SHORT).show(); onLanguageChanged() }
            .onFailure { Toast.makeText(ctx, R.string.backup_failed, Toast.LENGTH_LONG).show() }
    }

    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Section(stringResource(R.string.set_language)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("fa" to "فارسی", "en" to "English").forEach { (code, label) ->
                    FilterChip(selected = s.lang == code, onClick = {
                        if (s.lang != code) {
                            Store.update { it.copy(settings = it.settings.copy(lang = code)) }
                            onLanguageChanged()
                        }
                    }, label = { Text(label) })
                }
            }
        }

        Section(stringResource(R.string.set_unit)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlucoseUnit.entries.forEach { u ->
                    FilterChip(selected = s.unit == u, onClick = {
                        Store.update { it.copy(settings = it.settings.copy(unit = u)) }
                    }, label = { Text(unitLabel(u)) })
                }
            }
        }

        Section(stringResource(R.string.set_targets)) {
            // key on unit so the fields refresh when the unit changes
            var low by remember(s.unit) { mutableStateOf(glucoseValue(s.lowMgdl, s.unit, "en")) }
            var high by remember(s.unit) { mutableStateOf(glucoseValue(s.highMgdl, s.unit, "en")) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(low, {
                    low = it
                    parseNumber(it)?.let { v -> val mg = toMgdl(v, s.unit)
                        if (mg in 40.0..150.0) Store.update { d -> d.copy(settings = d.settings.copy(lowMgdl = mg)) } }
                }, label = { Text(stringResource(R.string.target_low)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                OutlinedTextField(high, {
                    high = it
                    parseNumber(it)?.let { v -> val mg = toMgdl(v, s.unit)
                        if (mg in 100.0..400.0) Store.update { d -> d.copy(settings = d.settings.copy(highMgdl = mg)) } }
                }, label = { Text(stringResource(R.string.target_high)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
            }
            Text(stringResource(R.string.targets_hint), style = MaterialTheme.typography.bodySmall)
        }

        Section(stringResource(R.string.set_repeat)) {
            Text(stringResource(R.string.repeat_every))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(3, 5, 10, 15).forEach { m ->
                    FilterChip(selected = s.nagMinutes == m, onClick = {
                        Store.update { it.copy(settings = it.settings.copy(nagMinutes = m)) }
                    }, label = { Text(stringResource(R.string.minutes_n, num(m))) })
                }
            }
            Text(stringResource(R.string.repeat_times))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(3, 6, 10, 20).forEach { n ->
                    FilterChip(selected = s.maxNags == n, onClick = {
                        Store.update { it.copy(settings = it.settings.copy(maxNags = n)) }
                    }, label = { Text(num(n)) })
                }
            }
        }

        Section(stringResource(R.string.set_permissions)) {
            Text(stringResource(R.string.permissions_hint), style = MaterialTheme.typography.bodySmall)
            PermRow(stringResource(R.string.perm_notifications), Notifier.canPost(ctx)) {
                ctx.startActivity(Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, ctx.packageName))
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PermRow(stringResource(R.string.perm_exact), Scheduler.canExact(ctx)) {
                    ctx.startActivity(Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:" + ctx.packageName)))
                }
            }
            if (Build.VERSION.SDK_INT >= 34) {
                val nm = ctx.getSystemService(android.app.NotificationManager::class.java)
                PermRow(stringResource(R.string.perm_fullscreen), nm.canUseFullScreenIntent()) {
                    ctx.startActivity(Intent(AndroidSettings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                        Uri.parse("package:" + ctx.packageName)))
                }
            }
            val pm = ctx.getSystemService(PowerManager::class.java)
            PermRow(stringResource(R.string.perm_battery), pm.isIgnoringBatteryOptimizations(ctx.packageName)) {
                runCatching {
                    ctx.startActivity(Intent(AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:" + ctx.packageName)))
                }.onFailure { ctx.startActivity(Intent(AndroidSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
            }
        }

        Section(stringResource(R.string.set_backup)) {
            Text(stringResource(R.string.backup_hint), style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { exportLauncher.launch("diabetes-backup.json") }) { Text(stringResource(R.string.backup_export)) }
                OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) }) { Text(stringResource(R.string.backup_import)) }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.disclaimer), Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun PermRow(label: String, ok: Boolean, onFix: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text((if (ok) "✅ " else "⚠️ ") + label, Modifier.weight(1f))
        if (!ok) OutlinedButton(onClick = onFix) { Text(stringResource(R.string.fix)) }
    }
}
