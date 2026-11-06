package com.nova.os.launcher.data
 
import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
 
/** Persists [NovaSettings] and launch counters in SharedPreferences. */
class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("nova_settings", Context.MODE_PRIVATE)
 
    fun load(): NovaSettings {
        val d = NovaSettings()
        return NovaSettings(
            themeMode = enumOf(prefs.getString("themeMode", null), d.themeMode),
            accentIndex = prefs.getInt("accentIndex", d.accentIndex),
            wallpaperId = prefs.getString("wallpaperId", d.wallpaperId) ?: d.wallpaperId,
            animatedWallpaper = prefs.getBoolean("animatedWallpaper", d.animatedWallpaper),
            iconSizeDp = prefs.getInt("iconSizeDp", d.iconSizeDp),
            showLabels = prefs.getBoolean("showLabels", d.showLabels),
            fontScale = prefs.getFloat("fontScale", d.fontScale),
            animationsEnabled = prefs.getBoolean("animationsEnabled", d.animationsEnabled),
            animationSpeed = prefs.getFloat("animationSpeed", d.animationSpeed),
            homePages = prefs.getInt("homePages", d.homePages),
            clockStyle = enumOf(prefs.getString("clockStyle", null), d.clockStyle),
            use24h = prefs.getBoolean("use24h", d.use24h),
            showWeather = prefs.getBoolean("showWeather", d.showWeather),
            showSearchBar = prefs.getBoolean("showSearchBar", d.showSearchBar),
            showDock = prefs.getBoolean("showDock", d.showDock),
            favColumns = prefs.getInt("favColumns", d.favColumns),
            drawerColumns = prefs.getInt("drawerColumns", d.drawerColumns),
            useLocationWeather = prefs.getBoolean("useLocationWeather", d.useLocationWeather),
            sort = enumOf(prefs.getString("sort", null), d.sort),
            favorites = readList("favorites"),
            dockApps = readList("dockApps"),
            hidden = readList("hidden").toSet(),
            setupDone = prefs.getBoolean("setupDone", d.setupDone),
            layoutVersion = prefs.getInt("layoutVersion", d.layoutVersion),
        )
    }
 
    fun save(s: NovaSettings) {
        prefs.edit()
            .putString("themeMode", s.themeMode.name)
            .putInt("accentIndex", s.accentIndex)
            .putString("wallpaperId", s.wallpaperId)
            .putBoolean("animatedWallpaper", s.animatedWallpaper)
            .putInt("iconSizeDp", s.iconSizeDp)
            .putBoolean("showLabels", s.showLabels)
            .putFloat("fontScale", s.fontScale)
            .putBoolean("animationsEnabled", s.animationsEnabled)
            .putFloat("animationSpeed", s.animationSpeed)
            .putInt("homePages", s.homePages)
            .putString("clockStyle", s.clockStyle.name)
            .putBoolean("use24h", s.use24h)
            .putBoolean("showWeather", s.showWeather)
            .putBoolean("showSearchBar", s.showSearchBar)
            .putBoolean("showDock", s.showDock)
            .putInt("favColumns", s.favColumns)
            .putInt("drawerColumns", s.drawerColumns)
            .putBoolean("useLocationWeather", s.useLocationWeather)
            .putString("sort", s.sort.name)
            .putString("favorites", s.favorites.joinToString("|"))
            .putString("dockApps", s.dockApps.joinToString("|"))
            .putString("hidden", s.hidden.joinToString("|"))
            .putBoolean("setupDone", s.setupDone)
            .putInt("layoutVersion", s.layoutVersion)
            .apply()
    }
 
    fun loadLaunchCounts(): Map<String, Int> = try {
        val o = JSONObject(prefs.getString("launchCounts", "{}").orEmpty())
        o.keys().asSequence().associateWith { o.optInt(it, 0) }
    } catch (e: Exception) {
        emptyMap()
    }
 
    fun saveLaunchCounts(counts: Map<String, Int>) {
        prefs.edit().putString("launchCounts", JSONObject(counts).toString()).apply()
    }
 
    private fun readList(key: String): List<String> =
        prefs.getString(key, "").orEmpty().split('|').filter { it.isNotBlank() }
 
    private inline fun <reified T : Enum<T>> enumOf(name: String?, default: T): T = try {
        if (name == null) default else enumValueOf<T>(name)
    } catch (e: IllegalArgumentException) {
        default
    }
}
 
