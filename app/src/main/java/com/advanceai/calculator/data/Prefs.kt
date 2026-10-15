package com.advanceai.calculator.data

import android.content.Context
import androidx.core.content.edit
import com.advanceai.calculator.engine.AngleMode
import com.advanceai.calculator.engine.DigitGrouping
import com.advanceai.calculator.engine.DisplayOptions
import org.json.JSONArray
import org.json.JSONObject

/** Which palette the app paints, chosen in the drop-down menu. */
enum class AppThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark");

    companion object {
        fun from(value: String?): AppThemeMode =
            entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}

/**
 * What the Settings sheet edits. Held as one value so the UI can show the
 * current choices without reading each preference separately.
 */
data class AppSettings(
    val digits: Int = DisplayOptions.DEFAULT.significantDigits,
    val grouping: DigitGrouping = DigitGrouping.NONE,
    val haptics: Boolean = true,
    val keySound: Boolean = false
)

data class HistoryEntry(
    val expression: String,
    val result: String,
    val timestamp: Long
)

/**
 * Small SharedPreferences-backed store. Deliberately dependency-free: the app
 * has no cloud component, so a key/value file is all the persistence needed.
 */
class Prefs(context: Context) {

    private val sp = context.getSharedPreferences("m_calculator", Context.MODE_PRIVATE)

    var angleMode: AngleMode
        get() = runCatching { AngleMode.valueOf(sp.getString(KEY_ANGLE, AngleMode.DEG.name)!!) }
            .getOrDefault(AngleMode.DEG)
        set(value) = sp.edit { putString(KEY_ANGLE, value.name) }

    var polarMode: Boolean
        get() = sp.getBoolean(KEY_POLAR, false)
        set(value) = sp.edit { putBoolean(KEY_POLAR, value) }

    var memory: Double
        get() = Double.fromBits(sp.getLong(KEY_MEMORY, 0L))
        set(value) = sp.edit { putLong(KEY_MEMORY, value.toBits()) }

    var lastExpression: String
        get() = sp.getString(KEY_LAST_EXPR, "") ?: ""
        set(value) = sp.edit { putString(KEY_LAST_EXPR, value) }

    var lastResult: String
        get() = sp.getString(KEY_LAST_RESULT, "") ?: ""
        set(value) = sp.edit { putString(KEY_LAST_RESULT, value) }

    /** Significant digits shown in results: one of [DisplayOptions.DIGIT_CHOICES]. */
    var precision: Int
        get() = sp.getInt(KEY_PRECISION, DisplayOptions.DEFAULT.significantDigits)
        set(value) = sp.edit { putInt(KEY_PRECISION, value.coerceIn(1, 15)) }

    /**
     * The last About page the Worker sent, as JSON. Kept so the About screen
     * shows real content offline, and so it is instant on opening.
     */
    var aboutJson: String
        get() = sp.getString(KEY_ABOUT, "") ?: ""
        set(value) = sp.edit { putString(KEY_ABOUT, value) }

    /** How digits are separated: none, 1,234,567 or 12,34,567. */
    var grouping: DigitGrouping
        get() = runCatching { DigitGrouping.valueOf(sp.getString(KEY_GROUPING, null)!!) }
            .getOrDefault(DigitGrouping.NONE)
        set(value) = sp.edit { putString(KEY_GROUPING, value.name) }

    /** A short knock on every key press. */
    var haptics: Boolean
        get() = sp.getBoolean(KEY_HAPTICS, true)
        set(value) = sp.edit { putBoolean(KEY_HAPTICS, value) }

    /** The system click sound on every key press. Off unless asked for. */
    var keySound: Boolean
        get() = sp.getBoolean(KEY_KEY_SOUND, false)
        set(value) = sp.edit { putBoolean(KEY_KEY_SOUND, value) }

    /** Everything the Settings sheet edits, read in one go. */
    fun settings(): AppSettings = AppSettings(
        digits = precision,
        grouping = grouping,
        haptics = haptics,
        keySound = keySound
    )

    fun save(settings: AppSettings) {
        precision = settings.digits
        grouping = settings.grouping
        haptics = settings.haptics
        keySound = settings.keySound
    }

    /** System / Light / Dark — see [AppThemeMode]. */
    var themeMode: AppThemeMode
        get() = AppThemeMode.from(sp.getString(KEY_THEME, null))
        set(value) = sp.edit { putString(KEY_THEME, value.name) }

    fun loadVariables(): MutableMap<String, Double> {
        val map = LinkedHashMap<String, Double>()
        val raw = sp.getString(KEY_VARIABLES, null) ?: return map
        runCatching {
            val json = JSONObject(raw)
            json.keys().forEach { key ->
                map[key] = json.getDouble(key)
            }
        }
        return map
    }

    fun saveVariables(values: Map<String, Double>) {
        val json = JSONObject()
        values.forEach { (key, value) -> json.put(key, value) }
        sp.edit { putString(KEY_VARIABLES, json.toString()) }
    }

    fun loadHistory(): MutableList<HistoryEntry> {
        val list = ArrayList<HistoryEntry>()
        val raw = sp.getString(KEY_HISTORY, null) ?: return list
        runCatching {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                list.add(
                    HistoryEntry(
                        expression = item.getString("e"),
                        result = item.getString("r"),
                        timestamp = item.optLong("t", 0L)
                    )
                )
            }
        }
        return list
    }

    fun saveHistory(history: List<HistoryEntry>) {
        val array = JSONArray()
        history.take(MAX_HISTORY).forEach { entry ->
            array.put(
                JSONObject()
                    .put("e", entry.expression)
                    .put("r", entry.result)
                    .put("t", entry.timestamp)
            )
        }
        sp.edit { putString(KEY_HISTORY, array.toString()) }
    }

    fun clearHistory() = sp.edit { remove(KEY_HISTORY) }

    private companion object {
        const val KEY_ANGLE = "angle_mode"
        const val KEY_POLAR = "polar_mode"
        const val KEY_MEMORY = "memory"
        const val KEY_LAST_EXPR = "last_expression"
        const val KEY_LAST_RESULT = "last_result"
        const val KEY_VARIABLES = "variables"
        const val KEY_HISTORY = "history"
        const val KEY_PRECISION = "precision"
        const val KEY_GROUPING = "indian_grouping"
        const val KEY_ABOUT = "about_json"
        const val KEY_HAPTICS = "haptics"
        const val KEY_KEY_SOUND = "key_sound"
        const val KEY_THEME = "theme_mode"
        const val MAX_HISTORY = 200
    }
}
