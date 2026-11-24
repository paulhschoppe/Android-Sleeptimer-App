package de.paul.sleeptimer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager

/**
 * Timer über den System-Wecker (AlarmManager): läuft auch weiter,
 * wenn die App geschlossen oder der Bildschirm gesperrt ist.
 *
 * Ablauf: [FADE_MILLIS] vor dem Ende startet der FadeService und macht die Musik langsam leiser,
 * am Ende pausiert er sie und stellt die Lautstärke wieder her. Ein zweiter Wecker kurz nach dem Ende
 * pausiert als Absicherung, falls der Dienst nicht laufen konnte.
 */
object Timer {
    /** So lange vor dem Ende wird die Musik langsam leiser. */
    const val FADE_MILLIS = 120_000L

    /** Wartezeit zwischen Pause und Zurücksetzen der Lautstärke (sonst ist kurz die volle Lautstärke zu hören). */
    const val RESTORE_DELAY_MILLIS = 1_500L

    /** Der Absicherungs-Wecker feuert so lange nach dem Ende. */
    private const val BACKUP_DELAY_MILLIS = 5_000L

    private const val KEY_END = "end"
    private const val KEY_SECONDS = "seconds"
    private const val KEY_MINUTES_OLD = "minutes"   // frühere Versionen speicherten nur Minuten
    private const val KEY_FADE_VOLUME = "fade_volume"
    const val DEFAULT_SECONDS = 30 * 60

    private fun prefs(context: Context) =
        context.getSharedPreferences("timer", Context.MODE_PRIVATE)

    /** Zuletzt eingestellte Dauer in Sekunden – wird beim nächsten Öffnen wieder angezeigt. */
    fun savedSeconds(context: Context): Int {
        val prefs = prefs(context)
        return prefs.getInt(KEY_SECONDS, prefs.getInt(KEY_MINUTES_OLD, DEFAULT_SECONDS / 60) * 60)
    }

    /** Endzeitpunkt (System-Uhr), oder null wenn kein Timer läuft. */
    fun endMillis(context: Context): Long? =
        prefs(context).getLong(KEY_END, 0L).takeIf { it > 0 }

    /** Restzeit in Millisekunden, oder null wenn kein Timer läuft. */
    fun remainingMillis(context: Context): Long? =
        endMillis(context)?.let { it - System.currentTimeMillis() }?.takeIf { it > 0 }

    fun start(context: Context, seconds: Int) {
        prefs(context).edit()
            .putInt(KEY_SECONDS, seconds)
            .remove(KEY_MINUTES_OLD)
            .apply()
        schedule(context, System.currentTimeMillis() + seconds * 1000L)
    }

    /** Verlängert den laufenden Timer; ein laufendes Ausblenden wird abgebrochen. */
    fun extend(context: Context, millis: Long) {
        val end = endMillis(context) ?: return
        stopFade(context)
        schedule(context, maxOf(end, System.currentTimeMillis()) + millis)
    }

    /** Vom Nutzer abgebrochen: Musik läuft mit der ursprünglichen Lautstärke weiter. */
    fun stop(context: Context) {
        stopFade(context)
        finish(context)
    }

    /** Timer ist erledigt: Zustand, Wecker und Benachrichtigung entfernen. */
    fun finish(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        alarms.cancel(alarmIntent(context, TimerReceiver.ACTION_FADE))
        alarms.cancel(alarmIntent(context, TimerReceiver.ACTION_END))
        prefs(context).edit().remove(KEY_END).apply()
        TimerNotification.cancel(context)
    }

    private fun schedule(context: Context, end: Long) {
        prefs(context).edit().putLong(KEY_END, end).apply()
        setAlarm(context, end - FADE_MILLIS, TimerReceiver.ACTION_FADE)
        setAlarm(context, end + BACKUP_DELAY_MILLIS, TimerReceiver.ACTION_END)
        TimerNotification.show(context, end)
    }

    private fun setAlarm(context: Context, time: Long, action: String) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val intent = alarmIntent(context, action)
        try {
            // Liegt der Zeitpunkt schon in der Vergangenheit (Timer kürzer als 2 Min), feuert der Wecker sofort.
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, intent)
        } catch (e: SecurityException) {
            // Falls exakte Wecker gesperrt sind: etwas ungenauer, funktioniert aber trotzdem.
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, intent)
        }
    }

    private fun alarmIntent(context: Context, action: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, TimerReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    // --- Lautstärke beim Ausblenden ------------------------------------------------

    /** Lautstärke vor dem Ausblenden; wird gespeichert, damit sie in jedem Fall zurückgesetzt werden kann. */
    fun fadeStartVolume(context: Context): Int? =
        prefs(context).getInt(KEY_FADE_VOLUME, -1).takeIf { it >= 0 }

    fun rememberVolume(context: Context, volume: Int) {
        prefs(context).edit().putInt(KEY_FADE_VOLUME, volume).apply()
    }

    fun restoreVolume(context: Context) {
        val volume = fadeStartVolume(context) ?: return
        context.getSystemService(AudioManager::class.java)
            .setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
        prefs(context).edit().remove(KEY_FADE_VOLUME).apply()
    }

    private fun stopFade(context: Context) {
        context.stopService(Intent(context, FadeService::class.java))
        restoreVolume(context)
    }
}
