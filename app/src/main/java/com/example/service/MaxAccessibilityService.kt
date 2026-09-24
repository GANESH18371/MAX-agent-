package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.data.model.ScreenNodeInfo
import com.example.data.model.ScreenStateDump
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executor
import java.util.concurrent.Executors

class MaxAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "MaxAccessibilityService"

        @Volatile
        var instance: MaxAccessibilityService? = null
            private set

        val isServiceRunning: Boolean
            get() = instance != null

        private val _currentAppPackage = MutableStateFlow("Unknown")
        val currentAppPackage: StateFlow<String> = _currentAppPackage.asStateFlow()

        private val _lastEventTime = MutableStateFlow(System.currentTimeMillis())
        val lastEventTime: StateFlow<Long> = _lastEventTime.asStateFlow()

        /**
         * Helper method to launch an app or open a URL directly using Context
         */
        fun launchAppOrUrl(context: Context, targetPackageOrUrl: String): Pair<Boolean, String> {
            return try {
                if (targetPackageOrUrl.startsWith("http://") || targetPackageOrUrl.startsWith("https://")) {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetPackageOrUrl)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    Pair(true, "Opened URL: $targetPackageOrUrl")
                } else {
                    val pm = context.packageManager
                    val launchIntent = pm.getLaunchIntentForPackage(targetPackageOrUrl)
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(launchIntent)
                        Pair(true, "App $targetPackageOrUrl launched successfully")
                    } else {
                        // Fallback: search installed apps matching label
                        val installed = pm.getInstalledApplications(0)
                        val match = installed.firstOrNull {
                            pm.getApplicationLabel(it).toString().contains(targetPackageOrUrl, ignoreCase = true)
                        }
                        if (match != null) {
                            val intent = pm.getLaunchIntentForPackage(match.packageName)?.apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            if (intent != null) {
                                context.startActivity(intent)
                                return Pair(true, "App ${match.packageName} launched")
                            }
                        }
                        Pair(false, "App package or label '$targetPackageOrUrl' not installed")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to launch $targetPackageOrUrl", e)
                Pair(false, "Launch error: ${e.localizedMessage}")
            }
        }
    }

    private val executor: Executor = Executors.newSingleThreadExecutor()

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "Max Accessibility Service Connected successfully!")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        event.packageName?.let { pkg ->
            val pkgStr = pkg.toString()
            if (pkgStr != _currentAppPackage.value && !pkgStr.contains("com.android.systemui")) {
                _currentAppPackage.value = pkgStr
                com.example.util.ContextManager.updateCurrentApp(applicationContext, pkgStr)
            }
        }
        _lastEventTime.value = System.currentTimeMillis()
    }

    override fun onInterrupt() {
        Log.w(TAG, "Max Accessibility Service Interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
        Log.d(TAG, "Max Accessibility Service Destroyed")
    }

    /**
     * Extracts full hierarchy of the current screen with Top-to-Bottom visual ordinal ordering
     */
    fun getScreenStateDump(): ScreenStateDump {
        val rootNode = rootInActiveWindow
        if (rootNode == null) {
            Log.w(TAG, "[REALTIME_STATE_WARN] rootInActiveWindow is null. Screen locked or accessibility inactive.")
            return ScreenStateDump(
                packageName = _currentAppPackage.value,
                rootNodesFormatted = "Screen content currently inaccessible or locked.",
                totalClickables = 0
            )
        }

        // Force fresh real-time state refresh from Android OS framework
        try {
            rootNode.refresh()
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing root node", e)
        }

        val clickableCount = intArrayOf(0)
        val tree = parseNode(rootNode, clickableCount)

        // Collect and sort all visible actionable items strictly top-to-bottom
        val rawNodes = mutableListOf<AccessibilityNodeInfo>()
        collectAllActionableNodes(rootNode, rawNodes)

        val sortedVisualNodes = rawNodes.distinctBy {
            val b = Rect()
            it.getBoundsInScreen(b)
            "${b.left}_${b.top}_${b.right}_${b.bottom}_${it.text}_${it.contentDescription}"
        }.sortedWith(Comparator { a, b ->
            val rA = Rect()
            val rB = Rect()
            a.getBoundsInScreen(rA)
            b.getBoundsInScreen(rB)
            val topDiff = rA.top - rB.top
            if (Math.abs(topDiff) > 30) topDiff else (rA.left - rB.left)
        })

        val ordinalNames = listOf("Pehla / 1st", "Doosra / 2nd", "Teesra / 3rd", "Chautha / 4th", "Paanchwa / 5th", "Chhatta / 6th", "Saatwa / 7th", "Aathwa / 8th")

        val visualSectionBuilder = StringBuilder()
        visualSectionBuilder.append("--- VISUAL SCREEN ITEMS (TOP TO BOTTOM ORDER FOR 'PEHLA / DOOSRA / TEESRA') ---\n")

        sortedVisualNodes.take(12).forEachIndexed { idx, node ->
            val b = Rect()
            node.getBoundsInScreen(b)
            val txt = node.text?.toString() ?: ""
            val desc = node.contentDescription?.toString() ?: ""
            val id = node.viewIdResourceName?.substringAfterLast('/') ?: ""
            val label = ordinalNames.getOrElse(idx) { "${idx + 1}th" }
            visualSectionBuilder.append("[Visual Item #${idx + 1} ($label)] text=\"$txt\" desc=\"$desc\" id=\"$id\" bounds=(${b.left},${b.top},${b.right},${b.bottom})\n")
        }

        visualSectionBuilder.append("\n--- HIERARCHY TREE ---\n")
        visualSectionBuilder.append(tree?.toFormattedString(0) ?: "Empty layout")

        return ScreenStateDump(
            packageName = _currentAppPackage.value,
            rootNodesFormatted = visualSectionBuilder.toString().take(2000),
            totalClickables = clickableCount[0]
        )
    }

    private fun collectAllActionableNodes(node: AccessibilityNodeInfo, outList: MutableList<AccessibilityNodeInfo>) {
        if (!node.isVisibleToUser) return

        val b = Rect()
        node.getBoundsInScreen(b)
        val hasTxt = !node.text.isNullOrBlank() || !node.contentDescription.isNullOrBlank() || !node.viewIdResourceName.isNullOrBlank()

        if ((node.isClickable || hasTxt) && b.height() > 10 && b.width() > 10) {
            outList.add(node)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                collectAllActionableNodes(child, outList)
            }
        }
    }

    private fun parseNode(node: AccessibilityNodeInfo, clickableCounter: IntArray): ScreenNodeInfo? {
        if (!node.isVisibleToUser) return null

        val text = node.text?.toString() ?: ""
        val contentDesc = node.contentDescription?.toString() ?: ""
        val viewId = node.viewIdResourceName ?: ""

        val bounds = Rect()
        node.getBoundsInScreen(bounds)

        if (node.isClickable) {
            clickableCounter[0]++
        }

        val childList = mutableListOf<ScreenNodeInfo>()
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                parseNode(child, clickableCounter)?.let { childList.add(it) }
            }
        }

        // Skip non-informative empty layout containers
        if (text.isBlank() && contentDesc.isBlank() && viewId.isBlank() &&
            !node.isClickable && !node.isEditable && !node.isScrollable && childList.isEmpty()
        ) {
            return null
        }

        return ScreenNodeInfo(
            nodeId = "${viewId.ifBlank { "id" }}_${bounds.centerX()}_${bounds.centerY()}",
            text = text,
            contentDescription = contentDesc,
            viewId = viewId,
            className = node.className?.toString() ?: "",
            isClickable = node.isClickable,
            isEditable = node.isEditable,
            isScrollable = node.isScrollable,
            bounds = bounds,
            children = childList
        )
    }

    /**
     * Attempts to click a node matching target text, viewId, content description, or ordinal visual index.
     */
    fun findAndClick(targetQuery: String): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        val cleanQuery = targetQuery.trim().lowercase()

        // Check for Visual Item Index matching (e.g. "visual item #2", "item #1", "pehla", "doosra", "teesra")
        val ordinalIndex = when {
            cleanQuery.contains("visual item #") -> cleanQuery.substringAfter("visual item #").takeWhile { it.isDigit() }.toIntOrNull()
            cleanQuery.contains("item #") -> cleanQuery.substringAfter("item #").takeWhile { it.isDigit() }.toIntOrNull()
            cleanQuery.contains("pehla") || cleanQuery.contains("1st") || cleanQuery == "1" -> 1
            cleanQuery.contains("doosra") || cleanQuery.contains("2nd") || cleanQuery == "2" -> 2
            cleanQuery.contains("teesra") || cleanQuery.contains("3rd") || cleanQuery == "3" -> 3
            cleanQuery.contains("chautha") || cleanQuery.contains("4th") || cleanQuery == "4" -> 4
            cleanQuery.contains("paanchwa") || cleanQuery.contains("5th") || cleanQuery == "5" -> 5
            else -> null
        }

        if (ordinalIndex != null && ordinalIndex > 0) {
            val rawNodes = mutableListOf<AccessibilityNodeInfo>()
            collectAllActionableNodes(rootNode, rawNodes)

            val sortedVisualNodes = rawNodes.distinctBy {
                val b = Rect()
                it.getBoundsInScreen(b)
                "${b.left}_${b.top}_${b.right}_${b.bottom}_${it.text}_${it.contentDescription}"
            }.sortedWith(Comparator { a, b ->
                val rA = Rect()
                val rB = Rect()
                a.getBoundsInScreen(rA)
                b.getBoundsInScreen(rB)
                val topDiff = rA.top - rB.top
                if (Math.abs(topDiff) > 30) topDiff else (rA.left - rB.left)
            })

            if (sortedVisualNodes.size >= ordinalIndex) {
                val targetNode = sortedVisualNodes[ordinalIndex - 1]
                val b = Rect()
                targetNode.getBoundsInScreen(b)
                Log.d(TAG, "Tapping Nth visual item ($ordinalIndex) at center (${b.centerX()}, ${b.centerY()})")
                tapAtCoordinates(b.centerX().toFloat(), b.centerY().toFloat())
                return true
            }
        }

        val matchedNodes = mutableListOf<AccessibilityNodeInfo>()
        searchMatchingNodes(rootNode, cleanQuery, matchedNodes)

        for (node in matchedNodes) {
            if (performClickOnNodeOrParent(node)) {
                return true
            }
        }
        return false
    }

    private fun searchMatchingNodes(
        node: AccessibilityNodeInfo,
        query: String,
        outList: MutableList<AccessibilityNodeInfo>
    ) {
        if (!node.isVisibleToUser) return

        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        val viewId = node.viewIdResourceName?.lowercase() ?: ""

        if (text.contains(query) || desc.contains(query) || viewId.contains(query)) {
            outList.add(node)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                searchMatchingNodes(child, query, outList)
            }
        }
    }

    private fun performClickOnNodeOrParent(node: AccessibilityNodeInfo): Boolean {
        var curr: AccessibilityNodeInfo? = node
        while (curr != null) {
            if (curr.isClickable) {
                val success = curr.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (success) return true
            }
            curr = curr.parent
        }
        return false
    }

    /**
     * Performs direct physical touch gesture tap at coordinates (x, y)
     */
    fun tapAtCoordinates(x: Float, y: Float, onComplete: ((Boolean) -> Unit)? = null) {
        val path = Path().apply {
            moveTo(x, y)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "Gesture tap completed at ($x, $y)")
                onComplete?.invoke(true)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.w(TAG, "Gesture tap cancelled at ($x, $y)")
                onComplete?.invoke(false)
            }
        }, null)
    }

    /**
     * Performs a swipe gesture
     */
    fun performSwipe(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long = 300,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                onComplete?.invoke(true)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                onComplete?.invoke(false)
            }
        }, null)
    }

    /**
     * Focuses and types text into input field
     */
    fun typeTextIntoActiveField(textToType: String): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        val editableNode = findEditableNode(rootNode)

        if (editableNode != null) {
            editableNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            val arguments = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, textToType)
            }
            val success = editableNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            if (success) return true
        }
        return false
    }

    private fun findEditableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isFocused && node.isEditable) return node
        if (node.isEditable) return node

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                val res = findEditableNode(child)
                if (res != null) return res
            }
        }
        return null
    }

    /**
     * Takes screenshot on Android 11+
     */
    fun captureScreen(onScreenshotCaptured: (Bitmap?) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            takeScreenshot(
                android.view.Display.DEFAULT_DISPLAY,
                executor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        val hardwareBuffer = screenshot.hardwareBuffer
                        val colorSpace = screenshot.colorSpace
                        val bitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace)
                        val softwareBitmap = bitmap?.copy(Bitmap.Config.ARGB_8888, false)
                        hardwareBuffer.close()
                        onScreenshotCaptured(softwareBitmap)
                    }

                    override fun onFailure(errorCode: Int) {
                        Log.e(TAG, "Screenshot capture failed with error: $errorCode")
                        onScreenshotCaptured(null)
                    }
                }
            )
        } else {
            onScreenshotCaptured(null)
        }
    }

    /**
     * Launches external app by package name
     */
    fun openApp(packageName: String): Boolean {
        return launchAppOrUrl(this, packageName).first
    }
}
