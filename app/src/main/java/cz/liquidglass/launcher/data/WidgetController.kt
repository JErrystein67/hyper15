package cz.liquidglass.launcher.data

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewConfiguration
import kotlin.math.ceil
import kotlin.math.hypot

private const val HOST_ID = 0x4C47

/**
 * Host view with a Launcher3-style long-press detector. It lives in the touch-intercept phase, so the
 * long press fires even over widgets that handle clicks / scrolling themselves, and the widget gets
 * ACTION_CANCEL afterwards. This is what makes "long-press anywhere on a widget" reliable inside Compose.
 */
class LauncherWidgetHostView(context: Context) : AppWidgetHostView(context) {
    var onLongPress: (() -> Unit)? = null

    private var longPressed = false
    private var downX = 0f
    private var downY = 0f
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val trigger = Runnable {
        longPressed = true
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        onLongPress?.invoke()
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                longPressed = false
                downX = ev.x; downY = ev.y
                postDelayed(trigger, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_MOVE ->
                if (hypot(ev.x - downX, ev.y - downY) > slop) removeCallbacks(trigger)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> removeCallbacks(trigger)
        }
        return longPressed || super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) {
            removeCallbacks(trigger)
            val was = longPressed
            longPressed = false
            return was || super.onTouchEvent(ev)
        }
        return longPressed || super.onTouchEvent(ev)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(trigger)
        super.onDetachedFromWindow()
    }
}

class LauncherWidgetHost(context: Context) : AppWidgetHost(context, HOST_ID) {
    override fun onCreateView(
        context: Context,
        appWidgetId: Int,
        appWidget: AppWidgetProviderInfo?,
    ): AppWidgetHostView = LauncherWidgetHostView(context)
}

/** Default size of a widget in grid cells. */
fun widgetSpan(info: AppWidgetProviderInfo, cellW: Float, cellH: Float): Pair<Int, Int> {
    val sx: Int
    val sy: Int
    if (Build.VERSION.SDK_INT >= 31 && info.targetCellWidth > 0 && info.targetCellHeight > 0) {
        sx = info.targetCellWidth; sy = info.targetCellHeight
    } else {
        sx = ceil(info.minWidth / cellW.coerceAtLeast(1f)).toInt()
        sy = ceil(info.minHeight / cellH.coerceAtLeast(1f)).toInt()
    }
    return sx.coerceIn(1, COLS) to sy.coerceIn(1, ROWS)
}

class WidgetController(context: Context) {
    private val appContext = context.applicationContext
    val manager: AppWidgetManager = AppWidgetManager.getInstance(appContext)
    val host = LauncherWidgetHost(appContext)
    private var listening = false

    fun start() {
        if (!listening) { runCatching { host.startListening() }; listening = true }
    }

    fun stop() {
        if (listening) { runCatching { host.stopListening() }; listening = false }
    }

    fun allocate(): Int = host.allocateAppWidgetId()
    fun delete(id: Int) { runCatching { host.deleteAppWidgetId(id) } }
    fun providers(): List<AppWidgetProviderInfo> = manager.installedProviders

    fun createView(context: Context, id: Int): LauncherWidgetHostView? {
        val info = manager.getAppWidgetInfo(id) ?: return null
        return host.createView(context, id, info) as? LauncherWidgetHostView
    }

    fun configure(activity: Activity, id: Int, requestCode: Int) {
        host.startAppWidgetConfigureActivityForResult(activity, id, 0, requestCode, null)
    }
}
