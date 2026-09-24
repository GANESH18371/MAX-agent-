package com.example.data.model

import android.graphics.Rect

data class ScreenNodeInfo(
    val nodeId: String,
    val text: String,
    val contentDescription: String,
    val viewId: String,
    val className: String,
    val isClickable: Boolean,
    val isEditable: Boolean,
    val isScrollable: Boolean,
    val bounds: Rect,
    val children: List<ScreenNodeInfo> = emptyList()
) {
    fun toFormattedString(depth: Int = 0): String {
        val indent = "  ".repeat(depth)
        val sb = StringBuilder()
        val textDesc = listOfNotNull(
            text.takeIf { it.isNotBlank() }?.let { "text=\"$it\"" },
            contentDescription.takeIf { it.isNotBlank() }?.let { "desc=\"$it\"" },
            viewId.takeIf { it.isNotBlank() }?.let { "id=\"$it\"" }
        ).joinToString(" ")

        val simpleClass = className.substringAfterLast('.')
        val flags = mutableListOf<String>()
        if (isClickable) flags.add("clickable")
        if (isEditable) flags.add("editable")
        if (isScrollable) flags.add("scrollable")
        val flagStr = if (flags.isNotEmpty()) " [${flags.joinToString(",")}]" else ""

        if (textDesc.isNotEmpty() || isClickable || isEditable) {
            sb.append("$indent• $simpleClass $textDesc bounds=(${bounds.left},${bounds.top},${bounds.right},${bounds.bottom})$flagStr nodeKey=\"$nodeId\"\n")
        }

        children.forEach { child ->
            sb.append(child.toFormattedString(depth + 1))
        }
        return sb.toString()
    }
}

data class ScreenStateDump(
    val packageName: String,
    val rootNodesFormatted: String,
    val totalClickables: Int,
    val timestamp: Long = System.currentTimeMillis()
)
