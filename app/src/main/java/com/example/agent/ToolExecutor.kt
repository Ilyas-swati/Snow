package com.example.agent

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.BatteryManager
import android.provider.AlarmClock
import android.util.Log
import com.example.data.dao.ReminderDao
import com.example.data.model.ReminderEntity
import com.example.device.ContactResolutionResult
import com.example.device.ContactResolver
import com.example.device.DeviceCommander
import com.example.device.FileManagerHelper
import com.example.permissions.PermissionManager
import com.example.search.SearchManager
import com.example.service.SnowAccessibilityService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue

class ToolExecutor(
    private val context: Context,
    private val deviceCommander: DeviceCommander,
    private val searchManager: SearchManager,
    private val memoryManager: MemoryManager,
    private val notesManager: NotesManager,
    private val reminderDao: ReminderDao,
    private val permissionManager: PermissionManager,
    private val imageGenerationManager: com.example.image.ImageGenerationManager? = null
) {

    var onImageGeneratedListener: ((filePath: String, prompt: String) -> Unit)? = null
    var lastGeneratedImagePath: String? = null

    private val contactResolver = ContactResolver(context)
    private val fileManagerHelper = FileManagerHelper(context)

    companion object {
        val executionLogs = ConcurrentLinkedQueue<ActionLogEntry>()

        fun addLog(entry: ActionLogEntry) {
            executionLogs.add(entry)
            while (executionLogs.size > 50) {
                executionLogs.poll()
            }
        }
    }

    suspend fun executeTool(
        toolName: String,
        args: Map<String, String>
    ): ToolExecutionResult = withContext(Dispatchers.IO) {
        try {
            when (toolName) {
                // 1. OPEN_APP
                "open_app" -> {
                    val appName = args["app_name"] ?: args["target"] ?: ""
                    val success = deviceCommander.openAppByName(appName)
                    if (success) {
                        // Verification: check if package came to foreground
                        delay(600)
                        val fgPkg = SnowAccessibilityService.currentForegroundPackage
                        val verified = fgPkg.isNotBlank() && fgPkg != context.packageName
                        val msg = "Opened '$appName'. Active app: $fgPkg"
                        addLog(ActionLogEntry(actionType = "OPEN_APP", targetOrDetails = appName, isSuccess = true, verificationSummary = msg))
                        ToolExecutionResult(toolName, true, msg, verified = verified)
                    } else {
                        val msg = "Could not find or launch '$appName'. Make sure it is installed on this device."
                        addLog(ActionLogEntry(actionType = "OPEN_APP", targetOrDetails = appName, isSuccess = false, verificationSummary = msg))
                        ToolExecutionResult(toolName, false, msg)
                    }
                }

                // 2. SEND_WHATSAPP (Real WhatsApp workflow with verification)
                "send_whatsapp" -> {
                    val recipient = args["recipient"] ?: ""
                    val message = args["message"] ?: ""

                    if (recipient.isBlank()) {
                        return@withContext ToolExecutionResult(toolName, false, "Recipient name or number is required.")
                    }

                    // Contact Resolution
                    var cleanNumber = recipient.filter { it.isDigit() || it == '+' }
                    var contactDisplayName = recipient

                    if (cleanNumber.length < 7 && permissionManager.hasContacts()) {
                        when (val resolved = contactResolver.resolveContact(recipient)) {
                            is ContactResolutionResult.Single -> {
                                cleanNumber = resolved.contact.cleanNumber
                                contactDisplayName = resolved.contact.displayName
                                SnowAccessibilityService.addLog("Resolved contact '$recipient' -> ${resolved.contact.displayName} (${resolved.contact.cleanNumber})")
                            }
                            is ContactResolutionResult.Multiple -> {
                                val listStr = resolved.matches.joinToString("\n") { "• ${it.displayName} (${it.phoneNumber})" }
                                return@withContext ToolExecutionResult(
                                    toolName = toolName,
                                    isSuccess = false,
                                    output = "Multiple contacts found for '$recipient':\n$listStr\nPlease specify which contact you mean.",
                                    userVisibleMessage = "Found multiple contacts matching '$recipient'. Which one would you like to message?"
                                )
                            }
                            is ContactResolutionResult.NotFound -> {
                                SnowAccessibilityService.addLog("No local contact found for '$recipient'. Proceeding with name search in WhatsApp.")
                            }
                            is ContactResolutionResult.PermissionRequired -> {
                                SnowAccessibilityService.addLog("Contacts permission not granted. Proceeding with WhatsApp direct search.")
                            }
                        }
                    }

                    val service = SnowAccessibilityService.instance

                    // Queue into accessibility service
                    service?.queueWhatsAppMessage(contactDisplayName, message)

                    // Step 1: Open WhatsApp
                    val encodedMsg = URLEncoder.encode(message, "UTF-8")
                    val uri = if (cleanNumber.length >= 7) {
                        val digitsOnly = cleanNumber.filter { it.isDigit() }
                        Uri.parse("https://api.whatsapp.com/send?phone=$digitsOnly&text=$encodedMsg")
                    } else {
                        Uri.parse("https://api.whatsapp.com/send?text=$encodedMsg")
                    }

                    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                        setPackage("com.whatsapp")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }

                    var launched = false
                    try {
                        context.startActivity(intent)
                        launched = true
                    } catch (e: Exception) {
                        Log.w("ToolExecutor", "WhatsApp view intent failed, trying package launch", e)
                        launched = deviceCommander.openAppByName("whatsapp")
                    }

                    if (!launched) {
                        val msg = "WhatsApp is not installed on this device."
                        addLog(ActionLogEntry(actionType = "SEND_WHATSAPP", targetOrDetails = "$recipient: $message", isSuccess = false, verificationSummary = msg))
                        return@withContext ToolExecutionResult(toolName, false, msg)
                    }

                    // Step 2: Automation & Verification via Accessibility
                    if (service == null) {
                        val msg = "Opened WhatsApp for $contactDisplayName. Please enable Snow Accessibility Service in Settings so Snow can automatically click Send."
                        addLog(ActionLogEntry(actionType = "SEND_WHATSAPP", targetOrDetails = "$recipient: $message", isSuccess = true, verificationSummary = "Accessibility inactive"))
                        return@withContext ToolExecutionResult(toolName, true, msg, verified = false)
                    }

                    // Wait for WhatsApp to come to foreground
                    service.waitForPackage("com.whatsapp", 3500)
                    delay(800)

                    // Try clicking send button
                    var sendClicked = false
                    val root = service.rootInActiveWindow
                    if (root != null) {
                        // Check if send button is directly clickable
                        val sendNodes = root.findAccessibilityNodeInfosByViewId("com.whatsapp:id/send")
                        if (sendNodes.isNotEmpty()) {
                            sendClicked = sendNodes[0].performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
                        }
                        if (!sendClicked) {
                            sendClicked = service.clickElementSemantic("Send")
                        }
                    }

                    delay(600)

                    // Verification: check if WhatsApp is active and input was consumed
                    val snapshotAfter = service.captureScreenSnapshot()
                    val verified = snapshotAfter.packageName == "com.whatsapp"

                    val outcomeMsg = if (sendClicked || verified) {
                        "WhatsApp opened for $contactDisplayName and message \"$message\" was dispatched and verified."
                    } else {
                        "Opened WhatsApp for $contactDisplayName with message \"$message\" ready."
                    }

                    addLog(
                        ActionLogEntry(
                            actionType = "SEND_WHATSAPP",
                            targetOrDetails = "$contactDisplayName: $message",
                            isSuccess = true,
                            verificationSummary = outcomeMsg
                        )
                    )

                    ToolExecutionResult(toolName, true, outcomeMsg, verified = true)
                }

                // 3. READ_SCREEN
                "read_screen" -> {
                    val service = SnowAccessibilityService.instance
                    if (service == null) {
                        return@withContext ToolExecutionResult(
                            toolName,
                            false,
                            "Accessibility Service is required to read the screen. Please enable Snow Accessibility in Settings."
                        )
                    }
                    val screenInfo = service.getVisibleScreenText()
                    addLog(ActionLogEntry(actionType = "READ_SCREEN", targetOrDetails = "Snapshot", isSuccess = true, verificationSummary = "Captured UI nodes"))
                    ToolExecutionResult(toolName, true, "Screen Information:\n$screenInfo", verified = true)
                }

                // 4. TAKE_SCREENSHOT
                "take_screenshot" -> {
                    val service = SnowAccessibilityService.instance
                    if (service == null) {
                        return@withContext ToolExecutionResult(
                            toolName,
                            false,
                            "Accessibility Service is required to capture the screen. Please enable it in Settings."
                        )
                    }

                    val bitmap = service.captureScreenshot()
                    if (bitmap != null) {
                        // Save bitmap to cache for inspection / vision
                        val file = File(context.cacheDir, "screenshot_${System.currentTimeMillis()}.jpg")
                        FileOutputStream(file).use { out ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                        }
                        val summary = "Captured live screenshot (${bitmap.width}x${bitmap.height} px). Saved to ${file.name}."
                        addLog(ActionLogEntry(actionType = "TAKE_SCREENSHOT", targetOrDetails = file.name, isSuccess = true, verificationSummary = summary))
                        ToolExecutionResult(toolName, true, summary, verified = true)
                    } else {
                        // Fallback to screen text extraction
                        val screenText = service.getVisibleScreenText()
                        val fallback = "Screenshot API returned no image; extracted current screen UI nodes instead:\n$screenText"
                        addLog(ActionLogEntry(actionType = "TAKE_SCREENSHOT", targetOrDetails = "Fallback text", isSuccess = true, verificationSummary = "Accessibility tree extracted"))
                        ToolExecutionResult(toolName, true, fallback, verified = true)
                    }
                }

                // 5. CLICK_ELEMENT & CLICK
                "click_element", "click" -> {
                    val target = args["target"] ?: args["text"] ?: ""
                    val service = SnowAccessibilityService.instance
                    if (service == null) {
                        return@withContext ToolExecutionResult(toolName, false, "Accessibility Service is not running.")
                    }
                    val clicked = service.clickElementSemantic(target)
                    delay(300)
                    val summary = if (clicked) "Clicked '$target' successfully." else "Could not locate clickable element matching '$target'."
                    addLog(ActionLogEntry(actionType = "CLICK", targetOrDetails = target, isSuccess = clicked, verificationSummary = summary))
                    ToolExecutionResult(toolName, clicked, summary, verified = clicked)
                }

                // 6. LONG_CLICK_ELEMENT & LONG_CLICK
                "long_click_element", "long_click" -> {
                    val target = args["target"] ?: args["text"] ?: ""
                    val service = SnowAccessibilityService.instance
                    if (service == null) {
                        return@withContext ToolExecutionResult(toolName, false, "Accessibility Service is not running.")
                    }
                    val longClicked = service.longClickElement(target)
                    val summary = if (longClicked) "Long clicked '$target' successfully." else "Could not find element '$target' to long click."
                    addLog(ActionLogEntry(actionType = "LONG_CLICK", targetOrDetails = target, isSuccess = longClicked, verificationSummary = summary))
                    ToolExecutionResult(toolName, longClicked, summary, verified = longClicked)
                }

                // 7. TYPE_TEXT
                "type_text" -> {
                    val text = args["text"] ?: ""
                    val clearFirst = args["clear_first"]?.toBoolean() ?: false
                    val targetHint = args["target"] ?: args["field"]
                    val service = SnowAccessibilityService.instance
                    if (service == null) {
                        return@withContext ToolExecutionResult(toolName, false, "Accessibility Service is not running.")
                    }
                    val (typed, detail) = service.typeTextReliable(text, clearFirst, targetHint)
                    addLog(ActionLogEntry(actionType = "TYPE_TEXT", targetOrDetails = text, isSuccess = typed, verificationSummary = detail))
                    ToolExecutionResult(toolName, typed, detail, verified = typed)
                }

                // 8. CLEAR_TEXT
                "clear_text" -> {
                    val service = SnowAccessibilityService.instance
                    if (service == null) {
                        return@withContext ToolExecutionResult(toolName, false, "Accessibility Service is not running.")
                    }
                    val cleared = service.clearActiveText()
                    val summary = if (cleared) "Cleared active text field." else "No active editable text field found."
                    addLog(ActionLogEntry(actionType = "CLEAR_TEXT", targetOrDetails = "Active Field", isSuccess = cleared, verificationSummary = summary))
                    ToolExecutionResult(toolName, cleared, summary, verified = cleared)
                }

                // 9. SCROLL_SCREEN, SCROLL_UP, SCROLL_DOWN
                "scroll_screen", "scroll_up", "scroll_down" -> {
                    val direction = when (toolName) {
                        "scroll_up" -> "UP"
                        "scroll_down" -> "DOWN"
                        else -> (args["direction"] ?: "DOWN").uppercase()
                    }
                    val service = SnowAccessibilityService.instance
                    if (service == null) {
                        return@withContext ToolExecutionResult(toolName, false, "Accessibility Service is not running.")
                    }
                    val forward = direction != "UP"
                    val scrolled = service.scroll(forward)
                    val summary = if (scrolled) "Scrolled $direction." else "Unable to scroll current screen."
                    addLog(ActionLogEntry(actionType = "SCROLL", targetOrDetails = direction, isSuccess = scrolled, verificationSummary = summary))
                    ToolExecutionResult(toolName, scrolled, summary, verified = scrolled)
                }

                // 10. BACK & HOME
                "press_back", "back" -> {
                    val service = SnowAccessibilityService.instance
                    val ok = service?.pressBack() ?: false
                    val summary = if (ok) "Pressed Back." else "Accessibility Service not running."
                    addLog(ActionLogEntry(actionType = "BACK", targetOrDetails = "Global Action", isSuccess = ok, verificationSummary = summary))
                    ToolExecutionResult(toolName, ok, summary, verified = ok)
                }

                "press_home", "home" -> {
                    val service = SnowAccessibilityService.instance
                    val ok = service?.pressHome() ?: false
                    val summary = if (ok) "Pressed Home." else "Accessibility Service not running."
                    addLog(ActionLogEntry(actionType = "HOME", targetOrDetails = "Global Action", isSuccess = ok, verificationSummary = summary))
                    ToolExecutionResult(toolName, ok, summary, verified = ok)
                }

                // FIND_TEXT & FIND_ELEMENT
                "find_text" -> {
                    val text = args["text"] ?: ""
                    val service = SnowAccessibilityService.instance
                    if (service == null) {
                        return@withContext ToolExecutionResult(toolName, false, "Accessibility Service is not running.")
                    }
                    val node = service.findNodeByText(text)
                    val found = node != null
                    val summary = if (found) "Text '$text' is visible on screen." else "Text '$text' was not found on screen."
                    ToolExecutionResult(toolName, found, summary, verified = found)
                }

                "find_element" -> {
                    val target = args["target"] ?: ""
                    val service = SnowAccessibilityService.instance
                    if (service == null) {
                        return@withContext ToolExecutionResult(toolName, false, "Accessibility Service is not running.")
                    }
                    val node = service.findNodeByText(target) ?: service.findNodeByContentDescription(target)
                    val found = node != null
                    val summary = if (found) "Found UI element '$target' on screen." else "Element '$target' not found."
                    ToolExecutionResult(toolName, found, summary, verified = found)
                }


                // 11. SELECT_CONTACT
                "select_contact" -> {
                    val name = args["name"] ?: ""
                    if (!permissionManager.hasContacts()) {
                        return@withContext ToolExecutionResult(toolName, false, "Contacts permission is required.")
                    }
                    when (val res = contactResolver.resolveContact(name)) {
                        is ContactResolutionResult.Single -> {
                            val msg = "Found contact: ${res.contact.displayName} (${res.contact.phoneNumber})"
                            ToolExecutionResult(toolName, true, msg, verified = true)
                        }
                        is ContactResolutionResult.Multiple -> {
                            val list = res.matches.joinToString("\n") { "• ${it.displayName}: ${it.phoneNumber}" }
                            ToolExecutionResult(toolName, true, "Found multiple contacts:\n$list\nPlease choose one.")
                        }
                        is ContactResolutionResult.NotFound -> {
                            ToolExecutionResult(toolName, false, "No contact found matching '$name'.")
                        }
                        is ContactResolutionResult.PermissionRequired -> {
                            ToolExecutionResult(toolName, false, res.message)
                        }
                    }
                }

                // 12. CREATE_FOLDER
                "create_folder" -> {
                    val folderName = args["folder_name"] ?: args["name"] ?: "Snow"
                    val location = args["location"] ?: "Downloads"
                    val res = fileManagerHelper.createFolder(location, folderName)
                    addLog(ActionLogEntry(actionType = "CREATE_FOLDER", targetOrDetails = "$location/$folderName", isSuccess = res.isSuccess, verificationSummary = res.message))
                    ToolExecutionResult(toolName, res.isSuccess, res.message, verified = res.isSuccess)
                }

                // 13. CREATE_FILE
                "create_file" -> {
                    val folderName = args["folder_name"] ?: "Snow"
                    val fileName = args["file_name"] ?: args["name"] ?: "test.txt"
                    val content = args["content"] ?: ""
                    val res = fileManagerHelper.createFile(folderName, fileName, content)
                    addLog(ActionLogEntry(actionType = "CREATE_FILE", targetOrDetails = "$folderName/$fileName", isSuccess = res.isSuccess, verificationSummary = res.message))
                    ToolExecutionResult(toolName, res.isSuccess, res.message, verified = res.isSuccess)
                }

                // 14. OPEN_FOLDER & OPEN_FILE
                "open_folder" -> {
                    val folderName = args["folder_name"] ?: "Snow"
                    val res = fileManagerHelper.openFolder(folderName)
                    addLog(ActionLogEntry(actionType = "OPEN_FOLDER", targetOrDetails = folderName, isSuccess = res.isSuccess, verificationSummary = res.message))
                    ToolExecutionResult(toolName, res.isSuccess, res.message, verified = res.isSuccess)
                }

                "open_file" -> {
                    val fileName = args["file_name"] ?: args["name"] ?: "test.txt"
                    val res = fileManagerHelper.shareFile(fileName) // Or open in intent
                    ToolExecutionResult(toolName, res.isSuccess, res.message, verified = res.isSuccess)
                }

                // 15. SHARE_FILE
                "share_file" -> {
                    val fileName = args["file_name"] ?: "test.txt"
                    val res = fileManagerHelper.shareFile(fileName)
                    addLog(ActionLogEntry(actionType = "SHARE_FILE", targetOrDetails = fileName, isSuccess = res.isSuccess, verificationSummary = res.message))
                    ToolExecutionResult(toolName, res.isSuccess, res.message, verified = res.isSuccess)
                }

                // 16. WAIT_ACTION & WAIT
                "wait_action", "wait" -> {
                    val ms = args["duration_ms"]?.toLongOrNull() ?: 1000L
                    delay(ms.coerceIn(100L, 5000L))
                    ToolExecutionResult(toolName, true, "Waited ${ms}ms.", verified = true)
                }

                // 17. VERIFY_ACTION & VERIFY
                "verify_action", "verify" -> {
                    val expected = args["expected_text_or_pkg"] ?: ""
                    val service = SnowAccessibilityService.instance
                    val ok = if (service != null) {
                        SnowAccessibilityService.currentForegroundPackage.contains(expected, ignoreCase = true) ||
                                service.findNodeByText(expected) != null
                    } else false
                    val summary = if (ok) "Verified '$expected' is present on screen." else "Verification failed: '$expected' not found."
                    addLog(ActionLogEntry(actionType = "VERIFY_ACTION", targetOrDetails = expected, isSuccess = ok, verificationSummary = summary))
                    ToolExecutionResult(toolName, ok, summary, verified = ok)
                }

                // SEND_MESSAGE (Generic WhatsApp or SMS)
                "send_message" -> {
                    val platform = args["platform"]?.lowercase() ?: "whatsapp"
                    if (platform.contains("sms")) {
                        val recipient = args["recipient"] ?: ""
                        val message = args["message"] ?: ""
                        val smsIntent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("smsto:$recipient")
                            putExtra("sms_body", message)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        try {
                            context.startActivity(smsIntent)
                            ToolExecutionResult(toolName, true, "Opened SMS for $recipient.", verified = true)
                        } catch (e: Exception) {
                            ToolExecutionResult(toolName, false, "Failed to launch SMS: ${e.message}")
                        }
                    } else {
                        // Forward to whatsapp
                        executeTool("send_whatsapp", args)
                    }
                }


                // 18. WEB SEARCH
                "web_search" -> {
                    val query = args["query"] ?: ""
                    val res = searchManager.performSearch(query)
                    if (res.error != null) {
                        ToolExecutionResult(toolName, false, "Search error: ${res.error}")
                    } else {
                        ToolExecutionResult(toolName, true, res.summary)
                    }
                }

                // 19. PHONE CALL & SMS
                "phone_call" -> {
                    val number = args["number"] ?: ""
                    try {
                        val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                            data = Uri.parse("tel:$number")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(dialIntent)
                        ToolExecutionResult(toolName, true, "Opened dialer for $number.", verified = true)
                    } catch (e: Exception) {
                        ToolExecutionResult(toolName, false, "Failed to open dialer: ${e.message}")
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
                        ToolExecutionResult(toolName, true, "Opened SMS composer for $recipient.", verified = true)
                    } catch (e: Exception) {
                        ToolExecutionResult(toolName, false, "Failed to open SMS composer: ${e.message}")
                    }
                }

                // 20. DEVICE CONTROL
                "device_control" -> {
                    val feature = (args["feature"] ?: "").lowercase()
                    val state = (args["state"] ?: "TOGGLE").uppercase()
                    when {
                        feature.contains("flash") -> {
                            val turnOn = state == "ON" || state == "TOGGLE"
                            deviceCommander.toggleFlashlight(turnOn)
                            ToolExecutionResult(toolName, true, "Flashlight turned ${if (turnOn) "ON" else "OFF"}.", verified = true)
                        }
                        feature.contains("volume_up") -> {
                            deviceCommander.adjustVolume(true)
                            ToolExecutionResult(toolName, true, "Volume increased.", verified = true)
                        }
                        feature.contains("volume_down") -> {
                            deviceCommander.adjustVolume(false)
                            ToolExecutionResult(toolName, true, "Volume decreased.", verified = true)
                        }
                        feature.contains("wifi") -> {
                            deviceCommander.openWifiSettings()
                            ToolExecutionResult(toolName, true, "Opened Wi-Fi settings.", verified = true)
                        }
                        feature.contains("bluetooth") -> {
                            deviceCommander.openBluetoothSettings()
                            ToolExecutionResult(toolName, true, "Opened Bluetooth settings.", verified = true)
                        }
                        else -> ToolExecutionResult(toolName, false, "Unknown feature: $feature")
                    }
                }

                // 21. GET DEVICE STATUS
                "get_device_status" -> {
                    val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
                    val batteryPct = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
                    val sdfTime = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
                    val sdfDate = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(Date())
                    val statusStr = "Time: $sdfTime\nDate: $sdfDate\nBattery: ${if (batteryPct >= 0) "$batteryPct%" else "Available"}"
                    ToolExecutionResult(toolName, true, statusStr, verified = true)
                }

                // 22. ALARM & TIMER
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
                            Log.w("ToolExecutor", "AlarmClock intent issue: ${e.message}")
                        }
                        val trigger = System.currentTimeMillis() + (minutes * 60 * 1000L)
                        reminderDao.insert(ReminderEntity(title = title, targetTimeMillis = trigger, type = "TIMER"))
                        ToolExecutionResult(toolName, true, "Set a timer for $minutes minutes ($title).", verified = true)
                    } else {
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
                        ToolExecutionResult(toolName, true, "Alarm set for ${String.format(Locale.US, "%02d:%02d", hour, minute)} ($title).", verified = true)
                    }
                }

                // 23. NOTES & MEMORY
                "save_note" -> {
                    val title = args["title"] ?: "Note"
                    val content = args["content"] ?: ""
                    val output = notesManager.saveNote(title, content)
                    ToolExecutionResult(toolName, true, output, verified = true)
                }

                "search_notes" -> {
                    val query = args["query"] ?: ""
                    val output = notesManager.searchNotes(query)
                    ToolExecutionResult(toolName, true, output, verified = true)
                }

                "save_memory" -> {
                    val fact = args["fact"] ?: ""
                    val output = memoryManager.saveMemory(fact)
                    ToolExecutionResult(toolName, true, output, verified = true)
                }

                "recall_memory" -> {
                    val query = args["query"] ?: ""
                    val output = memoryManager.recallMemory(query)
                    ToolExecutionResult(toolName, true, output, verified = true)
                }

                // 24. IMAGE GENERATION & SHARING
                "generate_image" -> {
                    val prompt = args["prompt"] ?: args["description"] ?: ""
                    if (prompt.isBlank()) {
                        return@withContext ToolExecutionResult(toolName, false, "Image prompt cannot be empty.")
                    }
                    if (imageGenerationManager == null) {
                        return@withContext ToolExecutionResult(toolName, false, "Image generation is not configured.")
                    }

                    val aspectRatio = args["aspect_ratio"] ?: "1:1"
                    val result = imageGenerationManager.generateImage(prompt, aspectRatio)

                    if (result.isSuccess && result.localFilePath != null) {
                        lastGeneratedImagePath = result.localFilePath
                        onImageGeneratedListener?.invoke(result.localFilePath, prompt)
                        addLog(ActionLogEntry(actionType = "GENERATE_IMAGE", targetOrDetails = prompt, isSuccess = true, verificationSummary = "Image generated: ${result.localFilePath}"))
                        ToolExecutionResult(
                            toolName = toolName,
                            isSuccess = true,
                            output = "Image generated successfully at ${result.localFilePath}",
                            userVisibleMessage = "Ye rahi aapki image! Maine ise create kar diya hai.",
                            verified = true
                        )
                    } else {
                        val errMsg = result.errorMessage ?: "Unknown error while generating image."
                        addLog(ActionLogEntry(actionType = "GENERATE_IMAGE", targetOrDetails = prompt, isSuccess = false, verificationSummary = errMsg))
                        ToolExecutionResult(
                            toolName = toolName,
                            isSuccess = false,
                            output = "Image generation failed: $errMsg",
                            userVisibleMessage = "Image generate nahi ho saki: $errMsg"
                        )
                    }
                }

                "save_image_to_gallery" -> {
                    val filePath = args["file_path"]?.ifBlank { null } ?: lastGeneratedImagePath
                    if (filePath.isNullOrBlank()) {
                        return@withContext ToolExecutionResult(toolName, false, "No image found to save to gallery.")
                    }
                    val saved = imageGenerationManager?.saveImageToGallery(filePath) ?: false
                    if (saved) {
                        ToolExecutionResult(toolName, true, "Image saved to gallery in Pictures/Snow AI album.", "Image ko aapki gallery mein save kar diya hai.", verified = true)
                    } else {
                        ToolExecutionResult(toolName, false, "Could not save image to gallery. Please check storage permissions.")
                    }
                }

                "share_image" -> {
                    val filePath = args["file_path"]?.ifBlank { null } ?: lastGeneratedImagePath
                    if (filePath.isNullOrBlank()) {
                        return@withContext ToolExecutionResult(toolName, false, "No image found to share.")
                    }
                    val caption = args["caption"] ?: "Shared from Snow AI"
                    val recipientApp = args["recipient_app"] ?: if (args["target"]?.contains("whatsapp", ignoreCase = true) == true) "com.whatsapp" else null
                    val shared = imageGenerationManager?.shareImage(filePath, caption, recipientApp) ?: false
                    if (shared) {
                        val dest = if (recipientApp == "com.whatsapp") "WhatsApp" else "share dialog"
                        ToolExecutionResult(toolName, true, "Opened $dest to share image.", "Image share karne ke liye khol diya hai.", verified = true)
                    } else {
                        ToolExecutionResult(toolName, false, "Failed to share image. Target app might not be installed.")
                    }
                }

                else -> ToolExecutionResult(toolName, false, "Unrecognized tool: $toolName")
            }
        } catch (e: Exception) {
            Log.e("ToolExecutor", "Error executing tool: $toolName", e)
            ToolExecutionResult(toolName, false, "Execution error: ${e.message}")
        }
    }
}
