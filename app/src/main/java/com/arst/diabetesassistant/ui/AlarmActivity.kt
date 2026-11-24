package com.arst.diabetesassistant.ui

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arst.diabetesassistant.R
import com.arst.diabetesassistant.alarm.DoseActions
import com.arst.diabetesassistant.data.DoseStatus
import com.arst.diabetesassistant.data.MedType
import com.arst.diabetesassistant.data.Store
import com.arst.diabetesassistant.util.localized
import com.arst.diabetesassistant.util.timeOf
import java.lang.ref.WeakReference

/** Full-screen alarm shown over the lock screen when a dose is due. */
class AlarmActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_MED = "med"
        private const val EXTRA_AT = "at"
        private var current: WeakReference<AlarmActivity>? = null

        fun intent(ctx: Context, medId: Long, at: Long) = Intent(ctx, AlarmActivity::class.java).apply {
            putExtra(EXTRA_MED, medId)
            putExtra(EXTRA_AT, at)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

        fun finishFor(medId: Long, at: Long) {
            val a = current?.get() ?: return
            if (a.medId == medId && a.at == at) a.runOnUiThread { a.stopAlert(); a.finish() }
        }
    }

    private var medId = -1L
    private var at = 0L
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun attachBaseContext(newBase: Context) {
        Store.init(newBase.applicationContext)
        super.attachBaseContext(newBase.localized())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        current = WeakReference(this)
        load(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        load(intent)
    }

    private fun load(i: Intent) {
        medId = i.getLongExtra(EXTRA_MED, -1)
        at = i.getLongExtra(EXTRA_AT, 0)
        val med = Store.med(medId)
        if (med == null) { finish(); return }
        val status = Store.dose(medId, at)?.status
        val pending = status == null || status == DoseStatus.PENDING
        if (pending) startAlert() else stopAlert()
        setContent {
            AppTheme {
                AlarmScreen(
                    name = med.name,
                    dose = med.dose,
                    note = med.note,
                    insulin = med.type == MedType.INSULIN,
                    time = timeOf(at),
                    missed = status == DoseStatus.MISSED,
                    onTaken = { act(DoseStatus.TAKEN) },
                    onSkip = { act(DoseStatus.SKIPPED) },
                    onLater = { stopAlert(); finish() },
                )
            }
        }
    }

    private fun act(s: DoseStatus) {
        stopAlert()
        DoseActions.mark(this, medId, at, s)
        finish()
    }

    private fun startAlert() {
        if (player != null) return
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
                )
                setDataSource(this@AlarmActivity, uri)
                isLooping = true
                prepare()
                start()
            }
        } catch (_: Exception) {
            player = null
        }
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION") getSystemService(Vibrator::class.java)
        }
        vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 600), 0))
        // the repeat reminder will ring again later; don't ring forever now
        handler.postDelayed({ stopAlert() }, 60_000)
    }

    fun stopAlert() {
        handler.removeCallbacksAndMessages(null)
        try { player?.stop() } catch (_: Exception) {}
        player?.release(); player = null
        vibrator?.cancel(); vibrator = null
    }

    override fun onDestroy() {
        stopAlert()
        if (current?.get() === this) current = null
        super.onDestroy()
    }
}

@Composable
private fun AlarmScreen(
    name: String, dose: String, note: String, insulin: Boolean, time: String, missed: Boolean,
    onTaken: () -> Unit, onSkip: () -> Unit, onLater: () -> Unit,
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.primaryContainer) {
        Column(
            Modifier.fillMaxSize().padding(28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(if (insulin) R.string.alarm_header_insulin else R.string.alarm_header_pill),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(12.dp))
            Text(time, fontSize = 56.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Text(name, fontSize = 30.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            if (dose.isNotBlank()) Text(dose, fontSize = 22.sp, textAlign = TextAlign.Center)
            if (note.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(note, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            }
            if (missed) {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.alarm_marked_missed), color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(40.dp))
            Button(
                onClick = onTaken,
                modifier = Modifier.fillMaxWidth().height(72.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) { Text(stringResource(R.string.btn_taken), fontSize = 24.sp) }
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onLater, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text(stringResource(R.string.btn_later), fontSize = 18.sp)
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onSkip, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text(stringResource(R.string.btn_skip), fontSize = 18.sp)
            }
        }
    }
}
