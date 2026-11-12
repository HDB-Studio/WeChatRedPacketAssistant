package com.example.twostepclicker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Path
import android.graphics.Point
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import androidx.core.content.ContextCompat

/**
 * 無障礙服務：只在使用者主動觸發時，依序執行兩個自訂點擊動作。
 * 不監聽、不自動操作、不在背景自行動作。
 */
class TwoStepAccessibilityService : AccessibilityService() {

    companion object {
        const val ACTION_RUN = "com.example.twostepclicker.ACTION_RUN"

        @Volatile
        var instance: TwoStepAccessibilityService? = null
            private set
    }

    private val handler = Handler(Looper.getMainLooper())
    private var runReceiver: BroadcastReceiver? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        registerRunReceiver()
    }

    private fun registerRunReceiver() {
        if (runReceiver != null) return
        runReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == ACTION_RUN) runSequence()
            }
        }
        ContextCompat.registerReceiver(
            this,
            runReceiver,
            IntentFilter(ACTION_RUN),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 刻意不做事：本服務不做任何被動自動化
    }

    override fun onInterrupt() {
        handler.removeCallbacksAndMessages(null)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        runReceiver?.let { runCatching { unregisterReceiver(it) } }
        runReceiver = null
        instance = null
        super.onDestroy()
    }

    /** 由通知按鈕或主畫面按鈕呼叫，開始執行兩步。 */
    fun runSequence() {
        val steps = StepStore.load(this)
        if (steps.isEmpty()) {
            toast("尚未設定任何步驟")
            return
        }

        val target = StepStore.targetPackage(this)
        val foreground = rootInActiveWindow?.packageName?.toString()
        if (target.isNotBlank() && foreground != null && foreground != target) {
            toast("目前前景 App 不是目標（$foreground）")
            return
        }

        handler.removeCallbacksAndMessages(null)
        // 預留時間給通知欄收合與畫面穩定
        handler.postDelayed({ executeStep(steps, 0) }, 700L)
    }

    private fun executeStep(steps: List<Step>, index: Int) {
        if (index >= steps.size) {
            toast("兩步點擊完成")
            return
        }
        val step = steps[index]
        val delay = if (index == 0) 0L else step.waitBeforeMs.coerceAtLeast(0L)
        handler.postDelayed({
            val ok = performStep(step)
            if (ok) {
                executeStep(steps, index + 1)
            } else {
                toast("步驟 ${index + 1} 找不到目標，已停止")
            }
        }, delay)
    }

    private fun performStep(step: Step): Boolean = when (step.kind) {
        StepKind.TEXT -> clickByText(step.value)
        StepKind.DESC -> clickByDesc(step.value)
        StepKind.ID -> clickByViewId(step.value)
        StepKind.POINT -> tapPercent(step.xPercent, step.yPercent)
    }

    private fun clickByText(text: String): Boolean {
        if (text.isBlank()) return false
        val nodes = rootInActiveWindow?.findAccessibilityNodeInfosByText(text) ?: return false
        val node = nodes.firstOrNull() ?: return false
        return clickNodeOrCenter(node)
    }

    private fun clickByViewId(id: String): Boolean {
        if (id.isBlank()) return false
        val root = rootInActiveWindow ?: return false
        val fullId = if (id.contains(":id/")) {
            id
        } else {
            val pkg = root.packageName?.toString() ?: packageName
            "$pkg:id/$id"
        }
        val nodes = root.findAccessibilityNodeInfosByViewId(fullId) ?: return false
        val node = nodes.firstOrNull() ?: return false
        return clickNodeOrCenter(node)
    }

    private fun clickByDesc(desc: String): Boolean {
        if (desc.isBlank()) return false
        val node = findByDesc(rootInActiveWindow, desc) ?: return false
        return clickNodeOrCenter(node)
    }

    private fun findByDesc(
        node: AccessibilityNodeInfo?,
        desc: String
    ): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.contentDescription?.toString() == desc) return node
        for (i in 0 until node.childCount) {
            val found = findByDesc(node.getChild(i), desc)
            if (found != null) return found
        }
        return null
    }

    /** 往上找到可點擊節點並點擊；找不到就點它的中心座標。 */
    private fun clickNodeOrCenter(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        while (current != null) {
            if (current.isClickable) {
                return current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            current = current.parent
        }
        val rect = Rect()
        node.getBoundsInScreen(rect)
        if (rect.width() <= 0 || rect.height() <= 0) return false
        return tap(rect.centerX().toFloat(), rect.centerY().toFloat())
    }

    private fun tapPercent(xPercent: Int, yPercent: Int): Boolean {
        val size = screenSize()
        val x = size.x * xPercent.coerceIn(0, 100) / 100f
        val y = size.y * yPercent.coerceIn(0, 100) / 100f
        return tap(x, y)
    }

    private fun tap(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 60L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture, null, null)
    }

    private fun screenSize(): Point {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.currentWindowMetrics.bounds
            Point(bounds.width(), bounds.height())
        } else {
            val dm = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(dm)
            Point(dm.widthPixels, dm.heightPixels)
        }
    }

    private fun toast(msg: String) {
        handler.post { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
    }
}
