package com.arst.diabetesassistant.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.arst.diabetesassistant.R
import com.arst.diabetesassistant.alarm.Scheduler
import com.arst.diabetesassistant.data.AppData
import com.arst.diabetesassistant.data.MedType
import com.arst.diabetesassistant.data.Medication
import com.arst.diabetesassistant.data.Store
import com.arst.diabetesassistant.util.minuteToHHmm
import com.arst.diabetesassistant.widget.NextDoseWidget

@Composable
fun MedsScreen(data: AppData, modifier: Modifier) {
    val ctx = LocalContext.current
    var editing by remember { mutableStateOf<Medication?>(null) }
    var creating by remember { mutableStateOf(false) }

    Box(modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (data.meds.isEmpty()) item { Text(stringResource(R.string.no_meds_yet)) }
            items(data.meds, key = { it.id }) { m ->
                Card(Modifier.fillMaxWidth(), onClick = { editing = m }) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text((if (m.type == MedType.INSULIN) "💉 " else "💊 ") + m.name,
                                style = MaterialTheme.typography.titleMedium)
                            if (m.dose.isNotBlank()) Text(m.dose)
                            Text(m.times.sorted().joinToString("  ·  ") { minuteToHHmm(it) },
                                style = MaterialTheme.typography.bodyMedium)
                        }
                        Switch(checked = m.active, onCheckedChange = { on ->
                            val updated = m.copy(active = on)
                            saveMed(ctx, m, updated)
                        })
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { creating = true },
            icon = { Icon(Icons.Filled.Add, null) },
            text = { Text(stringResource(R.string.add_med)) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    if (creating || editing != null) {
        MedDialog(
            initial = editing,
            onDismiss = { creating = false; editing = null },
            onSave = { m ->
                saveMed(ctx, editing, m)
                creating = false; editing = null
            },
            onDelete = editing?.let { old ->
                {
                    Scheduler.cancelMed(ctx, old)
                    Store.update { d -> d.copy(meds = d.meds.filter { it.id != old.id }) }
                    NextDoseWidget.updateAll(ctx)
                    editing = null
                }
            },
        )
    }
}

private fun saveMed(ctx: android.content.Context, old: Medication?, new: Medication) {
    if (old != null) Scheduler.cancelMed(ctx, old)
    Store.update { d ->
        if (d.meds.any { it.id == new.id }) d.copy(meds = d.meds.map { if (it.id == new.id) new else it })
        else d.copy(meds = d.meds + new)
    }
    Scheduler.scheduleMed(ctx, new)
    NextDoseWidget.updateAll(ctx)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MedDialog(
    initial: Medication?,
    onDismiss: () -> Unit,
    onSave: (Medication) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val ctx = LocalContext.current
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var dose by remember { mutableStateOf(initial?.dose ?: "") }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var type by remember { mutableStateOf(initial?.type ?: MedType.PILL) }
    val times = remember { mutableStateListOf<Int>().apply { addAll(initial?.times ?: emptyList()) } }
    var confirmDelete by remember { mutableStateOf(false) }

    fun pickTime() {
        TimePickerDialog(ctx, { _, h, min ->
            val v = h * 60 + min
            if (v !in times) { times.add(v); times.sort() }
        }, 8, 0, true).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.add_med else R.string.edit_med)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = type == MedType.PILL, onClick = { type = MedType.PILL },
                        label = { Text("💊 " + stringResource(R.string.type_pill)) })
                    FilterChip(selected = type == MedType.INSULIN, onClick = { type = MedType.INSULIN },
                        label = { Text("💉 " + stringResource(R.string.type_insulin)) })
                }
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.med_name)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(dose, { dose = it },
                    label = { Text(stringResource(if (type == MedType.INSULIN) R.string.med_dose_insulin else R.string.med_dose_pill)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(note, { note = it }, label = { Text(stringResource(R.string.med_note)) },
                    modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.med_times), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    times.forEach { t ->
                        InputChip(
                            selected = false,
                            onClick = { times.remove(t) },
                            label = { Text(minuteToHHmm(t)) },
                            trailingIcon = { Icon(Icons.Filled.Close, null) },
                        )
                    }
                }
                TextButton(onClick = { pickTime() }) {
                    Icon(Icons.Filled.Add, null); Text(stringResource(R.string.add_time))
                }
                if (type == MedType.INSULIN) {
                    Text(stringResource(R.string.insulin_disclaimer), style = MaterialTheme.typography.bodySmall)
                }
                if (onDelete != null) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error)
                        Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && times.isNotEmpty(),
                onClick = {
                    onSave(
                        Medication(
                            id = initial?.id ?: Store.newId(),
                            name = name.trim(), type = type, dose = dose.trim(),
                            times = times.toList().sorted(), active = initial?.active ?: true, note = note.trim(),
                        )
                    )
                },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )

    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            text = { Text(stringResource(R.string.confirm_delete_med, name)) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
