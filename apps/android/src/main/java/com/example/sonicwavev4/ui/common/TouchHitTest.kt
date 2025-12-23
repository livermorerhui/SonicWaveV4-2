package com.example.sonicwavev4.ui.common

import android.graphics.Rect
import android.view.MotionEvent
import android.view.View

object TouchHitTest {
    fun isInside(view: View?, event: MotionEvent, extraMarginPx: Int = 0): Boolean {
        if (view == null || !view.isShown) return false
        val rect = Rect()
        if (!view.getGlobalVisibleRect(rect)) return false
        if (extraMarginPx > 0) rect.inset(-extraMarginPx, -extraMarginPx)
        return rect.contains(event.rawX.toInt(), event.rawY.toInt())
    }
}
