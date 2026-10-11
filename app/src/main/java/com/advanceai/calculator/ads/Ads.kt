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

    /** True while this build still ships Google's test ads instead of real ones. */
    val usingTestIds: Boolean get() = appId == TEST_APP_ID

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

    /** Called from the activity: initialises the SDK once, then checks consent. */
    fun start(activity: Activity) {
        if (!started) {
            started = true
            if (usingTestIds) {
                Log.w(
                    TAG,
                    "AdMob is running on Google's test units — set admobAppId and " +
                        "admobBannerUnitId before publishing."
                )
            }
            initScope.launch {
                // Initialising on the main thread can block it long enough to
                // trip an ANR, so it runs on a background thread.
                withContext(Dispatchers.IO) {
                    MobileAds.initialize(
                        activity.applicationContext,
                        InitializationConfig.Builder(appId).build()
                    ) { }
                }
                sdkReady = true
                publish()
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
        information.requestConsentInfoUpdate(
            activity,
            ConsentRequestParameters.Builder().build(),
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                    consentSettled = true
                    privacyOptionsRequired =
                        information.privacyOptionsRequirementStatus ==
                        ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
                    publish()
                }
            },
            {
                // Google could not be reached. Keep whatever the previous
                // session decided — the same fallback the SDK docs describe.
                consentSettled = true
                publish()
            }
        )
    }

    /** Re-opens the privacy form for someone who wants to change their answer. */
    fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { }
    }

    private fun publish() {
        canRequestAds =
            sdkReady && consentSettled && consentInformation?.canRequestAds() == true
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
