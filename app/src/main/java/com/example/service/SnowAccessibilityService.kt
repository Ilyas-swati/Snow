package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

data class UIElement(
    val text: String?,
    val contentDescription: String?,
    val viewId: String?,
    val className: String?,
    val isClickable: Boolean,
    val isEditable: Boolean,
    val isScrollable: Boolean,
    val bounds: Rect
)

data class ScreenSnapshot(
    val packageName: String,
    val elements: List<UIElement>,
    val readableTexts: List<String>,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toFormattedString(): String = buildString {
        appendLine("Foreground App: $packageName")
        val editable = elements.filter { it.isEditable }
        if (editable.isNotEmpty()) {
            appendLine("Editable Fields:")
            editable.forEach { el ->
                appendLine(" - \"${el.text ?: el.contentDescription ?: "Input"}\" (id: ${el.viewId ?: "none"})")
            }
        }
        val buttons = elements.filter { it.isClickable && !it.isEditable }
        if (buttons.isNotEmpty()) {
            appendLine("Clickable Elements:")
            buttons.take(15).forEach { el ->
                val label = el.text ?: el.contentDescription ?: el.className?.substringAfterLast('.') ?: "Element"
                appendLine(" - \"$label\" (id: ${el.viewId ?: "none"})")
            }
        }
        if (readableTexts.isNotEmpty()) {
            appendLine("Screen Text:")
            readableTexts.take(20).forEach { appendLine(" • $it") }
        }
    }
}

class SnowAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "SnowAccessibility"

        @Volatile
        var instance: SnowAccessibilityService? = null
            private set

        val isServiceRunning: Boolean
            get() = instance != null

        // Real-time telemetry for diagnostics
        @Volatile
        var currentForegroundPackage: String = "Unknown"
            private set

        @Volatile
        var lastEventTimestamp: Long = 0L
            private set

        @Volatile
        var lastEventTypeString: String = "None"
            private set

        val eventLogs = ConcurrentLinkedQueue<String>()

        fun addLog(msg: String) {
            Log.d(TAG, msg)
            eventLogs.add("[${System.currentTimeMillis() % 100000}] $msg")
            while (eventLogs.size > 50) {
                eventLogs.poll()
            }
        }

        private var pendingWhatsAppRecipient: String? = null
        private var pendingWhatsAppMessage: String? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        addLog("Accessibility service connected and operational.")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        addLog("Accessibility service destroyed.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return
        currentForegroundPackage = pkg
        lastEventTimestamp = System.currentTimeMillis()
        lastEventTypeString = AccessibilityEvent.eventTypeToString(event.eventType)

        // Handle auto-send queue if WhatsApp event arrives
        if (pkg == "com.whatsapp" && pendingWhatsAppMessage != null) {
            handlePendingWhatsAppAutomatedFlow()
        }
    }

    override fun onInterrupt() {
        addLog("Accessibility service interrupted.")
    }

    // -------------------------------------------------------------
    // WHATSAPP AUTOMATION QUEUE
    // -------------------------------------------------------------
    fun queueWhatsAppMessage(recipient: String, message: String) {
        pendingWhatsAppRecipient = recipient
        pendingWhatsAppMessage = message
        addLog("Queued WhatsApp message for '$recipient': '$message'")
    }

    fun queueMessageForTyping(recipient: String, message: String) {
        queueWhatsAppMessage(recipient, message)
    }

    private fun handlePendingWhatsAppAutomatedFlow() {
        val message = pendingWhatsAppMessage ?: return
        val root = rootInActiveWindow ?: return

        // Look for input field (id "entry" or EditText)
        val inputNodes = root.findAccessibilityNodeInfosByViewId("com.whatsapp:id/entry")
        val targetInput = if (inputNodes.isNotEmpty()) {
            inputNodes[0]
        } else {
            findEditableNode(root)
        }

        if (targetInput != null) {
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, message)
            }
            val setSuccess = targetInput.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            if (setSuccess) {
                pendingWhatsAppMessage = null
                addLog("Typed message into WhatsApp input.")

                // Send button (id "send" or desc "Send")
                val sendNodes = root.findAccessibilityNodeInfosByViewId("com.whatsapp:id/send")
                if (sendNodes.isNotEmpty()) {
                    sendNodes[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    addLog("Clicked WhatsApp send button via ID.")
                } else {
                    clickElementSemantic("Send")
                }
            }
        }
    }

    // -------------------------------------------------------------
    // SCREEN UNDERSTANDING (ScreenObserver integration)
    // -------------------------------------------------------------
    fun captureScreenState(): ScreenState {
        return ScreenObserver.captureScreenState(
            rootNode = rootInActiveWindow,
            currentPackage = currentForegroundPackage
        )
    }

    fun captureScreenSnapshot(): ScreenSnapshot {
        val state = captureScreenState()
        val elements = state.allNodes.map {
            UIElement(
                text = it.text,
                contentDescription = it.contentDescription,
                viewId = it.viewId,
                className = it.className,
                isClickable = it.isClickable,
                isEditable = it.isEditable,
                isScrollable = it.isScrollable,
                bounds = it.bounds
            )
        }
        return ScreenSnapshot(
            packageName = currentForegroundPackage,
            elements = elements,
            readableTexts = state.visibleTexts
        )
    }

    fun getVisibleScreenText(): String {
        return captureScreenState().toCompactRepresentation()
    }

    // -------------------------------------------------------------
    // NODE SEARCH & INTERACTION (RELIABLE SEMANTIC APIS)
    // -------------------------------------------------------------
    fun findEditableNode(node: AccessibilityNodeInfo? = rootInActiveWindow): AccessibilityNodeInfo? {
        return ScreenObserver.findEditableNode(node)
    }

    fun findFocusedEditableNode(node: AccessibilityNodeInfo? = rootInActiveWindow): AccessibilityNodeInfo? {
        return ScreenObserver.findFocusedEditableNode(node)
    }

    fun findTextField(node: AccessibilityNodeInfo? = rootInActiveWindow, hintOrLabel: String? = null): AccessibilityNodeInfo? {
        return ScreenObserver.findTextField(node, hintOrLabel)
    }

    fun findNodeByText(text: String, node: AccessibilityNodeInfo? = rootInActiveWindow): AccessibilityNodeInfo? {
        return ScreenObserver.findNodeByText(node, text)
    }

    fun findNodeByContentDescription(desc: String, node: AccessibilityNodeInfo? = rootInActiveWindow): AccessibilityNodeInfo? {
        return ScreenObserver.findNodeByContentDescription(node, desc)
    }

    fun findClickableParent(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        return ScreenObserver.findClickableParent(node)
    }

    fun findScrollableNode(node: AccessibilityNodeInfo? = rootInActiveWindow): AccessibilityNodeInfo? {
        return ScreenObserver.findScrollableNode(node)
    }

    fun focusTextField(node: AccessibilityNodeInfo): Boolean {
        return ScreenObserver.focusTextField(node)
    }

    fun clearText(node: AccessibilityNodeInfo): Boolean {
        return ScreenObserver.clearText(node)
    }

    fun setText(node: AccessibilityNodeInfo, text: String): Boolean {
        return ScreenObserver.setText(node, text)
    }

    fun verifyText(node: AccessibilityNodeInfo, expectedText: String): Boolean {
        return ScreenObserver.verifyText(node, expectedText)
    }

    /**
     * Reliable text typing with verification and retry mechanisms.
     */
    suspend fun typeTextReliable(
        text: String,
        clearFirst: Boolean = false,
        fieldHint: String? = null
    ): Pair<Boolean, String> {
        val root = rootInActiveWindow ?: return Pair(false, "No active window found.")
        val targetNode = findTextField(root, fieldHint) ?: return Pair(false, "No editable input field found on screen.")

        try {
            focusTextField(targetNode)
            if (clearFirst) {
                clearText(targetNode)
                delay(100)
            }

            // Attempt 1: Direct ACTION_SET_TEXT
            var success = setText(targetNode, text)
            delay(150)

            // Verification check: re-query active window to confirm text was actually set
            val updatedRoot = rootInActiveWindow
            val updatedField = findTextField(updatedRoot, fieldHint)
            var isVerified = updatedField != null && verifyText(updatedField, text)

            if (!isVerified) {
                // Attempt 2: Click field coordinates first to open keyboard / gain focus, then set text
                val rect = Rect()
                targetNode.getBoundsInScreen(rect)
                if (rect.width() > 0 && rect.height() > 0) {
                    clickCoordinates(rect.centerX().toFloat(), rect.centerY().toFloat())
                    delay(300)
                    val retryRoot = rootInActiveWindow
                    val retryField = findTextField(retryRoot, fieldHint)
                    if (retryField != null) {
                        setText(retryField, text)
                        delay(150)
                        val finalRoot = rootInActiveWindow
                        val finalField = findTextField(finalRoot, fieldHint)
                        isVerified = finalField != null && verifyText(finalField, text)
                    }
                }
            }

            return if (isVerified) {
                addLog("Verified text input successfully into field: \"$text\"")
                Pair(true, "Successfully typed and verified text \"$text\".")
            } else if (success) {
                addLog("Typed text via ACTION_SET_TEXT, but could not visually verify.")
                Pair(true, "Typed text \"$text\" into field.")
            } else {
                Pair(false, "Failed to set text into input field.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error typing text", e)
            return Pair(false, "Error typing text: ${e.localizedMessage ?: e.message}")
        }
    }

    fun clickElementSemantic(targetQuery: String): Boolean {
        val root = rootInActiveWindow ?: return false

        // 1. Exact or partial semantic search by text
        val textMatch = findNodeByText(targetQuery, root)
        if (textMatch != null) {
            val clickable = findClickableParent(textMatch)
            if (clickable != null && clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                addLog("Clicked semantic match for '$targetQuery'")
                return true
            }
        }

        // 2. Semantic search by content description
        val descMatch = findNodeByContentDescription(targetQuery, root)
        if (descMatch != null) {
            val clickable = findClickableParent(descMatch)
            if (clickable != null && clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                addLog("Clicked content description match for '$targetQuery'")
                return true
            }
        }

        // 3. Search by View Resource ID
        val idNodes = root.findAccessibilityNodeInfosByViewId(targetQuery)
        if (idNodes.isNotEmpty()) {
            val clickable = findClickableParent(idNodes[0])
            if (clickable != null && clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                addLog("Clicked viewId match for '$targetQuery'")
                return true
            }
        }

        // 4. Coordinates tap fallback (only if semantic node was found but not clickable)
        val fallbackNode = textMatch ?: descMatch
        if (fallbackNode != null) {
            val rect = Rect()
            fallbackNode.getBoundsInScreen(rect)
            if (rect.width() > 0 && rect.height() > 0) {
                addLog("Tapping center coordinates of '$targetQuery' at (${rect.centerX()}, ${rect.centerY()})")
                return clickCoordinates(rect.centerX().toFloat(), rect.centerY().toFloat())
            }
        }

        return false
    }

    fun longClickElement(targetQuery: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val node = findNodeByText(targetQuery, root) ?: return false
        if (node.isLongClickable && node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)) {
            addLog("Long-clicked node '$targetQuery'")
            return true
        }
        val rect = Rect()
        node.getBoundsInScreen(rect)
        return longClickCoordinates(rect.centerX().toFloat(), rect.centerY().toFloat())
    }

    fun typeText(text: String, clearFirst: Boolean = false): Boolean {
        val root = rootInActiveWindow ?: return false
        val inputNode = findEditableNode(root) ?: return false

        inputNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)

        if (clearFirst) {
            val clearArgs = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
            }
            inputNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, clearArgs)
        }

        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val success = inputNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        if (success) {
            addLog("Successfully typed into editable node: \"$text\"")
        }
        return success
    }

    fun clearActiveText(): Boolean {
        val root = rootInActiveWindow ?: return false
        val inputNode = findEditableNode(root) ?: return false
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
        }
        return inputNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    fun scroll(forward: Boolean): Boolean {
        val root = rootInActiveWindow ?: return false
        val action = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        // Search for scrollable node
        val scrollableNode = findScrollableNode(root) ?: root
        val ok = scrollableNode.performAction(action)
        if (ok) {
            addLog("Scrolled ${if (forward) "down/forward" else "up/backward"}")
            return true
        }

        // Gesture swipe fallback
        val displayMetrics = resources.displayMetrics
        val width = displayMetrics.widthPixels.toFloat()
        val height = displayMetrics.heightPixels.toFloat()
        val startY = if (forward) height * 0.7f else height * 0.3f
        val endY = if (forward) height * 0.3f else height * 0.7f
        return swipe(width * 0.5f, startY, width * 0.5f, endY, 300)
    }

    fun pressBack(): Boolean {
        addLog("Performing Global Action: BACK")
        return performGlobalAction(GLOBAL_ACTION_BACK)
    }

    fun pressHome(): Boolean {
        addLog("Performing Global Action: HOME")
        return performGlobalAction(GLOBAL_ACTION_HOME)
    }

    fun pressRecents(): Boolean {
        addLog("Performing Global Action: RECENTS")
        return performGlobalAction(GLOBAL_ACTION_RECENTS)
    }

    fun openNotifications(): Boolean {
        addLog("Performing Global Action: NOTIFICATIONS")
        return performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
    }

    // -------------------------------------------------------------
    // GESTURE FALLBACKS (Android 7.0+ / API 24+)
    // -------------------------------------------------------------
    fun clickCoordinates(x: Float, y: Float): Boolean {
        val path = Path().apply {
            moveTo(x, y)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 80)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        addLog("Dispatching touch gesture tap at ($x, $y)")
        return dispatchGesture(gesture, null, null)
    }

    fun longClickCoordinates(x: Float, y: Float): Boolean {
        val path = Path().apply {
            moveTo(x, y)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 600)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        addLog("Dispatching long click touch gesture at ($x, $y)")
        return dispatchGesture(gesture, null, null)
    }

    fun swipe(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long = 300): Boolean {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        addLog("Dispatching swipe from ($startX, $startY) to ($endX, $endY)")
        return dispatchGesture(gesture, null, null)
    }

    // -------------------------------------------------------------
    // SCREENSHOT CAPTURE (Android 11+ / API 30+ Accessibility TakeScreenshot)
    // -------------------------------------------------------------
    suspend fun captureScreenshot(): Bitmap? = withContext(Dispatchers.Main) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val latch = CountDownLatch(1)
            var capturedBitmap: Bitmap? = null

            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshotResult: ScreenshotResult) {
                        try {
                            val hardwareBuffer = screenshotResult.hardwareBuffer
                            val colorSpace = screenshotResult.colorSpace
                            val bitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace)
                            capturedBitmap = bitmap?.copy(Bitmap.Config.ARGB_8888, false)
                            hardwareBuffer.close()
                            addLog("Successfully captured screen via AccessibilityService.takeScreenshot")
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to copy hardware buffer", e)
                        } finally {
                            latch.countDown()
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        addLog("Accessibility takeScreenshot failed with code: $errorCode")
                        latch.countDown()
                    }
                }
            )

            withContext(Dispatchers.IO) {
                latch.await(3, TimeUnit.SECONDS)
            }
            capturedBitmap
        } else {
            addLog("Accessibility takeScreenshot requires Android 11+ (API 30+)")
            null
        }
    }

    // -------------------------------------------------------------
    // UI CHANGE POLLING & WAITING HELPERS
    // -------------------------------------------------------------
    suspend fun waitForPackage(expectedPkg: String, timeoutMs: Long = 3000): Boolean {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            if (currentForegroundPackage == expectedPkg) return true
            delay(150)
        }
        return currentForegroundPackage == expectedPkg
    }

    suspend fun waitForNodeWithText(query: String, timeoutMs: Long = 3000): Boolean {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            val root = rootInActiveWindow
            if (root != null && findNodeByText(query, root) != null) return true
            delay(150)
        }
        return false
    }
}
