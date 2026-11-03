package com.nova.os.launcher.system

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import android.provider.AlarmClock
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale
import kotlin.math.roundToInt

fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

data class DeviceStats(
    val storageUsed: Long,
    val storageTotal: Long,
    val ramUsed: Long,
    val ramTotal: Long,
)

/** Thin wrappers around real Android APIs. Everything that needs special access falls back to a system screen. */
object SystemActions {

    // ---------- Intents ----------
    fun start(context: Context, intent: Intent, fallback: Intent? = null): Boolean {
        fun tryStart(i: Intent): Boolean = try {
            context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: Exception) {
            false
        }
        return tryStart(intent) || (fallback != null && tryStart(fallback))
    }

    fun openWifi(context: Context) {
        val main = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Intent(Settings.Panel.ACTION_WIFI)
        } else {
            Intent(Settings.ACTION_WIFI_SETTINGS)
        }
        start(context, main, Intent(Settings.ACTION_WIFI_SETTINGS))
    }

    fun openBluetooth(context: Context) =
        start(context, Intent(Settings.ACTION_BLUETOOTH_SETTINGS), Intent(Settings.ACTION_SETTINGS))

    fun openAirplane(context: Context) =
        start(context, Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS), Intent(Settings.ACTION_SETTINGS))

    fun openAppInfo(context: Context, pkg: String) = start(
        context,
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")),
        Intent(Settings.ACTION_SETTINGS)
    )

    fun uninstall(context: Context, pkg: String) =
        start(context, Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")))

    fun openWriteSettings(context: Context) = start(
        context,
        Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}")),
        Intent(Settings.ACTION_SETTINGS)
    )

    fun openDefaultHomeSettings(context: Context) =
        start(context, Intent(Settings.ACTION_HOME_SETTINGS), Intent(Settings.ACTION_SETTINGS))

    fun openSystemWallpaper(context: Context) =
        start(context, Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "배경화면 선택"))

    fun openAlarms(context: Context) =
        start(context, Intent(AlarmClock.ACTION_SHOW_ALARMS))

    fun webSearch(context: Context, query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        start(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(q))))
    }

    fun shareText(context: Context, text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        start(context, Intent.createChooser(send, "공유"))
    }

    @Suppress("DEPRECATION")
    fun isDefaultHome(context: Context): Boolean = try {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        context.packageManager.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName == context.packageName
    } catch (e: Exception) {
        false
    }

    // ---------- Connectivity state ----------
    @Suppress("DEPRECATION")
    fun isWifiEnabled(context: Context): Boolean = try {
        (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager)?.isWifiEnabled == true
    } catch (e: Exception) {
        false
    }

    @SuppressLint("MissingPermission")
    fun isBluetoothEnabled(context: Context): Boolean = try {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter?.isEnabled == true
    } catch (e: Exception) {
        false
    }

    // ---------- Display ----------
    fun canWriteSettings(context: Context): Boolean = try {
        Settings.System.canWrite(context)
    } catch (e: Exception) {
        false
    }

    fun readBrightness(context: Context): Float = try {
        Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
    } catch (e: Exception) {
        0.5f
    }

    /** Changes the real system brightness. Returns false when the WRITE_SETTINGS permission is missing. */
    fun setSystemBrightness(context: Context, value: Float): Boolean {
        if (!canWriteSettings(context)) return false
        return try {
            val cr = context.contentResolver
            Settings.System.putInt(
                cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            Settings.System.putInt(
                cr, Settings.System.SCREEN_BRIGHTNESS, (value * 255f).roundToInt().coerceIn(1, 255)
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Fallback: brightness for this window only (no permission needed). */
    fun setWindowBrightness(context: Context, value: Float) {
        val window = context.findActivity()?.window ?: return
        val lp = window.attributes
        lp.screenBrightness = value.coerceIn(0.02f, 1f)
        window.attributes = lp
    }

    fun isAutoRotate(context: Context): Boolean = try {
        Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 1) == 1
    } catch (e: Exception) {
        true
    }

    fun setAutoRotate(context: Context, enabled: Boolean): Boolean {
        if (!canWriteSettings(context)) return false
        return try {
            Settings.System.putInt(
                context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (enabled) 1 else 0
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    // ---------- Volume ----------
    fun readVolume(context: Context): Float = try {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        am.getStreamVolume(AudioManager.STREAM_MUSIC) / max.toFloat()
    } catch (e: Exception) {
        0.5f
    }

    fun setVolume(context: Context, value: Float) {
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            am.setStreamVolume(AudioManager.STREAM_MUSIC, (value * max).roundToInt().coerceIn(0, max), 0)
        } catch (e: Exception) {
            // Some devices (Do Not Disturb) may refuse.
        }
    }

    // ---------- Device info ----------
    fun readStats(context: Context): DeviceStats {
        var storageTotal = 0L
        var storageFree = 0L
        try {
            val st = StatFs(Environment.getDataDirectory().path)
            storageTotal = st.blockCountLong * st.blockSizeLong
            storageFree = st.availableBlocksLong * st.blockSizeLong
        } catch (e: Exception) {
            // ignore
        }
        var ramTotal = 0L
        var ramUsed = 0L
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val mi = ActivityManager.MemoryInfo()
            am.getMemoryInfo(mi)
            ramTotal = mi.totalMem
            ramUsed = mi.totalMem - mi.availMem
        } catch (e: Exception) {
            // ignore
        }
        return DeviceStats(storageTotal - storageFree, storageTotal, ramUsed, ramTotal)
    }

    fun formatBytes(bytes: Long): String {
        val gb = bytes / 1_073_741_824.0
        return if (gb >= 1) String.format(Locale.US, "%.1f GB", gb)
        else String.format(Locale.US, "%.0f MB", bytes / 1_048_576.0)
    }
}

/** Flashlight via CameraManager.setTorchMode (no camera permission needed). */
class TorchController(context: Context) {
    private val manager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    private val cameraId: String? = findTorchCamera()

    var isOn by mutableStateOf(false)
        private set

    val available: Boolean get() = cameraId != null

    init {
        if (cameraId != null) {
            try {
                manager?.registerTorchCallback(object : CameraManager.TorchCallback() {
                    override fun onTorchModeChanged(id: String, enabled: Boolean) {
                        if (id == cameraId) isOn = enabled
                    }
                }, Handler(Looper.getMainLooper()))
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    private fun findTorchCamera(): String? {
        val m = manager ?: return null
        return try {
            m.cameraIdList.firstOrNull { id ->
                m.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (e: Exception) {
            null
        }
    }

    fun toggle(): Boolean {
        val id = cameraId ?: return false
        return try {
            manager?.setTorchMode(id, !isOn)
            true
        } catch (e: Exception) {
            false
        }
    }
}
