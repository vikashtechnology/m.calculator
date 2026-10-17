package com.advanceai.calculator.data

import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.common.HybridBinarizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DonationTest {
    @Test
    fun paymentUriContainsTheBundledPayeeAndCurrency() {
        assertEquals("vikashraoa2343-1@oksbi", Donation.UPI_ID)
        assertEquals("AI Calculator", Donation.PAYEE_NAME)
        assertEquals(
            "upi://pay?pa=vikashraoa2343-1%40oksbi&pn=AI%20Calculator&cu=INR",
            Donation.PAYMENT_URI
        )
    }

    @Test
    fun qrDecodesToTheSameUriAfterCenterLogoClearance() {
        val matrix = Donation.qrMatrix()
        val pixels = IntArray(matrix.width * matrix.height) { index ->
            val x = index % matrix.width
            val y = index / matrix.width
            if (matrix.get(x, y)) 0xff000000.toInt() else 0xffffffff.toInt()
        }
        // The matrix reserves a white panel; simulate the centered opaque app mark
        // in the same inset area used by the renderer, using a high-contrast glyph.
        val (left, top, right, bottom) = Donation.logoBounds(matrix.width, matrix.height)
        val insetX = ((right - left) * 0.16f).toInt()
        val insetY = ((bottom - top) * 0.16f).toInt()
        for (y in top + insetY until bottom - insetY) {
            for (x in left + insetX until right - insetX) {
                if ((x + y) % 5 < 2 || kotlin.math.abs(x - y) < 2) {
                    pixels[y * matrix.width + x] = 0xff202050.toInt()
                }
            }
        }
        val source = RGBLuminanceSource(matrix.width, matrix.height, pixels)
        val result = QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source)))
        assertEquals(Donation.PAYMENT_URI, result.text)

        val bounds = Donation.logoBounds(matrix.width, matrix.height)
        assertTrue("center logo area is non-empty", bounds[2] > bounds[0] && bounds[3] > bounds[1])
        assertTrue(
            "outer border remains in the quiet zone",
            (0 until 4).all { x -> (0 until matrix.height).none { y -> matrix.get(x, y) } }
        )
    }
}
