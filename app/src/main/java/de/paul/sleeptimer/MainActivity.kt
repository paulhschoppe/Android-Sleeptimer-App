package de.paul.sleeptimer

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.TextView
import android.widget.Toast

/**
 * Einziger Bildschirm: Stunden, Minuten und Sekunden (5er-Schritte) an Rädern einstellen (wie in der Samsung-Uhr) → Start → Countdown.
 * Läuft der Timer ab, pausiert die Musik (siehe TimerReceiver).
 */
class MainActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())

    private lateinit var pickers: View
    private lateinit var hoursPicker: NumberPicker
    private lateinit var minutesPicker: NumberPicker
    private lateinit var secondsPicker: NumberPicker
    private lateinit var countdown: TextView
    private lateinit var button: Button

    /** Aktualisiert die Anzeige jede halbe Sekunde, solange die App sichtbar ist. */
    private val tick = object : Runnable {
        override fun run() {
            updateUi()
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val padding = dp(24)

        // Zuletzt verwendete Zeit vorbelegen → beim nächsten Öffnen nur noch "Start" drücken
        val saved = Timer.savedSeconds(this)
        hoursPicker = wheel(count = 24, step = 1, initial = saved / 3600)
        minutesPicker = wheel(count = 60, step = 1, initial = saved % 3600 / 60)
        secondsPicker = wheel(count = 12, step = SECONDS_STEP, initial = saved % 60 / SECONDS_STEP)

        pickers = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(labeled("Stunden", hoursPicker))
            addView(colon())
            addView(labeled("Minuten", minutesPicker))
            addView(colon())
            addView(labeled("Sekunden", secondsPicker))
        }

        countdown = TextView(this).apply {
            textSize = 64f
            gravity = Gravity.CENTER
        }

        button = Button(this).apply {
            textSize = 22f
            setOnClickListener { onButtonClick() }
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(pickers, wrapContent())
            addView(countdown, wrapContent())
            addView(
                button,
                LinearLayout.LayoutParams(dp(200), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(40)
                }
            )
            // Abstand zu Statusleiste und Navigationsleiste
            setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                view.setPadding(
                    bars.left + padding,
                    bars.top + padding,
                    bars.right + padding,
                    bars.bottom + padding
                )
                insets
            }
        }

        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        handler.post(tick)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tick)
    }

    private fun onButtonClick() {
        if (Timer.remainingMillis(this) != null) {
            Timer.stop(this)
        } else {
            val seconds = hoursPicker.value * 3600 + minutesPicker.value * 60 + secondsPicker.value * SECONDS_STEP
            if (seconds == 0) {
                Toast.makeText(this, "Bitte eine Zeit einstellen", Toast.LENGTH_SHORT).show()
                return
            }
            Timer.start(this, seconds)
        }
        updateUi()
    }

    private fun updateUi() {
        val remaining = Timer.remainingMillis(this)
        val running = remaining != null

        pickers.visibility = if (running) View.GONE else View.VISIBLE
        countdown.visibility = if (running) View.VISIBLE else View.GONE
        if (remaining != null) countdown.text = format(remaining)

        button.text = if (running) "Stopp" else "Start"
    }

    /**
     * Drehrad zum Wischen, zweistellig und endlos umlaufend wie in der Samsung-Uhr.
     * Rad-Position i zeigt den Wert i * step (Sekunden: 00, 05, 10, …).
     */
    private fun wheel(count: Int, step: Int, initial: Int) = NumberPicker(this).apply {
        minValue = 0
        maxValue = count - 1
        displayedValues = Array(count) { "%02d".format(it * step) }
        value = initial.coerceIn(0, count - 1)
        wrapSelectorWheel = true
        textSize = sp(40f)
        selectionDividerHeight = 0                                     // keine Trennlinien, wie bei Samsung
        descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS  // nur wischen, keine Tastatur
    }

    private fun labeled(label: String, picker: NumberPicker) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        addView(TextView(this@MainActivity).apply {
            text = label
            textSize = 16f
            alpha = 0.6f
        }, wrapContent())
        addView(picker, LinearLayout.LayoutParams(dp(88), ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun colon() = TextView(this).apply {
        text = ":"
        textSize = 40f
        setPadding(dp(2), dp(24), dp(2), 0)   // auf Höhe der Zahlen, nicht der Beschriftung
    }

    private fun format(millis: Long): String {
        val totalSeconds = (millis + 999) / 1000
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }

    private companion object {
        /** Das Sekunden-Rad springt in 5-Sekunden-Schritten. */
        const val SECONDS_STEP = 5
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun sp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)

    private fun wrapContent() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )
}
