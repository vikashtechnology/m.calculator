package com.advanceai.calculator.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.advanceai.calculator.BuildConfig
import com.advanceai.calculator.R
import com.advanceai.calculator.data.AboutPage
import com.advanceai.calculator.data.Donation
import com.google.zxing.common.BitMatrix

@Composable
fun AboutScreen(
    showMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val qrBitmap = remember(context) { renderDonationQr(context, Donation.qrMatrix()) }

    fun open(url: String, message: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            showMessage(message)
        } catch (_: SecurityException) {
            showMessage(message)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(6.dp))
        Image(
            painter = painterResource(R.drawable.app_icon),
            contentDescription = "AI Calculator app logo",
            modifier = Modifier.size(96.dp)
        )
        Text(
            text = AboutPage.Local.title,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 10.dp)
        )
        Text(
            text = "version ${BuildConfig.VERSION_NAME} · ${BuildConfig.APPLICATION_ID}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Text(
            text = AboutPage.Local.tagline,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 10.dp)
        )
        AboutPage.Local.body.forEach { paragraph ->
            Text(
                text = paragraph,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            )
        }

        InfoCard("Links") {
            AboutPage.Local.links.forEach { link ->
                ActionRow(link.label, link.url) {
                    open(link.url, "No app on this phone can open the link")
                }
            }
        }

        InfoCard("Support the app") {
            Text(
                "Scan the QR code with a UPI app, or open a UPI app directly. The QR is generated on this device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            Surface(
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 8.dp)
            ) {
                Image(
                    bitmap = qrBitmap.asImageBitmap(),
                    contentDescription = "Offline UPI donation QR code for AI Calculator",
                    modifier = Modifier.padding(10.dp).size(220.dp)
                )
            }
            Text(
                "Payee: ${Donation.PAYEE_NAME}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
            )
            Text(
                "UPI ID: ${Donation.UPI_ID}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        open(Donation.PAYMENT_URI, "No UPI app found. Install a UPI app or scan the QR code.")
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.OpenInNew, contentDescription = null)
                    Text("Open a UPI app", modifier = Modifier.padding(start = 6.dp))
                }
                OutlinedButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(Donation.UPI_ID))
                        showMessage("UPI ID copied")
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null)
                    Text("Copy UPI ID", modifier = Modifier.padding(start = 6.dp))
                }
            }
        }

        InfoCard("Privacy") {
            Text(
                "About text, donation information and preferences are bundled or stored on this device. They are not loaded from a server and no usage telemetry is sent. Calculator maths works offline. Ask AI sends the question and any attached content to the AI provider you selected when you use it; advertising may contact Google when ads are enabled. You can use the calculator without signing in.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun InfoCard(title: String, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
    ) {
        Column(modifier = Modifier.padding(vertical = 10.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 14.dp, bottom = 4.dp)
            )
            content()
        }
    }
}

@Composable
private fun ActionRow(label: String, detail: String, onClick: () -> Unit) {
    androidx.compose.material3.TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(label, color = MaterialTheme.colorScheme.onSurface)
                Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Filled.OpenInNew, contentDescription = null)
        }
    }
}

/** Rasterize the QR on-device and place the bundled app mark in its reserved white center. */
private fun renderDonationQr(context: Context, matrix: BitMatrix): Bitmap {
    val width = matrix.width
    val height = matrix.height
    val pixels = IntArray(width * height) { AndroidColor.WHITE }
    for (y in 0 until height) {
        for (x in 0 until width) if (matrix.get(x, y)) pixels[y * width + x] = AndroidColor.BLACK
    }
    val bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    val (left, top, right, bottom) = Donation.logoBounds(width, height)
    val logo = BitmapFactory.decodeResource(context.resources, R.drawable.app_icon)
    if (logo != null) {
        val canvas = Canvas(bitmap)
        val inset = ((right - left) * 0.16f).toInt()
        val target = android.graphics.Rect(left + inset, top + inset, right - inset, bottom - inset)
        canvas.drawBitmap(logo, null, target, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        logo.recycle()
    }
    return bitmap
}
