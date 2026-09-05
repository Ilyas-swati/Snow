package com.example.permissions

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.service.SnowAccessibilityService
import com.example.service.SnowNotificationListenerService

data class PermissionStatus(
    val title: String,
    val description: String,
    val isGranted: Boolean,
    val isCritical: Boolean,
    val settingsAction: () -> Unit
)

class PermissionManager(private val context: Context) {

    fun hasRecordAudio(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun hasCamera(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    fun hasLocation(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun hasContacts(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    fun hasPostNotifications(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun isAccessibilityServiceEnabled(): Boolean = SnowAccessibilityService.isServiceRunning

    fun isNotificationListenerEnabled(): Boolean = SnowNotificationListenerService.isConnected

    fun getAllPermissionStatuses(): List<PermissionStatus> {
        return listOf(
            PermissionStatus(
                title = "Microphone",
                description = "Required for voice input and wake word detection",
                isGranted = hasRecordAudio(),
                isCritical = true,
                settingsAction = { openAppSettings() }
            ),
            PermissionStatus(
                title = "Camera",
                description = "Required for visual AI understanding and object recognition",
                isGranted = hasCamera(),
                isCritical = false,
                settingsAction = { openAppSettings() }
            ),
            PermissionStatus(
                title = "Contacts",
                description = "Allows Snow to find contacts for calls and messages",
                isGranted = hasContacts(),
                isCritical = false,
                settingsAction = { openAppSettings() }
            ),
            PermissionStatus(
                title = "Location",
                description = "Provides real-time local weather and navigation support",
                isGranted = hasLocation(),
                isCritical = false,
                settingsAction = { openAppSettings() }
            ),
            PermissionStatus(
                title = "Notifications",
                description = "Enables alarms, reminders, and background alerts",
                isGranted = hasPostNotifications(),
                isCritical = false,
                settingsAction = { openAppSettings() }
            ),
            PermissionStatus(
                title = "Screen Control (Accessibility)",
                description = "Allows Snow to read screen text and assist inside apps",
                isGranted = isAccessibilityServiceEnabled(),
                isCritical = false,
                settingsAction = { openAccessibilitySettings() }
            ),
            PermissionStatus(
                title = "Notification Access",
                description = "Enables Snow to read permitted notifications aloud upon request",
                isGranted = isNotificationListenerEnabled(),
                isCritical = false,
                settingsAction = { openNotificationListenerSettings() }
            )
        )
    }

    fun openAppSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            val intent = Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    fun openAccessibilitySettings() {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            openAppSettings()
        }
    }

    fun openNotificationListenerSettings() {
        try {
            val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            openAppSettings()
        }
    }

    fun requestMissingCorePermissions(activity: Activity) {
        val missing = mutableListOf<String>()
        if (!hasRecordAudio()) missing.add(Manifest.permission.RECORD_AUDIO)
        if (!hasCamera()) missing.add(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasPostNotifications()) {
            missing.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(activity, missing.toTypedArray(), 1001)
        }
    }
}
