package cz.liquidglass.launcher.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Persists the home layout (pages, dock, folders, widgets) as JSON in SharedPreferences. */
class LayoutStore(context: Context) {
    private val prefs = context.getSharedPreferences("layout_v1", Context.MODE_PRIVATE)

    fun hasSaved(): Boolean = prefs.contains("items")

    fun load(): List<HomeItem> {
        val raw = prefs.getString("items", null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { parse(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    fun save(items: List<HomeItem>) {
        val arr = JSONArray()
        items.forEach { arr.put(toJson(it)) }
        prefs.edit().putString("items", arr.toString()).apply()
    }

    fun loadHidden(): Set<AppKey> =
        (prefs.getStringSet("hidden", emptySet()) ?: emptySet())
            .mapNotNull { runCatching { AppKey.parse(it) }.getOrNull() }
            .toSet()

    fun saveHidden(hidden: Set<AppKey>) {
        prefs.edit().putStringSet("hidden", hidden.map { it.flat }.toSet()).apply()
    }

    private fun toJson(i: HomeItem): JSONObject = JSONObject().apply {
        put("id", i.id); put("page", i.page); put("x", i.x); put("y", i.y)
        when (i) {
            is HomeItem.App -> { put("t", "app"); put("k", i.key.flat) }
            is HomeItem.Folder -> {
                put("t", "folder"); put("title", i.title)
                put("apps", JSONArray(i.apps.map { it.flat }))
            }
            is HomeItem.Widget -> {
                put("t", "widget"); put("wid", i.widgetId); put("p", i.provider)
                put("sx", i.spanX); put("sy", i.spanY)
            }
        }
    }

    private fun parse(o: JSONObject): HomeItem {
        val id = o.getString("id"); val page = o.getInt("page")
        val x = o.getInt("x"); val y = o.getInt("y")
        return when (o.getString("t")) {
            "app" -> HomeItem.App(id, AppKey.parse(o.getString("k")), page, x, y)
            "folder" -> {
                val a = o.getJSONArray("apps")
                HomeItem.Folder(id, o.getString("title"), (0 until a.length()).map { AppKey.parse(a.getString(it)) }, page, x, y)
            }
            else -> HomeItem.Widget(id, o.getInt("wid"), o.optString("p"), page, x, y, o.getInt("sx"), o.getInt("sy"))
        }
    }
}
