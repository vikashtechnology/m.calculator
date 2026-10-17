package com.advanceai.calculator.ai

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

/**
 * Dictating a question.
 *
 * This uses the phone's own speech recogniser — the same one the keyboard uses —
 * so there is no second service to pay for and no audio of ours anywhere. It is
 * a plain Android API: the recogniser returns text, and that text becomes the
 * question.
 *
 * Partial results are reported as they come, so words appear in the question box
 * while the user is still speaking.
 */
class VoiceInput(
    private val onPartial: (String) -> Unit,
    private val onFinal: (String) -> Unit,
    private val onProblem: (String) -> Unit
) {
    private var recognizer: SpeechRecognizer? = null
    private var listening = false

    val isListening: Boolean get() = listening

    fun start(context: Context) {
        if (listening) return
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onProblem("This phone has no speech recognition. Type the question instead.")
            return
        }
        val created = recognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also {
            it.setRecognitionListener(listener)
            recognizer = it
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        listening = true
        runCatching { created.startListening(intent) }.onFailure {
            listening = false
            onProblem("The microphone could not be started.")
        }
    }

    fun stop() {
        val created = recognizer ?: return
        listening = false
        runCatching { created.stopListening() }
    }

    /** Called when the screen goes away, so the microphone is never left open. */
    fun release() {
        listening = false
        recognizer?.let { created ->
            runCatching { created.cancel() }
            runCatching { created.destroy() }
        }
        recognizer = null
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() {
            listening = false
        }

        override fun onPartialResults(partialResults: Bundle?) {
            firstResult(partialResults, RecognizerIntent.EXTRA_PARTIAL_RESULTS)?.let(onPartial)
        }

        override fun onResults(results: Bundle?) {
            listening = false
            val text = firstResult(results, SpeechRecognizer.RESULTS_RECOGNITION)
            if (text.isNullOrBlank()) {
                onProblem("Nothing was picked up. Try again, a little closer to the phone.")
            } else {
                onFinal(text)
            }
        }

        override fun onError(error: Int) {
            listening = false
            onProblem(messageFor(error))
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        private fun firstResult(bundle: Bundle?, key: String): String? =
            bundle?.getStringArrayList(key)?.firstOrNull()
    }

    private fun messageFor(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH,
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Nothing was picked up. Try again."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
            "The microphone permission is needed to dictate a question."
        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            "The recogniser needs a network connection for this language."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
            "The recogniser is busy. Try again in a moment."
        else -> "The microphone stopped. Try again."
    }
}

/**
 * One [VoiceInput] per screen, released when the screen closes — a recogniser
 * left alive keeps the microphone indicator on, which looks like a bug.
 */
@Composable
fun rememberVoiceInput(
    onPartial: (String) -> Unit,
    onFinal: (String) -> Unit,
    onProblem: (String) -> Unit
): VoiceInput {
    val partial = rememberUpdatedState(onPartial)
    val final = rememberUpdatedState(onFinal)
    val problem = rememberUpdatedState(onProblem)
    val input = remember {
        VoiceInput(
            onPartial = { partial.value(it) },
            onFinal = { final.value(it) },
            onProblem = { problem.value(it) }
        )
    }
    DisposableEffect(Unit) {
        onDispose { input.release() }
    }
    return input
}
