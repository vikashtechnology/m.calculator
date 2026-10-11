package com.advanceai.calculator.ui.screens

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebResourceErrorCompat
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import com.advanceai.calculator.ai.GoogleSignIn
import com.advanceai.calculator.data.Telemetry
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Where the bundled Ask AI page is served from: a real https origin. */
private const val AI_PAGE_URL = "https://appassets.androidplatform.net/assets/ai/index.html"

/**
 * "Ask AI" — a WebView around the bundled page that drives Puter.js.
 *
 * Puter needs a page with an origin it can sign in, so the HTML is served over
 * `appassets.androidplatform.net` by [WebViewAssetLoader] instead of `file://`.
 * Sign-in opens a popup, which Android only allows when multiple windows are
 * enabled and [WebChromeClient.onCreateWindow] supplies a WebView for it — that
 * popup is shown here as a full-screen dialog.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AiScreen(
    onClose: () -> Unit,
    showMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val scope = rememberCoroutineScope()
    var googleBusy by remember { mutableStateOf(false) }

    var statusText by remember { mutableStateOf("loading…") }
    var statusKind by remember { mutableStateOf("") } // "ok" | "warn" | ""
    var pageLoading by remember { mutableStateOf(true) }
    var pageFailed by remember { mutableStateOf(false) }

    val holder = remember { WebViewHolder() }
    var popupDialog by remember { mutableStateOf<Dialog?>(null) }
    var fileCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }

    val assetLoader = remember {
        WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
            .build()
    }

    // The page hands picked files back to the WebView through this launcher.
    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val callback = fileCallback
        fileCallback = null
        callback?.onReceiveValue(
            WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
        )
    }

    fun refreshAuthInPage() {
        holder.view?.evaluateJavascript(
            "window.mCalculatorRefreshAuth && window.mCalculatorRefreshAuth();",
            null
        )
    }

    /**
     * Runs the Google account picker and hands the ID token to the page, which
     * uses that same Google account to sign in to Puter.
     */
    fun signInWithGoogle() {
        if (!GoogleSignIn.isConfigured || googleBusy) return
        googleBusy = true
        scope.launch {
            val result = GoogleSignIn.signIn(GoogleSignIn.activityOf(context))
            googleBusy = false
            val view = holder.view ?: return@launch
            if (result.ok) {
                statusText = result.name.ifEmpty { result.email.ifEmpty { "signed in" } }
                statusKind = "ok"
                val argument = JSONObject.quote(result.idToken)
                val email = JSONObject.quote(result.email)
                view.evaluateJavascript(
                    "window.mCalculatorGoogleToken && window.mCalculatorGoogleToken($argument, $email);",
                    null
                )
            } else if (result.error == "cancelled") {
                showMessage("Sign-in cancelled")
            } else {
                showMessage("Google sign-in failed: ${result.error}")
                refreshAuthInPage()
            }
        }
    }

    fun openPopupForAuth(source: WebView, resultMsg: Message) {
        val activity = context.findActivity()
        if (activity == null) {
            resultMsg.sendToTarget()
            return
        }
        val popup = WebView(activity).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.javaScriptCanOpenWindowsAutomatically = true
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            webViewClient = object : WebViewClientCompat() {
                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                    statusText = "signing in…"
                    statusKind = ""
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onCloseWindow(window: WebView) {
                    popupDialog?.dismiss()
                }
            }
        }
        val dialog = Dialog(activity).apply {
            setContentView(
                popup,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            window?.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setOnDismissListener {
                popup.stopLoading()
                popup.destroy()
                popupDialog = null
                // The token arrives by message from the popup; ask the page to
                // re-read the session as soon as the popup goes away.
                mainHandler.postDelayed({ refreshAuthInPage() }, 350)
            }
            show()
        }
        popupDialog = dialog
        (resultMsg.obj as WebView.WebViewTransport).webView = popup
        resultMsg.sendToTarget()
        source.requestFocus()
    }

    val webChrome = remember {
        object : WebChromeClient() {
            override fun onCreateWindow(
                view: WebView,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message
            ): Boolean {
                openPopupForAuth(view, resultMsg)
                return true
            }

            override fun onShowFileChooser(
                webView: WebView,
                callback: ValueCallback<Array<Uri>>?,
                params: FileChooserParams?
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = callback
                return try {
                    // createIntent() already carries the page's accept types;
                    // fall back to a general picker when the params are missing.
                    val intent = params?.createIntent()
                        ?: Intent(Intent.ACTION_GET_CONTENT).apply { type = "*/*" }
                    fileLauncher.launch(intent)
                    true
                } catch (e: Exception) {
                    fileCallback = null
                    false
                }
            }
        }
    }

    val webClient = remember {
        object : WebViewClientCompat() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val url = request.url
                val host = url.host ?: return false
                // Keep the app page, Puter and the pdf.js CDN inside the WebView.
                val staysInside = host == "appassets.androidplatform.net" ||
                    host.endsWith("puter.com") ||
                    host.endsWith("cdnjs.cloudflare.com")
                if (staysInside) return false
                return try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, url))
                    true
                } catch (e: Exception) {
                    true
                }
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                pageLoading = true
                pageFailed = false
            }

            override fun onPageFinished(view: WebView, url: String?) {
                pageLoading = false
                // Tell the page whether the in-app Google button can be used.
                view.evaluateJavascript(
                    "window.mCalculatorGoogleAvailable && " +
                        "window.mCalculatorGoogleAvailable(${GoogleSignIn.isConfigured});",
                    null
                )
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceErrorCompat
            ) {
                if (request.isForMainFrame) {
                    pageFailed = true
                    pageLoading = false
                    statusText = "offline"
                    statusKind = "warn"
                }
            }

            override fun onReceivedHttpError(
                view: WebView,
                request: WebResourceRequest,
                errorResponse: WebResourceResponse
            ) {
                if (request.isForMainFrame && errorResponse.statusCode >= 400) {
                    pageFailed = true
                    pageLoading = false
                }
            }
        }
    }

    BackHandler {
        val view = holder.view
        if (view != null && view.canGoBack()) view.goBack() else onClose()
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 12.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to the calculator",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = "Ask AI",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            if (GoogleSignIn.isConfigured) {
                Surface(
                    onClick = { signInWithGoogle() },
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Text(
                        text = if (googleBusy) "Signing in…" else "Google",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
            Surface(
                color = when (statusKind) {
                    "ok" -> MaterialTheme.colorScheme.secondaryContainer
                    "warn" -> MaterialTheme.colorScheme.errorContainer
                    else -> MaterialTheme.colorScheme.surfaceContainerHigh
                },
                contentColor = when (statusKind) {
                    "ok" -> MaterialTheme.colorScheme.onSecondaryContainer
                    "warn" -> MaterialTheme.colorScheme.onErrorContainer
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        settings.setSupportMultipleWindows(true)
                        settings.javaScriptCanOpenWindowsAutomatically = true
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.loadsImagesAutomatically = true
                        settings.mediaPlaybackRequiresUserGesture = true
                        webViewClient = webClient
                        webChromeClient = webChrome
                        addJavascriptInterface(
                            object {
                                @JavascriptInterface
                                fun onStatus(json: String) {
                                    mainHandler.post {
                                        runCatching {
                                            val payload = JSONObject(json)
                                            when (payload.optString("event")) {
                                                "status" -> {
                                                    statusText = payload.optString("text", "ready")
                                                    statusKind = payload.optString("kind")
                                                }
                                                "auth" -> {
                                                    val user = payload.optString("user")
                                                    statusText = when {
                                                        !payload.optBoolean("signedIn") -> "signed out"
                                                        user.isNotEmpty() -> user
                                                        else -> "signed in"
                                                    }
                                                    statusKind = if (payload.optBoolean("signedIn")) "ok" else "warn"
                                                }
                                                "copy" -> {
                                                    val text = payload.optString("text")
                                                    if (text.isNotEmpty()) {
                                                        clipboard.setText(AnnotatedString(text))
                                                        showMessage("Copied the answer")
                                                    }
                                                }
                                                "googleSignIn" -> {
                                                    if (!payload.optBoolean("signedIn")) {
                                                        signInWithGoogle()
                                                    }
                                                }
                                                "solved" -> {
                                                    Telemetry.record(
                                                        context,
                                                        Telemetry.EVENT_AI
                                                    )
                                                }
                                                "googleSignOut" -> {
                                                    scope.launch {
                                                        GoogleSignIn.signOut(
                                                            GoogleSignIn.activityOf(context)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                            "AndroidBridge"
                        )
                        loadUrl(AI_PAGE_URL)
                        holder.view = this
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            if (pageLoading && !pageFailed) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(36.dp))
                    Text(
                        text = "Opening Puter…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }

            if (pageFailed) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.CloudOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(40.dp)
                    )
                    Text(
                        text = "Ask AI needs an internet connection",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                    Text(
                        text = "The calculator itself keeps working offline.",
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    Button(
                        onClick = {
                            pageFailed = false
                            pageLoading = true
                            holder.view?.loadUrl(AI_PAGE_URL)
                        },
                        modifier = Modifier.padding(top = 18.dp)
                    ) {
                        Text("Try again")
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            popupDialog?.dismiss()
            holder.view?.stopLoading()
            holder.view?.destroy()
            holder.view = null
        }
    }
}

/** Non-reactive reference to the WebView, so setting it never recomposes. */
private class WebViewHolder {
    var view: WebView? = null
}

/** Walks the context chain until the hosting Activity is found. */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
