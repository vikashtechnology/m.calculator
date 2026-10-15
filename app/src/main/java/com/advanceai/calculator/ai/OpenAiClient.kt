package com.advanceai.calculator.ai

import android.content.Context
import android.util.Log
import com.advanceai.calculator.data.UserKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * The OpenAI-compatible client behind Ask AI.
 *
 * Works with any service that speaks `POST /chat/completions` — Groq by
 * default, and OpenRouter, Cerebras or the user's own endpoint if they prefer.
 * The question is sent straight from the phone to that service with the key the
 * user pasted; the app is not in the middle and nothing is written to disk.
 *
 * **Streaming.** The answer arrives as server-sent events and every fragment is
 * handed to [onDelta] as it lands, so the text appears while it is being
 * written instead of after a long wait. [stream] handles the whole event
 * format, including the `[DONE]` marker.
 *
 * **One question at a time.** No history is sent: each request carries the
 * system prompt plus this question only, which is what "no memory" means here.
 */
object OpenAiClient {

    private const val Tag = "OpenAiClient"

    /** Attachments that travel as text are cut to this many characters. */
    const val MaxTextChars = 20_000

    /** A file the user picked: the bytes are what actually gets sent. */
    data class Attachment(
        val mimeType: String,
        val bytes: ByteArray,
        val name: String = ""
    ) {
        val isImage: Boolean get() = mimeType.startsWith("image/")
        val isPdf: Boolean get() = mimeType == "application/pdf"
        val isText: Boolean get() = mimeType.startsWith("text/") || mimeType == "application/json"
    }

    data class Answer(
        val ok: Boolean,
        val text: String = "",
        val error: String = "",
        /** The model that actually answered, which may differ for a photo. */
        val model: String = ""
    )

    /**
     * The instruction is deliberately narrow: answer the question that was sent
     * and nothing else, and admit when there is no question in an upload.
     */
    private val systemPrompt = buildString {
        append("You are the maths assistant inside a calculator app. ")
        append("Answer the question the user sent — typed, or shown in an uploaded ")
        append("photo or text file — and nothing else. ")
        append("Reply with the answer first, then the steps, using short lines and ")
        append("plain text maths (^ for powers, sqrt() for roots, * for multiplication). ")
        append("Keep it brief: this is read on a phone screen. ")
        append("When only a file is sent, solve the question in that file; if it holds ")
        append("no maths question, reply exactly: ")
        append("\"I could not find a maths question in this file.\" ")
        append("Never invent a question, and never solve anything that was not sent.")
    }

    /** True when Ask AI has everything it needs for this question. */
    fun isConfigured(context: Context): Boolean =
        UserKeys.apiKey(context).isNotBlank() && baseUrlFor(context).isNotBlank()

    /** The base URL of the chosen provider, or the custom one the user typed. */
    fun baseUrlFor(context: Context): String {
        val provider = UserKeys.provider(context)
        return if (provider.needsBaseUrl) UserKeys.baseUrl(context).trim() else provider.baseUrl
    }

    /** What the settings card and the chat header show as the chosen model. */
    fun selectedModel(context: Context): String {
        val provider = UserKeys.provider(context)
        return UserKeys.model(context).ifBlank { provider.defaultModel }
    }

    /**
     * Which model answers. A photo needs a model that can see, so when the
     * chosen one cannot, the provider's vision model steps in for that question
     * only — and the answer says which model it was.
     */
    fun modelFor(context: Context, needsVision: Boolean): String {
        val provider = UserKeys.provider(context)
        val chosen = UserKeys.model(context).ifBlank { provider.defaultModel }
        if (!needsVision) return chosen
        if (provider.visionModel.isBlank()) return chosen
        // Already on the vision model, or on one that reads photos itself.
        if (chosen == provider.visionModel) return chosen
        return provider.visionModel
    }

    /**
     * Asks the model, streaming the answer back through [onDelta].
     *
     * [stopSignal] is polled between fragments so the Stop button can end a long
     * answer without waiting for it to finish.
     */
    suspend fun chat(
        context: Context,
        question: String,
        attachment: Attachment?,
        onDelta: (String) -> Unit,
        stopSignal: () -> Boolean = { false }
    ): Answer = withContext(Dispatchers.IO) {
        val key = UserKeys.apiKey(context)
        if (key.isBlank()) return@withContext Answer(ok = false, error = "no_key")

        val base = baseUrlFor(context)
        if (base.isBlank()) {
            return@withContext Answer(
                ok = false,
                error = "Add the address of your AI service in the key settings first."
            )
        }

        val model = modelFor(context, needsVision = attachment?.isImage == true)
        if (model.isBlank()) {
            return@withContext Answer(
                ok = false,
                error = "Pick a model in the key settings first (Load models shows what your " +
                    "key can use)."
            )
        }

        if (question.isBlank() && attachment == null) {
            return@withContext Answer(ok = false, error = "Type a question or add a photo first.")
        }

        val message = buildContent(question, attachment, model)
        if (message is Content.Problem) {
            return@withContext Answer(ok = false, error = message.text, model = model)
        }
        val body = requestBody(model, (message as Content.Ok).value)

        val url = URL("${base.trimEnd('/')}/chat/completions")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 120_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "text/event-stream")
            setRequestProperty("Authorization", "Bearer $key")
        }

        try {
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            if (code !in 200..299) {
                val text = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                return@withContext Answer(ok = false, error = describeError(code, text), model = model)
            }
            connection.inputStream.bufferedReader().use { reader ->
                readStream(reader, onDelta, stopSignal, model)
            }
        } catch (e: Exception) {
            if (stopSignal()) return@withContext Answer(ok = true, text = "", model = model)
            Log.w(Tag, "Chat request failed", e)
            Answer(
                ok = false,
                error = e.message ?: "The request failed. Check the connection.",
                model = model
            )
        } finally {
            runCatching { connection.disconnect() }
        }
    }

    /** The model ids the key can use, straight from the provider. */
    suspend fun listModels(context: Context): List<String> = withContext(Dispatchers.IO) {
        val key = UserKeys.apiKey(context)
        val base = baseUrlFor(context)
        if (key.isBlank() || base.isBlank()) return@withContext emptyList()
        val connection = runCatching {
            (URL("${base.trimEnd('/')}/models").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 30_000
                setRequestProperty("Authorization", "Bearer $key")
            }
        }.getOrNull() ?: return@withContext emptyList()

        try {
            if (connection.responseCode !in 200..299) return@withContext emptyList()
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            val data = JSONObject(text).optJSONArray("data") ?: return@withContext emptyList()
            buildList {
                for (index in 0 until data.length()) {
                    val id = data.optJSONObject(index)?.optString("id").orEmpty()
                    if (id.isNotEmpty()) add(id)
                }
            }.sorted()
        } catch (e: Exception) {
            Log.w(Tag, "Model list failed", e)
            emptyList()
        } finally {
            runCatching { connection.disconnect() }
        }
    }

    // ---- request building ---------------------------------------------------

    private sealed interface Content {
        data class Ok(val value: JSONArray) : Content
        data class Problem(val text: String) : Content
    }

    private fun requestBody(model: String, content: JSONArray): String {
        val messages = JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", systemPrompt))
            // "content" may be a plain string or, when a photo is attached, the
            // list of typed parts the OpenAI format uses.
            val single = content.length() == 1 &&
                content.optJSONObject(0)?.optString("type") == "text"
            put(
                JSONObject()
                    .put("role", "user")
                    .put("content", if (single) content.optJSONObject(0)?.optString("text") else content)
            )
        }
        return JSONObject()
            .put("model", model)
            .put("messages", messages)
            .put("stream", true)
            .put("max_completion_tokens", 4096)
            // The reasoning models can think for a long time before answering,
            // which is the opposite of what a calculator wants; low effort keeps
            // the answer quick. Only these models accept the field.
            .apply { if (model.startsWith("openai/gpt-oss")) put("reasoning_effort", "low") }
            .toString()
    }

    private fun buildContent(question: String, attachment: Attachment?, model: String): Content {
        val parts = JSONArray()
        val asked = question.trim()
        var text = if (asked.isEmpty()) "" else "The user asks: $asked"

        if (attachment != null) {
            when {
                attachment.isImage -> {
                    val prepared = ImagePrep.prepare(attachment.bytes)
                    if (prepared == null) {
                        return Content.Problem("That image could not be read. Try another one.")
                    }
                    if (text.isEmpty()) text = "Solve the question in this photo."
                    parts.put(JSONObject().put("type", "text").put("text", text))
                    parts.put(
                        JSONObject()
                            .put("type", "image_url")
                            .put(
                                "image_url",
                                JSONObject().put(
                                    "url",
                                    "data:${prepared.mimeType};base64,${prepared.base64}"
                                )
                            )
                    )
                    return Content.Ok(parts)
                }

                attachment.isText -> {
                    val body = attachment.bytes.toString(Charsets.UTF_8).take(MaxTextChars)
                    val header = if (text.isEmpty()) "The user sent this file" else text
                    return Content.Ok(
                        JSONArray().put(
                            JSONObject()
                                .put("type", "text")
                                .put("text", "$header:\n\n$body")
                        )
                    )
                }

                attachment.isPdf -> return Content.Problem(
                    "PDFs cannot be read by this model. Take a photo or a screenshot of the " +
                        "page instead — those work."
                )

                else -> return Content.Problem(
                    "That kind of file cannot be sent. Photos, screenshots and text files work."
                )
            }
        }

        return Content.Ok(
            JSONArray().put(JSONObject().put("type", "text").put("text", text))
        )
    }

    // ---- streaming ----------------------------------------------------------

    /**
     * Reads the event stream: lines of the form `data: {…}`, one JSON object per
     * fragment, ending with `data: [DONE]`. Everything the model says lives in
     * `choices[0].delta.content`.
     */
    private fun readStream(
        reader: BufferedReader,
        onDelta: (String) -> Unit,
        stopSignal: () -> Boolean,
        model: String
    ): Answer {
        val answer = StringBuilder()
        var sawAny = false
        var failure = ""

        while (true) {
            if (stopSignal()) break
            val line = reader.readLine() ?: break
            if (!line.startsWith("data:")) continue
            val payload = line.removePrefix("data:").trim()
            if (payload.isEmpty()) continue
            if (payload == "[DONE]") break

            val json = runCatching { JSONObject(payload) }.getOrNull() ?: continue
            // Some providers report a problem inside the stream rather than in
            // the HTTP status.
            json.optJSONObject("error")?.let { error ->
                failure = error.optString("message").ifEmpty { "The model stopped the answer." }
                break
            }
            val delta = json.optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("delta")
                ?.optString("content")
                .orEmpty()
            if (delta.isNotEmpty()) {
                sawAny = true
                answer.append(delta)
                onDelta(delta)
            }
        }

        val text = answer.toString().trim()
        return when {
            text.isNotEmpty() -> Answer(ok = true, text = text, model = model)
            // Stopped before anything arrived: not an error, just quiet.
            stopSignal() && !sawAny -> Answer(ok = true, text = "", model = model)
            failure.isNotEmpty() -> Answer(ok = false, error = failure, model = model)
            else -> Answer(
                ok = false,
                error = "The model sent an empty answer. Try again.",
                model = model
            )
        }
    }

    // ---- errors -------------------------------------------------------------

    private fun describeError(code: Int, body: String): String {
        val message = runCatching {
            val json = JSONObject(body)
            json.optJSONObject("error")?.optString("message")
                ?: json.optString("message")
                ?: json.optString("detail")
        }.getOrNull().orEmpty()

        val lower = message.lowercase()
        return when {
            code == 401 || code == 403 -> "That API key was refused. Check it in the key settings."
            code == 404 -> "That model is not available to this key — press Load models and pick one."
            code == 413 -> "That upload is too large. Try a smaller photo or a screenshot."
            code == 429 -> "The free limit for now is reached. Wait a minute and try again."
            code == 400 && lower.contains("image") ->
                "This model cannot read images. Pick one that can (the photo will use it)."
            code == 400 -> message.ifEmpty { "The model rejected that request." }
            code in 500..599 -> "The service is busy. Try again in a moment."
            else -> message.ifEmpty { "The request failed (HTTP $code)." }
        }
    }
}
