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
            findFirstEditableNode(root)
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
                    clickNodeWithTextOrDesc(root, "Send")
                }
            }
        }
    }

    // -------------------------------------------------------------
    // SCREEN UNDERSTANDING (LEVEL A - ACCESSIBILITY TREE)
    // -------------------------------------------------------------
    fun captureScreenSnapshot(): ScreenSnapshot {
        val root = rootInActiveWindow
        val elements = mutableListOf<UIElement>()
        val texts = mutableListOf<String>()

        if (root != null) {
            traverseNodeHierarchy(root, elements, texts)
        }

        return ScreenSnapshot(
            packageName = currentForegroundPackage,
            elements = elements,
            readableTexts = texts.distinct()
        )
    }

    private fun traverseNodeHierarchy(
        node: AccessibilityNodeInfo,
        elements: MutableList<UIElement>,
        texts: MutableList<String>
    ) {
        val textStr = node.text?.toString()?.trim()
        val descStr = node.contentDescription?.toString()?.trim()
        val bounds = Rect()
        node.getBoundsInScreen(bounds)

        if (!textStr.isNullOrBlank()) {
            texts.add(textStr)
        } else if (!descStr.isNullOrBlank()) {
            texts.add(descStr)
        }

        if (node.isClickable || node.isEditable || node.isScrollable || !textStr.isNullOrBlank() || !descStr.isNullOrBlank()) {
            elements.add(
                UIElement(
                    text = textStr,
                    contentDescription = descStr,
                    viewId = node.viewIdResourceName,
                    className = node.className?.toString(),
                    isClickable = node.isClickable,
                    isEditable = node.isEditable,
                    isScrollable = node.isScrollable,
                    bounds = bounds
                )
            )
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            traverseNodeHierarchy(child, elements, texts)
        }
    }

    fun getVisibleScreenText(): String {
        val snapshot = captureScreenSnapshot()
        return snapshot.toFormattedString()
    }

    // -------------------------------------------------------------
    // NODE SEARCH & INTERACTION
    // -------------------------------------------------------------
    fun findFirstEditableNode(node: AccessibilityNodeInfo? = rootInActiveWindow): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isEditable || node.className == "android.widget.EditText") return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findFirstEditableNode(child)
            if (found != null) return found
        }
        return null
    }

    fun findNodeByText(text: String, node: AccessibilityNodeInfo? = rootInActiveWindow): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.text?.toString()?.contains(text, ignoreCase = true) == true ||
            node.contentDescription?.toString()?.contains(text, ignoreCase = true) == true
        ) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findNodeByText(text, child)
            if (found != null) return found
        }
        return null
    }

    fun findNodeByViewId(viewId: String): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
        return nodes.firstOrNull()
    }

    fun clickNodeWithTextOrDesc(node: AccessibilityNodeInfo? = rootInActiveWindow, query: String): Boolean {
        if (node == null) return false
        val matches = (node.text?.toString()?.contains(query, ignoreCase = true) == true) ||
                (node.contentDescription?.toString()?.contains(query, ignoreCase = true) == true)

        if (matches) {
            var clickableNode: AccessibilityNodeInfo? = node
            while (clickableNode != null && !clickableNode.isClickable) {
                clickableNode = clickableNode.parent
            }
            if (clickableNode != null && clickableNode.isClickable) {
                val ok = clickableNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (ok) {
                    addLog("Clicked node matching '$query'")
                    return true
                }
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (clickNodeWithTextOrDesc(child, query)) return true
        }
        return false
    }

    fun clickElement(targetQuery: String): Boolean {
        val root = rootInActiveWindow ?: return false
        // 1. Try direct text / contentDescription click
        if (clickNodeWithTextOrDesc(root, targetQuery)) {
            return true
        }

        // 2. Try by viewId
        val idNodes = root.findAccessibilityNodeInfosByViewId(targetQuery)
        if (idNodes.isNotEmpty()) {
            val n = idNodes[0]
            if (n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                addLog("Clicked node by viewId '$targetQuery'")
                return true
            }
        }

        // 3. Fallback: find node and click at center coordinates using GestureDescription
        val node = findNodeByText(targetQuery, root)
        if (node != null) {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            if (rect.width() > 0 && rect.height() > 0) {
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
        val inputNode = findFirstEditableNode(root) ?: return false

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
        val inputNode = findFirstEditableNode(root) ?: return false
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

    private fun findScrollableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findScrollableNode(child)
            if (found != null) return found
        }
        return null
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
