package com.example.service

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class SnowAccessibilityService : AccessibilityService() {

    companion object {
        var instance: SnowAccessibilityService? = null
            private set

        val isServiceRunning: Boolean
            get() = instance != null

        private var pendingRecipient: String? = null
        private var pendingMessage: String? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d("SnowAccessibility", "Service connected")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    fun queueMessageForTyping(recipient: String, message: String) {
        pendingRecipient = recipient
        pendingMessage = message
        Log.d("SnowAccessibility", "Queued message for $recipient: $message")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val packageName = event.packageName?.toString() ?: return

        // Handle automated typing and sending in WhatsApp
        if (packageName == "com.whatsapp" && pendingMessage != null) {
            val rootNode = rootInActiveWindow ?: return
            val messageToType = pendingMessage ?: return

            // Search for EditText to type message
            val inputNodes = rootNode.findAccessibilityNodeInfosByViewId("com.whatsapp:id/entry")
            if (inputNodes.isNotEmpty()) {
                val inputNode = inputNodes[0]
                val arguments = Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, messageToType)
                }
                val setSuccess = inputNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                if (setSuccess) {
                    pendingMessage = null // Consumed
                    Log.d("SnowAccessibility", "Message typed into WhatsApp input")

                    // Click send button
                    val sendButtons = rootNode.findAccessibilityNodeInfosByViewId("com.whatsapp:id/send")
                    if (sendButtons.isNotEmpty()) {
                        sendButtons[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        Log.d("SnowAccessibility", "WhatsApp send button clicked")
                    } else {
                        // Search by content description "Send"
                        clickNodeWithText(rootNode, "Send")
                    }
                }
            } else {
                // Fallback search by class name
                findAndFillEditText(rootNode, messageToType)
            }
        }
    }

    private fun findAndFillEditText(node: AccessibilityNodeInfo, text: String): Boolean {
        if (node.className == "android.widget.EditText") {
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
                pendingMessage = null
                return true
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (findAndFillEditText(child, text)) return true
        }
        return false
    }

    private fun clickNodeWithText(node: AccessibilityNodeInfo, text: String): Boolean {
        if (node.contentDescription?.toString()?.contains(text, ignoreCase = true) == true ||
            node.text?.toString()?.contains(text, ignoreCase = true) == true
        ) {
            if (node.isClickable) {
                return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (clickNodeWithText(child, text)) return true
        }
        return false
    }

    override fun onInterrupt() {
        Log.w("SnowAccessibility", "Accessibility service interrupted")
    }
}
