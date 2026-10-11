package com.advanceai.calculator.data

import android.content.Context

/**
 * Keys the user pastes into the app themselves.
 *
 * Ask AI works on the user's own Gemini key, so nobody has to sign up for
 * anything and nothing is tied to an account. The key is kept in the app's
 * private SharedPreferences file — the same place as the settings — and can be
 * replaced or removed at any time from the Ask AI screen.
 */
object UserKeys {

    private const val PrefsName = "m_calculator_keys"
    private const val KeyGemini = "gemini_api_key"
    private const val KeyModel = "gemini_model"

    fun geminiKey(context: Context): String =
        prefs(context).getString(KeyGemini, "").orEmpty()

    fun setGeminiKey(context: Context, key: String) {
        prefs(context).edit().putString(KeyGemini, key.trim()).apply()
    }

    fun clearGeminiKey(context: Context) {
        prefs(context).edit().remove(KeyGemini).apply()
    }

    /** The model the user picked on the Ask AI card, or "" for the default. */
    fun geminiModel(context: Context): String =
        prefs(context).getString(KeyModel, "").orEmpty()

    fun setGeminiModel(context: Context, model: String) {
        prefs(context).edit().putString(KeyModel, model.trim()).apply()
    }

    /** "AIza…4f2c" — enough to recognise a key without printing all of it. */
    fun describe(key: String): String = when {
        key.isBlank() -> "none"
        key.length <= 10 -> "saved"
        else -> key.take(6) + "…" + key.takeLast(4)
    }

    /** Google's keys start with AIza and are about 39 characters. */
    fun looksValid(key: String): Boolean {
        val trimmed = key.trim()
        return trimmed.startsWith("AIza") && trimmed.length in 30..60
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)
}
