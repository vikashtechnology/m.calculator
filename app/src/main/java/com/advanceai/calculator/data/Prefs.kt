package com.advanceai.calculator.data

import android.content.Context
import androidx.core.content.edit
import com.advanceai.calculator.engine.AngleMode
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

    var precision: Int
        get() = sp.getInt(KEY_PRECISION, 12)
        set(value) = sp.edit { putInt(KEY_PRECISION, value) }

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
        const val KEY_THEME = "theme_mode"
        const val MAX_HISTORY = 200
    }
}
