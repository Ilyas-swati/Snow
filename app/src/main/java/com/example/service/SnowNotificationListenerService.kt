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

        private val recentNotificationsList = java.util.Collections.synchronizedList(mutableListOf<ReceivedNotification>())

        fun getRecentNotifications(): List<ReceivedNotification> {
            synchronized(recentNotificationsList) {
                return recentNotificationsList.toList()
            }
        }

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

        if (text.isNotBlank()) {
            val item = ReceivedNotification(
                packageName = pkg,
                sender = title.ifBlank { pkg.substringAfterLast('.') },
                message = text
            )
            _latestNotification.value = item
            synchronized(recentNotificationsList) {
                recentNotificationsList.add(0, item)
                if (recentNotificationsList.size > 15) {
                    recentNotificationsList.removeAt(recentNotificationsList.size - 1)
                }
            }
        }
    }
}
