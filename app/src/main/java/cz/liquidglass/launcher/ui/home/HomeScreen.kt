package cz.liquidglass.launcher.ui.home

import android.app.WallpaperManager
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.TextView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.liquidglass.launcher.LauncherViewModel
import cz.liquidglass.launcher.data.AppInfo
import cz.liquidglass.launcher.data.AppKey
import cz.liquidglass.launcher.data.COLS
import cz.liquidglass.launcher.data.DOCK
import cz.liquidglass.launcher.data.DOCK_SLOTS
import cz.liquidglass.launcher.data.HomeItem
import cz.liquidglass.launcher.data.LauncherWidgetHostView
import cz.liquidglass.launcher.data.ROWS
import cz.liquidglass.launcher.data.homePageCount
import cz.liquidglass.launcher.ui.glass.GlassPill
import cz.liquidglass.launcher.ui.glass.glass
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

// ------------------------------------------------------------------------------------------------
// Drag & drop plumbing
// ------------------------------------------------------------------------------------------------

private class DragState(val item: HomeItem, val startRoot: Offset, val size: IntSize) {
    var offset by mutableStateOf(Offset.Zero)
}

/**
 * Drag state lives above the pager: the dragged item is rendered as a ghost in a root overlay, so
 * it survives page flips (edge-hover) and is never clipped by a page. All pages stay composed
 * (beyondViewportPageCount) so the originating pointer gesture is never cancelled.
 */
@Stable
private class DragController(private val vm: LauncherViewModel) {
    var drag by mutableStateOf<DragState?>(null)
    var gridOrigin = Offset.Zero
    var cellSize = Size.Zero
    var dockRect = Rect.Zero
    var page = 0

    fun begin(item: HomeItem, root: Offset, size: IntSize) {
        vm.setEdit(true)
        drag = DragState(item, root, size)
    }

    fun move(delta: Offset) {
        drag?.let { it.offset = it.offset + delta }
    }

    fun cancel() { drag = null }

    fun end() {
        val d = drag ?: return
        drag = null
        if (cellSize.width <= 0f || cellSize.height <= 0f) return
        val topLeft = d.startRoot + d.offset
        val center = topLeft + Offset(d.size.width / 2f, d.size.height / 2f)
        val item = d.item
        if (item !is HomeItem.Widget && center.y > dockRect.top - 24f && dockRect.width > 0f) {
            val slot = ((center.x - dockRect.left) / (dockRect.width / DOCK_SLOTS)).toInt().coerceIn(0, DOCK_SLOTS - 1)
            vm.drop(item.id, DOCK, slot, 0)
        } else {
            val cx = ((topLeft.x - gridOrigin.x) / cellSize.width).roundToInt().coerceIn(0, COLS - item.spanX)
            val cy = ((topLeft.y - gridOrigin.y) / cellSize.height).roundToInt().coerceIn(0, ROWS - item.spanY)
            vm.drop(item.id, page, cx, cy)
        }
    }
}

private class HomeEnv(
    val vm: LauncherViewModel,
    val ctl: DragController,
    val edit: Boolean,
    val appMap: Map<AppKey, AppInfo>,
    val wiggle: () -> Float,
)

/** Long-press (normal mode) or immediate (edit mode) drag of an item. */
@Composable
private fun Modifier.homeDrag(item: HomeItem, edit: Boolean, ctl: DragController): Modifier {
    var pos by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val haptics = LocalHapticFeedback.current
    return this
        .onGloballyPositioned { pos = it.positionInRoot(); size = it.size }
        .pointerInput(item, edit) {
            if (edit) {
                detectDragGestures(
                    onDragStart = { ctl.begin(item, pos, size) },
                    onDragEnd = { ctl.end() },
                    onDragCancel = { ctl.cancel() },
                ) { change, delta ->
                    change.consume()
                    ctl.move(delta)
                }
            } else {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        ctl.begin(item, pos, size)
                    },
                    onDragEnd = { ctl.end() },
                    onDragCancel = { ctl.cancel() },
                ) { change, delta ->
                    change.consume()
                    ctl.move(delta)
                }
            }
        }
}

// ------------------------------------------------------------------------------------------------
// Screen
// ------------------------------------------------------------------------------------------------

@Composable
fun HomeScreen(vm: LauncherViewModel, modifier: Modifier = Modifier) {
    val state by vm.state.collectAsStateWithLifecycle()
    val apps by vm.apps.apps.collectAsStateWithLifecycle()
    val appMap = remember(apps) { apps.associateBy { it.key } }
    val density = LocalDensity.current
    val view = LocalView.current
    val haptics = LocalHapticFeedback.current
    val screenW = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }

    val edit = state.edit
    val items = state.items
    val pageCount = items.homePageCount()
    val shownPages = if (edit) pageCount + 1 else pageCount
    val pager = rememberPagerState { shownPages }
    val ctl = remember { DragController(vm) }

    var gridSize by remember { mutableStateOf(IntSize.Zero) }
    val cellW = gridSize.width / COLS.toFloat()
    val cellH = gridSize.height / ROWS.toFloat()
    SideEffect {
        ctl.cellSize = Size(cellW, cellH)
        ctl.page = pager.currentPage
        vm.cellW = cellW
        vm.cellH = cellH
        vm.visiblePage = pager.currentPage
    }

    val wiggle: () -> Float = if (edit) {
        val t = rememberInfiniteTransition(label = "wiggle")
        val s = t.animateFloat(
            initialValue = -1.3f, targetValue = 1.3f,
            animationSpec = infiniteRepeatable(tween(130, easing = LinearEasing), RepeatMode.Reverse),
            label = "w",
        )
        ({ s.value })
    } else {
        ({ 0f })
    }
    val env = HomeEnv(vm, ctl, edit, appMap, wiggle)

    // flip pages while hovering a dragged item over the screen edge
    val zone by remember {
        derivedStateOf {
            val d = ctl.drag
            if (d == null) 0 else {
                val cx = d.startRoot.x + d.offset.x + d.size.width / 2f
                when {
                    cx < screenW * 0.08f -> -1
                    cx > screenW * 0.92f -> 1
                    else -> 0
                }
            }
        }
    }
    val pagesNow by rememberUpdatedState(shownPages)
    LaunchedEffect(zone) {
        if (zone != 0) {
            while (true) {
                delay(700)
                val t = pager.currentPage + zone
                if (t in 0 until pagesNow) pager.animateScrollToPage(t)
            }
        }
    }

    LaunchedEffect(Unit) { vm.homeEvents.collect { pager.animateScrollToPage(0) } }

    // wallpaper parallax
    LaunchedEffect(pager) {
        val wm = WallpaperManager.getInstance(view.context)
        snapshotFlow { pager.currentPage + pager.currentPageOffsetFraction }.collect { p ->
            val n = (pagesNow - 1).coerceAtLeast(1)
            runCatching {
                wm.setWallpaperOffsetSteps(1f / n, 1f)
                wm.setWallpaperOffsets(view.windowToken, (p / n).coerceIn(0f, 1f), 0.5f)
            }
        }
    }

    Box(modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
        ) {
            Box(Modifier.fillMaxWidth().height(56.dp)) { EditBar(edit, vm) }

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onGloballyPositioned {
                        gridSize = it.size
                        ctl.gridOrigin = it.positionInRoot()
                    }
            ) {
                HomePager(pager, items, env, cellW, cellH, density, haptics)
            }

            PageDots(shownPages, pager.currentPage)
            Dock(items, env)
        }

        ctl.drag?.let { d -> Ghost(d, env, density) }
    }
}

@Composable
private fun HomePager(
    pager: PagerState,
    items: List<HomeItem>,
    env: HomeEnv,
    cellW: Float,
    cellH: Float,
    density: Density,
    haptics: androidx.compose.ui.hapticfeedback.HapticFeedback,
) {
    val vm = env.vm
    val edit = env.edit
    HorizontalPager(
        state = pager,
        modifier = Modifier.fillMaxSize(),
        beyondViewportPageCount = 8,
        key = { it },
    ) { page ->
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val o = ((pager.currentPage - page) + pager.currentPageOffsetFraction)
                    val a = abs(o).coerceIn(0f, 1f)
                    alpha = 1f - 0.5f * a
                    val s = 1f - 0.06f * a
                    scaleX = s; scaleY = s
                }
                .pointerInput(edit) {
                    detectTapGestures(
                        onLongPress = {
                            if (!edit) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                vm.onBackgroundLongPress()
                            }
                        },
                        onTap = { if (edit) vm.setEdit(false) },
                    )
                }
        ) {
            items.filter { it.page == page }.forEach { item ->
                key(item.id) { PagedItem(item, env, cellW, cellH, density) }
            }
        }
    }
}

@Composable
private fun PagedItem(item: HomeItem, env: HomeEnv, cellW: Float, cellH: Float, density: Density) {
    val target = IntOffset((item.x * cellW).roundToInt(), (item.y * cellH).roundToInt())
    val off by animateIntOffsetAsState(target, spring(dampingRatio = 0.8f, stiffness = 400f), label = "pos")
    val dragged = env.ctl.drag?.item?.id == item.id
    val w = with(density) { (item.spanX * cellW).toDp() }
    val h = with(density) { (item.spanY * cellH).toDp() }
    Box(
        Modifier
            .offset { off }
            .size(w, h)
            .graphicsLayer { alpha = if (dragged) 0f else 1f }
    ) {
        ItemFace(item, env, showLabel = true)
    }
}

// ------------------------------------------------------------------------------------------------
// Item faces
// ------------------------------------------------------------------------------------------------

@Composable
private fun ItemFace(item: HomeItem, env: HomeEnv, showLabel: Boolean) {
    when (item) {
        is HomeItem.Widget -> WidgetFace(item, env)
        else -> {
            val sign = if (item.id.hashCode() % 2 == 0) 1f else -1f
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { rotationZ = if (env.edit) env.wiggle() * sign else 0f }
                    .homeDrag(item, env.edit, env.ctl)
                    .pointerInput(item.id, env.edit) {
                        detectTapGestures {
                            when {
                                item is HomeItem.App && !env.edit -> env.vm.launchApp(item.key)
                                item is HomeItem.Folder -> env.vm.openFolder(item.id)
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                ItemVisual(item, env.appMap, showLabel)
                if (env.edit) {
                    Badge(Modifier.align(Alignment.TopStart).padding(start = 8.dp, top = 6.dp)) {
                        env.vm.removeItem(item.id)
                    }
                }
            }
        }
    }
}

@Composable
private fun Badge(modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Remove, contentDescription = "Odebrat", tint = Color.White, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun WidgetFace(item: HomeItem.Widget, env: HomeEnv) {
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .padding(6.dp)
            .glass(26.dp)
    ) {
        val w = maxWidth.value.toInt()
        val h = maxHeight.value.toInt()
        AndroidView(
            factory = { c ->
                (env.vm.widgets.createView(c, item.widgetId) as View?)
                    ?: TextView(c).apply {
                        text = "Widget není dostupný"
                        setTextColor(android.graphics.Color.WHITE)
                        gravity = Gravity.CENTER
                    }
            },
            update = { v ->
                if (v is LauncherWidgetHostView) {
                    v.onLongPress = { env.vm.onWidgetLongPress() }
                    v.updateAppWidgetSize(Bundle.EMPTY, w, h, w, h)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        // Edit mode: a Compose overlay sits ABOVE the AndroidView. It blocks the widget's own touch
        // handling so dragging / resizing / removing never fights with the remote views.
        if (env.edit) WidgetEditOverlay(item, env)
    }
}

@Composable
private fun WidgetEditOverlay(item: HomeItem.Widget, env: HomeEnv) {
    var dx by remember { mutableFloatStateOf(0f) }
    var dy by remember { mutableFloatStateOf(0f) }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.18f))
            .homeDrag(item, true, env.ctl)
    ) {
        Badge(Modifier.align(Alignment.TopStart).padding(8.dp)) { env.vm.removeItem(item.id) }
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
                .size(34.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.92f))
                .pointerInput(item.id, item.spanX, item.spanY) {
                    detectDragGestures(
                        onDragEnd = {
                            val cw = env.ctl.cellSize.width.coerceAtLeast(1f)
                            val ch = env.ctl.cellSize.height.coerceAtLeast(1f)
                            env.vm.resizeWidget(
                                item.id,
                                item.spanX + (dx / cw).roundToInt(),
                                item.spanY + (dy / ch).roundToInt(),
                            )
                            dx = 0f; dy = 0f
                        },
                        onDragCancel = { dx = 0f; dy = 0f },
                    ) { change, delta ->
                        change.consume()
                        dx += delta.x; dy += delta.y
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.OpenInFull, contentDescription = "Změnit velikost", tint = Color.Black, modifier = Modifier.size(16.dp))
        }
    }
}

// ------------------------------------------------------------------------------------------------
// Chrome: dock, dots, edit bar, ghost
// ------------------------------------------------------------------------------------------------

@Composable
private fun Dock(items: List<HomeItem>, env: HomeEnv) {
    val dockItems = items.filter { it.page == DOCK }
    Box(
        Modifier
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .fillMaxWidth()
            .height(88.dp)
            .onGloballyPositioned { env.ctl.dockRect = it.boundsInRoot() }
            .glass(34.dp)
    ) {
        Row(Modifier.fillMaxSize()) {
            repeat(DOCK_SLOTS) { slot ->
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    dockItems.firstOrNull { it.x == slot }?.let { item ->
                        key(item.id) {
                            val dragged = env.ctl.drag?.item?.id == item.id
                            Box(Modifier.fillMaxSize().graphicsLayer { alpha = if (dragged) 0f else 1f }) {
                                ItemFace(item, env, showLabel = false)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PageDots(count: Int, current: Int) {
    if (count <= 1) return
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.Center) {
        repeat(count) { i ->
            Box(
                Modifier
                    .padding(horizontal = 4.dp)
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = if (i == current) 0.95f else 0.35f))
            )
        }
    }
}

@Composable
private fun EditBar(edit: Boolean, vm: LauncherViewModel) {
    AnimatedVisibility(
        visible = edit,
        enter = fadeIn() + slideInVertically { -it },
        exit = fadeOut() + slideOutVertically { -it },
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassPill(onClick = { vm.openPicker() }) {
                Icon(Icons.Rounded.Add, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Text("Widgety", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            GlassPill(onClick = { vm.setEdit(false) }) {
                Text("Hotovo", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun Ghost(d: DragState, env: HomeEnv, density: Density) {
    Box(
        Modifier
            .offset { IntOffset((d.startRoot.x + d.offset.x).roundToInt(), (d.startRoot.y + d.offset.y).roundToInt()) }
            .size(with(density) { d.size.width.toDp() }, with(density) { d.size.height.toDp() })
            .graphicsLayer { scaleX = 1.12f; scaleY = 1.12f }
    ) {
        val item = d.item
        if (item is HomeItem.Widget) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(6.dp)
                    .glass(26.dp, tint = Color.White.copy(alpha = 0.18f))
            )
        } else {
            ItemVisual(item, env.appMap, showLabel = item.page != DOCK)
        }
    }
}
