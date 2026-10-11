package com.advanceai.calculator.ai

import android.util.Base64
import android.util.Log
import com.advanceai.calculator.BuildConfig
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
 * The API key comes from the build (see gradle.properties / the GEMINI_API_KEY
 * repository secret). Without a key [isConfigured] is false and the Ask AI
 * screen keeps using the bundled Puter page.
 */
object GeminiClient {

    private const val Tag = "GeminiClient"
    private const val Endpoint = "https://generativelanguage.googleapis.com/v1beta/models"

    /** Files above this size are refused before anything is uploaded. */
    const val MaxAttachmentBytes = 15 * 1024 * 1024

    val apiKey: String = BuildConfig.GEMINI_API_KEY
    val model: String = BuildConfig.GEMINI_MODEL.ifBlank { "gemini-2.5-flash" }

    val isConfigured: Boolean
        get() = apiKey.isNotBlank()

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
        append("You are the solver inside a maths calculator app. ")
        append("Solve only the question contained in the file the user uploads. ")
        append("Reply with the answer first, then the steps, using short lines and ")
        append("plain text maths (^ for powers, sqrt() for roots, * for multiplication). ")
        append("If the upload does not contain a maths question, reply exactly: ")
        append("\"I could not find a maths question in this file.\" ")
        append("Never invent a question, and never solve anything that is not in the file.")
    }

    suspend fun solve(question: String, attachment: Attachment?): Answer =
        withContext(Dispatchers.IO) { request(question, attachment) }

    private fun request(question: String, attachment: Attachment?): Answer {
        if (!isConfigured) return Answer(ok = false, error = "not_configured")
        if (attachment != null && attachment.bytes.size > MaxAttachmentBytes) {
            return Answer(ok = false, error = "That file is larger than 15 MB. Try a screenshot instead.")
        }

        val parts = JSONArray().apply {
            put(JSONObject().put("text", systemPrompt()))
            val asked = question.trim()
            if (asked.isNotEmpty()) {
                put(JSONObject().put("text", "The user also added: $asked"))
            }
            if (attachment != null) {
                put(
                    JSONObject().put(
                        "inlineData",
                        JSONObject()
                            .put("mimeType", attachment.mimeType)
                            .put("data", Base64.encodeToString(attachment.bytes, Base64.NO_WRAP))
                    )
                )
            }
        }

        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", parts)))
            .put(
                "generationConfig",
                JSONObject().put("temperature", 0.2).put("maxOutputTokens", 4096)
            )
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
            404 -> "The model \"$model\" is not available for this key."
            429 -> "Too many requests just now. Wait a moment and try again."
            in 500..599 -> "The Gemini service is busy. Try again in a moment."
            else -> message.ifEmpty { "The request failed (HTTP $code)." }
        }
    }
}
