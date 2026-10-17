package com.advanceai.calculator.ui

import android.content.Context
import android.content.Intent

/**
 * Hands a result to whichever app the user picks — a chat, a note, an email.
 * The text is exactly what was calculated, with no decoration added.
 */
fun shareText(context: Context, text: String) {
    if (text.isBlank()) return
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, "Share with"))
}
