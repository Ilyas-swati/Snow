package com.example.device

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.example.service.SnowAccessibilityService
import java.net.URLEncoder

class DeviceCommander(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager

    fun openAppByName(appName: String): Boolean {
        val query = appName.trim().lowercase()
        val pm = context.packageManager

        // Known common mappings
        val commonPackages = mapOf(
            "whatsapp" to "com.whatsapp",
            "youtube" to "com.google.android.youtube",
            "camera" to "com.google.android.GoogleCamera",
            "chrome" to "com.android.chrome",
            "maps" to "com.google.android.apps.maps",
            "settings" to "com.android.settings",
            "calculator" to "com.google.android.calculator",
            "photos" to "com.google.android.apps.photos",
            "gallery" to "com.google.android.apps.photos",
            "spotify" to "com.spotify.music",
            "clock" to "com.google.android.deskclock",
            "calendar" to "com.google.android.calendar",
            "contacts" to "com.google.android.contacts",
            "phone" to "com.google.android.dialer",
            "messages" to "com.google.android.apps.messaging"
        )

        for ((key, pkg) in commonPackages) {
            if (query.contains(key)) {
                val launchIntent = pm.getLaunchIntentForPackage(pkg)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    return true
                }
            }
        }

        // Search through all installed applications
        try {
            val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            for (appInfo in installedApps) {
                val label = pm.getApplicationLabel(appInfo).toString().lowercase()
                if (label.contains(query) || query.contains(label)) {
                    val launchIntent = pm.getLaunchIntentForPackage(appInfo.packageName)
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(launchIntent)
                        return true
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("DeviceCommander", "Error searching apps", e)
        }

        // Fallback: generic browser search or settings
        if (query.contains("setting")) {
            val intent = Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return true
        }

        return false
    }

    fun sendWhatsAppMessage(recipient: String, message: String): Boolean {
        // Queue message into Accessibility Service if it's running
        SnowAccessibilityService.instance?.queueMessageForTyping(recipient, message)

        // Try direct WhatsApp URL intent
        return try {
            val cleanNumber = recipient.filter { it.isDigit() }
            val encodedMessage = URLEncoder.encode(message, "UTF-8")

            val uri = if (cleanNumber.length >= 7) {
                Uri.parse("https://api.whatsapp.com/send?phone=$cleanNumber&text=$encodedMessage")
            } else {
                Uri.parse("https://api.whatsapp.com/send?text=$encodedMessage")
            }

            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.whatsapp")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            // WhatsApp not installed or error, fallback to generic share
            try {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, message)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Send via").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
                true
            } catch (ex: Exception) {
                Log.e("DeviceCommander", "Error launching WhatsApp/Share", ex)
                false
            }
        }
    }

    fun toggleFlashlight(turnOn: Boolean): Boolean {
        return try {
            val cameraId = cameraManager?.cameraIdList?.firstOrNull() ?: return false
            cameraManager.setTorchMode(cameraId, turnOn)
            true
        } catch (e: Exception) {
            Log.e("DeviceCommander", "Flashlight error", e)
            false
        }
    }

    fun adjustVolume(increase: Boolean): Boolean {
        return try {
            val direction = if (increase) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
            audioManager?.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
            true
        } catch (e: Exception) {
            Log.e("DeviceCommander", "Volume error", e)
            false
        }
    }

    fun openWifiSettings() {
        try {
            val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("DeviceCommander", "Wifi settings error", e)
        }
    }

    fun openBluetoothSettings() {
        try {
            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("DeviceCommander", "Bluetooth settings error", e)
        }
    }
}
