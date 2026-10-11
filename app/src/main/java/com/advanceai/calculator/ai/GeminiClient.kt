package com.advanceai.calculator.ai

import android.content.Context
import android.util.Base64
import android.util.Log
import com.advanceai.calculator.BuildConfig
import com.advanceai.calculator.data.UserKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Ask AI without a WebView.
 *
 * Sends the uploaded question (photo, PDF or text) straight to the Gemini API
 * from the app, which is why the account step can use the phone's own Google
 * account picker instead of Google's web login page: there is no embedded
 * browser for Google to refuse.
 *
 * The key is the user's own: they paste it once on the Ask AI screen (see
 * [UserKeys]) and it is kept in the app's private storage. Nothing to sign up
 * for, nothing tied to an account. A key baked into the build is only a
 * fallback for people who have not pasted one yet.
 *
 * Every call is one-shot: no conversation history is kept or sent, so questions
 * never build on each other.
 */
object GeminiClient {

    private const val Tag = "GeminiClient"
    private const val Endpoint = "https://generativelanguage.googleapis.com/v1beta/models"

    /** Files above this size are refused before anything is uploaded. */
    const val MaxAttachmentBytes = 15 * 1024 * 1024

    private var appContext: Context? = null

    /** Called once from the app so the user's saved key can be read. */
    fun install(context: Context) {
        appContext = context.applicationContext
    }

    /** The user's own key when they saved one, otherwise the one in the build. */
    val apiKey: String
        get() {
            val context = appContext
            val personal = if (context != null) UserKeys.geminiKey(context) else ""
            return personal.ifBlank { BuildConfig.GEMINI_API_KEY }
        }

    /** True when the key came from the user rather than the build. */
    val usesUserKey: Boolean
        get() {
            val context = appContext ?: return false
            return UserKeys.geminiKey(context).isNotBlank()
        }

    /** True when Ask AI has a key to work with at all. */
    val hasKey: Boolean
        get() = apiKey.isNotBlank()

    /** The model name compiled into the build, if any. */
    val builtInModel: String = BuildConfig.GEMINI_MODEL

    /** What the next request will use: the user's pick, the build's, or 3.6 Flash. */
    val model: String
        get() = AiModels.resolve(requireNotNull(appContext) { "GeminiClient.install was not called" })

    /** True when Ask AI can answer at all. */
    val isConfigured: Boolean
        get() = hasKey

    /** A file the user picked: the bytes are what actually gets sent. */
    data class Attachment(
        val mimeType: String,
        val bytes: ByteArray,
        val name: String = ""
    )

    data class Answer(
        val ok: Boolean,
        val text: String = "",
        val error: String = ""
    )

    /**
     * The instruction is deliberately narrow: solve the question in the upload
     * and nothing else, and admit when there is no question in it.
     */
    private fun systemPrompt(): String = buildString {
        append("You are the maths assistant inside a calculator app. ")
        append("Answer the question the user sent — typed, or shown in an uploaded ")
        append("photo, PDF or text file — and nothing else. ")
        append("Reply with the answer first, then the steps, using short lines and ")
        append("plain text maths (^ for powers, sqrt() for roots, * for multiplication). ")
        append("When only a file is sent, solve the question in that file; if it holds ")
        append("no maths question, reply exactly: ")
        append("\"I could not find a maths question in this file.\" ")
        append("Never invent a question, and never solve anything that was not sent.")
    }

    suspend fun solve(question: String, attachment: Attachment?): Answer =
        withContext(Dispatchers.IO) { request(question, attachment) }

    /** How the attachment travels: base64 in JSON, for either endpoint. */
    private fun encoded(attachment: Attachment): String =
        Base64.encodeToString(attachment.bytes, Base64.NO_WRAP)

    private fun request(question: String, attachment: Attachment?): Answer {
        if (apiKey.isBlank()) return Answer(ok = false, error = "no_key")
        if (attachment != null && attachment.bytes.size > MaxAttachmentBytes) {
            return Answer(ok = false, error = "That file is larger than 15 MB. Try a screenshot instead.")
        }

        if (question.isBlank() && attachment == null) {
            return Answer(ok = false, error = "Type a question or add a photo first.")
        }

        val parts = JSONArray().apply {
            put(JSONObject().put("text", systemPrompt()))
            val asked = question.trim()
            if (asked.isNotEmpty()) {
                put(
                    JSONObject().put(
                        "text",
                        if (attachment == null) "The user asks: $asked"
                        else "The user also added: $asked"
                    )
                )
            }
            if (attachment != null) {
                put(
                    JSONObject().put(
                        "inlineData",
                        JSONObject()
                            .put("mimeType", attachment.mimeType)
                            .put("data", encoded(attachment))
                    )
                )
            }
        }

        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", parts)))
            // Note: temperature/topP are ignored by the 3.6 and newer Flash
            // models, so only the output ceiling is sent.
            .put("generationConfig", JSONObject().put("maxOutputTokens", 8192))
            .toString()
            .toByteArray(Charsets.UTF_8)

        val url = URL("$Endpoint/${URLEncoder.encode(model, "UTF-8")}:generateContent")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 120_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("x-goog-api-key", apiKey)
            setRequestProperty("Content-Length", body.size.toString())
        }

        return try {
            connection.outputStream.use { it.write(body) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { reader -> reader.readText() }.orEmpty()
            if (code !in 200..299) {
                Answer(ok = false, error = describeError(code, text))
            } else {
                readAnswer(text)
            }
        } catch (e: Exception) {
            Log.w(Tag, "Gemini request failed", e)
            Answer(ok = false, error = e.message ?: "The request failed. Check the connection.")
        } finally {
            connection.disconnect()
        }
    }

    private fun readAnswer(responseText: String): Answer {
        val json = runCatching { JSONObject(responseText) }.getOrNull()
            ?: return Answer(ok = false, error = "The model sent something unreadable.")

        json.optJSONObject("promptFeedback")?.optString("blockReason")?.takeIf { it.isNotEmpty() }
            ?.let { reason -> return Answer(ok = false, error = "The upload was blocked ($reason).") }

        val candidate = json.optJSONArray("candidates")?.optJSONObject(0)
            ?: return Answer(ok = false, error = "No answer came back. Try again.")

        val pieces = candidate.optJSONObject("content")?.optJSONArray("parts")
        val answer = buildString {
            if (pieces != null) {
                for (index in 0 until pieces.length()) {
                    val piece = pieces.optJSONObject(index)?.optString("text").orEmpty()
                    if (piece.isNotEmpty()) append(piece)
                }
            }
        }.trim()

        if (answer.isEmpty()) {
            return when (candidate.optString("finishReason")) {
                "SAFETY", "PROHIBITED_CONTENT" ->
                    Answer(ok = false, error = "The upload was blocked by the safety filter.")
                "MAX_TOKENS" ->
                    Answer(ok = false, error = "The answer was cut off. Ask a shorter question.")
                else -> Answer(ok = false, error = "The model returned an empty answer.")
            }
        }
        return Answer(ok = true, text = answer)
    }

    private fun describeError(code: Int, body: String): String {
        val message = runCatching {
            JSONObject(body).optJSONObject("error")?.optString("message")
        }.getOrNull().orEmpty()
        return when (code) {
            400 -> if (message.contains("API key", ignoreCase = true)) {
                "That Gemini API key is not valid. Check the GEMINI_API_KEY secret."
            } else {
                message.ifEmpty { "The model rejected that upload." }
            }
            401, 403 -> "The API key was refused. Check the GEMINI_API_KEY secret."
            404 -> "The model \"$model\" is not available for this key — pick another on the " +
                "Ask AI settings card."
            429 -> "Too many requests just now. Wait a moment and try again."
            in 500..599 -> "The Gemini service is busy. Try again in a moment."
            else -> message.ifEmpty { "The request failed (HTTP $code)." }
        }
    }
}
