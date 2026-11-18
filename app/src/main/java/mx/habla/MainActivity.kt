package mx.habla

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener {

    private lateinit var tts: TextToSpeech
    private lateinit var input: EditText
    private lateinit var status: TextView
    private var ready = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        input = findViewById(R.id.input)
        status = findViewById(R.id.status)

        // Teclado normal del celular, texto que baja de línea y tecla "enviar" que habla.
        input.setRawInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
        input.imeOptions = EditorInfo.IME_ACTION_SEND
        input.setHorizontallyScrolling(false)
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) { speak(); true } else false
        }

        findViewById<Button>(R.id.speak).setOnClickListener { speak() }
        findViewById<Button>(R.id.clear).setOnClickListener {
            tts.stop()
            input.setText("")
            input.requestFocus()
        }

        tts = TextToSpeech(this, this)
        Updater.check(this)
    }

    override fun onInit(result: Int) {
        if (result != TextToSpeech.SUCCESS) {
            status.text = "No se pudo iniciar la voz del celular"
            return
        }
        val mx = Locale("es", "MX")
        val ok = tts.isLanguageAvailable(mx) >= TextToSpeech.LANG_AVAILABLE
        if (ok) {
            tts.language = mx
            // Si hay una voz específica de México, la usa.
            tts.voices
                ?.filter { it.locale.language == "es" && it.locale.country == "MX" }
                ?.sortedBy { it.isNetworkConnectionRequired }
                ?.firstOrNull()
                ?.let { tts.voice = it }
            ready = true
        } else {
            // Respaldo: otro español, avisando que falta la voz mexicana.
            val fallback = listOf(Locale("es", "US"), Locale("es", "ES"), Locale("es"))
                .firstOrNull { tts.isLanguageAvailable(it) >= TextToSpeech.LANG_AVAILABLE }
            if (fallback != null) {
                tts.language = fallback
                ready = true
            }
            status.text = "Falta la voz de español (México). Toca aquí para instalarla"
            status.setOnClickListener {
                try {
                    startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA))
                } catch (e: Exception) {
                    startActivity(Intent("com.android.settings.TTS_SETTINGS"))
                }
            }
        }
    }

    private fun speak() {
        val text = input.text.toString().trim()
        if (text.isEmpty() || !ready) return
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "habla")
    }

    override fun onDestroy() {
        tts.stop()
        tts.shutdown()
        super.onDestroy()
    }
}
