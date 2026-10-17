package com.advanceai.calculator.ai

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The Ask AI conversation.
 *
 * Deliberately memoryless: turns live in this object only while the app is
 * running, are never written to disk, and are never sent back to the model — so
 * every question is answered on its own. Closing the app, or *New chat*, wipes
 * everything.
 *
 * Solving runs on an application-level scope rather than the screen's, so the
 * answer still arrives (and can raise a notification) if the user wanders off
 * to another part of the app while the model is thinking.
 */
object AiChat {

    data class Turn(
        val fromUser: Boolean,
        val text: String,
        val attachment: String? = null,
        val kind: Kind = Kind.MESSAGE,
        /** Which model wrote this, shown under the bubble. */
        val model: String? = null,
        val at: Long = System.currentTimeMillis()
    ) {
        enum class Kind { MESSAGE, PENDING, ERROR }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _turns = MutableStateFlow<List<Turn>>(emptyList())
    val turns: StateFlow<List<Turn>> = _turns.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private var job: Job? = null

    /** Set by the Stop button; polled while the answer streams in. */
    @Volatile
    private var stopWanted = false

    /** How many chat screens are on screen right now (0 = the user is elsewhere). */
    private var visible = 0

    fun onScreenVisible() {
        visible += 1
    }

    fun onScreenHidden() {
        visible = (visible - 1).coerceAtLeast(0)
    }

    fun ask(context: Context, question: String, attachment: PickedAttachment?) {
        val asked = question.trim()
        if (asked.isEmpty() && attachment == null) return
        if (_busy.value) return

        val label = attachment?.let { "${it.name} · ${describeKind(it.mimeType)}" }
        _turns.value = _turns.value + Turn(
            fromUser = true,
            text = asked,
            attachment = label
        )
        // The answer bubble is created empty and grows as fragments arrive, so
        // the words appear while the model is still writing them.
        _turns.value = _turns.value + Turn(fromUser = false, text = "", kind = Turn.Kind.PENDING)
        _busy.value = true
        stopWanted = false

        val appContext = context.applicationContext
        job = scope.launch {
            val result = OpenAiClient.chat(
                context = appContext,
                question = asked,
                attachment = attachment?.let {
                    OpenAiClient.Attachment(it.mimeType, it.bytes, it.name)
                },
                onDelta = { fragment -> appendToPending(fragment) },
                stopSignal = { stopWanted }
            )

            val stopped = stopWanted
            val answerTurn = when {
                stopped -> Turn(
                    fromUser = false,
                    text = pendingText().ifEmpty { "Stopped." },
                    model = result.model.ifEmpty { null }
                )
                result.ok -> Turn(fromUser = false, text = result.text, model = result.model.ifEmpty { null })
                else -> Turn(
                    fromUser = false,
                    text = messageFor(result.error),
                    kind = Turn.Kind.ERROR
                )
            }
            _turns.value = _turns.value.filterNot { it.kind == Turn.Kind.PENDING } + answerTurn
            _busy.value = false
            stopWanted = false
            if (visible == 0 && answerTurn.text.isNotBlank()) {
                AnswerNotifier.notify(appContext, result.ok, answerTurn.text)
            }
        }
    }

    /** Adds one streamed fragment to the answer that is being written. */
    private fun appendToPending(fragment: String) {
        val turns = _turns.value
        val index = turns.indexOfLast { it.kind == Turn.Kind.PENDING }
        if (index < 0) return
        val pending = turns[index]
        _turns.value = turns.toMutableList().also { list ->
            // The first fragment turns "waiting" into text that is arriving.
            list[index] = pending.copy(text = pending.text + fragment)
        }
    }

    private fun pendingText(): String =
        _turns.value.lastOrNull { it.kind == Turn.Kind.PENDING }?.text.orEmpty().trim()

    /**
     * Asks the request to stop. The event stream is read on a worker thread, so
     * the flag is what actually interrupts it; cancelling the coroutine as well
     * means a request that has not started yet never does.
     */
    fun cancel() {
        stopWanted = true
        job?.cancel()
        job = null
        val partial = pendingText()
        _turns.value = _turns.value.filterNot { it.kind == Turn.Kind.PENDING }
        if (partial.isNotEmpty()) {
            _turns.value = _turns.value + Turn(fromUser = false, text = partial)
        }
        _busy.value = false
    }

    /** "New chat": forgets every turn, which is all this screen ever keeps. */
    fun clear() {
        stopWanted = true
        job?.cancel()
        job = null
        _busy.value = false
        _turns.value = emptyList()
    }

    private fun describeKind(mimeType: String): String = when {
        mimeType.startsWith("image/") -> "photo"
        mimeType == "application/pdf" -> "PDF"
        else -> "text"
    }

    private fun messageFor(error: String): String = when (error) {
        "no_key" -> "Paste your API key first, then ask again — the key settings are one tap " +
            "away in the header."
        else -> error
    }
}

/** What the camera or the file picker handed back, kept small and in memory. */
class PickedAttachment(
    val name: String,
    val mimeType: String,
    val bytes: ByteArray
)
