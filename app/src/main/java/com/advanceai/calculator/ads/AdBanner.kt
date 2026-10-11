package com.advanceai.calculator.ads

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.libraries.ads.mobile.sdk.banner.AdSize
import com.google.android.libraries.ads.mobile.sdk.banner.AdView
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAd
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRequest
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback

/**
 * The AdMob banner: one anchored adaptive ad across the bottom of the app.
 *
 * It draws nothing until [Ads.canRequestAds] turns true (SDK up *and* consent
 * given) and it collapses back to nothing when no ad arrives, so the layout is
 * never left with a hole — offline, the app is the same calculator it always
 * was, minus the ad.
 */
@Composable
fun AdBanner(modifier: Modifier = Modifier) {
    if (!Ads.canRequestAds) return

    val context = LocalContext.current
    val activity = remember(context) { context.activityOrNull() } ?: return
    // One request per AdView; the SDK refreshes the ad itself afterwards when
    // the unit is set up to refresh.
    val slot = remember { OneShot() }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { viewContext -> AdView(viewContext) },
            update = { view ->
                if (slot.claim()) {
                    view.loadAd(bannerRequest(context, activity), EmptyAdLoadCallback)
                }
                // A banner can come back blank without this nudge.
                view.requestLayout()
            },
            onRelease = { view -> view.destroy() }
        )
    }
}

/**
 * A large anchored adaptive banner, sized to the screen it is on. 320dp is the
 * narrowest width the API accepts, so split-screen windows cannot trip it.
 */
private fun bannerRequest(context: Context, activity: Activity): BannerAdRequest {
    val width = context.resources.configuration.screenWidthDp.coerceAtLeast(320)
    val size = AdSize.getLargeAnchoredAdaptiveBannerAdSize(activity, width)
    return BannerAdRequest.Builder(Ads.bannerUnit, size).build()
}

/**
 * Nothing to do on either outcome: a loaded ad draws itself, and a failed one
 * simply leaves the slot empty. Neither is worth a user-visible message.
 */
private object EmptyAdLoadCallback : AdLoadCallback<BannerAd>

/** Hands out a single "yes" so the ad is requested exactly once per AdView. */
private class OneShot {
    private var used = false

    fun claim(): Boolean {
        if (used) return false
        used = true
        return true
    }
}
