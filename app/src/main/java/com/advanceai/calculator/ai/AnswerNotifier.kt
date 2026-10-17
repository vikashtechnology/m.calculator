package com.advanceai.calculator.ai

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.advanceai.calculator.R

/**
 * A single "your answer is ready" notification, shown when a solve finishes
 * while the user is somewhere else in the app.
 *
 * Nothing else ever posts a notification, and nothing is shown when the user is
 * already looking at the chat.
 */
object AnswerNotifier {

    private const val ChannelId = "ai_answers"
    private const val NotificationId = 4101

    /** True when notifications may be posted (always true below Android 13). */
    fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun notify(context: Context, ok: Boolean, text: String) {
        if (!canNotify(context)) return
        ensureChannel(context)

        val body = text.replace('\n', ' ').let {
            if (it.length > 140) it.take(139) + "…" else it
        }
        val notification = NotificationCompat.Builder(context, ChannelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (ok) "Your answer is ready" else "Ask AI hit a problem")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(NotificationId, notification)
        }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(ChannelId) != null) return
        val channel = NotificationChannel(
            ChannelId,
            "Ask AI answers",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Tells you when a question you asked has been solved"
        }
        manager.createNotificationChannel(channel)
    }
}
