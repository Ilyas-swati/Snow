package com.example.service

import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

data class ScreenNode(
    val text: String?,
    val contentDescription: String?,
    val viewId: String?,
    val className: String?,
    val isClickable: Boolean,
    val isEditable: Boolean,
    val isScrollable: Boolean,
    val bounds: Rect,
    val depth: Int = 0
) {
    val bestIdentifier: String
        get() = text?.takeIf { it.isNotBlank() }
            ?: contentDescription?.takeIf { it.isNotBlank() }
            ?: viewId?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: className?.substringAfterLast('.') ?: "Element"
}

data class ScreenState(
    val packageName: String,
    val windowTitle: String? = null,
    val visibleTexts: List<String> = emptyList(),
    val contentDescriptions: List<String> = emptyList(),
    val clickableNodes: List<ScreenNode> = emptyList(),
    val editableNodes: List<ScreenNode> = emptyList(),
    val scrollableNodes: List<ScreenNode> = emptyList(),
    val allNodes: List<ScreenNode> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * Compact representation designed specifically for AI prompt context,
     * avoiding token flooding while retaining essential interactive elements.
     */
    fun toCompactRepresentation(): String = buildString {
        appendLine("FOREGROUND APP: $packageName")
        if (!windowTitle.isNullOrBlank()) {
            appendLine("WINDOW TITLE: $windowTitle")
        }

        if (editableNodes.isNotEmpty()) {
            appendLine("EDITABLE INPUT FIELDS (${editableNodes.size}):")
            editableNodes.take(5).forEach { node ->
                val idStr = node.viewId?.substringAfterLast('/') ?: "no-id"
                val currVal = if (!node.text.isNullOrBlank()) " [current text: \"${node.text}\"]" else " [empty]"
                appendLine(" • \"${node.bestIdentifier}\" (id: $idStr)$currVal")
            }
        }

        if (clickableNodes.isNotEmpty()) {
            appendLine("INTERACTIVE ACTIONS / BUTTONS:")
            clickableNodes.take(15).forEach { node ->
                val idStr = node.viewId?.substringAfterLast('/') ?: "no-id"
                appendLine(" • \"${node.bestIdentifier}\" (id: $idStr)")
            }
        }

        if (visibleTexts.isNotEmpty()) {
            appendLine("KEY VISIBLE SCREEN CONTENT:")
            visibleTexts.take(20).forEach { text ->
                appendLine(" • $text")
            }
        }
    }
}

class ScreenObserver {

    companion object {
        private const val TAG = "ScreenObserver"

        fun captureScreenState(
            rootNode: AccessibilityNodeInfo?,
            currentPackage: String,
            windowTitle: String? = null
        ): ScreenState {
            if (rootNode == null) {
                return ScreenState(packageName = currentPackage, windowTitle = windowTitle)
            }

            val visibleTexts = mutableListOf<String>()
            val contentDescriptions = mutableListOf<String>()
            val clickableNodes = mutableListOf<ScreenNode>()
            val editableNodes = mutableListOf<ScreenNode>()
            val scrollableNodes = mutableListOf<ScreenNode>()
            val allNodes = mutableListOf<ScreenNode>()

            traverseNode(
                node = rootNode,
                depth = 0,
                visibleTexts = visibleTexts,
                contentDescriptions = contentDescriptions,
                clickableNodes = clickableNodes,
                editableNodes = editableNodes,
                scrollableNodes = scrollableNodes,
                allNodes = allNodes
            )

            return ScreenState(
                packageName = currentPackage,
                windowTitle = windowTitle,
                visibleTexts = visibleTexts.distinct(),
                contentDescriptions = contentDescriptions.distinct(),
                clickableNodes = clickableNodes,
                editableNodes = editableNodes,
                scrollableNodes = scrollableNodes,
                allNodes = allNodes
            )
        }

        private fun traverseNode(
            node: AccessibilityNodeInfo,
            depth: Int,
            visibleTexts: MutableList<String>,
            contentDescriptions: MutableList<String>,
            clickableNodes: MutableList<ScreenNode>,
            editableNodes: MutableList<ScreenNode>,
            scrollableNodes: MutableList<ScreenNode>,
            allNodes: MutableList<ScreenNode>
        ) {
            val textStr = node.text?.toString()?.trim()
            val descStr = node.contentDescription?.toString()?.trim()
            val bounds = Rect()
            node.getBoundsInScreen(bounds)

            if (!textStr.isNullOrBlank()) {
                visibleTexts.add(textStr)
            }
            if (!descStr.isNullOrBlank()) {
                contentDescriptions.add(descStr)
            }

            val screenNode = ScreenNode(
                text = textStr,
                contentDescription = descStr,
                viewId = node.viewIdResourceName,
                className = node.className?.toString(),
                isClickable = node.isClickable,
                isEditable = node.isEditable || node.className == "android.widget.EditText",
                isScrollable = node.isScrollable,
                bounds = bounds,
                depth = depth
            )

            allNodes.add(screenNode)

            if (screenNode.isEditable) {
                editableNodes.add(screenNode)
            } else if (screenNode.isClickable) {
                clickableNodes.add(screenNode)
            }

            if (screenNode.isScrollable) {
                scrollableNodes.add(screenNode)
            }

            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                traverseNode(child, depth + 1, visibleTexts, contentDescriptions, clickableNodes, editableNodes, scrollableNodes, allNodes)
            }
        }

        // --- Semantic Node Searching Helpers ---

        fun findEditableNode(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (root == null) return null
            if (root.isEditable || root.className == "android.widget.EditText") return root
            for (i in 0 until root.childCount) {
                val child = root.getChild(i) ?: continue
                val found = findEditableNode(child)
                if (found != null) return found
            }
            return null
        }

        fun findFocusedEditableNode(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (root == null) return null
            if ((root.isEditable || root.className == "android.widget.EditText") && root.isFocused) {
                return root
            }
            for (i in 0 until root.childCount) {
                val child = root.getChild(i) ?: continue
                val found = findFocusedEditableNode(child)
                if (found != null) return found
            }
            return null
        }

        fun findTextField(root: AccessibilityNodeInfo?, hintOrLabel: String? = null): AccessibilityNodeInfo? {
            if (root == null) return null
            if (hintOrLabel != null && hintOrLabel.isNotBlank()) {
                val match = findNodeByText(root, hintOrLabel)
                if (match != null && (match.isEditable || match.className == "android.widget.EditText")) {
                    return match
                }
            }
            // Fallback to focused editable or first editable
            return findFocusedEditableNode(root) ?: findEditableNode(root)
        }

        fun findNodeByText(root: AccessibilityNodeInfo?, text: String): AccessibilityNodeInfo? {
            if (root == null || text.isBlank()) return null
            val nodeText = root.text?.toString()
            val desc = root.contentDescription?.toString()
            if (nodeText?.contains(text, ignoreCase = true) == true || desc?.contains(text, ignoreCase = true) == true) {
                return root
            }
            for (i in 0 until root.childCount) {
                val child = root.getChild(i) ?: continue
                val found = findNodeByText(child, text)
                if (found != null) return found
            }
            return null
        }

        fun findNodeByContentDescription(root: AccessibilityNodeInfo?, description: String): AccessibilityNodeInfo? {
            if (root == null || description.isBlank()) return null
            val desc = root.contentDescription?.toString()
            if (desc?.contains(description, ignoreCase = true) == true) {
                return root
            }
            for (i in 0 until root.childCount) {
                val child = root.getChild(i) ?: continue
                val found = findNodeByContentDescription(child, description)
                if (found != null) return found
            }
            return null
        }

        fun findClickableParent(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            var current = node
            while (current != null) {
                if (current.isClickable) return current
                current = current.parent
            }
            return null
        }

        fun findScrollableNode(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (root == null) return null
            if (root.isScrollable) return root
            for (i in 0 until root.childCount) {
                val child = root.getChild(i) ?: continue
                val found = findScrollableNode(child)
                if (found != null) return found
            }
            return null
        }

        // --- Text Input Actions with Verification ---

        fun focusTextField(node: AccessibilityNodeInfo): Boolean {
            return node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        }

        fun clearText(node: AccessibilityNodeInfo): Boolean {
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
            }
            return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        }

        fun setText(node: AccessibilityNodeInfo, text: String): Boolean {
            focusTextField(node)
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        }

        fun verifyText(node: AccessibilityNodeInfo, expectedText: String): Boolean {
            // Re-read current text from node
            val actual = node.text?.toString() ?: ""
            return actual.contains(expectedText, ignoreCase = true)
        }
    }
}
