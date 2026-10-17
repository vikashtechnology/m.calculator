package com.advanceai.calculator.data

import android.content.Context
import com.advanceai.calculator.ai.AiProvider

/**
 * The AI service the user chose, and the key they pasted for it.
 *
 * Ask AI runs on the user's own key, so nobody signs up for anything and no
 * account is involved. The key is kept in the app's private SharedPreferences
 * file — the same place as the settings — can be replaced or removed at any
 * time from the Ask AI screen, and is never uploaded anywhere except as the
 * `Authorization` header of the question itself.
 */
object UserKeys {

    private const val PrefsName = "m_calculator_keys"
    private const val KeyProvider = "ai_provider"
    private const val KeyApiKey = "ai_api_key"
    private const val KeyModel = "ai_model"
    private const val KeyBaseUrl = "ai_base_url"

    /** Keys written by the Gemini version of the app; deleted on first launch. */
    private const val LegacyGeminiKey = "gemini_api_key"
    private const val LegacyGeminiModel = "gemini_model"

    fun provider(context: Context): AiProvider =
        AiProvider.from(prefs(context).getString(KeyProvider, null))

    fun setProvider(context: Context, provider: AiProvider) {
        prefs(context).edit().putString(KeyProvider, provider.name).apply()
    }

    fun apiKey(context: Context): String =
        prefs(context).getString(KeyApiKey, "").orEmpty()

    fun setApiKey(context: Context, key: String) {
        prefs(context).edit().putString(KeyApiKey, key.trim()).apply()
    }

    fun clearApiKey(context: Context) {
        prefs(context).edit().remove(KeyApiKey).apply()
    }

    /** The model the user picked, or "" for the provider's default. */
    fun model(context: Context): String =
        prefs(context).getString(KeyModel, "").orEmpty()

    fun setModel(context: Context, model: String) {
        prefs(context).edit().putString(KeyModel, model.trim()).apply()
    }

    /** Only used by [AiProvider.CUSTOM]: "https://my-server/v1". */
    fun baseUrl(context: Context): String =
        prefs(context).getString(KeyBaseUrl, "").orEmpty()

    fun setBaseUrl(context: Context, url: String) {
        prefs(context).edit().putString(KeyBaseUrl, url.trim()).apply()
    }

    /**
     * Drops the keys the Gemini version stored. They can never work with the
     * providers the app uses now, and leaving a dead key behind would show a
     * misleading "Saved: AIza…4f2c" on the settings card.
     */
    fun forgetLegacyKeys(context: Context) {
        val prefs = prefs(context)
        if (!prefs.contains(LegacyGeminiKey) && !prefs.contains(LegacyGeminiModel)) return
        prefs.edit()
            .remove(LegacyGeminiKey)
            .remove(LegacyGeminiModel)
            .apply()
    }

    /** "gsk_…4f2c" — enough to recognise a key without printing all of it. */
    fun describe(key: String): String = when {
        key.isBlank() -> "none"
        key.length <= 10 -> "saved"
        else -> key.take(6) + "…" + key.takeLast(4)
    }

    /**
     * A loose sanity check, not a format test: providers change key shapes
     * without warning, so the only things worth rejecting are a blank value and
     * something that is obviously not a key (spaces, or far too short).
     */
    fun looksValid(key: String): Boolean {
        val trimmed = key.trim()
        return trimmed.length >= 16 && trimmed.none { it.isWhitespace() }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)
}
