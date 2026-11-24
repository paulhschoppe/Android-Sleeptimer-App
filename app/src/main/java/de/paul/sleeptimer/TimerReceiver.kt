package de.paul.sleeptimer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper

/** Empfängt die Wecker des Timers und die Knöpfe der Benachrichtigung – auch wenn die App nicht offen ist. */
class TimerReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_FADE = "de.paul.sleeptimer.FADE"     // 2 Min vor Ende: leiser werden
        const val ACTION_END = "de.paul.sleeptimer.END"       // kurz nach Ende: Absicherung
        const val ACTION_EXTEND = "de.paul.sleeptimer.EXTEND" // Knopf "+10 Min"
        const val ACTION_STOP = "de.paul.sleeptimer.STOP"     // Knopf "Stopp"

        const val EXTEND_MILLIS = 10 * 60_000L
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_FADE -> if (Timer.endMillis(context) != null) FadeService.start(context)

            ACTION_END -> if (Timer.endMillis(context) != null) {
                // Normalerweise hat der FadeService schon alles erledigt; das hier greift nur, falls nicht.
                Media.pause(context)
                val pending = goAsync()
                Handler(Looper.getMainLooper()).postDelayed({
                    context.stopService(Intent(context, FadeService::class.java))
                    Timer.restoreVolume(context)
                    Timer.finish(context)
                    pending.finish()
                }, Timer.RESTORE_DELAY_MILLIS)
            }

            ACTION_EXTEND -> Timer.extend(context, EXTEND_MILLIS)

            ACTION_STOP -> Timer.stop(context)
        }
    }
}
