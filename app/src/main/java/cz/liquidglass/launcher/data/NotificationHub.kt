package cz.liquidglass.launcher.data

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NotifItem(
    val key: String,
    val pkg: String,
    val title: String,
    val text: String,
    val time: Long,
    val intent: PendingIntent?,
    val clearable: Boolean,
)

/** Feeds the notification half of the pull-down panel. The user must grant notification access once. */
class NotificationHub : NotificationListenerService() {

    override fun onListenerConnected() {
        instance = this
        refresh()
    }

    override fun onListenerDisconnected() {
        instance = null
        _items.value = emptyList()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = refresh()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = refresh()

    private fun refresh() {
        val active = runCatching { activeNotifications?.toList() }.getOrNull().orEmpty()
        _items.value = active
            .filter { it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0 && it.packageName != packageName }
            .sortedByDescending { it.postTime }
            .map { n ->
                val e = n.notification.extras
                NotifItem(
                    key = n.key,
                    pkg = n.packageName,
                    title = e.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
                    text = (e.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: e.getCharSequence(Notification.EXTRA_TEXT))
                        ?.toString().orEmpty(),
                    time = n.postTime,
                    intent = n.notification.contentIntent,
                    clearable = n.isClearable,
                )
            }
    }

    companion object {
        private val _items = MutableStateFlow<List<NotifItem>>(emptyList())
        val items: StateFlow<List<NotifItem>> = _items.asStateFlow()

        @Volatile
        private var instance: NotificationHub? = null

        fun isEnabled(context: Context): Boolean =
            NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

        fun dismiss(key: String) { runCatching { instance?.cancelNotification(key) } }
        fun clearAll() { runCatching { instance?.cancelAllNotifications() } }
    }
}
