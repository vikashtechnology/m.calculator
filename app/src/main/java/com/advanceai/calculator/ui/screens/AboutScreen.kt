package com.advanceai.calculator.ui.screens

import android.content.Intent
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.advanceai.calculator.BuildConfig
import com.advanceai.calculator.R
import com.advanceai.calculator.data.About
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
    val scope = rememberCoroutineScope()

    var page by remember { mutableStateOf(About.cached(context)) }
    var refreshing by remember { mutableStateOf(false) }

    // The privacy page lives on the same Worker that serves the About text, so
    // it is derived from the one URL the build knows.
    val privacyUrl = remember {
        val base = BuildConfig.TELEMETRY_URL.ifBlank { "https://ai-calculator-stats.rajkatrina90.workers.dev" }
        base.trimEnd('/') + "/privacy"
    }

    fun open(url: String) {
        val opened = runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.isSuccess
        if (!opened) showMessage("No app on this phone can open $url")
    }

    LaunchedEffect(Unit) {
        refreshing = true
        About.refresh(context)?.let { page = it }
        refreshing = false
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(6.dp))
        Image(
            painter = painterResource(R.mipmap.ic_launcher),
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
            text = "version ${BuildConfig.VERSION_NAME} · ${BuildConfig.APPLICATION_ID}",
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

        SectionCard("The app") {
            LinkRow(
                label = "Privacy policy",
                detail = "What leaves the phone, and what does not",
                onClick = { open(privacyUrl) }
            )
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

        Row(
            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (refreshing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = "Checking for newer text…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                TextButton(onClick = {
                    scope.launch {
                        refreshing = true
                        val fresh = About.refresh(context)
                        if (fresh != null) page = fresh else
                            showMessage("Could not reach the app's page — showing the copy on the phone")
                        refreshing = false
                    }
                }) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (page.live) "Refresh" else "Try again",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
            }
        }
    }
}

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
