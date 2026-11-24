package de.paul.sleeptimer

import android.content.Context
import android.media.AudioManager
import android.view.KeyEvent

/** Simuliert die Pause-Medientaste – wirkt auf die App, die gerade Musik spielt (Spotify, YouTube Music, …). */
object Media {
    fun pause(context: Context) {
        val audio = context.getSystemService(AudioManager::class.java)
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE))
    }
}
