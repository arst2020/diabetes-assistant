package com.arst.diabetesassistant.util

import android.content.Context
import android.content.res.Configuration
import com.arst.diabetesassistant.data.GlucoseUnit
import com.arst.diabetesassistant.data.Store
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

const val MGDL_PER_MMOL = 18.0182

fun locale(lang: String = Store.current.settings.lang): Locale = Locale.forLanguageTag(lang)

/** Context whose resources use the language chosen inside the app (for notifications / widget). */
fun Context.localized(lang: String = Store.current.settings.lang): Context {
    val cfg = Configuration(resources.configuration)
    cfg.setLocale(Locale.forLanguageTag(lang))
    return createConfigurationContext(cfg)
}

fun minuteToHHmm(minute: Int, lang: String = Store.current.settings.lang): String =
    String.format(locale(lang), "%02d:%02d", minute / 60, minute % 60)

fun timeOf(millis: Long, lang: String = Store.current.settings.lang): String {
    val t = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    return String.format(locale(lang), "%02d:%02d", t.hour, t.minute)
}

fun num(n: Int, lang: String = Store.current.settings.lang): String = String.format(locale(lang), "%d", n)

fun glucoseValue(mgdl: Double, unit: GlucoseUnit, lang: String = Store.current.settings.lang): String =
    when (unit) {
        GlucoseUnit.MGDL -> String.format(locale(lang), "%d", Math.round(mgdl))
        GlucoseUnit.MMOL -> String.format(locale(lang), "%.1f", mgdl / MGDL_PER_MMOL)
    }

fun toMgdl(value: Double, unit: GlucoseUnit): Double =
    if (unit == GlucoseUnit.MMOL) value * MGDL_PER_MMOL else value

fun fromMgdl(mgdl: Double, unit: GlucoseUnit): Double =
    if (unit == GlucoseUnit.MMOL) mgdl / MGDL_PER_MMOL else mgdl

/** Parses user input; accepts Persian/Arabic digits and the Persian decimal separator. */
fun parseNumber(s: String): Double? {
    val sb = StringBuilder()
    for (c in s.trim()) {
        sb.append(
            when (c) {
                in '۰'..'۹' -> '0' + (c - '۰')
                in '٠'..'٩' -> '0' + (c - '٠')
                '٫', ',', '/' -> '.'
                else -> c
            }
        )
    }
    return sb.toString().toDoubleOrNull()
}

/** Date label: Jalali (Solar Hijri) for Persian, ISO-like for English. */
fun dateLabel(millis: Long, lang: String = Store.current.settings.lang): String {
    val d = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
    return dateLabel(d, lang)
}

fun dateLabel(d: LocalDate, lang: String = Store.current.settings.lang): String =
    if (lang == "fa") {
        val (y, m, day) = toJalali(d.year, d.monthValue, d.dayOfMonth)
        String.format(locale(lang), "%d/%02d/%02d", y, m, day)
    } else {
        String.format(Locale.US, "%d-%02d-%02d", d.year, d.monthValue, d.dayOfMonth)
    }

/** Gregorian -> Jalali conversion (standard arithmetic algorithm). */
fun toJalali(gy: Int, gm: Int, gd: Int): Triple<Int, Int, Int> {
    val gdm = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
    val gy2 = if (gm > 2) gy + 1 else gy
    var days = 355666 + 365 * gy + (gy2 + 3) / 4 - (gy2 + 99) / 100 + (gy2 + 399) / 400 + gd + gdm[gm - 1]
    var jy = -1595 + 33 * (days / 12053)
    days %= 12053
    jy += 4 * (days / 1461)
    days %= 1461
    if (days > 365) {
        jy += (days - 1) / 365
        days = (days - 1) % 365
    }
    val jm: Int
    val jd: Int
    if (days < 186) {
        jm = 1 + days / 31; jd = 1 + days % 31
    } else {
        jm = 7 + (days - 186) / 30; jd = 1 + (days - 186) % 30
    }
    return Triple(jy, jm, jd)
}
