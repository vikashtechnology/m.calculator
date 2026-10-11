package com.advanceai.calculator.ads

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.libraries.ads.mobile.sdk.banner.AdSize
import com.google.android.libraries.ads.mobile.sdk.banner.AdView
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAd
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRequest
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback

/**
 * The AdMob banner.
 *
 * It is a fixed 320×50 banner on purpose: the slot around it (see `AppRoot`)
 * is exactly that tall and is reserved from the first frame, so an ad arriving
 * — or never arriving — cannot resize anything else on the screen. A large
 * adaptive banner would be up to 90dp tall and would take that space from the
 * keypad the moment it loaded.
 *
 * Nothing is drawn until [Ads.canRequestAds] turns true, and nothing is left
 * behind when no ad fills: the slot simply stays empty.
 */
@Composable
fun AdBanner(modifier: Modifier = Modifier) {
    if (!Ads.canRequestAds) return

    // One request per AdView; the SDK refreshes the ad itself afterwards when
    // the unit is set up to refresh.
    val slot = remember { OneShot() }

    AndroidView(
        modifier = modifier,
        factory = { viewContext -> AdView(viewContext) },
        update = { view ->
            if (slot.claim()) {
                val request = BannerAdRequest.Builder(Ads.bannerUnit, AdSize.BANNER).build()
                view.loadAd(request, EmptyAdLoadCallback)
            }
            // A banner can come back blank without this nudge.
            view.requestLayout()
        },
        onRelease = { view -> view.destroy() }
    )
}

/**
 * Nothing to do on either outcome: a loaded ad draws itself, and a failed one
 * leaves the slot empty. Neither is worth a user-visible message.
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
