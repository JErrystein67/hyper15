package cz.liquidglass.launcher.data

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import java.util.UUID

/** Pseudo-page of the dock. Dock items use x = slot index. */
const val DOCK = -1
const val COLS = 4
const val ROWS = 6
const val DOCK_SLOTS = 4

fun newId(): String = UUID.randomUUID().toString()

@Immutable
data class AppKey(val pkg: String, val cls: String, val userSerial: Long) {
    val flat: String get() = "$pkg/$cls@$userSerial"

    companion object {
        fun parse(s: String): AppKey {
            val at = s.lastIndexOf('@')
            val slash = s.indexOf('/')
            return AppKey(s.substring(0, slash), s.substring(slash + 1, at), s.substring(at + 1).toLong())
        }
    }
}

@Immutable
data class AppInfo(val key: AppKey, val label: String, val icon: ImageBitmap)

sealed interface HomeItem {
    val id: String
    val page: Int
    val x: Int
    val y: Int
    val spanX: Int
    val spanY: Int
    fun at(page: Int, x: Int, y: Int): HomeItem

    data class App(
        override val id: String,
        val key: AppKey,
        override val page: Int,
        override val x: Int,
        override val y: Int,
    ) : HomeItem {
        override val spanX: Int get() = 1
        override val spanY: Int get() = 1
        override fun at(page: Int, x: Int, y: Int): HomeItem = copy(page = page, x = x, y = y)
    }

    data class Folder(
        override val id: String,
        val title: String,
        val apps: List<AppKey>,
        override val page: Int,
        override val x: Int,
        override val y: Int,
    ) : HomeItem {
        override val spanX: Int get() = 1
        override val spanY: Int get() = 1
        override fun at(page: Int, x: Int, y: Int): HomeItem = copy(page = page, x = x, y = y)
    }

    data class Widget(
        override val id: String,
        val widgetId: Int,
        val provider: String,
        override val page: Int,
        override val x: Int,
        override val y: Int,
        override val spanX: Int,
        override val spanY: Int,
    ) : HomeItem {
        override fun at(page: Int, x: Int, y: Int): HomeItem = copy(page = page, x = x, y = y)
    }
}

fun List<HomeItem>.homePageCount(): Int =
    (filter { it.page >= 0 }.maxOfOrNull { it.page } ?: 0) + 1

fun overlaps(o: HomeItem, page: Int, x: Int, y: Int, sx: Int, sy: Int): Boolean =
    o.page == page && !(x + sx <= o.x || o.x + o.spanX <= x || y + sy <= o.y || o.y + o.spanY <= y)
