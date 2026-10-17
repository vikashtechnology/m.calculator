package com.advanceai.calculator.data

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.common.BitMatrix

/** Local donation details and the one canonical URI used for both QR and UPI intents. */
object Donation {
    const val UPI_ID = "vikashraoa2343-1@oksbi"
    const val PAYEE_NAME = "AI Calculator"
    const val PAYMENT_URI = "upi://pay?pa=vikashraoa2343-1%40oksbi&pn=AI%20Calculator&cu=INR"

    /**
     * Create the QR entirely on-device. Four modules of quiet zone are retained;
     * H correction plus a modest white logo panel leaves it readable when branded.
     */
    fun qrMatrix(): BitMatrix {
        val matrix = QRCodeWriter().encode(
            PAYMENT_URI,
            BarcodeFormat.QR_CODE,
            512,
            512,
            mapOf(
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H,
                EncodeHintType.MARGIN to 4,
                EncodeHintType.CHARACTER_SET to "UTF-8"
            )
        )
        val (left, top, right, bottom) = logoBounds(matrix.width, matrix.height)
        for (y in top until bottom) {
            for (x in left until right) matrix.unset(x, y)
        }
        return matrix
    }

    /** White square reserved for the centered app mark; also used by the renderer. */
    fun logoBounds(width: Int, height: Int): IntArray {
        val side = (minOf(width, height) * 0.22f).toInt().coerceAtLeast(1)
        val left = (width - side) / 2
        val top = (height - side) / 2
        return intArrayOf(left, top, left + side, top + side)
    }
}
