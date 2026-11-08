package cz.liquidglass.launcher.ui.home

import android.appwidget.AppWidgetProviderInfo
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import cz.liquidglass.launcher.data.AppInfo
import cz.liquidglass.launcher.data.AppKey
import cz.liquidglass.launcher.data.HomeItem
import cz.liquidglass.launcher.data.widgetSpan
import cz.liquidglass.launcher.ui.RevealConnection
import cz.liquidglass.launcher.ui.RevealState
import cz.liquidglass.launcher.ui.glass.glass
import cz.liquidglass.launcher.ui.glass.liquidGlass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

// ------------------------------------------------------------------------------------------------
// App drawer
// ------------------------------------------------------------------------------------------------

@Composable
fun AppDrawer(
    reveal: RevealState,
    apps: List<AppInfo>,
    onLaunch: (AppInfo) -> Unit,
    onMenu: (AppInfo) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(apps, query) {
        if (query.isBlank()) apps else apps.filter { it.label.contains(query.trim(), ignoreCase = true) }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val range = constraints.maxHeight * 0.75f
        val conn = remember(range) { RevealConnection(reveal, { range }, panel = false) }
        val dragState = rememberDraggableState { reveal.dragBy(it / range) }
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = (1f - reveal.drawer) * size.height
                    alpha = (reveal.drawer * 1.4f).coerceIn(0f, 1f)
                }
                .background(Color.Black.copy(alpha = 0.30f))
                .nestedScroll(conn)
                .draggable(
                    dragState, Orientation.Vertical,
                    onDragStopped = { reveal.settle(it / range) },
                )
        ) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars).padding(horizontal = 16.dp)) {
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth().glass(26.dp, shadowRadius = 16.dp).padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Rounded.Search, null, tint = Color.White.copy(alpha = 0.8f))
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) Text("Hledat aplikace", color = Color.White.copy(alpha = 0.55f), fontSize = 16.sp)
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                            cursorBrush = SolidColor(Color.White),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(filtered, key = { it.key.flat }) { app ->
                        Box(
                            Modifier.pointerInput(app.key) {
                                detectTapGestures(onTap = { onLaunch(app) }, onLongPress = { onMenu(app) })
                            },
                            contentAlignment = Alignment.Center,
                        ) { AppIcon(app, showLabel = true) }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------
// Folder
// ------------------------------------------------------------------------------------------------

@Composable
fun FolderOverlay(
    folder: HomeItem.Folder,
    appMap: Map<AppKey, AppInfo>,
    onClose: () -> Unit,
    onRename: (String) -> Unit,
    onLaunch: (AppKey) -> Unit,
    onRemove: (AppKey) -> Unit,
) {
    var title by remember(folder.id) { mutableStateOf(folder.title) }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.25f))
            .pointerInput(Unit) { detectTapGestures { onClose() } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(24.dp)
                .fillMaxWidth()
                .glass(36.dp, tint = Color.White.copy(alpha = 0.12f))
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BasicTextField(
                value = title,
                onValueChange = { title = it; onRename(it) },
                singleLine = true,
                textStyle = TextStyle(
                    color = Color.White, fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                ),
                cursorBrush = SolidColor(Color.White),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(14.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.heightIn(max = 360.dp),
            ) {
                items(folder.apps, key = { it.flat }) { k ->
                    Box(
                        Modifier
                            .padding(6.dp)
                            .pointerInput(k) {
                                detectTapGestures(onTap = { onLaunch(k) }, onLongPress = { onRemove(k) })
                            },
                        contentAlignment = Alignment.Center,
                    ) { AppIcon(appMap[k], showLabel = true) }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Podržením ikonu vyjmete ze složky", color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
        }
    }
}

// ------------------------------------------------------------------------------------------------
// App context menu
// ------------------------------------------------------------------------------------------------

@Composable
fun AppMenu(
    app: AppInfo,
    hiddenFromHome: Boolean,
    onToggleHome: () -> Unit,
    onInfo: () -> Unit,
    onUninstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.25f))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(270.dp)
                .glass(30.dp, tint = Color.White.copy(alpha = 0.12f))
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(10.dp)
        ) {
            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                AppIcon(app, showLabel = false, iconSize = 40.dp)
                Spacer(Modifier.width(12.dp))
                Text(app.label, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            MenuRow(if (hiddenFromHome) "Přidat na plochu" else "Odebrat z plochy") { onToggleHome(); onDismiss() }
            MenuRow("Informace o aplikaci") { onInfo(); onDismiss() }
            MenuRow("Odinstalovat", Color(0xFFFF6B6B)) { onUninstall(); onDismiss() }
        }
    }
}

@Composable
private fun MenuRow(text: String, color: Color = Color.White, onClick: () -> Unit) {
    Text(
        text, color = color, fontSize = 16.sp,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
    )
}

// ------------------------------------------------------------------------------------------------
// Widget gallery
// ------------------------------------------------------------------------------------------------

@Composable
fun WidgetPicker(
    providers: List<AppWidgetProviderInfo>,
    cellW: Float,
    cellH: Float,
    onPick: (AppWidgetProviderInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    val groups by produceState<List<Pair<String, List<AppWidgetProviderInfo>>>>(emptyList(), providers) {
        value = withContext(Dispatchers.Default) {
            val pm = ctx.packageManager
            providers
                .groupBy { runCatching { it.providerInfo.applicationInfo.loadLabel(pm).toString() }.getOrDefault(it.provider.packageName) }
                .entries.sortedBy { it.key.lowercase() }
                .map { it.key to it.value }
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.30f))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.72f)
                .glass(36.dp, tint = Color.White.copy(alpha = 0.12f))
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(top = 20.dp)
        ) {
            Text("Widgety", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(8.dp))
            LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
                items(groups, key = { it.first }) { (name, list) ->
                    Text(name, color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 8.dp))
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(list, key = { it.provider.flattenToString() + it.profile.hashCode() }) { info ->
                            WidgetCard(info, cellW, cellH) { onPick(info) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetCard(info: AppWidgetProviderInfo, cellW: Float, cellH: Float, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val currentClick by rememberUpdatedState(onClick)
    val preview by produceState<ImageBitmap?>(null, info) {
        value = withContext(Dispatchers.Default) {
            runCatching {
                val d = info.loadPreviewImage(ctx, 0) ?: info.loadIcon(ctx, 0) ?: return@runCatching null
                val w = d.intrinsicWidth.coerceAtLeast(1)
                val h = d.intrinsicHeight.coerceAtLeast(1)
                val s = min(1f, 320f / max(w, h))
                d.toBitmap((w * s).toInt().coerceAtLeast(1), (h * s).toInt().coerceAtLeast(1)).asImageBitmap()
            }.getOrNull()
        }
    }
    val label = remember(info) { runCatching { info.loadLabel(ctx.packageManager) }.getOrDefault("") }
    val (sx, sy) = remember(info, cellW, cellH) { widgetSpan(info, cellW, cellH) }
    Column(
        Modifier
            .width(150.dp)
            .glass(24.dp, shadowRadius = 14.dp)
            .clickable { currentClick() }
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
            preview?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("$sx × $sy", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
    }
}
