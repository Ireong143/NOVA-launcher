package com.nova.os.launcher.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Process
import android.provider.MediaStore
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import java.text.Collator
import java.util.Locale

/** Loads launchable apps through [LauncherApps] (the official launcher API). */
object AppRepository {
    private const val ICON_PX = 144

    @Suppress("DEPRECATION")
    fun load(context: Context): List<AppInfo> {
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
            ?: return emptyList()
        val pm = context.packageManager
        val user = Process.myUserHandle()
        val out = ArrayList<AppInfo>()
        val seen = HashSet<String>()
        for (info in launcherApps.getActivityList(null, user)) {
            try {
                val appInfo = info.applicationInfo
                val pkg = appInfo.packageName
                if (pkg == context.packageName) continue
                val key = info.componentName.flattenToString()
                if (!seen.add(key)) continue
                val pi = try {
                    pm.getPackageInfo(pkg, 0)
                } catch (e: Exception) {
                    null
                }
                val bitmap = info.getIcon(0).toBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888)
                out.add(
                    AppInfo(
                        key = key,
                        packageName = pkg,
                        label = info.label?.toString().orEmpty().ifBlank { pkg },
                        icon = bitmap.asImageBitmap(),
                        component = info.componentName,
                        user = user,
                        isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                        versionName = pi?.versionName.orEmpty(),
                        installTime = pi?.firstInstallTime ?: 0L,
                    )
                )
            } catch (e: Exception) {
                // Skip apps that fail to load.
            }
        }
        return out
    }

    fun sortApps(apps: List<AppInfo>, mode: SortMode, counts: Map<String, Int>): List<AppInfo> {
        val collator = Collator.getInstance(Locale.getDefault())
        val byName = Comparator<AppInfo> { a, b -> collator.compare(a.label, b.label) }
        return when (mode) {
            SortMode.NAME_ASC -> apps.sortedWith(byName)
            SortMode.NAME_DESC -> apps.sortedWith(byName.reversed())
            SortMode.RECENT -> apps.sortedWith(compareByDescending<AppInfo> { it.installTime }.then(byName))
            SortMode.FREQUENT ->
                apps.sortedWith(compareByDescending<AppInfo> { counts[it.packageName] ?: 0 }.then(byName))
        }
    }

    /** Finds the device's default phone / messages / browser / camera apps for the first-run dock. */
    @Suppress("DEPRECATION")
    fun resolveDefaultDock(context: Context, installed: Set<String>): List<String> {
        val pm = context.packageManager
        val intents = listOf(
            Intent(Intent.ACTION_DIAL),
            Intent(Intent.ACTION_VIEW, Uri.parse("sms:")),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.example.com")),
            Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
        )
        return intents.mapNotNull {
            try {
                pm.resolveActivity(it, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
            } catch (e: Exception) {
                null
            }
        }.filter { it in installed }.distinct()
    }
}

/** App search with Korean initial-consonant (초성) support. */
object SearchUtil {
    private const val CHO = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ"

    private fun choseong(s: String): String = buildString {
        for (ch in s) {
            if (ch in '\uAC00'..'\uD7A3') append(CHO[(ch.code - 0xAC00) / 588]) else append(ch)
        }
    }

    fun matches(app: AppInfo, query: String): Boolean {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return true
        val label = app.label.lowercase()
        if (label.contains(q) || app.packageName.lowercase().contains(q)) return true
        val onlyChoseong = q.all { it in CHO || it == ' ' }
        return onlyChoseong && choseong(label).contains(q)
    }
}
