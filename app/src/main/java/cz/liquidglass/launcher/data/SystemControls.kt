package cz.liquidglass.launcher.data

import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import kotlin.math.roundToInt

/**
 * State + actions for the quick settings. Android 10+ forbids third-party apps from toggling Wi-Fi,
 * Bluetooth, airplane mode or mobile data directly, so those open the official system panel/screen;
 * torch, volume, (with WRITE_SETTINGS) brightness / rotation and (with policy access) DND work in place.
 */
class SystemControls(context: Context) {
    private val ctx = context.applicationContext
    private val cr = ctx.contentResolver
    private val audio = ctx.getSystemService(AudioManager::class.java)
    private val wifiManager = ctx.getSystemService(WifiManager::class.java)
    private val btAdapter = ctx.getSystemService(BluetoothManager::class.java)?.adapter
    private val nm = ctx.getSystemService(NotificationManager::class.java)
    private val camera = ctx.getSystemService(CameraManager::class.java)
    private val main = Handler(Looper.getMainLooper())

    private val torchId: String? = runCatching {
        camera.cameraIdList.firstOrNull { id ->
            val c = camera.getCameraCharacteristics(id)
            c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        }
    }.getOrNull()

    var wifi by mutableStateOf(false); private set
    var bluetooth by mutableStateOf(false); private set
    var torch by mutableStateOf(false); private set
    var dnd by mutableStateOf(false); private set
    var airplane by mutableStateOf(false); private set
    var autoRotate by mutableStateOf(true); private set
    var brightness by mutableFloatStateOf(0.5f); private set
    var volume by mutableFloatStateOf(0.5f); private set

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) = refresh()
    }
    private val observer = object : ContentObserver(main) {
        override fun onChange(selfChange: Boolean) = refresh()
    }
    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == torchId) torch = enabled
        }
    }
    private var started = false

    fun start() {
        if (started) return
        started = true
        val f = IntentFilter().apply {
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction("android.media.VOLUME_CHANGED_ACTION")
            addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
            addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
        }
        ContextCompat.registerReceiver(ctx, receiver, f, ContextCompat.RECEIVER_NOT_EXPORTED)
        cr.registerContentObserver(Settings.System.CONTENT_URI, true, observer)
        runCatching { camera.registerTorchCallback(torchCallback, main) }
        refresh()
    }

    fun stop() {
        if (!started) return
        started = false
        runCatching { ctx.unregisterReceiver(receiver) }
        cr.unregisterContentObserver(observer)
        runCatching { camera.unregisterTorchCallback(torchCallback) }
    }

    fun refresh() {
        wifi = runCatching { wifiManager.isWifiEnabled }.getOrDefault(false)
        bluetooth = btAdapter?.isEnabled == true
        dnd = nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
        airplane = Settings.Global.getInt(cr, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
        autoRotate = Settings.System.getInt(cr, Settings.System.ACCELEROMETER_ROTATION, 1) == 1
        brightness = (Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f).coerceIn(0f, 1f)
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
    }

    fun toggleWifi() = open(Settings.Panel.ACTION_WIFI)
    fun toggleBluetooth() = open(Settings.ACTION_BLUETOOTH_SETTINGS)
    fun toggleAirplane() = open(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
    fun openSettings() = open(Settings.ACTION_SETTINGS)

    fun toggleTorch() {
        val id = torchId ?: return
        runCatching { camera.setTorchMode(id, !torch) }
    }

    fun toggleDnd() {
        if (nm.isNotificationPolicyAccessGranted) {
            nm.setInterruptionFilter(
                if (dnd) NotificationManager.INTERRUPTION_FILTER_ALL else NotificationManager.INTERRUPTION_FILTER_PRIORITY
            )
        } else open(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
    }

    fun toggleRotation() {
        if (Settings.System.canWrite(ctx)) {
            Settings.System.putInt(cr, Settings.System.ACCELEROMETER_ROTATION, if (autoRotate) 0 else 1)
            refresh()
        } else requestWriteSettings()
    }

    fun setBrightness(v: Float) {
        if (!Settings.System.canWrite(ctx)) { requestWriteSettings(); return }
        brightness = v.coerceIn(0f, 1f)
        Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, (brightness * 255).roundToInt().coerceIn(1, 255))
    }

    fun setVolume(v: Float) {
        volume = v.coerceIn(0f, 1f)
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, (volume * max).roundToInt(), 0)
    }

    private fun open(action: String) {
        runCatching { ctx.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    private fun requestWriteSettings() {
        runCatching {
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
