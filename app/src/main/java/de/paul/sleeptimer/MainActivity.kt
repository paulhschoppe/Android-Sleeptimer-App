package de.paul.sleeptimer

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.TextView
import android.widget.Toast

/**
 * Einziger Bildschirm: Stunden, Minuten und Sekunden (5er-Schritte) an Rädern einstellen wie in der Samsung-Uhr,
 * darunter Schnellwahl-Knöpfe. Nach "Start" laufen die Räder in 5-Sekunden-Schritten rückwärts und zeigen die Restzeit.
 * Die letzten 2 Minuten wird die Musik leiser, dann pausiert (siehe FadeService).
 */
class MainActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())

    private lateinit var presets: View
    private lateinit var hoursPicker: NumberPicker
    private lateinit var minutesPicker: NumberPicker
    private lateinit var secondsPicker: NumberPicker
    private lateinit var button: Button

    /** null = noch nicht festgelegt, sonst ob die Räder gerade die Restzeit anzeigen. */
    private var countingDown: Boolean? = null

    /** Laufende Roll-Animationen je Rad. */
    private val rolling = HashMap<NumberPicker, ValueAnimator>()

    /** Aktualisiert die Anzeige mehrmals pro Sekunde, solange die App sichtbar ist. */
    private val tick = object : Runnable {
        override fun run() {
            updateUi()
            handler.postDelayed(this, 250)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val padding = dp(24)

        hoursPicker = wheel(count = 24)
        minutesPicker = wheel(count = 60)
        secondsPicker = wheel(count = 60 / SECONDS_STEP, step = SECONDS_STEP)
        // Zuletzt verwendete Zeit vorbelegen → beim nächsten Öffnen nur noch "Start" drücken
        setWheels(Timer.savedSeconds(this))

        val pickers = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(labeled("Stunden", hoursPicker))
            addView(colon())
            addView(labeled("Minuten", minutesPicker))
            addView(colon())
            addView(labeled("Sekunden", secondsPicker))
        }

        // Voreinstellungen: ein Tipp stellt alle Räder auf genau diese Zeit
        presets = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            for (minutes in PRESET_MINUTES) {
                addView(
                    Button(this@MainActivity).apply {
                        text = "$minutes Min"
                        textSize = 16f
                        setOnClickListener { setWheels(minutes * 60) }
                    },
                    LinearLayout.LayoutParams(dp(110), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        marginStart = dp(6)
                        marginEnd = dp(6)
                    }
                )
            }
        }

        button = Button(this).apply {
            textSize = 22f
            setOnClickListener { onButtonClick() }
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(pickers, wrapContent())
            addView(presets, wrapContent().apply { topMargin = dp(16) })
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
            askForNotificationPermission()
        }
        updateUi()
    }

    private fun updateUi() {
        val remaining = Timer.remainingMillis(this)
        val running = remaining != null

        if (countingDown != running) {
            countingDown = running
            finishRolling()
            // Timer vorbei oder gestoppt → wieder die zuletzt gestellte Zeit zeigen
            if (!running) setWheels(Timer.savedSeconds(this))
            // INVISIBLE statt GONE: der Start/Stopp-Knopf bleibt an derselben Stelle
            presets.visibility = if (running) View.INVISIBLE else View.VISIBLE
            button.text = if (running) "Stopp" else "Start"
        }

        if (remaining != null) {
            // Restzeit auf volle 5 Sekunden aufgerundet – das Sekunden-Rad zählt in seinen 5er-Schritten herunter
            val seconds = ((remaining + 999) / 1000).toInt()
            val shown = (seconds + SECONDS_STEP - 1) / SECONDS_STEP * SECONDS_STEP
            rollTo(hoursPicker, shown / 3600)
            rollTo(minutesPicker, shown % 3600 / 60)
            rollTo(secondsPicker, shown % 60 / SECONDS_STEP)
        }
    }

    /** Stellt alle drei Räder auf die angegebene Dauer – egal, was vorher eingestellt war. */
    private fun setWheels(seconds: Int) {
        hoursPicker.value = seconds / 3600
        minutesPicker.value = seconds % 3600 / 60
        secondsPicker.value = seconds % 60 / SECONDS_STEP
    }

    /**
     * Lässt das Rad auf [target] laufen. Geht es genau eine Zahl rückwärts (der Normalfall im Countdown),
     * rollt das Rad weich nach unten wie beim Wischen; alles andere (Start, +5 Min) springt direkt.
     */
    private fun rollTo(picker: NumberPicker, target: Int) {
        if (rolling.containsKey(picker)) {
            // Vorige Animation noch nicht fertig: nur eingreifen, wenn sich das Ziel geändert hat
            if (picker.tag == target) return
            rolling.remove(picker)?.end()
        }
        if (picker.value == target) return

        val count = picker.maxValue + 1
        val oneDown = (picker.value - 1 + count) % count == target
        if (!oneDown || picker.height == 0) {
            picker.value = target
            return
        }

        // Höhe einer Zeile, genau wie NumberPicker sie berechnet (3 sichtbare Zeilen)
        val rowHeight = (picker.height / 3f + 0.5f).toInt()
        var applied = 0
        picker.tag = target
        rolling[picker] = ValueAnimator.ofInt(0, rowHeight).apply {
            duration = 400
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                val now = it.animatedValue as Int
                picker.scrollBy(0, now - applied)   // positiv = Rad dreht nach unten, Wert wird kleiner
                applied = now
                picker.invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (rolling[picker] === animation) rolling.remove(picker)
                    if (picker.value != target) picker.value = target   // Sicherheitsnetz
                }
            })
            start()
        }
    }

    /** Alle Roll-Animationen sofort abschließen, damit die Räder sauber auf ihrer Zeile stehen. */
    private fun finishRolling() {
        rolling.values.toList().forEach { it.end() }
        rolling.clear()
    }

    /** Für die Countdown-Benachrichtigung; fragt Android nur, solange noch nicht entschieden wurde. */
    private fun askForNotificationPermission() {
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        // Gerade erlaubt → Benachrichtigung für den bereits laufenden Timer nachreichen
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            Timer.endMillis(this)?.let { TimerNotification.show(this, it) }
        }
    }

    /**
     * Drehrad zum Wischen, zweistellig und endlos umlaufend wie in der Samsung-Uhr.
     * Rad-Position i zeigt den Wert i * step (Sekunden: 00, 05, 10, …).
     */
    private fun wheel(count: Int, step: Int = 1) = NumberPicker(this).apply {
        minValue = 0
        maxValue = count - 1
        displayedValues = Array(count) { "%02d".format(it * step) }
        wrapSelectorWheel = true
        textSize = sp(40f)
        selectionDividerHeight = 0                                     // keine Trennlinien, wie bei Samsung
        descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS  // nur wischen, keine Tastatur
        // Während des Countdowns lassen sich die Räder nicht verstellen
        setOnTouchListener { _, _ -> countingDown == true }
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

    private companion object {
        /** Das Sekunden-Rad springt in 5-Sekunden-Schritten – beim Einstellen und beim Herunterzählen. */
        const val SECONDS_STEP = 5

        /** Schnellwahl-Knöpfe unter den Rädern. */
        val PRESET_MINUTES = intArrayOf(15, 10)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun sp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)

    private fun wrapContent() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )
}
