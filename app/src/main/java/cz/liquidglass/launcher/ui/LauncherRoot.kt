package cz.liquidglass.launcher.ui

import android.app.Activity
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.liquidglass.launcher.LauncherViewModel
import cz.liquidglass.launcher.data.HomeItem
import cz.liquidglass.launcher.data.NotificationHub
import cz.liquidglass.launcher.ui.glass.LocalGlassLight
import cz.liquidglass.launcher.ui.glass.rememberTiltLight
import cz.liquidglass.launcher.ui.home.AppDrawer
import cz.liquidglass.launcher.ui.home.AppMenu
import cz.liquidglass.launcher.ui.home.FolderOverlay
import cz.liquidglass.launcher.ui.home.HomeScreen
import cz.liquidglass.launcher.ui.home.WidgetPicker
import cz.liquidglass.launcher.ui.panel.ControlPanel
import kotlin.math.max

@Composable
fun LauncherRoot(
    vm: LauncherViewModel,
    activity: Activity,
    onAddWidget: (AppWidgetProviderInfo) -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val scope = rememberCoroutineScope()
    val reveal = remember { RevealState(scope) }

    val state by vm.state.collectAsStateWithLifecycle()
    val apps by vm.apps.apps.collectAsStateWithLifecycle()
    val notifs by NotificationHub.items.collectAsStateWithLifecycle()
    var notifAccess by remember { mutableStateOf(NotificationHub.isEnabled(context)) }
    LifecycleResumeEffect(Unit) {
        notifAccess = NotificationHub.isEnabled(context)
        vm.controls.refresh()
        onPauseOrDispose { }
    }

    val appMap = remember(apps) { apps.associateBy { it.key } }
    val iconsByPkg = remember(apps) { apps.distinctBy { it.key.pkg }.associateBy { it.key.pkg } }
    val light = rememberTiltLight()

    val screenH = with(density) { config.screenHeightDp.dp.toPx() }
    val screenW = with(density) { config.screenWidthDp.dp.toPx() }
    val range = screenH * 0.75f

    val panelVisible by remember { derivedStateOf { reveal.value > 0.001f } }
    val drawerVisible by remember { derivedStateOf { reveal.value < -0.001f } }
    val folder = state.items.firstOrNull { it.id == state.openFolderId } as? HomeItem.Folder
    val menuApp = state.menuApp?.let { appMap[it] }
    val overlayOpen = folder != null || state.showPicker || menuApp != null
    val overlayNow by rememberUpdatedState(overlayOpen)

    // window-level backdrop blur behind the sheets (API 31+)
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 31) {
            snapshotFlow {
                val p = max(reveal.panel, reveal.drawer)
                (max(p, if (overlayNow) 0.6f else 0f) * 70).toInt()
            }.collect { r -> runCatching { activity.window.setBackgroundBlurRadius(r) } }
        }
    }

    LaunchedEffect(Unit) { vm.homeEvents.collect { reveal.animateTo(0f) } }

    BackHandler(enabled = panelVisible || drawerVisible || state.edit || overlayOpen) {
        when {
            menuApp != null -> vm.closeMenu()
            state.showPicker -> vm.closePicker()
            folder != null -> vm.closeFolder()
            panelVisible || drawerVisible -> reveal.animateTo(0f)
            state.edit -> vm.setEdit(false)
        }
    }

    val homeDrag = rememberDraggableState { reveal.dragBy(it / range) }

    CompositionLocalProvider(
        LocalGlassLight provides light,
        LocalContentColor provides Color.White,
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val p = max(reveal.panel, reveal.drawer)
                        val s = 1f - 0.06f * p
                        scaleX = s; scaleY = s
                        alpha = 1f - 0.45f * p
                    }
                    .draggable(
                        state = homeDrag,
                        orientation = Orientation.Vertical,
                        enabled = !state.edit && !overlayOpen,
                        onDragStarted = { start -> reveal.panelPage = if (start.x < screenW / 2f) 0 else 1 },
                        onDragStopped = { reveal.settle(it / range) },
                    )
            ) {
                HomeScreen(vm)
            }

            if (drawerVisible) {
                AppDrawer(
                    reveal = reveal,
                    apps = apps,
                    onLaunch = { vm.launchApp(it.key); reveal.animateTo(0f) },
                    onMenu = { vm.showMenu(it.key) },
                )
            }

            if (panelVisible) {
                ControlPanel(
                    reveal = reveal,
                    controls = vm.controls,
                    notifications = notifs,
                    iconsByPkg = iconsByPkg,
                    notifAccess = notifAccess,
                    onGrantAccess = {
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    },
                    onClearAll = { NotificationHub.clearAll() },
                    onDismissNotif = { NotificationHub.dismiss(it.key) },
                    onOpenNotif = { n ->
                        runCatching { n.intent?.send() }
                        reveal.animateTo(0f)
                    },
                )
            }

            folder?.let { f ->
                FolderOverlay(
                    folder = f,
                    appMap = appMap,
                    onClose = vm::closeFolder,
                    onRename = { vm.renameFolder(f.id, it) },
                    onLaunch = vm::launchApp,
                    onRemove = { vm.removeFromFolder(f.id, it) },
                )
            }

            if (state.showPicker) {
                val providers = remember { vm.widgets.providers() }
                WidgetPicker(
                    providers = providers,
                    cellW = vm.cellW,
                    cellH = vm.cellH,
                    onPick = { onAddWidget(it) },
                    onDismiss = vm::closePicker,
                )
            }

            menuApp?.let { app ->
                val hidden = app.key in state.hidden
                AppMenu(
                    app = app,
                    hiddenFromHome = hidden,
                    onToggleHome = { if (hidden) vm.unhide(app.key) else vm.hide(app.key) },
                    onInfo = { vm.apps.appDetails(app.key) },
                    onUninstall = { vm.apps.uninstall(app.key) },
                    onDismiss = vm::closeMenu,
                )
            }
        }
    }
}
