package com.arst.diabetesassistant.data

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.io.File

/**
 * Tiny JSON-file store. All data stays on the phone (app-private storage).
 * Single process, so a synchronized in-memory copy + atomic file write is enough.
 */
object Store {
    private const val TAG = "Store"
    private const val KEEP_DOSES_MS = 400L * 24 * 3600 * 1000

    private lateinit var file: File
    private val _data = MutableStateFlow(AppData())
    val data: StateFlow<AppData> get() = _data
    val current: AppData get() = _data.value

    @Synchronized
    fun init(ctx: Context) {
        if (::file.isInitialized) return
        file = File(ctx.filesDir, "data.json")
        _data.value = load(file)
    }

    private fun load(f: File): AppData {
        if (!f.exists()) return AppData()
        return try {
            appDataFromJson(JSONObject(f.readText()))
        } catch (e: Exception) {
            Log.e(TAG, "load failed", e)
            // keep the broken file for inspection, start fresh
            f.copyTo(File(f.parentFile, "data.broken.${System.currentTimeMillis()}.json"), overwrite = true)
            AppData()
        }
    }

    @Synchronized
    fun update(fn: (AppData) -> AppData): AppData {
        val now = System.currentTimeMillis()
        var next = fn(_data.value)
        // prune very old dose events
        if (next.doses.values.any { now - it.scheduledAt > KEEP_DOSES_MS }) {
            next = next.copy(doses = next.doses.filterValues { now - it.scheduledAt <= KEEP_DOSES_MS })
        }
        _data.value = next
        save(next)
        return next
    }

    @Synchronized
    fun newId(): Long {
        var id = 0L
        update { id = it.nextId; it.copy(nextId = it.nextId + 1) }
        return id
    }

    private fun save(d: AppData) {
        try {
            val tmp = File(file.parentFile, "data.json.tmp")
            tmp.writeText(d.toJson().toString())
            if (!tmp.renameTo(file)) {
                file.writeText(tmp.readText()); tmp.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "save failed", e)
        }
    }

    // ---------- backup / restore ----------
    fun exportTo(ctx: Context, uri: Uri) {
        ctx.contentResolver.openOutputStream(uri)?.use {
            it.write(current.toJson().toString(2).toByteArray())
        }
    }

    fun importFrom(ctx: Context, uri: Uri) {
        val text = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: return
        val parsed = appDataFromJson(JSONObject(text))
        update { parsed }
    }

    // ---------- helpers ----------
    fun med(id: Long): Medication? = current.meds.firstOrNull { it.id == id }

    fun dose(medId: Long, at: Long): DoseEvent? = current.doses[DoseEvent.keyOf(medId, at)]

    fun setDose(ev: DoseEvent) = update { it.copy(doses = it.doses + (ev.key to ev)) }
}
