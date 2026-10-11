package com.advanceai.calculator.ai

import android.content.Context
import com.advanceai.calculator.data.Telemetry
import com.advanceai.calculator.data.UserKeys
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
        _turns.value = _turns.value + Turn(fromUser = false, text = "Thinking…", kind = Turn.Kind.PENDING)
        _busy.value = true

        val appContext = context.applicationContext
        job = scope.launch {
            val result = GeminiClient.solve(
                question = asked,
                attachment = attachment?.let {
                    GeminiClient.Attachment(it.mimeType, it.bytes, it.name)
                }
            )
            val answerTurn = if (result.ok) {
                Turn(fromUser = false, text = result.text)
            } else {
                Turn(fromUser = false, text = messageFor(result.error), kind = Turn.Kind.ERROR)
            }
            // Replace the "Thinking…" bubble with the answer.
            _turns.value = _turns.value.filterNot { it.kind == Turn.Kind.PENDING } + answerTurn
            _busy.value = false
            if (result.ok) {
                // Anonymous count for the dashboard; the question itself is not sent.
                Telemetry.record(appContext, Telemetry.EVENT_AI)
            }
            if (visible == 0) {
                AnswerNotifier.notify(appContext, result.ok, answerTurn.text)
            }
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
        _turns.value = _turns.value.filterNot { it.kind == Turn.Kind.PENDING }
        _busy.value = false
    }

    /** "New chat": forgets every turn, which is all this screen ever keeps. */
    fun clear() {
        cancel()
        _turns.value = emptyList()
    }

    private fun describeKind(mimeType: String): String = when {
        mimeType.startsWith("image/") -> "photo"
        mimeType == "application/pdf" -> "PDF"
        else -> "text"
    }

    private fun messageFor(error: String): String = when (error) {
        "no_key" -> "Add your Gemini API key first, then ask again."
        "sign_in_required" -> "Your sign-in expired. Sign in again to carry on."
        else -> error
    }
}

/** What the camera or the file picker handed back, kept small and in memory. */
class PickedAttachment(
    val name: String,
    val mimeType: String,
    val bytes: ByteArray
)

/**
 * Where the model names live. The default is Gemini 3.6 Flash, as asked for;
 * newer Flash models are one tap away on the key card because Google retires
 * these quickly.
 */
object AiModels {

    const val DEFAULT = "gemini-3.6-flash"

    val choices: List<Pair<String, String>> = listOf(
        "gemini-3.6-flash" to "3.6 Flash (default)",
        "gemini-3.8-flash" to "3.8 Flash (newest)",
        "gemini-3.5-flash" to "3.5 Flash",
        "gemini-3.5-flash-lite" to "3.5 Flash-Lite"
    )

    /** The model to use: the user's pick, the build's pick, or the default. */
    fun resolve(context: Context): String {
        val saved = UserKeys.geminiModel(context).trim()
        if (saved.isNotEmpty()) return saved
        val built = GeminiClient.builtInModel.trim()
        return built.ifEmpty { DEFAULT }
    }

    fun describe(model: String): String =
        choices.firstOrNull { it.first == model }?.second ?: model
}
