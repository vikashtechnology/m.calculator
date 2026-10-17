package com.advanceai.calculator.data

import android.content.Context
import com.advanceai.calculator.BuildConfig
import org.json.JSONArray
import org.json.JSONObject

/**
 * The About page.
 *
 * Everything is bundled in the app now — no network, no admin dashboard. The
 * GitHub profile link and the donate details live here directly.
 */
data class AboutPage(
    val title: String,
    val tagline: String,
    val body: List<String>,
    val links: List<Link>,
    val contact: String,
    /** The donate button; switched on and off from the dashboard. */
    val donate: Donate = Donate(),
    val updatedAt: Long = 0L,
    /** True when this came from the network rather than the bundled copy. */
    val live: Boolean = false
) {
    data class Link(val label: String, val url: String)

    /**
     * Where a voluntary contribution goes. [upi] is the address a UPI app pays
     * to; the app turns it into a payment link and a QR code, so nothing is
     * hard-wired and the admin can change or disable it without a new APK.
     */
    data class Donate(
        val enabled: Boolean = true,
        val upi: String = "vikashraoa2343-1@oksbi",
        val name: String = "AI Calculator"
    ) {
        /** `upi://pay?...` — what a UPI app understands, and what the QR holds. */
        fun paymentLink(): String {
            val payee = java.net.URLEncoder.encode(name.ifBlank { "AI Calculator" }, "UTF-8")
            return "upi://pay?pa=$upi&pn=$payee&cu=INR"
        }
    }

    companion object {
        /**
         * Shown when the Worker has never been reached. Kept in step with the
         * dashboard's default text.
         */
        val Fallback = AboutPage(
            title = "AI Calculator",
            tagline = "A calculator that does the maths a phone calculator usually cannot.",
            // The same words the Worker serves by default: what the app does,
            // and what stays private. Nothing here is written for a developer.
            body = listOf(
                "Graph, solve equations, work with matrices, convert units and run " +
                    "statistics — plus everyday calculators for GST, tax, EMI, BMI and " +
                    "dates, all in one place.",
                "Every answer is worked out on your phone, so the calculator keeps " +
                    "working with no connection, and nothing about a calculation leaves " +
                    "the device.",
                "Ask AI is there when you want something explained step by step, using " +
                    "your own free key. There are no accounts and no sign-in."
            ),
            links = listOf(Link("GitHub profile", "https://github.com/vikkiRoy899")),
            contact = "",
            // The same default the Worker carries, so the button works before
            // the Worker is redeployed and with no connection at all.
            donate = Donate(
                enabled = true,
                upi = "vikashraoa2343-1@oksbi",
                name = "AI Calculator"
            )
        )
    }
}

/** The About page is fully local now — no Worker, no network. */
object About {
    fun cached(context: Context): AboutPage = AboutPage.Fallback
}
