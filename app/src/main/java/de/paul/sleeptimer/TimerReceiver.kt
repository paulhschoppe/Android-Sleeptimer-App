package de.paul.sleeptimer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Wird vom System aufgerufen, wenn der Timer abläuft – auch wenn die App nicht offen ist. */
class TimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Media.pause(context)
        Timer.clearRunning(context)
    }
}
