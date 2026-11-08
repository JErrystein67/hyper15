package cz.liquidglass.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Rect
import android.net.Uri
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.Collator
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Loads launchable activities through [LauncherApps] (same API Lawnchair/Launcher3 use),
 * including work-profile apps, and keeps them in sync via the package callback.
 */
class AppRepository(context: Context) {
    private val ctx = context.applicationContext
    private val launcherApps = ctx.getSystemService(LauncherApps::class.java)
    private val users = ctx.getSystemService(UserManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val iconCache = ConcurrentHashMap<AppKey, androidx.compose.ui.graphics.ImageBitmap>()
    private val collator = Collator.getInstance(Locale.getDefault())

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps.asStateFlow()

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) = invalidate(packageName)
        override fun onPackageAdded(packageName: String, user: UserHandle) = invalidate(packageName)
        override fun onPackageChanged(packageName: String, user: UserHandle) = invalidate(packageName)
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = reload()
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = reload()
    }

    fun start() {
        launcherApps.registerCallback(callback)
        reload()
    }

    fun reload() {
        scope.launch { _apps.value = loadAll() }
    }

    private fun invalidate(pkg: String) {
        iconCache.keys.toList().filter { it.pkg == pkg }.forEach { iconCache.remove(it) }
        reload()
    }

    private fun loadAll(): List<AppInfo> =
        users.userProfiles.flatMap { profile ->
            val serial = users.getSerialNumberForUser(profile)
            launcherApps.getActivityList(null, profile).map { li ->
                val key = AppKey(li.componentName.packageName, li.componentName.className, serial)
                val icon = iconCache.getOrPut(key) {
                    li.getBadgedIcon(0).toBitmap(ICON_PX, ICON_PX).asImageBitmap()
                }
                AppInfo(key, li.label.toString(), icon)
            }
        }
            .filter { it.key.pkg != ctx.packageName }
            .sortedWith { a, b -> collator.compare(a.label, b.label) }

    private fun userFor(key: AppKey): UserHandle =
        users.getUserForSerialNumber(key.userSerial) ?: Process.myUserHandle()

    fun launch(key: AppKey, bounds: Rect? = null) {
        runCatching {
            launcherApps.startMainActivity(ComponentName(key.pkg, key.cls), userFor(key), bounds, null)
        }
    }

    fun appDetails(key: AppKey) {
        runCatching {
            launcherApps.startAppDetailsActivity(ComponentName(key.pkg, key.cls), userFor(key), null, null)
        }
    }

    fun uninstall(key: AppKey) {
        runCatching {
            ctx.startActivity(
                Intent(Intent.ACTION_DELETE, Uri.fromParts("package", key.pkg, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    private companion object {
        const val ICON_PX = 144
    }
}
