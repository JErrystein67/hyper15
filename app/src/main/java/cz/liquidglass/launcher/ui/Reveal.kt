package cz.liquidglass.launcher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * One progress value for both vertical sheets:  +1 = control/notification panel fully pulled down,
 * -1 = app drawer fully pulled up, 0 = home. Everything is driven from here so the finger can
 * continuously move from "closed" to either sheet.
 */
@Stable
class RevealState(private val scope: CoroutineScope) {
    private val anim = Animatable(0f)

    val value: Float get() = anim.value
    val panel: Float get() = max(anim.value, 0f)
    val drawer: Float get() = max(-anim.value, 0f)

    /** 0 = notifications, 1 = controls (used by the compact, paged layout). */
    var panelPage by mutableIntStateOf(0)

    fun dragBy(delta: Float) {
        scope.launch { anim.snapTo((anim.value + delta).coerceIn(-1f, 1f)) }
    }

    /** [velocity] is normalized (px/s divided by the drag range); positive = downwards. */
    fun settle(velocity: Float) {
        val v = anim.value
        val target = when {
            velocity > 1f -> if (v < 0f) 0f else 1f
            velocity < -1f -> if (v > 0f) 0f else -1f
            v > 0.4f -> 1f
            v < -0.4f -> -1f
            else -> 0f
        }
        animateTo(target)
    }

    fun animateTo(target: Float) {
        scope.launch { anim.animateTo(target, spring(dampingRatio = 0.85f, stiffness = 380f)) }
    }
}

/** Lets a scrollable list inside a sheet hand leftover scroll to the sheet (closing it). */
class RevealConnection(
    private val reveal: RevealState,
    private val range: () -> Float,
    private val panel: Boolean,
) : NestedScrollConnection {

    private fun mid(): Boolean =
        if (panel) reveal.value in 0.001f..0.999f else reveal.value in -0.999f..-0.001f

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (mid()) {
            reveal.dragBy(available.y / range())
            return Offset(0f, available.y)
        }
        return Offset.Zero
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        val closing = if (panel) available.y < 0f else available.y > 0f
        if (closing) {
            reveal.dragBy(available.y / range())
            return Offset(0f, available.y)
        }
        return Offset.Zero
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        if (mid()) {
            reveal.settle(available.y / range())
            return available
        }
        return Velocity.Zero
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        if (mid()) {
            reveal.settle(available.y / range())
            return available
        }
        return Velocity.Zero
    }
}
