package com.vikashtechnology.mcalculator.data

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.content.edit
import com.vikashtechnology.mcalculator.BuildConfig
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * Anonymous, aggregate-only usage counts for the admin dashboard.
 *
 * Counts how often the app is opened, how many calculations are evaluated and
 * how many questions Ask AI solves. Nothing about the expression, the answer or
 * the uploaded file ever leaves the device, and there is no account: the only
 * identifier is a random install id created on first launch.
 *
 * Sending is off when no endpoint is configured ([BuildConfig.TELEMETRY_URL])
 * and can be switched off at any time from the drop-down menu; the counters are
 * kept locally and posted in small batches, so a failed upload is retried later.
 */
object Telemetry {

    private const val Tag = "Telemetry"
    private const val PrefsName = "m_calculator_stats"
    private const val KeyInstallId = "install_id"
    private const val KeyEnabled = "enabled"
    private const val KeyPending = "pending"
    private const val KeyLastFlush = "last_flush"
    private const val FlushIntervalMs = 30_000L

    const val EVENT_APP_OPEN = "app_open"
    const val EVENT_CALC = "calc"
    const val EVENT_AI = "ai"

    private val knownEvents = listOf(EVENT_APP_OPEN, EVENT_CALC, EVENT_AI)

    private var appContext: Context? = null
    private val lock = Any()

    val endpoint: String = BuildConfig.TELEMETRY_URL

    val isSupported: Boolean
        get() = endpoint.isNotBlank()

    fun install(context: Context) {
        val app = context.applicationContext
        synchronized(lock) {
            if (appContext == null) {
                appContext = app
                ensureInstallId()
            }
        }
    }

    /** True when the user allows anonymous counts (default: allowed). */
    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KeyEnabled, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean(KeyEnabled, enabled) }
        if (!enabled) {
            // Forget what was pending so nothing is sent after opting out.
            prefs(context).edit { putString(KeyPending, null) }
        }
    }

    /** Adds one to a counter. Cheap: the upload happens on another thread. */
    fun record(context: Context, event: String) {
        if (!isSupported || !knownEvents.contains(event)) return
        val app = context.applicationContext
        install(app)
        if (!isEnabled(app)) return
        synchronized(lock) {
            val sp = prefs(app)
            val pending = readCounts(sp.getString(KeyPending, null))
            pending[event] = (pending[event] ?: 0) + 1
            sp.edit { putString(KeyPending, writeCounts(pending)) }
            val now = System.currentTimeMillis()
            if (now - sp.getLong(KeyLastFlush, 0L) < FlushIntervalMs) return
            sp.edit { putLong(KeyLastFlush, now) }
        }
        flushAsync(app)
    }

    /** Sends whatever is pending, if anything. Safe to call from anywhere. */
    fun flush(context: Context) {
        if (!isSupported) return
        val app = context.applicationContext
        install(app)
        if (!isEnabled(app)) return
        flushAsync(app)
    }

    private fun flushAsync(context: Context) {
        Thread {
            runCatching { sendPending(context) }
                .onFailure { Log.d(Tag, "stats not sent yet: ${it.message}") }
        }.apply { isDaemon = true }.start()
    }

    private fun sendPending(context: Context) {
        val sp = prefs(context)
        val pending = synchronized(lock) { readCounts(sp.getString(KeyPending, null)) }
        if (pending.isEmpty()) return

        val payload = JSONObject().apply {
            put("id", installId(context))
            put("day", today())
            put("v", BuildConfig.VERSION_NAME)
            put("sdk", Build.VERSION.SDK_INT)
            put("events", JSONObject(pending.toMap()))
        }
        val body = payload.toString().toByteArray(Charsets.UTF_8)

        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 8_000
            readTimeout = 8_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Content-Length", body.size.toString())
        }
        try {
            connection.outputStream.use { it.write(body) }
            val code = connection.responseCode
            if (code in 200..299) {
                // Only the events that were actually sent are cleared.
                synchronized(lock) {
                    val latest = readCounts(sp.getString(KeyPending, null))
                    pending.forEach { (event, count) ->
                        val left = (latest[event] ?: 0) - count
                        if (left > 0) latest[event] = left else latest.remove(event)
                    }
                    sp.edit { putString(KeyPending, writeCounts(latest)) }
                }
            } else {
                Log.d(Tag, "stats endpoint answered $code")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun installId(context: Context): String {
        val sp = prefs(context)
        val existing = sp.getString(KeyInstallId, null)
        if (!existing.isNullOrBlank()) return existing
        val fresh = UUID.randomUUID().toString().replace("-", "").take(24)
        sp.edit { putString(KeyInstallId, fresh) }
        return fresh
    }

    private fun ensureInstallId() {
        val context = appContext ?: return
        installId(context)
    }

    private fun today(): String = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        .format(java.util.Date())

    private fun prefs(context: Context) =
        context.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)

    private fun readCounts(raw: String?): MutableMap<String, Int> {
        val counts = LinkedHashMap<String, Int>()
        if (raw.isNullOrBlank()) return counts
        runCatching {
            val json = JSONObject(raw)
            json.keys().forEach { key ->
                val value = json.optInt(key, 0)
                if (value > 0 && knownEvents.contains(key)) counts[key] = value
            }
        }
        return counts
    }

    private fun writeCounts(counts: Map<String, Int>): String {
        val json = JSONObject()
        counts.forEach { (event, count) -> if (count > 0) json.put(event, count) }
        return json.toString()
    }
}
