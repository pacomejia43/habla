package mx.habla

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
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
    private lateinit var speedBtn: Button
    private var ready = false

    // Frases pendientes de leer, una por una, para poder cambiar el tono de preguntas y exclamaciones.
    private val pending = mutableListOf<String>()
    private var session = 0

    private val speeds = floatArrayOf(1.0f, 1.5f, 2.0f)
    private val speedLabels = arrayOf("1x", "1.5x", "2x")
    private var speedIdx = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        input = findViewById(R.id.input)
        status = findViewById(R.id.status)
        speedBtn = findViewById(R.id.speed)

        // Teclado normal del celular, texto que baja de línea y tecla "enviar" que habla.
        input.setRawInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
        input.imeOptions = EditorInfo.IME_ACTION_SEND
        input.setHorizontallyScrolling(false)
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) { speak(); true } else false
        }

        findViewById<Button>(R.id.speak).setOnClickListener { speak() }
        speedBtn.setOnClickListener {
            speedIdx = (speedIdx + 1) % speeds.size
            speedBtn.text = speedLabels[speedIdx]
        }
        findViewById<Button>(R.id.clear).setOnClickListener {
            stopSpeaking()
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

        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}

            override fun onDone(utteranceId: String?) {
                if (utteranceId == "s$session") runOnUiThread { speakNext() }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                if (utteranceId == "s$session") runOnUiThread { speakNext() }
            }
        })

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
        // Quitamos ¿ y ¡ porque el celular las podría leer en voz alta; el tono ya marca la pregunta.
        val text = input.text.toString().replace("¿", "").replace("¡", "").trim()
        if (text.isEmpty() || !ready) return

        stopSpeaking()
        Regex("[^.!?\\n]+[.!?]*").findAll(text)
            .map { it.value.trim() }
            .filter { it.isNotEmpty() }
            .forEach { pending.add(it) }
        speakNext()
    }

    private fun speakNext() {
        if (pending.isEmpty()) return
        val sentence = pending.removeAt(0)
        tts.setSpeechRate(speeds[speedIdx])
        tts.setPitch(
            when {
                sentence.endsWith("?") -> 1.25f   // pregunta: sube el tono
                sentence.endsWith("!") -> 1.15f   // exclamación: tono un poco más alto
                else -> 1.0f
            }
        )
        tts.speak(sentence, TextToSpeech.QUEUE_FLUSH, null, "s$session")
    }

    private fun stopSpeaking() {
        session++          // invalida las frases que aún estén terminando
        pending.clear()
        tts.stop()
    }

    override fun onDestroy() {
        stopSpeaking()
        tts.shutdown()
        super.onDestroy()
    }
}
