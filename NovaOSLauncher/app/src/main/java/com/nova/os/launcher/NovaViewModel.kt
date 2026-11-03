
package com.nova.os.launcher

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.media.RingtoneManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nova.os.launcher.data.AppInfo
import com.nova.os.launcher.data.AppRepository
import com.nova.os.launcher.data.BatteryState
import com.nova.os.launcher.data.CalcState
import com.nova.os.launcher.data.Note
import com.nova.os.launcher.data.NotesRepository
import com.nova.os.launcher.data.NovaSettings
import com.nova.os.launcher.data.Overlay
import com.nova.os.launcher.data.SettingsRepository
import com.nova.os.launcher.data.SortMode
import com.nova.os.launcher.data.WeatherRepository
import com.nova.os.launcher.data.WeatherState
import com.nova.os.launcher.data.WeatherStatus
import com.nova.os.launcher.system.TorchController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/** Stopwatch based on timestamps, so it keeps running while other screens are open. */
class StopwatchState {
    var running by mutableStateOf(false)
        private set
    private var accMs by mutableLongStateOf(0L)
    private var startedAt = 0L
    val laps = mutableStateListOf<Long>()

    fun elapsed(now: Long = SystemClock.elapsedRealtime()): Long =
        (accMs + if (running) now - startedAt else 0L).coerceAtLeast(0L)

    fun toggle() {
        if (running) {
            accMs += SystemClock.elapsedRealtime() - startedAt
            running = false
        } else {
            startedAt = SystemClock.elapsedRealtime()
            running = true
        }
    }

    fun lap() {
        if (running) laps.add(0, elapsed())
    }

    fun reset() {
        running = false
        accMs = 0L
        startedAt = 0L
        laps.clear()
    }
}

/** Countdown timer driven by a coroutine in the ViewModel scope. */
class TimerState(private val scope: CoroutineScope, private val onFinished: () -> Unit) {
    var totalMs by mutableLongStateOf(0L)
        private set
    var remainingMs by mutableLongStateOf(0L)
        private set
    var running by mutableStateOf(false)
        private set
    var finished by mutableStateOf(false)
        private set
    private var endAt = 0L
    private var job: Job? = null

    fun addSeconds(sec: Int) {
        if (running) return
        totalMs = (totalMs + sec * 1000L).coerceIn(0L, 99L * 3600_000L)
        remainingMs = totalMs
        finished = false
    }

    fun start() {
        if (running || remainingMs <= 0L) return
        finished = false
        endAt = SystemClock.elapsedRealtime() + remainingMs
        running = true
        job = scope.launch {
            while (isActive) {
                val left = endAt - SystemClock.elapsedRealtime()
                if (left <= 0L) {
                    remainingMs = 0L
                    running = false
                    finished = true
                    onFinished()
                    break
                }
                remainingMs = left
                delay(100)
            }
        }
    }

    fun pause() {
        if (!running) return
        job?.cancel()
        remainingMs = (endAt - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        running = false
    }

    fun reset() {
        job?.cancel()
        running = false
        finished = false
        remainingMs = totalMs
    }

    fun clear() {
        job?.cancel()
        running = false
        finished = false
        totalMs = 0L
        remainingMs = 0L
    }
}

class NovaViewModel(application: Application) : AndroidViewModel(application) {
    private val app: Application = application
    private val settingsRepo = SettingsRepository(app)
    private val notesRepo = NotesRepository(app)
    private val mainHandler = Handler(Looper.getMainLooper())

    // ------------------------------------------------------------------ navigation
    var overlay by mutableStateOf(Overlay.None)
        private set
    var drawerAutoFocus by mutableStateOf(false)
        private set
    var homeResetTick by mutableIntStateOf(0)
        private set
    var resumeTick by mutableIntStateOf(0)
        private set
    private var returnTo = Overlay.None

    fun open(target: Overlay, focusSearch: Boolean = false) {
        returnTo = if (overlay == Overlay.Settings && (target == Overlay.Apps || target == Overlay.About)) {
            Overlay.Settings
        } else {
            Overlay.None
        }
        drawerAutoFocus = focusSearch
        overlay = target
    }

    /** Open the dedicated SAND OS search screen. */
    fun openSearch() {
        returnTo = Overlay.None
        drawerAutoFocus = false
        overlay = Overlay.Search
    }

    /** Back button: return to the previous layer (e.g. Settings) or the home screen. */
    fun back() {
        val t = returnTo
        returnTo = Overlay.None
        drawerAutoFocus = false
        overlay = t
    }

    /** Dismiss any layer completely. */
    fun close() {
        returnTo = Overlay.None
        drawerAutoFocus = false
        overlay = Overlay.None
    }

    fun goHome() {
        close()
        homeResetTick++
    }

    fun onResumed() {
        resumeTick++
    }

    // ------------------------------------------------------------------ settings
    private val _settings = MutableStateFlow(settingsRepo.load())
    val settings: StateFlow<NovaSettings> = _settings.asStateFlow()

    fun update(transform: (NovaSettings) -> NovaSettings) {
        _settings.update(transform)
        settingsRepo.save(_settings.value)
    }

    fun resetSettings() {
        update { NovaSettings() }
        ensureDefaults(_apps.value)
        toast("설정을 초기화했어요")
    }

    // ------------------------------------------------------------------ apps
    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val allApps: StateFlow<List<AppInfo>> = _apps.asStateFlow()
    private val _launchCounts = MutableStateFlow(settingsRepo.loadLaunchCounts())

    val drawerApps: StateFlow<List<AppInfo>> =
        combine(_apps, _settings, _launchCounts) { apps, s, counts ->
            AppRepository.sortApps(apps.filter { it.packageName !in s.hidden }, s.sort, counts)
        }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val favoriteApps: StateFlow<List<AppInfo>> = combine(_apps, _settings) { apps, s ->
        val byPackage = apps.filter { it.packageName !in s.hidden }.groupBy { it.packageName }
        s.favorites.mapNotNull { byPackage[it]?.firstOrNull() }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val dockApps: StateFlow<List<AppInfo>> = combine(_apps, _settings) { apps, s ->
        val byPackage = apps.filter { it.packageName !in s.hidden }.groupBy { it.packageName }
        s.dockApps.mapNotNull { byPackage[it]?.firstOrNull() }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val frequentApps: StateFlow<List<AppInfo>> = combine(_apps, _settings, _launchCounts) { apps, s, counts ->
        apps.filter { it.packageName !in s.hidden && (counts[it.packageName] ?: 0) > 0 }
            .distinctBy { it.packageName }
            .sortedByDescending { counts[it.packageName] ?: 0 }
            .take(12)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private var appsJob: Job? = null

    fun refreshApps(delayMs: Long = 0L) {
        appsJob?.cancel()
        appsJob = viewModelScope.launch {
            if (delayMs > 0) delay(delayMs)
            val list = withContext(Dispatchers.Default) {
                try {
                    AppRepository.load(app)
                } catch (e: Exception) {
                    emptyList()
                }
            }
            if (list.isNotEmpty() || _apps.value.isEmpty()) _apps.value = list
            ensureDefaults(list)
        }
    }

    private fun ensureDefaults(apps: List<AppInfo>) {
        val s = _settings.value
        if (s.setupDone || apps.isEmpty()) return
        val installed = apps.map { it.packageName }.toSet()
        val dock = AppRepository.resolveDefaultDock(app, installed).take(4)
        val rest = apps.filter { it.packageName !in dock }.distinctBy { it.packageName }
        val sorted = AppRepository.sortApps(rest, SortMode.NAME_ASC, emptyMap())
        val favorites = (sorted.filter { !it.isSystem } + sorted.filter { it.isSystem })
            .take(8).map { it.packageName }
        update { it.copy(dockApps = dock, favorites = favorites, setupDone = true) }
    }

    fun launchApp(info: AppInfo) {
        try {
            val launcherApps = app.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            launcherApps.startMainActivity(info.component, info.user, null, null)
            close()
            val counts = _launchCounts.value.toMutableMap()
            counts[info.packageName] = (counts[info.packageName] ?: 0) + 1
            _launchCounts.value = counts
            settingsRepo.saveLaunchCounts(counts)
        } catch (e: Exception) {
            toast("앱을 열 수 없어요: ${info.label}")
        }
    }

    fun toggleFavorite(pkg: String) {
        val s = _settings.value
        if (pkg in s.favorites) {
            update { it.copy(favorites = it.favorites - pkg) }
            toast("즐겨찾기에서 제거했어요")
        } else if (s.favorites.size >= MAX_FAVORITES) {
            toast("즐겨찾기는 최대 ${MAX_FAVORITES}개까지 가능해요")
        } else {
            update { it.copy(favorites = it.favorites + pkg) }
            toast("즐겨찾기에 추가했어요")
        }
    }

    fun toggleDock(pkg: String) {
        update {
            when {
                pkg in it.dockApps -> it.copy(dockApps = it.dockApps - pkg)
                it.dockApps.size >= MAX_DOCK -> it.copy(dockApps = it.dockApps.drop(1) + pkg)
                else -> it.copy(dockApps = it.dockApps + pkg)
            }
        }
    }

    fun setHidden(pkg: String, hidden: Boolean) {
        update {
            if (hidden) {
                it.copy(
                    hidden = it.hidden + pkg,
                    favorites = it.favorites - pkg,
                    dockApps = it.dockApps - pkg,
                )
            } else {
                it.copy(hidden = it.hidden - pkg)
            }
        }
        toast(if (hidden) "앱을 숨겼어요. 앱 관리에서 다시 표시할 수 있어요" else "앱을 다시 표시해요")
    }

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshApps(delayMs = 400L)
        }
    }

    // ------------------------------------------------------------------ battery
    private val _battery = MutableStateFlow(BatteryState())
    val battery: StateFlow<BatteryState> = _battery.asStateFlow()

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent != null) updateBattery(intent)
        }
    }

    private fun updateBattery(i: Intent) {
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val temp = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f
        val health = i.getIntExtra(BatteryManager.EXTRA_HEALTH, 0)
        val plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        val percent = if (level >= 0 && scale > 0) (level * 100f / scale).roundToInt() else 0
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            (status == BatteryManager.BATTERY_STATUS_FULL && plugged)
        _battery.value = BatteryState(
            percent = percent,
            charging = charging,
            tempC = temp,
            plugged = plugged,
            health = when (health) {
                BatteryManager.BATTERY_HEALTH_GOOD -> "양호"
                BatteryManager.BATTERY_HEALTH_OVERHEAT -> "과열"
                BatteryManager.BATTERY_HEALTH_DEAD -> "불량"
                BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "과전압"
                BatteryManager.BATTERY_HEALTH_COLD -> "저온"
                else -> "정상"
            },
            valid = true,
        )
    }

    // ------------------------------------------------------------------ weather
    private val _weather = MutableStateFlow(WeatherState())
    val weather: StateFlow<WeatherState> = _weather.asStateFlow()
    private var weatherJob: Job? = null

    fun refreshWeather(force: Boolean = false) {
        val w = _weather.value
        val fresh = w.status == WeatherStatus.READY && System.currentTimeMillis() - w.updatedAt < 30 * 60_000L
        if (!force && fresh) return
        weatherJob?.cancel()
        weatherJob = viewModelScope.launch {
            if (_weather.value.status != WeatherStatus.READY) {
                _weather.value = _weather.value.copy(status = WeatherStatus.LOADING)
            }
            val result = WeatherRepository.fetch(app, _settings.value.useLocationWeather)
            _weather.value = when {
                result != null -> result
                _weather.value.status == WeatherStatus.READY -> _weather.value
                else -> _weather.value.copy(status = WeatherStatus.ERROR)
            }
        }
    }

    // ------------------------------------------------------------------ wallpaper
    private val wallpaperFile get() = File(app.filesDir, "wallpaper.jpg")
    private val _customWallpaper = MutableStateFlow<ImageBitmap?>(null)
    val customWallpaper: StateFlow<ImageBitmap?> = _customWallpaper.asStateFlow()

    private fun loadCustomWallpaper() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val f = wallpaperFile
                if (f.exists()) {
                    val bmp = BitmapFactory.decodeFile(f.absolutePath)
                    if (bmp != null) _customWallpaper.value = bmp.asImageBitmap()
                }
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    fun setCustomWallpaper(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val resolver = app.contentResolver
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IllegalStateException("invalid image")
                var sample = 1
                while (max(bounds.outWidth, bounds.outHeight) / sample > 2200) sample *= 2
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                var bmp: Bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                    ?: throw IllegalStateException("decode failed")
                val rotation = try {
                    resolver.openInputStream(uri)?.use { exifRotation(it) } ?: 0
                } catch (e: Exception) {
                    0
                }
                if (rotation != 0) {
                    val m = Matrix().apply { postRotate(rotation.toFloat()) }
                    bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
                }
                wallpaperFile.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                _customWallpaper.value = bmp.asImageBitmap()
                update { it.copy(wallpaperId = "custom") }
                toast("배경화면을 적용했어요")
            } catch (e: Exception) {
                toast("이미지를 불러오지 못했어요")
            }
        }
    }

    private fun exifRotation(stream: java.io.InputStream): Int =
        when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }

    // ------------------------------------------------------------------ notes
    private val _notes = MutableStateFlow<List<Note>>(emptyList())
    val notes: StateFlow<List<Note>> = _notes.asStateFlow()
    private var notesJob: Job? = null

    private fun loadNotes() {
        viewModelScope.launch {
            _notes.value = withContext(Dispatchers.IO) { notesRepo.load() }
        }
    }

    private fun persistNotes() {
        notesJob?.cancel()
        notesJob = viewModelScope.launch(Dispatchers.IO) {
            delay(300)
            notesRepo.save(_notes.value.filter { it.text.isNotBlank() })
        }
    }

    fun createNote(): Long {
        val id = System.currentTimeMillis()
        _notes.update { listOf(Note(id, "", id)) + it }
        return id
    }

    fun updateNote(id: Long, text: String) {
        _notes.update { list ->
            list.map { if (it.id == id) it.copy(text = text, updatedAt = System.currentTimeMillis()) else it }
        }
        persistNotes()
    }

    fun togglePin(id: Long) {
        _notes.update { list -> list.map { if (it.id == id) it.copy(pinned = !it.pinned) else it } }
        persistNotes()
    }

    fun deleteNote(id: Long) {
        _notes.update { list -> list.filter { it.id != id } }
        persistNotes()
    }

    // ------------------------------------------------------------------ tools
    val calc = CalcState()
    val stopwatch = StopwatchState()
    val timer = TimerState(viewModelScope) { onTimerFinished() }
    val torch by lazy { TorchController(app) }

    @Suppress("DEPRECATION")
    private fun onTimerFinished() {
        try {
            val vibrator = app.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 400, 200, 400, 200, 400), -1))
        } catch (e: Exception) {
            // ignore
        }
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            val ringtone = RingtoneManager.getRingtone(app, uri)
            ringtone.play()
            viewModelScope.launch {
                delay(6000)
                ringtone.stop()
            }
        } catch (e: Exception) {
            // ignore
        }
        toast("타이머가 끝났어요")
    }

    fun toast(message: String) {
        mainHandler.post { Toast.makeText(app, message, Toast.LENGTH_SHORT).show() }
    }

    // ------------------------------------------------------------------ lifecycle
    init {
        loadNotes()
        loadCustomWallpaper()
        refreshApps()

        ContextCompat.registerReceiver(
            app,
            packageReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addDataScheme("package")
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        val sticky = ContextCompat.registerReceiver(
            app,
            batteryReceiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        if (sticky != null) updateBattery(sticky)
    }

    override fun onCleared() {
        try {
            app.unregisterReceiver(packageReceiver)
            app.unregisterReceiver(batteryReceiver)
        } catch (e: Exception) {
            // already unregistered
        }
        super.onCleared()
    }

    companion object {
        const val MAX_FAVORITES = 20
        const val MAX_DOCK = 4
    }
}
