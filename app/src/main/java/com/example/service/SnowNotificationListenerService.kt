package com.example.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ReceivedNotification(
    val packageName: String,
    val sender: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

class SnowNotificationListenerService : NotificationListenerService() {

    companion object {
        private val _latestNotification = MutableStateFlow<ReceivedNotification?>(null)
        val latestNotification: StateFlow<ReceivedNotification?> = _latestNotification.asStateFlow()

        var isConnected: Boolean = false
            private set
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        isConnected = true
        Log.d("SnowNotification", "Listener connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        isConnected = false
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val pkg = sbn.packageName ?: return
        val extras = sbn.notification.extras ?: return

        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

        if (pkg == "com.whatsapp" && text.isNotBlank()) {
            Log.d("SnowNotification", "WhatsApp notification from $title: $text")
            _latestNotification.value = ReceivedNotification(
                packageName = pkg,
                sender = title,
                message = text
            )
        }
    }
}
