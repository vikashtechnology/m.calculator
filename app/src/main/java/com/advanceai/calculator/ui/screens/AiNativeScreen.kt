package com.advanceai.calculator.ui.screens

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.advanceai.calculator.ai.GeminiClient
import com.advanceai.calculator.ai.GoogleSignIn
import com.advanceai.calculator.data.UserKeys
import kotlinx.coroutines.launch

/**
 * Ask AI, done natively: pick a photo, a PDF or a text file, send it to Gemini
 * and read the answer in the app. No WebView, so "Continue with Google" can use
 * the phone's own account picker instead of Google's web login page.
 */
@Composable
fun AiNativeScreen(
    onClose: () -> Unit,
    showMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var picked by remember { mutableStateOf<PickedFile?>(null) }
    var question by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var answer by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var googleEmail by remember { mutableStateOf(GoogleSignIn.cachedEmail(context)) }
    var googleBusy by remember { mutableStateOf(false) }
    // Proxy mode means the Worker checks a Google sign-in before it answers.
    var signedIn by remember { mutableStateOf(GoogleSignIn.isSignedIn(context)) }
    // The user's own Gemini key: no account, no sign-up, just a key they paste.
    var keySaved by remember { mutableStateOf(GeminiClient.hasKey) }
    var keyDraft by remember { mutableStateOf("") }
    var keyCard by remember { mutableStateOf(false) }
    var usePuter by remember { mutableStateOf(false) }

    if (usePuter) {
        // The zero-setup path for anyone who would rather not bring a key.
        AiScreen(onClose = onClose, showMessage = showMessage, modifier = modifier)
        return
    }

    BackHandler { onClose() }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val file = runCatching { readPickedFile(context, uri) }.getOrNull()
        if (file == null) {
            error = "That file could not be read. Try another one."
        } else if (file.bytes.size > GeminiClient.MaxAttachmentBytes) {
            error = "That file is larger than 15 MB. Try a screenshot instead."
        } else {
            picked = file
            answer = null
            error = null
        }
    }

    fun saveKey() {
        val key = keyDraft.trim()
        if (key.isEmpty()) {
            showMessage("Paste the key first")
            return
        }
        UserKeys.setGeminiKey(context, key)
        keyDraft = ""
        keySaved = true
        keyCard = false
        error = null
        showMessage(
            if (UserKeys.looksValid(key)) "Key saved — Ask AI is ready"
            else "Saved. If that key is wrong, Gemini will say so when you ask."
        )
    }

    fun signInWithGoogle() {
        if (!GoogleSignIn.isConfigured || googleBusy) {
            showMessage("Add the GOOGLE_WEB_CLIENT_ID secret to use your Google account")
            return
        }
        googleBusy = true
        scope.launch {
            val result = GoogleSignIn.signIn(GoogleSignIn.activityOf(context))
            googleBusy = false
            when {
                result.ok -> {
                    googleEmail = result.email
                    signedIn = true
                    showMessage("Signed in as ${result.email}")
                }
                result.error == "cancelled" -> Unit
                result.error == "no_google_account" ->
                    showMessage("No Google account on this device yet — add one in Settings")
                else -> showMessage("Google sign-in failed: ${result.error}")
            }
        }
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
            if (GeminiClient.usesProxy) {
                Surface(
                    onClick = { signInWithGoogle() },
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = when {
                            googleBusy -> "Signing in…"
                            googleEmail.isNotEmpty() -> "Google ✓"
                            else -> "Google"
                        },
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            } else {
                Surface(
                    onClick = { keyCard = true },
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (GeminiClient.usesUserKey) "Key ✓" else "API key",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Upload a photo, a PDF or a text file of a maths question. " +
                    "Only what is in the file gets solved, and the working comes back with it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (GeminiClient.usesProxy && !signedIn) {
                GoogleGateCard(
                    busy = googleBusy,
                    configured = GoogleSignIn.isConfigured,
                    onSignIn = { signInWithGoogle() }
                )
            } else if (!keySaved || keyCard) {
                ApiKeyCard(
                    draft = keyDraft,
                    onDraftChange = { keyDraft = it },
                    savedKey = if (GeminiClient.usesUserKey) GeminiClient.apiKey else "",
                    onSave = { saveKey() },
                    onClear = {
                        UserKeys.clearGeminiKey(context)
                        keySaved = GeminiClient.hasKey
                        keyDraft = ""
                        showMessage("Your key was removed")
                    },
                    onOpenStudio = {
                        val opened = runCatching {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://aistudio.google.com/apikey")
                                )
                            )
                        }.isSuccess
                        if (!opened) showMessage("Open aistudio.google.com/apikey in a browser")
                    },
                    onUsePuter = { usePuter = true },
                    hasBuiltInKey = GeminiClient.apiKey.isNotBlank() && !GeminiClient.usesUserKey
                )
            } else {
            val current = picked
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    if (current == null) {
                        Text(
                            text = "No file chosen yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            current.thumbnail?.let { bitmap ->
                                Image(
                                    bitmap = bitmap,
                                    contentDescription = null,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .size(56.dp)
                                        .background(MaterialTheme.colorScheme.surface)
                                )
                            }
                            Column(modifier = Modifier.padding(start = 10.dp)) {
                                Text(
                                    text = current.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${current.mimeType} · ${formatSize(current.bytes.size)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                picker.launch(arrayOf("image/*", "application/pdf", "text/*"))
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = if (current == null) "Choose a file" else "Replace",
                                modifier = Modifier.padding(start = 6.dp)
                            )
                        }
                        if (current != null) {
                            OutlinedButton(
                                onClick = {
                                    picked = null
                                    answer = null
                                    error = null
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(text = "Clear", modifier = Modifier.padding(start = 6.dp))
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = question,
                onValueChange = { question = it },
                label = { Text("Anything to add? (optional)") },
                singleLine = false,
                maxLines = 3,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    val file = picked ?: return@Button
                    val token = GoogleSignIn.cachedToken(context)
                    if (GeminiClient.usesProxy && token == null) {
                        signedIn = false
                        showMessage("Please sign in with Google again to continue")
                        return@Button
                    }
                    busy = true
                    answer = null
                    error = null
                    scope.launch {
                        val result = GeminiClient.solve(
                            question = question,
                            attachment = GeminiClient.Attachment(
                                mimeType = file.mimeType,
                                bytes = file.bytes,
                                name = file.name
                            ),
                            idToken = token
                        )
                        busy = false
                        when {
                            result.ok -> answer = result.text
                            result.error == "no_key" -> {
                                keySaved = false
                                keyCard = true
                                showMessage("Add your Gemini API key to ask a question")
                            }
                            result.error == "sign_in_required" -> {
                                signedIn = false
                                showMessage("Your sign-in was not accepted — tap Continue with Google")
                            }
                            else -> error = result.error
                        }
                    }
                },
                enabled = picked != null && !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = if (busy) "Solving…" else "Solve this question")
            }

            if (busy) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp))
                    Text(
                        text = "Reading the question and working it out…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            error?.let { message ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }

            answer?.let { text ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Answer",
                            style = MaterialTheme.typography.titleSmall
                        )
                        SelectionContainer {
                            Text(
                                text = text,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier
                                    .padding(top = 8.dp, bottom = 8.dp)
                                    .heightIn(min = 60.dp)
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TextButton(onClick = {
                                clipboard.setText(AnnotatedString(text))
                                showMessage("Copied the answer")
                            }) {
                                Text("Copy")
                            }
                            TextButton(onClick = {
                                answer = null
                                error = null
                            }) {
                                Text("Clear the answer")
                            }
                        }
                    }
                }
            }

            Text(
                text = if (GeminiClient.usesProxy) {
                    "The file goes to your own stats Worker, which asks Gemini " +
                        "\"${GeminiClient.model}\" on your signed-in behalf. Nothing is stored by this app."
                } else {
                    "The file goes to Google's Gemini API with the model " +
                        "\"${GeminiClient.model}\" and nothing is stored by this app."
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 18.dp)
            )
            }
        }
    }
}

/** The "just tap Continue with Google" gate: no key, no password. */
@Composable
private fun GoogleGateCard(
    busy: Boolean,
    configured: Boolean,
    onSignIn: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Sign in to use Ask AI", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "One tap and you are in: the AI runs under your Google " +
                    "account, so there is no key to paste and nothing to set up.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = onSignIn, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(text = if (busy) "Signing in…" else "Continue with Google")
            }
            Text(
                text = if (configured) {
                    "Your phone's own account picker opens — the accounts already on this device."
                } else {
                    "This build still needs the GOOGLE_WEB_CLIENT_ID secret to reach the " +
                        "account picker. Until then, builds with a GEMINI_API_KEY use the " +
                        "key mode instead."
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** What the picker returned, already read into memory. */
private class PickedFile(
    val name: String,
    val mimeType: String,
    val bytes: ByteArray,
    val thumbnail: androidx.compose.ui.graphics.ImageBitmap?
)

private fun readPickedFile(
    context: android.content.Context,
    uri: Uri
): PickedFile {
    val resolver = context.contentResolver
    val mimeType = resolver.getType(uri) ?: "application/octet-stream"
    val name = resolver.query(uri, null, null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
    } ?: (uri.lastPathSegment ?: "upload")

    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
        ?: throw IllegalStateException("The file could not be opened")

    val thumbnail = if (mimeType.startsWith("image/")) {
        runCatching {
            resolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input)?.asImageBitmap()
            }
        }.getOrNull()
    } else {
        null
    }

    return PickedFile(
        name = name,
        mimeType = mimeType,
        bytes = bytes,
        thumbnail = thumbnail
    )
}

private fun formatSize(bytes: Int): String = when {
    bytes >= 1024 * 1024 -> "${"%.1f".format(bytes / 1024.0 / 1024.0)} MB"
    bytes >= 1024 -> "${bytes / 1024} KB"
    else -> "$bytes B"
}

/**
 * "Bring your own key": the user pastes their own Gemini API key once. No
 * account, no sign-up, and the key never leaves the phone except to Google.
 */
@Composable
private fun ApiKeyCard(
    draft: String,
    onDraftChange: (String) -> Unit,
    savedKey: String,
    onSave: () -> Unit,
    onClear: () -> Unit,
    onOpenStudio: () -> Unit,
    onUsePuter: () -> Unit,
    hasBuiltInKey: Boolean
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Use your own Gemini key", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Ask AI runs on your own free key from Google AI Studio. " +
                    "It takes a minute, no account to create, and the key stays " +
                    "stored on this phone.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (savedKey.isNotBlank()) {
                Text(
                    text = "Your saved key: ${UserKeys.describe(savedKey)}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onClear) { Text("Remove the key") }
                    TextButton(onClick = onSave) { Text("Save a different key") }
                }
            }

            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                label = { Text(if (savedKey.isBlank()) "Gemini API key" else "New key") },
                placeholder = { Text("AIza…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (draft.isNotBlank() && !UserKeys.looksValid(draft)) {
                Text(
                    text = "That does not look like an AI Studio key (they start with AIza). " +
                        "It can still be saved if you know it is right.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onSave, enabled = draft.isNotBlank()) { Text("Save the key") }
                OutlinedButton(onClick = onOpenStudio) { Text("Get a free key") }
            }

            if (hasBuiltInKey) {
                Text(
                    text = "This build already has a key, so Ask AI works without one of your own.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = "The key is only sent to Google's Gemini API with your question. " +
                    "Delete it here any time.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            TextButton(onClick = onUsePuter) {
                Text("No key? Use the free Puter page instead")
            }
        }
    }
}
