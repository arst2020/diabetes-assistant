package com.arst.diabetesassistant.data

import com.arst.diabetesassistant.alarm.Scheduler
import java.time.LocalDate

data class ScheduledDose(
    val med: Medication,
    val minute: Int,
    val at: Long,
    val status: DoseStatus?, // null = no record yet (upcoming)
)

fun dosesOn(data: AppData, date: LocalDate = LocalDate.now()): List<ScheduledDose> =
    data.meds.filter { it.active }.flatMap { med ->
        med.times.map { minute ->
            val at = Scheduler.atToday(minute, date)
            ScheduledDose(med, minute, at, data.doses[DoseEvent.keyOf(med.id, at)]?.status)
        }
    }.sortedBy { it.at }

/** Next dose today/tomorrow that still needs action. */
fun nextDose(data: AppData, now: Long = System.currentTimeMillis()): ScheduledDose? {
    val today = dosesOn(data)
    today.firstOrNull { it.status == DoseStatus.PENDING }?.let { return it }
    today.firstOrNull { it.status == null && it.at >= now }?.let { return it }
    return dosesOn(data, LocalDate.now().plusDays(1)).firstOrNull()
}

fun isDone(s: DoseStatus?) = s == DoseStatus.TAKEN || s == DoseStatus.SKIPPED
