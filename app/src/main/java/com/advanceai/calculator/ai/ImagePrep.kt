package com.advanceai.calculator.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream

/**
 * Shrinks a photo before it is sent to the model.
 *
 * Phone cameras produce files far bigger than the providers accept for an
 * inline image (their base64 limit is a few megabytes), so the long side is
 * capped, then the JPEG quality and the size step down until the result fits.
 * A page of a textbook photographed at 4000px still reads perfectly at 1600px.
 */
object ImagePrep {

    private const val MaxSide = 1600
    private const val LimitBytes = 3_500_000

    data class Prepared(val base64: String, val mimeType: String)

    /** Null when the bytes are not an image the phone can decode. */
    fun prepare(bytes: ByteArray): Prepared? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        // Halve on the way in: decoding a 12-megapixel photo at full size just
        // to shrink it afterwards is what runs a cheap phone out of memory.
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 800) {
            sample *= 2
        }
        val decoded = BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample }
        ) ?: return null

        var current: Bitmap = decoded
        try {
            var quality = 85
            var side = MaxSide
            repeat(4) {
                val scaled = scaleToFit(current, side)
                if (scaled !== current) current.recycle()
                current = scaled

                val out = ByteArrayOutputStream()
                current.compress(Bitmap.CompressFormat.JPEG, quality, out)
                val data = out.toByteArray()
                if (data.size <= LimitBytes) {
                    return Prepared(Base64.encodeToString(data, Base64.NO_WRAP), "image/jpeg")
                }
                quality -= 15
                side = side * 3 / 4
            }
            return null
        } finally {
            current.recycle()
        }
    }

    private fun scaleToFit(bitmap: Bitmap, maxSide: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxSide) return bitmap
        val ratio = maxSide.toDouble() / longest
        val width = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val height = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }
}
