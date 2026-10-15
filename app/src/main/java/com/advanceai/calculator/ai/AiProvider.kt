package com.advanceai.calculator.ai

/**
 * Where Ask AI sends its questions.
 *
 * Every provider here speaks the same OpenAI-style chat-completions API, so the
 * app has one client and the user picks the service. They all hand out free API
 * keys that need no credit card, and the key is pasted into the app by the user
 * — nothing is bundled in the build, so nothing can leak out of it.
 *
 * Groq is the default because it is the fastest of the three by a wide margin
 * (its hardware runs open models at several hundred tokens a second), which is
 * exactly what makes a chat feel instant.
 */
enum class AiProvider(
    val label: String,
    /** Base URL, without `/chat/completions` on the end. */
    val baseUrl: String,
    /** Where the user gets a free key. */
    val keyUrl: String,
    /** Shown in the key box, to give a hint at what a key looks like. */
    val keyHint: String,
    /** Used until the user picks something else; blank = they must pick. */
    val defaultModel: String,
    /**
     * The model to use when a photo is attached, if [defaultModel] cannot read
     * images. Photos need a multimodal model, and switching silently would be
     * worse than telling the user which model answered.
     */
    val visionModel: String,
    /** Shown before the live list is fetched, in case the fetch fails. */
    val knownModels: List<String>
) {
    GROQ(
        label = "Groq",
        baseUrl = "https://api.groq.com/openai/v1",
        keyUrl = "https://console.groq.com/keys",
        keyHint = "gsk_…",
        defaultModel = "openai/gpt-oss-120b",
        visionModel = "qwen/qwen3.6-27b",
        knownModels = listOf(
            "openai/gpt-oss-120b",
            "openai/gpt-oss-20b",
            "qwen/qwen3.8-27b",
            "qwen/qwen3.6-27b",
            "groq/compound"
        )
    ),

    OPENROUTER(
        label = "OpenRouter",
        baseUrl = "https://openrouter.ai/api/v1",
        keyUrl = "https://openrouter.ai/keys",
        keyHint = "sk-or-…",
        // One key, many models — but which ones are free changes weekly, so the
        // app asks the provider for the list instead of guessing.
        defaultModel = "",
        visionModel = "",
        knownModels = emptyList()
    ),

    CEREBRAS(
        label = "Cerebras",
        baseUrl = "https://api.cerebras.ai/v1",
        keyUrl = "https://cloud.cerebras.ai",
        keyHint = "csk-…",
        defaultModel = "",
        visionModel = "",
        knownModels = emptyList()
    ),

    CUSTOM(
        label = "Other",
        baseUrl = "",
        keyUrl = "",
        keyHint = "the key your service gave you",
        defaultModel = "",
        visionModel = "",
        knownModels = emptyList()
    );

    /** True when the user has to fill in a base URL themselves. */
    val needsBaseUrl: Boolean get() = this == CUSTOM

    companion object {
        fun from(name: String?): AiProvider =
            entries.firstOrNull { it.name == name } ?: GROQ

        /**
         * A friendlier name for the model ids the providers use. Anything not
         * listed is shown as it is, because providers add and retire models
         * faster than an app can be updated.
         */
        fun describeModel(id: String): String = when (id) {
            "openai/gpt-oss-120b" -> "GPT-OSS 120B (default)"
            "openai/gpt-oss-20b" -> "GPT-OSS 20B (fastest)"
            "qwen/qwen3.8-27b" -> "Qwen 3.8 27B"
            "qwen/qwen3.6-27b" -> "Qwen 3.6 27B (reads photos)"
            "groq/compound" -> "Compound (can search)"
            "groq/compound-mini" -> "Compound Mini"
            else -> id
        }
    }
}
