package cz.liquidglass.launcher

import android.app.Application
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cz.liquidglass.launcher.data.AppInfo
import cz.liquidglass.launcher.data.AppKey
import cz.liquidglass.launcher.data.AppRepository
import cz.liquidglass.launcher.data.COLS
import cz.liquidglass.launcher.data.DOCK
import cz.liquidglass.launcher.data.DOCK_SLOTS
import cz.liquidglass.launcher.data.HomeItem
import cz.liquidglass.launcher.data.LayoutStore
import cz.liquidglass.launcher.data.ROWS
import cz.liquidglass.launcher.data.SystemControls
import cz.liquidglass.launcher.data.WidgetController
import cz.liquidglass.launcher.data.newId
import cz.liquidglass.launcher.data.overlaps
import cz.liquidglass.launcher.data.widgetSpan
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** true = long-press on empty space immediately opens the widget gallery, false = only edit mode. */
const val LONG_PRESS_OPENS_PICKER = false

data class LauncherUiState(
    val items: List<HomeItem> = emptyList(),
    val edit: Boolean = false,
    val openFolderId: String? = null,
    val showPicker: Boolean = false,
    val menuApp: AppKey? = null,
    val hidden: Set<AppKey> = emptySet(),
    val ready: Boolean = false,
)

class LauncherViewModel(app: Application) : AndroidViewModel(app) {
    val apps = AppRepository(app)
    val widgets = WidgetController(app)
    val controls = SystemControls(app)
    private val store = LayoutStore(app)

    private var hidden: Set<AppKey> = store.loadHidden()
    private val firstRun = !store.hasSaved()

    private val _state = MutableStateFlow(LauncherUiState(hidden = hidden))
    val state: StateFlow<LauncherUiState> = _state.asStateFlow()

    private val _home = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val homeEvents: SharedFlow<Unit> = _home.asSharedFlow()

    /** Reported by the UI (px) – used to size newly added widgets. */
    var cellW = 0f
    var cellH = 0f
    var visiblePage = 0

    private val items: List<HomeItem> get() = _state.value.items

    init {
        apps.start()
        viewModelScope.launch {
            val initial = apps.apps.first { it.isNotEmpty() }
            val start = if (firstRun) seedDock(initial) else store.load()
            _state.update { it.copy(items = start, ready = true) }
            apps.apps.collect { reconcile(it) }
        }
    }

    // ---------------------------------------------------------------- persistence helpers

    private fun setItems(list: List<HomeItem>) {
        _state.update { it.copy(items = list) }
        store.save(list)
    }

    private fun newId2() = newId()

    // ---------------------------------------------------------------- app list <-> layout

    private fun seedDock(list: List<AppInfo>): List<HomeItem> {
        val pm = getApplication<Application>().packageManager
        val intents = listOf(
            Intent(Intent.ACTION_DIAL),
            Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://")),
            Intent(MediaStore.ACTION_IMAGE_CAPTURE),
        )
        val picked = mutableListOf<AppInfo>()
        for (i in intents) {
            val pkg = pm.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName ?: continue
            list.firstOrNull { it.key.pkg == pkg && it !in picked }?.let(picked::add)
        }
        return picked.take(DOCK_SLOTS).mapIndexed { i, a -> HomeItem.App(newId2(), a.key, DOCK, i, 0) }
    }

    /** Drops uninstalled apps, places new ones in the first free cell. */
    private fun reconcile(list: List<AppInfo>) {
        if (!_state.value.ready) return
        val keys = list.mapTo(HashSet()) { it.key }
        var cur: List<HomeItem> = items.mapNotNull { item ->
            when (item) {
                is HomeItem.App -> item.takeIf { it.key in keys && it.key !in hidden }
                is HomeItem.Folder -> {
                    val a = item.apps.filter { it in keys && it !in hidden }
                    when {
                        a.size >= 2 -> item.copy(apps = a)
                        a.size == 1 -> HomeItem.App(newId2(), a[0], item.page, item.x, item.y)
                        else -> null
                    }
                }
                is HomeItem.Widget -> item
            }
        }
        val placed = cur.flatMap {
            when (it) {
                is HomeItem.App -> listOf(it.key)
                is HomeItem.Folder -> it.apps
                else -> emptyList()
            }
        }.toSet()
        list.forEach { a ->
            if (a.key !in placed && a.key !in hidden) {
                val (p, x, y) = findFree(cur, 1, 1, 0)
                cur = cur + HomeItem.App(newId2(), a.key, p, x, y)
            }
        }
        if (cur != items) setItems(cur)
    }

    private fun findFree(list: List<HomeItem>, sx: Int, sy: Int, startPage: Int): Triple<Int, Int, Int> {
        val maxPage = list.filter { it.page >= 0 }.maxOfOrNull { it.page } ?: -1
        val order = (startPage..maxPage + 1) + (0 until startPage)
        for (p in order) for (y in 0..ROWS - sy) for (x in 0..COLS - sx) {
            if (list.none { overlaps(it, p, x, y, sx, sy) }) return Triple(p, x, y)
        }
        return Triple(maxPage + 1, 0, 0)
    }

    private fun compact(list: List<HomeItem>): List<HomeItem> {
        val used = list.filter { it.page >= 0 }.map { it.page }.distinct().sorted()
        val map = used.withIndex().associate { it.value to it.index }
        return list.map { if (it.page >= 0) it.at(map.getValue(it.page), it.x, it.y) else it }
    }

    // ---------------------------------------------------------------- edit mode

    fun setEdit(on: Boolean) {
        if (_state.value.edit == on) return
        _state.update { it.copy(edit = on) }
        if (!on) setItems(compact(items))
    }

    fun onBackgroundLongPress() {
        setEdit(true)
        if (LONG_PRESS_OPENS_PICKER) openPicker()
    }

    fun onWidgetLongPress() = onBackgroundLongPress()

    fun onHomePressed() {
        _state.update { it.copy(openFolderId = null, showPicker = false, menuApp = null) }
        setEdit(false)
        _home.tryEmit(Unit)
    }

    // ---------------------------------------------------------------- drag & drop

    fun drop(id: String, page: Int, x: Int, y: Int) {
        val item = items.find { it.id == id } ?: return

        if (page == DOCK) {
            if (item is HomeItem.Widget || x !in 0 until DOCK_SLOTS) return
            if (items.any { it.id != id && it.page == DOCK && it.x == x }) return
            setItems(items.map { if (it.id == id) it.at(DOCK, x, 0) else it })
            return
        }

        val sx = item.spanX
        val sy = item.spanY
        if (x < 0 || y < 0 || x + sx > COLS || y + sy > ROWS) return
        val hit = items.filter { it.id != id && overlaps(it, page, x, y, sx, sy) }
        if (hit.isEmpty()) {
            setItems(items.map { if (it.id == id) it.at(page, x, y) else it })
            return
        }
        val target = hit.singleOrNull() ?: return
        if (item !is HomeItem.App) return
        when (target) {
            is HomeItem.App -> {
                val folder = HomeItem.Folder(newId2(), "Složka", listOf(target.key, item.key), page, target.x, target.y)
                setItems(items.filter { it.id != id && it.id != target.id } + folder)
            }
            is HomeItem.Folder -> setItems(
                items.filter { it.id != id }
                    .map { if (it.id == target.id) target.copy(apps = target.apps + item.key) else it }
            )
            else -> Unit
        }
    }

    fun resizeWidget(id: String, spanX: Int, spanY: Int) {
        val w = items.find { it.id == id } as? HomeItem.Widget ?: return
        val nx = spanX.coerceIn(1, COLS - w.x)
        val ny = spanY.coerceIn(1, ROWS - w.y)
        if (items.any { it.id != id && overlaps(it, w.page, w.x, w.y, nx, ny) }) return
        setItems(items.map { if (it.id == id) w.copy(spanX = nx, spanY = ny) else it })
    }

    fun removeItem(id: String) {
        val item = items.find { it.id == id } ?: return
        when (item) {
            is HomeItem.Widget -> {
                widgets.delete(item.widgetId)
                setItems(items - item)
            }
            is HomeItem.App -> hide(item.key)
            is HomeItem.Folder -> { // dissolve
                var cur: List<HomeItem> = items - item
                item.apps.forEachIndexed { i, k ->
                    val (p, x, y) = if (i == 0) Triple(item.page, item.x, item.y) else findFree(cur, 1, 1, item.page)
                    cur = cur + HomeItem.App(newId2(), k, p, x, y)
                }
                setItems(cur)
            }
        }
    }

    // ---------------------------------------------------------------- hide / show on home

    fun hide(key: AppKey) {
        hidden = hidden + key
        store.saveHidden(hidden)
        _state.update { it.copy(hidden = hidden) }
        reconcile(apps.apps.value)
    }

    fun unhide(key: AppKey) {
        hidden = hidden - key
        store.saveHidden(hidden)
        _state.update { it.copy(hidden = hidden) }
        reconcile(apps.apps.value)
    }

    // ---------------------------------------------------------------- folders

    fun openFolder(id: String) = _state.update { it.copy(openFolderId = id) }
    fun closeFolder() = _state.update { it.copy(openFolderId = null) }

    fun renameFolder(id: String, title: String) {
        setItems(items.map { if (it.id == id && it is HomeItem.Folder) it.copy(title = title) else it })
    }

    fun removeFromFolder(folderId: String, key: AppKey) {
        val f = items.find { it.id == folderId } as? HomeItem.Folder ?: return
        val rest = f.apps - key
        val without = items.filter { it.id != folderId }
        val base: List<HomeItem> = when {
            rest.size >= 2 -> without + f.copy(apps = rest)
            rest.size == 1 -> without + HomeItem.App(newId2(), rest[0], f.page, f.x, f.y)
            else -> without
        }
        val (p, x, y) = findFree(base, 1, 1, maxOf(f.page, 0))
        setItems(base + HomeItem.App(newId2(), key, p, x, y))
        closeFolder()
    }

    // ---------------------------------------------------------------- widgets

    fun openPicker() = _state.update { it.copy(showPicker = true) }
    fun closePicker() = _state.update { it.copy(showPicker = false) }

    fun placeWidget(widgetId: Int, info: AppWidgetProviderInfo) {
        val (sx, sy) = widgetSpan(info, cellW, cellH)
        val (p, x, y) = findFree(items, sx, sy, visiblePage)
        setItems(
            items + HomeItem.Widget(newId2(), widgetId, info.provider.flattenToString(), p, x, y, sx, sy)
        )
        _state.update { it.copy(showPicker = false) }
    }

    // ---------------------------------------------------------------- apps

    fun launchApp(key: AppKey) {
        apps.launch(key)
        closeFolder()
    }

    fun showMenu(key: AppKey) = _state.update { it.copy(menuApp = key) }
    fun closeMenu() = _state.update { it.copy(menuApp = null) }

    override fun onCleared() {
        controls.stop()
        widgets.stop()
        super.onCleared()
    }
}
