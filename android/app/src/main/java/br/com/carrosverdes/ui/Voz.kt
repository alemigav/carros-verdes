package br.com.carrosverdes.ui

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * Leitura em voz alta pelo motor do próprio Android: funciona sem internet
 * e sem serviço de terceiros. Quem não enxerga bem ouve o resultado.
 */
class Voz(context: Context) {

    private var motor: TextToSpeech? = null
    var disponivel by mutableStateOf(false)
        private set

    private var idiomaConfigurado = false

    init {
        motor = TextToSpeech(context.applicationContext) { status ->
            disponivel = status == TextToSpeech.SUCCESS
        }
    }

    private fun configurarIdioma(t: TextToSpeech) {
        val resultado = t.setLanguage(Locale("pt", "BR"))
        if (resultado == TextToSpeech.LANG_MISSING_DATA || resultado == TextToSpeech.LANG_NOT_SUPPORTED) {
            t.setLanguage(Locale.getDefault())
        }
        t.setSpeechRate(0.92f)
        idiomaConfigurado = true
    }

    fun falar(texto: String) {
        val t = motor ?: return
        if (!disponivel) return
        if (!idiomaConfigurado) configurarIdioma(t)
        t.stop()
        t.speak(texto, TextToSpeech.QUEUE_FLUSH, null, "carros-verdes")
    }

    fun parar() {
        motor?.stop()
    }

    fun encerrar() {
        motor?.stop()
        motor?.shutdown()
        motor = null
        disponivel = false
    }
}

val LocalVoz = compositionLocalOf<Voz?> { null }
