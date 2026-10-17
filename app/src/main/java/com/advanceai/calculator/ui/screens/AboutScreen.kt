package com.advanceai.calculator.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.advanceai.calculator.BuildConfig
import com.advanceai.calculator.R
import com.advanceai.calculator.data.About
import com.advanceai.calculator.data.AboutPage
import com.advanceai.calculator.ui.theme.qrInk
import com.advanceai.calculator.ui.theme.qrPlate
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.launch

/**
 * The About page: the app icon and version, then the text the admin dashboard
 * holds (see `About`), then the links. It scrolls, because the text is whatever
 * the dashboard was given — it can be as long as it likes.
 */
@Composable
fun AboutScreen(
    showMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var page by remember { mutableStateOf(About.cached(context)) }


    fun open(url: String) {
        val opened = runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.isSuccess
        if (!opened) showMessage("No app on this phone can open $url")
    }


    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(6.dp))
        // Not `R.mipmap.ic_launcher`: from API 26 that id resolves to the
        // adaptive-icon XML in `mipmap-anydpi-v26`, and Compose's
        // `painterResource` refuses anything that is not a vector or a raster
        // asset — it throws "Only VectorDrawables and rasterized asset types are
        // supported" and the app closes. `drawable-nodpi/app_icon.png` is the
        // same artwork as a plain bitmap, so it always loads.
        Image(
            painter = painterResource(R.drawable.app_icon),
            contentDescription = null,
            modifier = Modifier.size(96.dp)
        )
        Text(
            text = page.title,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 10.dp)
        )
        Text(
            text = "Version ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 2.dp)
        )
        Text(
            text = page.tagline,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 10.dp)
        )

        page.body.forEach { paragraph ->
            Text(
                text = paragraph,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (page.links.isNotEmpty()) {
            SectionCard("Links") {
                page.links.forEach { link ->
                    LinkRow(label = link.label, onClick = { open(link.url) })
                }
            }
        }

        // The donate card, when the dashboard has it switched on. The QR is
        // drawn from the UPI id in the page itself, so changing the id in the
        // dashboard changes the code as well — there is no picture to replace.
        if (page.donate.enabled) {
            DonateCard(
                donate = page.donate,
                onPay = {
                    val opened = runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(page.donate.paymentLink()))
                        )
                    }.isSuccess
                    if (!opened) {
                        showMessage(
                            "No UPI app answered. The id is ${page.donate.upi} — " +
                                "pay it from your bank's app instead."
                        )
                    }
                },
                onCopy = {
                    val clipboard =
                        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("UPI id", page.donate.upi))
                    showMessage("UPI id copied — ${page.donate.upi}")
                }
            )
        }

        SectionCard("The app") {
            LinkRow(
                label = "Rate or review on Google Play",
                detail = "Reviews are what let other people find it",
                onClick = { open("market://details?id=${BuildConfig.APPLICATION_ID}") }
            )
            if (page.contact.isNotBlank()) {
                LinkRow(
                    label = "Contact",
                    detail = page.contact,
                    onClick = { open("mailto:${page.contact}") }
                )
            }
        }

    }
}

/**
 * The donate card: a QR code the payer scans, the UPI id in plain text, and a
 * button that hands the payment link to whichever UPI app is installed.
 *
 * The code is deliberately black on white whatever the theme, because a camera
 * has to read it — a dark-mode QR on a dark surface is a coin toss.
 */
@Composable
private fun DonateCard(
    donate: AboutPage.Donate,
    onPay: () -> Unit,
    onCopy: () -> Unit
) {
    val ink = qrInk.toArgb()
    val plate = qrPlate.toArgb()
    val context = LocalContext.current
    val logo = remember { runCatching { BitmapFactory.decodeResource(context.resources, R.drawable.app_icon) }.getOrNull() }
    val qr = remember(donate.upi, donate.name, ink, plate, logo) {
        qrBitmap(donate.paymentLink(), ink, plate, logo)
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 2.dp,
        shadowElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Support the app",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )
            Text(
                text = "It is free, has no accounts, and one small ad keeps it going. " +
                    "If it has been useful, a contribution is welcome — entirely optional.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )
            if (qr != null) {
                // White plate behind the code even in dark mode: scanners need
                // the contrast, and a rounded white card reads as deliberate.
                Surface(
                    color = qrPlate,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.size(180.dp)
                ) {
                    Image(
                        bitmap = qr,
                        contentDescription = "UPI payment QR code for ${donate.upi}",
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
            Text(
                text = donate.upi,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.padding(top = 12.dp)
            )
            Text(
                text = "Scan with any UPI app, or pay this id",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(onClick = onPay) { Text("Open a UPI app") }
                TextButton(onClick = onCopy) { Text("Copy id") }
            }
        }
    }
}

/** Encodes [text] as a QR code, or null if the encoder refuses it. */
private fun qrBitmap(text: String, ink: Int, plate: Int, logo: Bitmap?): ImageBitmap? = runCatching {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 512, 512)
    val width = matrix.width
    val height = matrix.height
    val pixels = IntArray(width * height)
    for (y in 0 until height) {
        val row = y * width
        for (x in 0 until width) {
            // QR codes are black-on-white by specification; theming them would
            // break the camera's ability to find the three corner squares.
            pixels[row + x] = if (matrix[x, y]) ink else plate
        }
    }
    val qr = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    // Overlay the app logo in the centre. The centre is left clear by the QR
    // spec's error correction, so a small logo there does not break scanning.
    if (logo != null) {
        val canvas = Canvas(qr)
        val logoSize = width / 5
        val left = (width - logoSize) / 2
        val top = (height - logoSize) / 2
        val src = Rect(0, 0, logo.width, logo.height)
        val dst = Rect(left, top, left + logoSize, top + logoSize)
        val backing = Paint().apply { color = plate; style = Paint.Style.FILL }
        canvas.drawRect((left - 4).toFloat(), (top - 4).toFloat(),
            (left + logoSize + 4).toFloat(), (top + logoSize + 4).toFloat(), backing)
        canvas.drawBitmap(logo, src, dst, Paint(Paint.ANTI_ALIAS_FLAG))
    }
    qr.asImageBitmap()
}.getOrNull()

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 2.dp,
        shadowElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        Column(modifier = Modifier.padding(vertical = 10.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 14.dp, bottom = 4.dp)
            )
            content()
        }
    }
}

@Composable
private fun LinkRow(label: String, detail: String? = null, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = label, style = MaterialTheme.typography.bodyMedium)
                if (!detail.isNullOrBlank()) {
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
