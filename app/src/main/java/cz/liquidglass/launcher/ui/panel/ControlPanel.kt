package cz.liquidglass.launcher.ui.panel

import android.text.format.DateFormat
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AirplanemodeActive
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.liquidglass.launcher.data.AppInfo
import cz.liquidglass.launcher.data.NotifItem
import cz.liquidglass.launcher.data.SystemControls
import cz.liquidglass.launcher.ui.RevealConnection
import cz.liquidglass.launcher.ui.RevealState
import cz.liquidglass.launcher.ui.glass.GlassPill
import cz.liquidglass.launcher.ui.glass.glass
import cz.liquidglass.launcher.ui.glass.liquidGlass
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ControlPanel(
    reveal: RevealState,
    controls: SystemControls,
    notifications: List<NotifItem>,
    iconsByPkg: Map<String, AppInfo>,
    notifAccess: Boolean,
    onGrantAccess: () -> Unit,
    onClearAll: () -> Unit,
    onDismissNotif: (NotifItem) -> Unit,
    onOpenNotif: (NotifItem) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val range = constraints.maxHeight * 0.75f
        val wide = maxWidth >= 640.dp
        val conn = remember(range) { RevealConnection(reveal, { range }, panel = true) }
        val dragState = rememberDraggableState { reveal.dragBy(it / range) }

        Box(
            Modifier
                .fillMaxSize()
                .drawBehind { drawRect(Color.Black.copy(alpha = 0.30f * reveal.panel)) }
                .nestedScroll(conn)
                .draggable(dragState, Orientation.Vertical, onDragStopped = { reveal.settle(it / range) })
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationY = -(1f - reveal.panel) * size.height }
                    .windowInsetsPadding(WindowInsets.systemBars)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Header()
                Spacer(Modifier.height(12.dp))

                val notifPane: @Composable (Modifier) -> Unit = { m ->
                    NotificationsPane(m, notifications, iconsByPkg, notifAccess, onGrantAccess, onClearAll, onDismissNotif, onOpenNotif)
                }
                val controlsPane: @Composable (Modifier) -> Unit = { m -> ControlsPane(m, controls) }

                if (wide) {
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        notifPane(Modifier.weight(1f).fillMaxHeight())
                        controlsPane(Modifier.weight(1f))
                    }
                } else {
                    val pager = rememberPagerState(initialPage = reveal.panelPage) { 2 }
                    HorizontalPager(pager, Modifier.weight(1f)) { p ->
                        if (p == 0) notifPane(Modifier.fillMaxSize().padding(end = 4.dp))
                        else controlsPane(Modifier.fillMaxWidth().padding(start = 4.dp))
                    }
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.Center) {
                        repeat(2) { i ->
                            Box(
                                Modifier.padding(horizontal = 4.dp).size(7.dp).clip(CircleShape)
                                    .background(Color.White.copy(alpha = if (pager.currentPage == i) 0.95f else 0.35f))
                            )
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().padding(top = 6.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.width(44.dp).height(5.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.55f)))
                }
            }
        }
    }
}

@Composable
private fun Header() {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(60_000 - now % 60_000)
        }
    }
    val ctx = LocalContext.current
    val time = remember(now) { DateFormat.getTimeFormat(ctx).format(Date(now)) }
    val date = remember(now) { SimpleDateFormat("EEEE d. MMMM", Locale.getDefault()).format(Date(now)) }
    Column(Modifier.padding(horizontal = 4.dp)) {
        Text(time, color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Light)
        Text(date, color = Color.White.copy(alpha = 0.8f), fontSize = 16.sp)
    }
}

// ------------------------------------------------------------------------------------------------
// Notifications (left)
// ------------------------------------------------------------------------------------------------

@Composable
private fun NotificationsPane(
    modifier: Modifier,
    notifications: List<NotifItem>,
    iconsByPkg: Map<String, AppInfo>,
    access: Boolean,
    onGrantAccess: () -> Unit,
    onClearAll: () -> Unit,
    onDismiss: (NotifItem) -> Unit,
    onOpen: (NotifItem) -> Unit,
) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Oznámení", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (notifications.isNotEmpty()) GlassPill(onClick = onClearAll) {
                Text("Vymazat", color = Color.White, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        when {
            !access -> GlassCard {
                Text("Pro zobrazení oznámení povolte launcheru přístup k oznámením.", color = Color.White, fontSize = 14.sp)
                Spacer(Modifier.height(10.dp))
                GlassPill(onClick = onGrantAccess) { Text("Povolit přístup", color = Color.White, fontSize = 14.sp) }
            }
            notifications.isEmpty() -> GlassCard {
                Text("Žádná nová oznámení", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
            }
            else -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 12.dp, top = 4.dp, start = 2.dp, end = 2.dp),
            ) {
                items(notifications, key = { it.key }) { n ->
                    NotificationCard(n, iconsByPkg[n.pkg], { onOpen(n) }, { onDismiss(n) })
                }
            }
        }
    }
}

@Composable
private fun GlassCard(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().glass(26.dp, shadowRadius = 18.dp).padding(16.dp)) { content() }
}

@Composable
private fun NotificationCard(n: NotifItem, icon: AppInfo?, onOpen: () -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val time = remember(n.time) { DateFormat.getTimeFormat(ctx).format(Date(n.time)) }
    Row(
        Modifier
            .fillMaxWidth()
            .glass(24.dp, shadowRadius = 18.dp)
            .clickable(onClick = onOpen)
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        if (icon != null) {
            Image(icon.icon, null, Modifier.size(38.dp).clip(RoundedCornerShape(9.dp)))
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(n.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (n.text.isNotBlank()) Text(n.text, color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(time, color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
            if (n.clearable) {
                Icon(
                    Icons.Rounded.Close, "Smazat", tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 6.dp).size(20.dp).clickable(onClick = onDismiss),
                )
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------
// Controls (right): glass capsules that fill white with a coloured icon when active
// ------------------------------------------------------------------------------------------------

@Composable
private fun ControlsPane(modifier: Modifier, c: SystemControls) {
    BoxWithConstraints(modifier) {
        val gap = 12.dp
        val u = (maxWidth - gap * 3) / 4
        val sliderH = u * 3 + gap * 2
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        GlassToggle(Icons.Rounded.Wifi, "Wi-Fi", c.wifi, Color(0xFF0A84FF), c::toggleWifi, Modifier.size(u))
                        GlassToggle(Icons.Rounded.Bluetooth, "Bluetooth", c.bluetooth, Color(0xFF0A84FF), c::toggleBluetooth, Modifier.size(u))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        GlassToggle(Icons.Rounded.AirplanemodeActive, "Letadlo", c.airplane, Color(0xFFFF9F0A), c::toggleAirplane, Modifier.size(u))
                        GlassToggle(Icons.Rounded.DoNotDisturbOn, "Nerušit", c.dnd, Color(0xFF5E5CE6), c::toggleDnd, Modifier.size(u))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        GlassToggle(Icons.Rounded.FlashlightOn, "Svítilna", c.torch, Color(0xFFFFB800), c::toggleTorch, Modifier.size(u))
                        GlassToggle(Icons.Rounded.ScreenRotation, "Rotace", c.autoRotate, Color(0xFFFF453A), c::toggleRotation, Modifier.size(u))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    GlassSlider(Icons.Rounded.LightMode, c.brightness, Color(0xFFFFB800), c::changeBrightness, Modifier.width(u).height(sliderH))
                    GlassSlider(Icons.Rounded.VolumeUp, c.volume, Color(0xFF0A84FF), c::changeVolume, Modifier.width(u).height(sliderH))
                }
            }
            GlassPill(onClick = c::openSettings, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Settings, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Text("Nastavení", color = Color.White, fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun GlassToggle(
    icon: ImageVector,
    description: String,
    active: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val fill = animateFloatAsState(if (active) 1f else 0f, spring(dampingRatio = 0.8f, stiffness = 300f), label = "fill")
    val iconColor by animateColorAsState(if (active) accent else Color.White, label = "icon")
    Box(
        modifier
            .liquidGlass(cornerRadius = 100.dp, shadowRadius = 20.dp)
            .clip(CircleShape)
            .drawBehind {
                val f = fill.value
                if (f > 0f) drawCircle(Color.White.copy(alpha = 0.96f), radius = size.minDimension / 2f * f)
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = iconColor, modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun GlassSlider(
    icon: ImageVector,
    value: Float,
    accent: Color,
    onChange: (Float) -> Unit,
    modifier: Modifier,
) {
    val v = value.coerceIn(0f, 1f)
    val cb by rememberUpdatedState(onChange)
    val tint by animateColorAsState(if (v > 0.16f) accent else Color.White, label = "sliderIcon")
    Box(
        modifier
            .glass(cornerRadius = 36.dp, shadowRadius = 20.dp)
            .drawBehind {
                val fh = size.height * v
                drawRect(Color.White.copy(alpha = 0.96f), Offset(0f, size.height - fh), Size(size.width, fh))
            }
            .pointerInput(Unit) { detectTapGestures { cb(1f - (it.y / size.height).coerceIn(0f, 1f)) } }
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, _ ->
                    change.consume()
                    cb(1f - (change.position.y / size.height).coerceIn(0f, 1f))
                }
            },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.padding(bottom = 18.dp).size(26.dp))
    }
}
