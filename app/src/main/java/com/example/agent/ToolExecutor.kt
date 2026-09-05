package com.example.agent

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.BatteryManager
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.util.Log
import com.example.data.dao.ReminderDao
import com.example.data.model.ReminderEntity
import com.example.device.DeviceCommander
import com.example.permissions.PermissionManager
import com.example.search.SearchManager
import com.example.service.SnowAccessibilityService
import com.example.service.SnowNotificationListenerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ToolExecutor(
    private val context: Context,
    private val deviceCommander: DeviceCommander,
    private val searchManager: SearchManager,
    private val memoryManager: MemoryManager,
    private val notesManager: NotesManager,
    private val reminderDao: ReminderDao,
    private val permissionManager: PermissionManager
) {

    suspend fun executeTool(
        toolName: String,
        args: Map<String, String>
    ): ToolExecutionResult = withContext(Dispatchers.IO) {
        try {
            when (toolName) {
                "open_app" -> {
                    val appName = args["app_name"] ?: ""
                    val success = deviceCommander.openAppByName(appName)
                    if (success) {
                        ToolExecutionResult(toolName, true, "Application '$appName' was successfully opened.")
                    } else {
                        ToolExecutionResult(toolName, false, "Could not find or launch '$appName'. Make sure it is installed.")
                    }
                }

                "web_search" -> {
                    val query = args["query"] ?: ""
                    val result = searchManager.performSearch(query)
                    if (result.error != null) {
                        ToolExecutionResult(toolName, false, "Search failed: ${result.error}")
                    } else {
                        ToolExecutionResult(toolName, true, result.summary)
                    }
                }

                "search_contacts" -> {
                    val nameQuery = args["name"] ?: ""
                    if (!permissionManager.hasContacts()) {
                        return@withContext ToolExecutionResult(
                            toolName,
                            false,
                            "Contacts permission is required to find contacts. Please grant it in Settings."
                        )
                    }
                    val contacts = findContactsByName(nameQuery)
                    if (contacts.isEmpty()) {
                        ToolExecutionResult(toolName, true, "No contacts found matching '$nameQuery'.")
                    } else {
                        val formatted = contacts.joinToString("\n") { "${it.first}: ${it.second}" }
                        ToolExecutionResult(toolName, true, "Found contacts:\n$formatted")
                    }
                }

                "send_whatsapp" -> {
                    val recipient = args["recipient"] ?: ""
                    val message = args["message"] ?: ""
                    val success = deviceCommander.sendWhatsAppMessage(recipient, message)
                    if (success) {
                        ToolExecutionResult(toolName, true, "Prepared WhatsApp message to $recipient: '$message'")
                    } else {
                        ToolExecutionResult(toolName, false, "Failed to launch WhatsApp.")
                    }
                }

                "send_sms" -> {
                    val recipient = args["recipient"] ?: ""
                    val message = args["message"] ?: ""
                    try {
                        val smsIntent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("smsto:$recipient")
                            putExtra("sms_body", message)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(smsIntent)
                        ToolExecutionResult(toolName, true, "Opened SMS composer for $recipient.")
                    } catch (e: Exception) {
                        ToolExecutionResult(toolName, false, "Unable to open SMS composer: ${e.message}")
                    }
                }

                "phone_call" -> {
                    val number = args["number"] ?: ""
                    try {
                        val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                            data = Uri.parse("tel:$number")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(dialIntent)
                        ToolExecutionResult(toolName, true, "Opened phone dialer with $number.")
                    } catch (e: Exception) {
                        ToolExecutionResult(toolName, false, "Failed to open phone dialer: ${e.message}")
                    }
                }

                "set_alarm_or_timer" -> {
                    val title = args["title"] ?: "Snow Reminder"
                    val minutesOrTime = args["minutes_or_time"] ?: ""
                    val type = args["type"] ?: "TIMER"

                    if (type.equals("TIMER", ignoreCase = true)) {
                        val minutes = minutesOrTime.filter { it.isDigit() }.toIntOrNull() ?: 10
                        val timerIntent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                            putExtra(AlarmClock.EXTRA_MESSAGE, title)
                            putExtra(AlarmClock.EXTRA_LENGTH, minutes * 60)
                            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        try {
                            context.startActivity(timerIntent)
                        } catch (e: Exception) {
                            Log.w("ToolExecutor", "System timer intent not supported, logging reminder locally: ${e.message}")
                        }
                        val triggerTime = System.currentTimeMillis() + (minutes * 60 * 1000L)
                        reminderDao.insert(ReminderEntity(title = title, targetTimeMillis = triggerTime, type = "TIMER"))
                        ToolExecutionResult(toolName, true, "Set a timer for $minutes minutes ($title).")
                    } else {
                        // Alarm
                        var hour = 8
                        var minute = 0
                        if (minutesOrTime.contains(":")) {
                            val parts = minutesOrTime.split(":")
                            hour = parts.getOrNull(0)?.filter { it.isDigit() }?.toIntOrNull() ?: 8
                            minute = parts.getOrNull(1)?.filter { it.isDigit() }?.toIntOrNull() ?: 0
                        }
                        val alarmIntent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                            putExtra(AlarmClock.EXTRA_MESSAGE, title)
                            putExtra(AlarmClock.EXTRA_HOUR, hour)
                            putExtra(AlarmClock.EXTRA_MINUTES, minute)
                            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        try {
                            context.startActivity(alarmIntent)
                        } catch (e: Exception) {
                            Log.w("ToolExecutor", "Alarm intent warning: ${e.message}")
                        }
                        val cal = Calendar.getInstance().apply {
                            set(Calendar.HOUR_OF_DAY, hour)
                            set(Calendar.MINUTE, minute)
                            set(Calendar.SECOND, 0)
                            if (before(Calendar.getInstance())) add(Calendar.DATE, 1)
                        }
                        reminderDao.insert(ReminderEntity(title = title, targetTimeMillis = cal.timeInMillis, type = "ALARM"))
                        ToolExecutionResult(toolName, true, "Alarm set for ${String.format(Locale.US, "%02d:%02d", hour, minute)} ($title).")
                    }
                }

                "create_reminder" -> {
                    val title = args["title"] ?: "Reminder"
                    val timeDesc = args["time_description"] ?: "tomorrow"
                    val cal = Calendar.getInstance()
                    if (timeDesc.contains("tomorrow", ignoreCase = true) || timeDesc.contains("kal", ignoreCase = true)) {
                        cal.add(Calendar.DATE, 1)
                    }
                    if (timeDesc.contains("8", ignoreCase = true)) {
                        cal.set(Calendar.HOUR_OF_DAY, 8)
                        cal.set(Calendar.MINUTE, 0)
                    } else if (timeDesc.contains("7", ignoreCase = true)) {
                        cal.set(Calendar.HOUR_OF_DAY, 7)
                        cal.set(Calendar.MINUTE, 0)
                    } else {
                        cal.add(Calendar.HOUR_OF_DAY, 1)
                    }
                    reminderDao.insert(ReminderEntity(title = title, targetTimeMillis = cal.timeInMillis, type = "REMINDER"))
                    val sdf = SimpleDateFormat("MMM d 'at' h:mm a", Locale.getDefault())
                    ToolExecutionResult(toolName, true, "Reminder scheduled for ${sdf.format(cal.time)}: '$title'.")
                }

                "save_note" -> {
                    val title = args["title"] ?: "Note"
                    val content = args["content"] ?: ""
                    val output = notesManager.saveNote(title, content)
                    ToolExecutionResult(toolName, true, output)
                }

                "search_notes" -> {
                    val query = args["query"] ?: ""
                    val output = notesManager.searchNotes(query)
                    ToolExecutionResult(toolName, true, output)
                }

                "save_memory" -> {
                    val fact = args["fact"] ?: ""
                    val output = memoryManager.saveMemory(fact)
                    ToolExecutionResult(toolName, true, output)
                }

                "recall_memory" -> {
                    val query = args["query"] ?: ""
                    val output = memoryManager.recallMemory(query)
                    ToolExecutionResult(toolName, true, output)
                }

                "forget_memory" -> {
                    val query = args["query"] ?: ""
                    val output = memoryManager.forgetMatching(query)
                    ToolExecutionResult(toolName, true, output)
                }

                "device_control" -> {
                    val feature = (args["feature"] ?: "").lowercase()
                    val state = (args["state"] ?: "TOGGLE").uppercase()
                    when {
                        feature.contains("flash") -> {
                            val turnOn = state == "ON" || state == "TOGGLE"
                            deviceCommander.toggleFlashlight(turnOn)
                            ToolExecutionResult(toolName, true, "Flashlight turned ${if (turnOn) "ON" else "OFF"}.")
                        }
                        feature.contains("volume_up") -> {
                            deviceCommander.adjustVolume(true)
                            ToolExecutionResult(toolName, true, "Volume increased.")
                        }
                        feature.contains("volume_down") -> {
                            deviceCommander.adjustVolume(false)
                            ToolExecutionResult(toolName, true, "Volume decreased.")
                        }
                        feature.contains("wifi") -> {
                            deviceCommander.openWifiSettings()
                            ToolExecutionResult(toolName, true, "Opened Wi-Fi settings.")
                        }
                        feature.contains("bluetooth") -> {
                            deviceCommander.openBluetoothSettings()
                            ToolExecutionResult(toolName, true, "Opened Bluetooth settings.")
                        }
                        else -> {
                            ToolExecutionResult(toolName, false, "Unknown device feature: $feature")
                        }
                    }
                }

                "get_device_status" -> {
                    val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
                    val batteryPct = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
                    val sdfTime = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
                    val sdfDate = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(Date())
                    val statusStr = "Time: $sdfTime\nDate: $sdfDate\nBattery: ${if (batteryPct >= 0) "$batteryPct%" else "Available"}"
                    ToolExecutionResult(toolName, true, statusStr)
                }

                "read_notifications" -> {
                    val list = SnowNotificationListenerService.getRecentNotifications()
                    if (!SnowNotificationListenerService.isConnected) {
                        ToolExecutionResult(
                            toolName,
                            false,
                            "Notification listener access is not enabled. Please enable it in Settings → Permissions."
                        )
                    } else if (list.isEmpty()) {
                        ToolExecutionResult(toolName, true, "No recent notifications received.")
                    } else {
                        val formatted = list.take(5).joinToString("\n") { "• From ${it.sender}: ${it.message}" }
                        ToolExecutionResult(toolName, true, "Recent notifications:\n$formatted")
                    }
                }

                "screen_action" -> {
                    val service = SnowAccessibilityService.instance
                    if (service == null) {
                        return@withContext ToolExecutionResult(
                            toolName,
                            false,
                            "Screen Control is currently inactive. Please enable Snow Accessibility Service in Settings."
                        )
                    }
                    val action = (args["action"] ?: "READ_SCREEN").uppercase()
                    val targetText = args["target_text"] ?: ""
                    when (action) {
                        "READ_SCREEN" -> {
                            val text = service.getVisibleScreenText()
                            ToolExecutionResult(toolName, true, "Screen Content:\n$text")
                        }
                        "CLICK_TEXT" -> {
                            val clicked = service.clickTextOnScreen(targetText)
                            ToolExecutionResult(toolName, clicked, if (clicked) "Clicked '$targetText' on screen." else "Could not find '$targetText' on screen.")
                        }
                        "SCROLL_DOWN" -> {
                            val scrolled = service.scrollScreen(true)
                            ToolExecutionResult(toolName, scrolled, "Scrolled screen down.")
                        }
                        "BACK" -> {
                            val back = service.pressBack()
                            ToolExecutionResult(toolName, back, "Pressed back button.")
                        }
                        else -> {
                            ToolExecutionResult(toolName, false, "Unknown screen action: $action")
                        }
                    }
                }

                "file_operation" -> {
                    val action = (args["action"] ?: "LIST").uppercase()
                    val filename = args["filename"] ?: "notes.txt"
                    val content = args["content"] ?: ""
                    val dir = context.getExternalFilesDir(null) ?: context.filesDir
                    when (action) {
                        "CREATE" -> {
                            val file = File(dir, filename)
                            file.writeText(content, Charsets.UTF_8)
                            ToolExecutionResult(toolName, true, "Created file '${file.name}' with ${content.length} characters.")
                        }
                        "READ" -> {
                            val file = File(dir, filename)
                            if (file.exists()) {
                                ToolExecutionResult(toolName, true, file.readText(Charsets.UTF_8))
                            } else {
                                ToolExecutionResult(toolName, false, "File '$filename' does not exist.")
                            }
                        }
                        "LIST" -> {
                            val files = dir.listFiles()?.map { it.name } ?: emptyList()
                            ToolExecutionResult(toolName, true, "Stored files: " + files.joinToString(", ").ifBlank { "None" })
                        }
                        else -> ToolExecutionResult(toolName, false, "Unknown file action: $action")
                    }
                }

                "daily_briefing" -> {
                    val sdfTime = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
                    val sdfDate = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date())
                    val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
                    val batteryPct = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
                    val activeReminders = reminderDao.getActiveReminders()
                    val recentNotes = notesManager.getRecentNotesSummary()

                    val briefing = buildString {
                        append("Good morning! Today is $sdfDate, and the time is $sdfTime. ")
                        if (batteryPct >= 0) append("Battery level is at $batteryPct%. ")
                        if (activeReminders.isNotEmpty()) {
                            append("You have ${activeReminders.size} scheduled reminder(s): " + activeReminders.take(2).joinToString { it.title } + ". ")
                        } else {
                            append("You have no pending reminders. ")
                        }
                        if (recentNotes.isNotBlank() && recentNotes != "No recent notes.") {
                            append("Recent notes: $recentNotes")
                        }
                    }
                    ToolExecutionResult(toolName, true, briefing)
                }

                else -> ToolExecutionResult(toolName, false, "Tool '$toolName' is not recognized.")
            }
        } catch (e: Exception) {
            Log.e("ToolExecutor", "Error executing tool: $toolName", e)
            ToolExecutionResult(toolName, false, "Execution error: ${e.message}")
        }
    }

    private fun findContactsByName(name: String): List<Pair<String, String>> {
        val list = mutableListOf<Pair<String, String>>()
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$name%")

        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(uri, projection, selection, selectionArgs, null)
            cursor?.let {
                val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (it.moveToNext() && list.size < 5) {
                    val contactName = if (nameIndex >= 0) it.getString(nameIndex) ?: "" else ""
                    val contactNumber = if (numberIndex >= 0) it.getString(numberIndex) ?: "" else ""
                    if (contactName.isNotBlank() && contactNumber.isNotBlank()) {
                        list.add(contactName to contactNumber)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ToolExecutor", "Failed to query contacts", e)
        } finally {
            cursor?.close()
        }
        return list
    }
}
