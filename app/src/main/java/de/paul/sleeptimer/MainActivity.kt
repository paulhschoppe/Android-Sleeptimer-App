package de.paul.sleeptimer

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * Einziger Bildschirm: Minuten eintippen → Start → Countdown.
 * Läuft der Timer ab, pausiert die Musik (siehe TimerReceiver).
 */
class MainActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())

    private lateinit var minutesInput: EditText
    private lateinit var minutesLabel: TextView
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

        minutesInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            imeOptions = EditorInfo.IME_ACTION_DONE
            textSize = 56f
            gravity = Gravity.CENTER
            minEms = 3
            setSelectAllOnFocus(true)
            // Zuletzt verwendeten Wert vorbelegen → beim nächsten Öffnen nur noch "Start" drücken
            setText(Timer.savedMinutes(this@MainActivity).toString())
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    onButtonClick()
                    true
                } else {
                    false
                }
            }
        }

        minutesLabel = TextView(this).apply {
            text = "Minuten"
            textSize = 18f
            gravity = Gravity.CENTER
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
            addView(minutesInput, wrapContent())
            addView(minutesLabel, wrapContent())
            addView(countdown, wrapContent())
            addView(
                button,
                LinearLayout.LayoutParams(dp(200), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(32)
                }
            )
            // Abstand zu Statusleiste, Navigationsleiste und Tastatur
            setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
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
            val minutes = minutesInput.text.toString().toIntOrNull()
            if (minutes == null || minutes !in 1..1440) {
                Toast.makeText(this, "Bitte 1 bis 1440 Minuten eingeben", Toast.LENGTH_SHORT).show()
                return
            }
            Timer.start(this, minutes)
            hideKeyboard()
        }
        updateUi()
    }

    private fun updateUi() {
        val remaining = Timer.remainingMillis(this)
        val running = remaining != null

        minutesInput.visibility = if (running) View.GONE else View.VISIBLE
        minutesLabel.visibility = if (running) View.GONE else View.VISIBLE
        countdown.visibility = if (running) View.VISIBLE else View.GONE
        if (remaining != null) countdown.text = format(remaining)

        button.text = if (running) "Stopp" else "Start"
    }

    private fun hideKeyboard() {
        getSystemService(InputMethodManager::class.java)
            .hideSoftInputFromWindow(minutesInput.windowToken, 0)
        minutesInput.clearFocus()
    }

    private fun format(millis: Long): String {
        val totalSeconds = (millis + 999) / 1000
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun wrapContent() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )
}
