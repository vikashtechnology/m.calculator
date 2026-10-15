package com.advanceai.calculator.data

import android.content.Context
import com.advanceai.calculator.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * The About page.
 *
 * The words come from the stats Worker (`GET /v1/about`), which the admin
 * dashboard can edit — so the text, the links and the contact address can be
 * changed without shipping a new APK. The last copy that arrived is kept on the
 * phone, and [Fallback] is used until the first one does, so the page is never
 * empty and never needs the network to show something.
 */
data class AboutPage(
    val title: String,
    val tagline: String,
    val body: List<String>,
    val links: List<Link>,
    val contact: String,
    val updatedAt: Long = 0L,
    /** True when this came from the network rather than the bundled copy. */
    val live: Boolean = false
) {
    data class Link(val label: String, val url: String)

    companion object {
        /**
         * Shown when the Worker has never been reached. Kept in step with the
         * dashboard's default text.
         */
        val Fallback = AboutPage(
            title = "AI Calculator",
            tagline = "A calculator that does the maths a phone calculator usually cannot.",
            body = listOf(
                "Graphing with pan, zoom and trace, a solver for equations, derivatives, " +
                    "integrals and series, matrices with inverses and determinants, unit " +
                    "conversion across eleven kinds of measurement, statistics, and ten " +
                    "everyday calculators from GST and EMI to BMI and dates.",
                "The maths engine is pure Kotlin and runs entirely on the phone — nothing " +
                    "about a calculation leaves it. Ask AI is the one part that uses the " +
                    "internet, and only with the free API key you paste in yourself.",
                "Built with Kotlin and Jetpack Compose. It has no accounts, no sign-in, " +
                    "and one small banner ad, which is what keeps it free."
            ),
            links = emptyList(),
            contact = ""
        )
    }
}

/** Reads the About page, from the Worker when it can, from the phone otherwise. */
object About {

    private const val Path = "/v1/about"

    /** The Worker's base URL, from the same build property the counters use. */
    private val baseUrl: String = BuildConfig.TELEMETRY_URL

    /**
     * The page to show right now: the cached copy if there is one (instant, and
     * always available offline), otherwise the bundled text.
     */
    fun cached(context: Context): AboutPage {
        val stored = Prefs(context).aboutJson
        if (stored.isBlank()) return AboutPage.Fallback
        return parse(stored)?.copy(live = true) ?: AboutPage.Fallback
    }

    /** Fetches the latest page. Null when there is nothing new or no network. */
    suspend fun refresh(context: Context): AboutPage? = withContext(Dispatchers.IO) {
        if (baseUrl.isBlank()) return@withContext null
        val connection = runCatching {
            (URL(baseUrl.trimEnd('/') + Path).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 15_000
            }
        }.getOrNull() ?: return@withContext null

        try {
            if (connection.responseCode !in 200..299) return@withContext null
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            val parsed = parse(text) ?: return@withContext null
            Prefs(context).aboutJson = text
            parsed.copy(live = true)
        } catch (e: Exception) {
            null
        } finally {
            runCatching { connection.disconnect() }
        }
    }

    /** Lenient on purpose: a field the dashboard left empty falls back to text. */
    private fun parse(json: String): AboutPage? {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return null
        val fallback = AboutPage.Fallback

        val paragraphs = mutableListOf<String>()
        root.optJSONArray("body")?.let { array ->
            for (index in 0 until array.length()) {
                val line = array.optString(index).trim()
                if (line.isNotEmpty()) paragraphs.add(line)
            }
        }

        val links = mutableListOf<AboutPage.Link>()
        root.optJSONArray("links")?.let { array ->
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val label = item.optString("label").trim()
                val url = item.optString("url").trim()
                if (label.isNotEmpty() && url.startsWith("http")) {
                    links.add(AboutPage.Link(label, url))
                }
            }
        }

        val title = root.optString("title").trim().ifEmpty { fallback.title }
        val tagline = root.optString("tagline").trim().ifEmpty { fallback.tagline }
        return AboutPage(
            title = title,
            tagline = tagline,
            body = paragraphs.ifEmpty { fallback.body },
            links = links,
            contact = root.optString("contact").trim(),
            updatedAt = root.optLong("updated", 0L)
        )
    }

    /** The JSON the dashboard saves, for the tests to compare shapes against. */
    fun encode(page: AboutPage): JSONObject = JSONObject()
        .put("title", page.title)
        .put("tagline", page.tagline)
        .put("body", JSONArray(page.body))
        .put("links", JSONArray().apply {
            page.links.forEach { link ->
                put(JSONObject().put("label", link.label).put("url", link.url))
            }
        })
        .put("contact", page.contact)
        .put("updated", page.updatedAt)
}
