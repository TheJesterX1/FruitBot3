package com.fruitbot.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent

class GestureService : AccessibilityService() {

    companion object {
        @Volatile var instance: GestureService? = null
        @Volatile var foregroundPkg = ""

        fun connected() = instance != null

        fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, ms: Long = 60): Boolean {
            val svc = instance ?: return false
            return try {
                val path = Path().apply { moveTo(x1, y1); lineTo(x2, y2) }
                val stroke = GestureDescription.StrokeDescription(path, 0L, ms)
                svc.dispatchGesture(
                    GestureDescription.Builder().addStroke(stroke).build(), null, null)
                true
            } catch (e: Exception) { false }
        }
    }

    override fun onServiceConnected() { instance = this }
    override fun onDestroy() { super.onDestroy(); instance = null }
    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val p = event.packageName?.toString()
            if (!p.isNullOrBlank()) foregroundPkg = p
        }
    }
}
