package com.advanceai.calculator.ads

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.advanceai.calculator.BuildConfig
import com.google.android.libraries.ads.mobile.sdk.MobileAds
import com.google.android.libraries.ads.mobile.sdk.initialization.InitializationConfig
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The AdMob wiring.
 *
 * Two things have to happen before an ad may be requested: the ads SDK has to
 * be initialised, and — in the EEA, the UK and Switzerland — the consent form
 * has to be answered. Both are kicked off from the main activity when the app
 * starts, and the answer lands in [canRequestAds], which the banner waits for.
 * If either step fails (no network, for instance) no ad is shown and the
 * calculator behaves exactly as it did before ads existed.
 */
object Ads {

    /** Google's sample app ID, used when the build sets no `admobAppId`. */
    const val TEST_APP_ID = "ca-app-pub-3940256099942544~3347511713"

    private const val TAG = "Ads"

    private val appId: String = BuildConfig.ADMOB_APP_ID

    /** Unit the banner loads. Overridden by `-PadmobBannerUnitId=…` at build time. */
    val bannerUnit: String = BuildConfig.ADMOB_BANNER_UNIT_ID

    /**
     * True while this build ships Google's test ads instead of real ones: a
     * build with no IDs of its own, or any debug build (see build.gradle.kts —
     * tapping your own live ads is what gets AdMob accounts suspended).
     */
    val usingTestIds: Boolean get() = appId == TEST_APP_ID || BuildConfig.DEBUG

    /** True when the build is a debug one, for the Settings row. */
    val isDebugBuild: Boolean get() = BuildConfig.DEBUG

    private val initScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var started = false
    private var sdkReady = false
    private var consentSettled = false
    private var consentInformation: ConsentInformation? = null

    /** True once the SDK is up *and* consent allows an ad to be requested. */
    var canRequestAds by mutableStateOf(false)
        private set

    /** True when Google requires an in-app entry point back to the privacy form. */
    var privacyOptionsRequired by mutableStateOf(false)
        private set

    /**
     * Where the banner has got to, in a sentence. Shown in Settings so "are the
     * ads working?" can be answered on the phone instead of in a log, and
     * written to logcat under the `Ads` tag as well.
     */
    var status by mutableStateOf("Ads have not been started yet")
        private set

    /** Which units this build asks for, for the same Settings row. */
    val unitSummary: String
        get() = when {
            !usingTestIds -> "this app's own AdMob units"
            BuildConfig.DEBUG -> "Google test units (debug build — safe to tap)"
            else -> "Google test units (no earnings)"
        }

    /** Called from the activity: initialises the SDK once, then checks consent. */
    fun start(activity: Activity) {
        if (!started) {
            started = true
            Log.i(
                TAG,
                "AdMob starting on ${if (usingTestIds) "Google's test units" else "the app's own units"} " +
                    "(app id $appId, banner $bannerUnit)"
            )
            status = "Starting the ads SDK…"
            initScope.launch {
                // Initialising on the main thread can block it long enough to
                // trip an ANR, so it runs on a background thread.
                val outcome = runCatching {
                    withContext(Dispatchers.IO) {
                        MobileAds.initialize(
                            activity.applicationContext,
                            InitializationConfig.Builder(appId).build()
                        ) {
                            // The SDK says it is up: this is the moment consent
                            // may be trusted and an ad requested.
                            sdkReady = true
                            publish()
                        }
                    }
                }
                if (outcome.isFailure) {
                    // A malformed app ID, or no Play services on the phone.
                    // Silently doing nothing here is what makes "no ads"
                    // impossible to diagnose, so say so.
                    val error = outcome.exceptionOrNull()
                    Log.e(TAG, "The ads SDK could not be initialised", error)
                    status = "The ads SDK could not start: ${error?.message ?: "unknown error"}"
                } else {
                    // The callback above normally sets this; setting it here too
                    // means a callback that never arrives cannot silence the
                    // banner forever.
                    sdkReady = true
                    publish()
                }
            }
        }
        requestConsent(activity)
    }

    /**
     * Asked on every launch: this picks up the answer given last time and lets
     * Google ask again when a decision has expired.
     */
    private fun requestConsent(activity: Activity) {
        val information = UserMessagingPlatform.getConsentInformation(activity)
        consentInformation = information
        status = "Checking what the ad rules require here…"
        information.requestConsentInfoUpdate(
            activity,
            ConsentRequestParameters.Builder().build(),
            {
                // Read the requirement as soon as it is known, so the menu entry
                // is there even if the form step itself fails below.
                privacyOptionsRequired =
                    information.privacyOptionsRequirementStatus ==
                    ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
                status = "Waiting for the consent form…"
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    consentSettled = true
                    Log.i(TAG, "Consent step finished (form error: $formError)")
                    publish()
                }
            },
            { requestError ->
                // Google could not be reached. Keep whatever the previous
                // session decided — the same fallback the SDK docs describe.
                consentSettled = true
                Log.w(TAG, "Consent information could not be updated: $requestError")
                publish()
            }
        )
    }

    /** Re-opens the privacy form for someone who wants to change their answer. */
    fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { }
    }

    private fun publish() {
        val allowed = consentInformation?.canRequestAds() == true
        canRequestAds = sdkReady && consentSettled && allowed
        status = when {
            canRequestAds -> "Asking for an ad…"
            !sdkReady -> "Starting the ads SDK…"
            !consentSettled -> "Waiting for the consent form…"
            else -> "No ad may be requested on this device yet (consent not given)"
        }
        if (canRequestAds) {
            Log.i(TAG, "Consent allows ads; the banner may request one now")
        }
    }

    // ---- what the banner reports back ------------------------------------

    /** The banner filled: it is on screen and earning. */
    internal fun reportLoaded() {
        Log.i(TAG, "Banner loaded and showing")
        status = "Banner is showing"
    }

    /** The banner did not fill. [error] is whatever the SDK explained. */
    internal fun reportFailed(error: Any?) {
        Log.w(TAG, "Banner did not load: $error")
        status = "No ad right now — the ad service answered: $error"
    }

    /** The slot is not on screen: the screen is too short for it. */
    internal fun reportSlotHidden(heightDp: Int) {
        if (canRequestAds) return
        Log.i(TAG, "No banner requested: the screen is only ${heightDp}dp tall")
        status = "No room for a banner on this screen (${heightDp}dp tall; 616dp is needed)"
    }

    /** No activity to hang the consent form on (a preview build, say). */
    internal fun reportNoActivity() {
        status = "Ads cannot run without a window to show the consent form in"
    }
}

/** The activity behind a Compose context, if there is one. */
fun Context.activityOrNull(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
