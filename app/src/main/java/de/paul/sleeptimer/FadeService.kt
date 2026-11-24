package de.paul.sleeptimer

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import kotlin.math.ceil

/**
 * Läuft nur während der letzten 2 Minuten: senkt die Medienlautstärke gleichmäßig bis 0,
 * pausiert dann die Musik und stellt die ursprüngliche Lautstärke wieder her.
 * Als Vordergrunddienst, damit Android ihn bei gesperrtem Bildschirm nicht anhält.
 */
class FadeService : Service() {

    companion object {
        private const val TAG = "FadeService"
        private const val STEP_MILLIS = 250L

        fun start(context: Context) {
            try {
                context.startForegroundService(Intent(context, FadeService::class.java))
            } catch (e: Exception) {
                // Darf Android den Dienst nicht starten, pausiert der Absicherungs-Wecker am Ende ohne Ausblenden.
                Log.w(TAG, "Ausblenden nicht möglich", e)
            }
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var wakeLock: PowerManager.WakeLock? = null
    private var running = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val end = Timer.endMillis(this)
        val notification = TimerNotification.build(this, end ?: System.currentTimeMillis(), fading = true)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(TimerNotification.ID_FADE, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE)
        } else {
            startForeground(TimerNotification.ID_FADE, notification)
        }

        if (end == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (running) return START_NOT_STICKY
        running = true
        TimerNotification.cancel(this)   // die Dienst-Benachrichtigung ersetzt die normale

        val audio = getSystemService(AudioManager::class.java)
        val startVolume = Timer.fadeStartVolume(this)
            ?: audio.getStreamVolume(AudioManager.STREAM_MUSIC).also { Timer.rememberVolume(this, it) }
        val fadeLength = (end - System.currentTimeMillis()).coerceAtLeast(1)

        // CPU wach halten, solange ausgeblendet wird (Bildschirm bleibt aus)
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Sleeptimer:fade")
            .apply { acquire(fadeLength + 10_000) }

        handler.post(object : Runnable {
            override fun run() {
                val remaining = end - System.currentTimeMillis()
                if (remaining <= 0) {
                    pauseAndRestore()
                    return
                }
                val target = ceil(startVolume * remaining / fadeLength.toDouble()).toInt()
                // nur leiser machen – hat jemand selbst noch leiser gestellt, bleibt das so
                if (audio.getStreamVolume(AudioManager.STREAM_MUSIC) > target) {
                    audio.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
                }
                handler.postDelayed(this, STEP_MILLIS)
            }
        })
        return START_NOT_STICKY
    }

    private fun pauseAndRestore() {
        Media.pause(this)
        handler.postDelayed({
            Timer.restoreVolume(this)
            Timer.finish(this)
            stopSelf()
        }, Timer.RESTORE_DELAY_MILLIS)
    }

    /** Android beendet kurze Vordergrunddienste nach ~3 Min – sollte nie passieren, dann aber sauber aufräumen. */
    override fun onTimeout(startId: Int) {
        Timer.restoreVolume(this)
        stopSelf()
    }

    override fun onTimeout(startId: Int, fgsType: Int) = onTimeout(startId)

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        wakeLock?.takeIf { it.isHeld }?.release()
        super.onDestroy()
    }
}
