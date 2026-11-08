package cz.liquidglass.launcher

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import cz.liquidglass.launcher.ui.LauncherRoot
import cz.liquidglass.launcher.ui.theme.LauncherTheme

class MainActivity : ComponentActivity() {
    private val vm: LauncherViewModel by viewModels()

    private var pendingId = -1
    private var pendingInfo: AppWidgetProviderInfo? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            LauncherTheme {
                LauncherRoot(vm = vm, activity = this, onAddWidget = ::addWidget)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        vm.widgets.start()
        vm.controls.start()
    }

    override fun onStop() {
        vm.widgets.stop()
        vm.controls.stop()
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) vm.onHomePressed()
    }

    // ------------------------------------------------------------ widget flow: allocate -> bind -> configure -> place

    fun addWidget(info: AppWidgetProviderInfo) {
        val id = vm.widgets.allocate()
        pendingId = id
        pendingInfo = info
        if (vm.widgets.manager.bindAppWidgetIdIfAllowed(id, info.profile, info.provider, null)) {
            afterBind(id, info)
        } else {
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile)
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQ_BIND)
        }
    }

    private fun afterBind(id: Int, info: AppWidgetProviderInfo) {
        if (info.configure != null) vm.widgets.configure(this, id, REQ_CONFIGURE) else commit(id, info)
    }

    private fun commit(id: Int, info: AppWidgetProviderInfo) {
        vm.placeWidget(id, info)
        pendingId = -1
        pendingInfo = null
    }

    private fun cancelPending(id: Int) {
        vm.widgets.delete(id)
        pendingId = -1
        pendingInfo = null
    }

    @Deprecated("Required by AppWidgetHost.startAppWidgetConfigureActivityForResult")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        val info = pendingInfo
        val id = pendingId
        if (info == null || id < 0) return
        when (requestCode) {
            REQ_BIND -> if (resultCode == RESULT_OK) afterBind(id, info) else cancelPending(id)
            REQ_CONFIGURE -> if (resultCode == RESULT_OK) commit(id, info) else cancelPending(id)
        }
    }

    private companion object {
        const val REQ_BIND = 1001
        const val REQ_CONFIGURE = 1002
    }
}
