package de.paul.sleeptimer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.text.format.DateFormat
import java.util.Date

/** Benachrichtigung mit Countdown und den Knöpfen "+5 Min" und "Stopp" – auch auf dem Sperrbildschirm. */
object TimerNotification {
    private const val CHANNEL = "timer"

    /** Normale Countdown-Benachrichtigung. */
    private const val ID = 1

    /** Benachrichtigung des FadeService (eigene ID, damit sich beide nicht gegenseitig löschen). */
    const val ID_FADE = 2

    fun show(context: Context, end: Long) {
        // Ohne Erlaubnis zeigt Android sie einfach nicht an – der Timer funktioniert trotzdem.
        context.getSystemService(NotificationManager::class.java).notify(ID, build(context, end, fading = false))
    }

    fun cancel(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(ID)
    }

    fun build(context: Context, end: Long, fading: Boolean): Notification {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Laufender Timer", NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )

        val time = DateFormat.getTimeFormat(context).format(Date(end))
        val openApp = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (fading) "Musik wird leiser …" else "Sleeptimer läuft")
            .setContentText("Musik pausiert um $time")
            // läuft live rückwärts bis zum Ende
            .setWhen(end)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_STOPWATCH)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(openApp)
            .addAction(action(context, "+5 Min", TimerReceiver.ACTION_EXTEND))
            .addAction(action(context, "Stopp", TimerReceiver.ACTION_STOP))
            .build()
    }

    private fun action(context: Context, title: String, action: String): Notification.Action {
        val intent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, TimerReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Action.Builder(null as Icon?, title, intent).build()
    }
}
