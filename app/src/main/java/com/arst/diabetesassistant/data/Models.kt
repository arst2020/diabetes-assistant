package com.arst.diabetesassistant.data

import org.json.JSONArray
import org.json.JSONObject

enum class MedType { PILL, INSULIN }

/** times = minutes from midnight (e.g. 8:30 -> 510) */
data class Medication(
    val id: Long,
    val name: String,
    val type: MedType,
    val dose: String,
    val times: List<Int>,
    val active: Boolean = true,
    val note: String = "",
)

enum class DoseStatus { PENDING, TAKEN, SKIPPED, MISSED }

data class DoseEvent(
    val medId: Long,
    val scheduledAt: Long,
    val status: DoseStatus,
    val actionAt: Long? = null,
) {
    val key: String get() = keyOf(medId, scheduledAt)

    companion object {
        fun keyOf(medId: Long, at: Long) = "$medId@$at"
    }
}

enum class GlucoseTag { FASTING, BEFORE_MEAL, AFTER_MEAL, BEDTIME, OTHER }

data class GlucoseEntry(
    val id: Long,
    val time: Long,
    val mgdl: Double,
    val tag: GlucoseTag,
    val note: String = "",
)

enum class GlucoseUnit { MGDL, MMOL }

data class Settings(
    val lang: String = "fa",
    val unit: GlucoseUnit = GlucoseUnit.MGDL,
    /** thresholds always stored in mg/dL */
    val lowMgdl: Double = 70.0,
    val highMgdl: Double = 180.0,
    val nagMinutes: Int = 5,
    val maxNags: Int = 6,
)

data class AppData(
    val nextId: Long = 1,
    val meds: List<Medication> = emptyList(),
    val doses: Map<String, DoseEvent> = emptyMap(),
    val glucose: List<GlucoseEntry> = emptyList(),
    val settings: Settings = Settings(),
)

// ---------------- JSON ----------------

private inline fun <reified T : Enum<T>> enumOr(name: String?, def: T): T =
    runCatching { enumValueOf<T>(name ?: "") }.getOrDefault(def)

fun AppData.toJson(): JSONObject = JSONObject().apply {
    put("version", 1)
    put("nextId", nextId)
    put("meds", JSONArray().apply {
        meds.forEach { m ->
            put(JSONObject().apply {
                put("id", m.id); put("name", m.name); put("type", m.type.name)
                put("dose", m.dose); put("active", m.active); put("note", m.note)
                put("times", JSONArray(m.times))
            })
        }
    })
    put("doses", JSONArray().apply {
        doses.values.forEach { d ->
            put(JSONObject().apply {
                put("medId", d.medId); put("at", d.scheduledAt); put("status", d.status.name)
                d.actionAt?.let { put("actionAt", it) }
            })
        }
    })
    put("glucose", JSONArray().apply {
        glucose.forEach { g ->
            put(JSONObject().apply {
                put("id", g.id); put("time", g.time); put("mgdl", g.mgdl)
                put("tag", g.tag.name); put("note", g.note)
            })
        }
    })
    put("settings", JSONObject().apply {
        put("lang", settings.lang); put("unit", settings.unit.name)
        put("low", settings.lowMgdl); put("high", settings.highMgdl)
        put("nagMinutes", settings.nagMinutes); put("maxNags", settings.maxNags)
    })
}

fun appDataFromJson(o: JSONObject): AppData {
    val meds = mutableListOf<Medication>()
    o.optJSONArray("meds")?.let { a ->
        for (i in 0 until a.length()) {
            val m = a.getJSONObject(i)
            val t = m.optJSONArray("times") ?: JSONArray()
            meds += Medication(
                id = m.getLong("id"),
                name = m.optString("name"),
                type = enumOr(m.optString("type"), MedType.PILL),
                dose = m.optString("dose"),
                times = (0 until t.length()).map { t.getInt(it) },
                active = m.optBoolean("active", true),
                note = m.optString("note"),
            )
        }
    }
    val doses = mutableMapOf<String, DoseEvent>()
    o.optJSONArray("doses")?.let { a ->
        for (i in 0 until a.length()) {
            val d = a.getJSONObject(i)
            val ev = DoseEvent(
                medId = d.getLong("medId"),
                scheduledAt = d.getLong("at"),
                status = enumOr(d.optString("status"), DoseStatus.PENDING),
                actionAt = if (d.has("actionAt")) d.getLong("actionAt") else null,
            )
            doses[ev.key] = ev
        }
    }
    val glucose = mutableListOf<GlucoseEntry>()
    o.optJSONArray("glucose")?.let { a ->
        for (i in 0 until a.length()) {
            val g = a.getJSONObject(i)
            glucose += GlucoseEntry(
                id = g.getLong("id"),
                time = g.getLong("time"),
                mgdl = g.getDouble("mgdl"),
                tag = enumOr(g.optString("tag"), GlucoseTag.OTHER),
                note = g.optString("note"),
            )
        }
    }
    val s = o.optJSONObject("settings") ?: JSONObject()
    val def = Settings()
    return AppData(
        nextId = o.optLong("nextId", 1),
        meds = meds,
        doses = doses,
        glucose = glucose.sortedByDescending { it.time },
        settings = Settings(
            lang = s.optString("lang", def.lang),
            unit = enumOr(s.optString("unit"), def.unit),
            lowMgdl = s.optDouble("low", def.lowMgdl),
            highMgdl = s.optDouble("high", def.highMgdl),
            nagMinutes = s.optInt("nagMinutes", def.nagMinutes),
            maxNags = s.optInt("maxNags", def.maxNags),
        ),
    )
}
