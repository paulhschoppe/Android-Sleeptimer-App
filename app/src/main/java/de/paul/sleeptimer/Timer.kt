package de.paul.sleeptimer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * Timer über den System-Wecker (AlarmManager): läuft auch weiter,
 * wenn die App geschlossen oder der Bildschirm gesperrt ist.
 */
object Timer {
    private const val KEY_END = "end"
    private const val KEY_SECONDS = "seconds"
    private const val KEY_MINUTES_OLD = "minutes"   // frühere Versionen speicherten nur Minuten
    const val DEFAULT_SECONDS = 30 * 60

    private fun prefs(context: Context) =
        context.getSharedPreferences("timer", Context.MODE_PRIVATE)

    private fun alarmIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, TimerReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    /** Zuletzt eingestellte Dauer in Sekunden – wird beim nächsten Öffnen wieder angezeigt. */
    fun savedSeconds(context: Context): Int {
        val prefs = prefs(context)
        return prefs.getInt(KEY_SECONDS, prefs.getInt(KEY_MINUTES_OLD, DEFAULT_SECONDS / 60) * 60)
    }

    fun start(context: Context, seconds: Int) {
        val end = System.currentTimeMillis() + seconds * 1000L
        prefs(context).edit()
            .putInt(KEY_SECONDS, seconds)
            .remove(KEY_MINUTES_OLD)
            .putLong(KEY_END, end)
            .apply()

        val alarms = context.getSystemService(AlarmManager::class.java)
        try {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, end, alarmIntent(context))
        } catch (e: SecurityException) {
            // Falls exakte Wecker gesperrt sind: etwas ungenauer, funktioniert aber trotzdem.
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, end, alarmIntent(context))
        }
    }

    fun stop(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(alarmIntent(context))
        clearRunning(context)
    }

    fun clearRunning(context: Context) {
        prefs(context).edit().remove(KEY_END).apply()
    }

    /** Restzeit in Millisekunden, oder null wenn kein Timer läuft. */
    fun remainingMillis(context: Context): Long? {
        val end = prefs(context).getLong(KEY_END, 0L)
        if (end == 0L) return null
        val remaining = end - System.currentTimeMillis()
        return if (remaining > 0) remaining else null
    }
}
