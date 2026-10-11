package com.advanceai.calculator.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.advanceai.calculator.ai.AiChat
import com.advanceai.calculator.ai.AiModels
import com.advanceai.calculator.ai.GeminiClient
import com.advanceai.calculator.ai.PickedAttachment
import com.advanceai.calculator.data.UserKeys
import kotlinx.coroutines.launch
import java.io.File

/**
 * Ask AI as a chat: type a question, or take a photo or attach a file, and the
 * answer comes back as a message.
 *
 * There is no memory on purpose — every question is sent on its own, turns are
 * never stored on disk, and *New chat* (or closing the app) wipes the thread.
 */
@Composable
fun AiChatScreen(
    onClose: () -> Unit,
    showMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val turns by AiChat.turns.collectAsState()
    val busy by AiChat.busy.collectAsState()
    val listState = rememberLazyListState()

    var draft by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<PickedAttachment?>(null) }
    var preview by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var settingsOpen by remember { mutableStateOf(!GeminiClient.hasKey) }
    var pendingPhoto by remember { mutableStateOf<Uri?>(null) }

    BackHandler { onClose() }

    // While this screen is open, answers appear here instead of as notifications.
    DisposableEffect(Unit) {
        AiChat.onScreenVisible()
        onDispose { AiChat.onScreenHidden() }
    }

    // Ask for the notification permission once (Android 13+); an answer that
    // lands while the user is browsing the calculator is announced with it.
    val notificationPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) showMessage("Answers will be shown in the app only")
    }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /** The camera writes here, then the file is read back into memory. */
    fun newCameraUri(): Uri? = runCatching {
        val dir = File(context.cacheDir, "ai_camera").apply { mkdirs() }
        val file = File(dir, "question_${System.currentTimeMillis()}.jpg")
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }.getOrNull()

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { saved ->
        val uri = pendingPhoto
        pendingPhoto = null
        if (!saved || uri == null) return@rememberLauncherForActivityResult
        val picked = runCatching { readAttachment(context, uri, "camera.jpg") }.getOrNull()
        if (picked == null) {
            showMessage("That photo could not be read")
        } else {
            pending = picked
            preview = decodePreview(context, uri)
        }
    }

    val cameraPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val uri = newCameraUri()
            if (uri == null) {
                showMessage("The camera is not available")
            } else {
                pendingPhoto = uri
                cameraLauncher.launch(uri)
            }
        } else {
            showMessage("Camera permission is needed to photograph a question")
        }
    }

    fun takePhoto() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            val uri = newCameraUri()
            if (uri == null) {
                showMessage("The camera is not available")
            } else {
                pendingPhoto = uri
                cameraLauncher.launch(uri)
            }
        } else {
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val picked = runCatching { readAttachment(context, uri, "upload") }.getOrNull()
        if (picked == null) {
            showMessage("That file could not be read")
        } else if (picked.bytes.size > GeminiClient.MaxAttachmentBytes) {
            showMessage("That file is larger than 15 MB — try a photo instead")
        } else {
            pending = picked
            preview = decodePreview(context, uri)
        }
    }

    fun send() {
        val question = draft
        val attachment = pending
        if (question.isBlank() && attachment == null) return
        if (!GeminiClient.hasKey) {
            settingsOpen = true
            showMessage("Add your Gemini API key first")
            return
        }
        AiChat.ask(context, question, attachment)
        draft = ""
        pending = null
        preview = null
    }

    LaunchedEffect(turns.size, busy) {
        if (turns.isNotEmpty()) listState.animateScrollToItem(turns.size - 1)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
    ) {
        ChatHeader(
            model = AiModels.resolve(context),
            onClose = onClose,
            onNewChat = {
                AiChat.clear()
                pending = null
                preview = null
                draft = ""
            },
            onSettings = { settingsOpen = !settingsOpen }
        )

        Box(modifier = Modifier.weight(1f)) {
            if (turns.isEmpty()) {
                ChatWelcome(
                    onTakePhoto = { takePhoto() },
                    onAttach = { fileLauncher.launch(arrayOf("image/*", "application/pdf", "text/*")) },
                    onExample = { text ->
                        draft = text
                    }
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(turns) { turn ->
                        ChatBubble(
                            turn = turn,
                            onCopy = { text ->
                                clipboard.setText(AnnotatedString(text))
                                showMessage("Copied the answer")
                            }
                        )
                    }
                    item { Box(modifier = Modifier.size(6.dp)) }
                }
            }
        }

        if (settingsOpen) {
            ChatSettingsCard(
                onOpenStudio = {
                    val opened = runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikey"))
                        )
                    }.isSuccess
                    if (!opened) showMessage("Open aistudio.google.com/apikey in a browser")
                },
                onDismiss = { settingsOpen = false }
            )
        }

        ChatComposer(
            draft = draft,
            onDraftChange = { draft = it },
            attachment = pending,
            preview = preview,
            busy = busy,
            onClearAttachment = {
                pending = null
                preview = null
            },
            onTakePhoto = { takePhoto() },
            onAttach = { fileLauncher.launch(arrayOf("image/*", "application/pdf", "text/*")) },
            onSend = { send() },
            onStop = {
                AiChat.cancel()
                scope.launch { showMessage("Stopped") }
            }
        )
    }
}

@Composable
private fun ChatHeader(
    model: String,
    onClose: () -> Unit,
    onNewChat: () -> Unit,
    onSettings: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        color = colors.surfaceContainerHigh,
        contentColor = colors.onSurface,
        shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
        shadowElevation = 6.dp,
        tonalElevation = 3.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 4.dp, end = 8.dp, top = 6.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to the calculator",
                    tint = colors.primary
                )
            }
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .shadow(3.dp, CircleShape)
                    .background(colors.primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = colors.onPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp)
            ) {
                Text(
                    text = "Ask AI",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "${AiModels.describe(model)} · no memory",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onNewChat) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "New chat",
                    tint = colors.onSurfaceVariant
                )
            }
            IconButton(onClick = onSettings) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "Key and model",
                    tint = colors.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ChatBubble(
    turn: AiChat.Turn,
    onCopy: (String) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val mine = turn.fromUser
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start
    ) {
        if (!mine) {
            Box(
                modifier = Modifier
                    .padding(end = 8.dp, top = 4.dp)
                    .size(26.dp)
                    .background(colors.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = colors.onPrimaryContainer,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
        Surface(
            color = when {
                mine -> colors.primaryContainer
                turn.kind == AiChat.Turn.Kind.ERROR -> colors.errorContainer
                else -> colors.surfaceContainerHigh
            },
            contentColor = when {
                mine -> colors.onPrimaryContainer
                turn.kind == AiChat.Turn.Kind.ERROR -> colors.onErrorContainer
                else -> colors.onSurface
            },
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (mine) 18.dp else 4.dp,
                bottomEnd = if (mine) 4.dp else 18.dp
            ),
            shadowElevation = 2.dp,
            modifier = Modifier
                .widthIn(max = 320.dp)
                .shadow(2.dp, RoundedCornerShape(18.dp))
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                turn.attachment?.let { label ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.PhotoCamera,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(start = 6.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                when (turn.kind) {
                    AiChat.Turn.Kind.PENDING -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(15.dp),
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = "Working it out…",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    else -> Text(
                        text = turn.text,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (!mine && turn.kind != AiChat.Turn.Kind.PENDING) {
                    TextButton(
                        onClick = { onCopy(turn.text) },
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Text("Copy", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatWelcome(
    onTakePhoto: () -> Unit,
    onAttach: () -> Unit,
    onExample: (String) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .shadow(8.dp, CircleShape)
                .background(colors.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = colors.onPrimaryContainer,
                modifier = Modifier.size(36.dp)
            )
        }
        Text(
            text = "Ask me a maths question",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(top = 16.dp)
        )
        Text(
            text = "Type it, photograph it, or attach a file. " +
                "Each question is answered on its own — this chat has no memory.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )
        Row(
            modifier = Modifier.padding(top = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(onClick = onTakePhoto) {
                Icon(
                    imageVector = Icons.Filled.PhotoCamera,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Text(text = "Camera", modifier = Modifier.padding(start = 6.dp))
            }
            Button(onClick = onAttach) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Text(text = "Attach a file", modifier = Modifier.padding(start = 6.dp))
            }
        }
        Column(
            modifier = Modifier.padding(top = 22.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                "Solve 2x² − 5x + 3 = 0 step by step",
                "A shirt costs ₹1,999 after 25% off — what was the price?",
                "Find the derivative of x³ sin x"
            ).forEach { example ->
                Surface(
                    onClick = { onExample(example) },
                    color = colors.surfaceContainer,
                    contentColor = colors.onSurfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    shadowElevation = 1.dp
                ) {
                    Text(
                        text = example,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatComposer(
    draft: String,
    onDraftChange: (String) -> Unit,
    attachment: PickedAttachment?,
    preview: androidx.compose.ui.graphics.ImageBitmap?,
    busy: Boolean,
    onClearAttachment: () -> Unit,
    onTakePhoto: () -> Unit,
    onAttach: () -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        color = colors.surfaceContainerHigh,
        contentColor = colors.onSurface,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        shadowElevation = 10.dp,
        tonalElevation = 3.dp,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            attachment?.let { file ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
                ) {
                    preview?.let { bitmap ->
                        Image(
                            bitmap = bitmap,
                            contentDescription = null,
                            modifier = Modifier
                                .size(38.dp)
                                .shadow(3.dp, RoundedCornerShape(9.dp))
                                .background(colors.surface, RoundedCornerShape(9.dp))
                        )
                    }
                    Text(
                        text = file.name,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = if (preview == null) 0.dp else 8.dp)
                    )
                    IconButton(onClick = onClearAttachment) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Remove the attachment",
                            tint = colors.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.Bottom) {
                IconButton(onClick = onTakePhoto) {
                    Icon(
                        imageVector = Icons.Filled.PhotoCamera,
                        contentDescription = "Take a photo",
                        tint = colors.secondary
                    )
                }
                IconButton(onClick = onAttach) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Attach a file",
                        tint = colors.secondary
                    )
                }
                OutlinedTextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    placeholder = { Text("Ask a maths question…") },
                    maxLines = 4,
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.primary,
                        unfocusedBorderColor = colors.outlineVariant
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp, max = 130.dp)
                )
                Box(modifier = Modifier.padding(start = 6.dp, bottom = 2.dp)) {
                    if (busy) {
                        FilledIconButton(
                            onClick = onStop,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = colors.error,
                                contentColor = colors.onError
                            ),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Stop"
                            )
                        }
                    } else {
                        FilledIconButton(
                            onClick = onSend,
                            enabled = draft.isNotBlank() || attachment != null,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send"
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatSettingsCard(
    onOpenStudio: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    var keyDraft by remember { mutableStateOf("") }
    var savedKey by remember { mutableStateOf(UserKeys.geminiKey(context)) }
    var model by remember { mutableStateOf(AiModels.resolve(context)) }

    Surface(
        color = colors.surfaceContainerHighest,
        contentColor = colors.onSurface,
        shape = RoundedCornerShape(18.dp),
        shadowElevation = 8.dp,
        tonalElevation = 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Key and model",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close the settings",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            OutlinedTextField(
                value = keyDraft,
                onValueChange = { keyDraft = it },
                label = { Text(if (savedKey.isBlank()) "Gemini API key" else "Replace the key") },
                placeholder = { Text("AIza…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (savedKey.isNotBlank()) {
                Text(
                    text = "Saved: ${UserKeys.describe(savedKey)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val key = keyDraft.trim()
                        if (key.isEmpty()) return@Button
                        UserKeys.setGeminiKey(context, key)
                        savedKey = key
                        keyDraft = ""
                    },
                    enabled = keyDraft.isNotBlank()
                ) { Text("Save the key") }
                OutlinedButton(onClick = onOpenStudio) { Text("Get a free key") }
                if (savedKey.isNotBlank()) {
                    TextButton(onClick = {
                        UserKeys.clearGeminiKey(context)
                        savedKey = ""
                    }) { Text("Remove") }
                }
            }

            Text(
                text = "Model",
                style = MaterialTheme.typography.labelLarge,
                color = colors.onSurfaceVariant
            )
            AiModels.choices.chunked(2).forEach { row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(bottom = 4.dp)
                ) {
                    row.forEach { (id, label) ->
                        Surface(
                            onClick = {
                                UserKeys.setGeminiModel(context, id)
                                model = id
                            },
                            color = if (model == id) colors.primaryContainer else colors.surfaceContainer,
                            contentColor = if (model == id) colors.onPrimaryContainer else colors.onSurfaceVariant,
                            shape = RoundedCornerShape(10.dp),
                            shadowElevation = if (model == id) 2.dp else 1.dp
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
            Text(
                text = "Ask AI has no memory: each message is sent on its own, and " +
                    "nothing is saved on the phone. Closing the app clears the chat.",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant
            )
        }
    }
}

// ---- reading what the camera or picker handed back ---------------------------

/** Opens [uri] through the content resolver and keeps the bytes in memory. */
fun readAttachment(
    context: android.content.Context,
    uri: Uri,
    fallbackName: String
): PickedAttachment {
    val resolver = context.contentResolver
    val mimeType = resolver.getType(uri) ?: "application/octet-stream"
    val name = resolver.query(uri, null, null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
    } ?: fallbackName

    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
        ?: throw IllegalStateException("The file could not be opened")
    if (bytes.size > GeminiClient.MaxAttachmentBytes) {
        throw IllegalStateException("That file is larger than 15 MB")
    }
    return PickedAttachment(name = name, mimeType = mimeType, bytes = bytes)
}

private fun decodePreview(
    context: android.content.Context,
    uri: Uri
): androidx.compose.ui.graphics.ImageBitmap? = runCatching {
    context.contentResolver.openInputStream(uri)?.use { input ->
        BitmapFactory.decodeStream(input)?.asImageBitmap()
    }
}.getOrNull()
