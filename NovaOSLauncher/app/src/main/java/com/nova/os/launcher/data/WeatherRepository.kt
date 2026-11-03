package com.nova.os.launcher.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.roundToInt

/**
 * Weather from Open-Meteo (no API key). Location comes from the device (if the user allowed it)
 * or from an IP-based lookup as a fallback.
 */
object WeatherRepository {

    suspend fun fetch(context: Context, useLocation: Boolean): WeatherState? = withContext(Dispatchers.IO) {
        try {
            var lat: Double? = null
            var lon: Double? = null
            var place = ""

            if (useLocation && hasPermission(context)) {
                val loc = lastLocation(context)
                if (loc != null) {
                    lat = loc.latitude
                    lon = loc.longitude
                    place = reverseGeocode(context, loc)
                }
            }
            if (lat == null || lon == null) {
                val ip = JSONObject(httpGet("https://ipwho.is/"))
                if (!ip.optBoolean("success", true)) return@withContext null
                lat = ip.getDouble("latitude")
                lon = ip.getDouble("longitude")
                place = ip.optString("city")
            }

            val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,weather_code,is_day" +
                "&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=1"
            val root = JSONObject(httpGet(url))
            val cur = root.getJSONObject("current")
            val daily = root.getJSONObject("daily")
            WeatherState(
                status = WeatherStatus.READY,
                tempC = cur.getDouble("temperature_2m").roundToInt(),
                high = daily.getJSONArray("temperature_2m_max").getDouble(0).roundToInt(),
                low = daily.getJSONArray("temperature_2m_min").getDouble(0).roundToInt(),
                code = cur.getInt("weather_code"),
                isDay = cur.optInt("is_day", 1) == 1,
                place = place,
                updatedAt = System.currentTimeMillis(),
            )
        } catch (e: Exception) {
            null
        }
    }

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private suspend fun lastLocation(context: Context): Location? {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        var best: Location? = null
        for (provider in listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)) {
            try {
                val l = lm.getLastKnownLocation(provider)
                if (l != null && (best == null || l.time > best.time)) best = l
            } catch (e: Exception) {
                // provider not available
            }
        }
        if (best == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            best = withTimeoutOrNull(6000L) {
                suspendCancellableCoroutine<Location?> { cont ->
                    val signal = CancellationSignal()
                    cont.invokeOnCancellation { signal.cancel() }
                    try {
                        lm.getCurrentLocation(
                            LocationManager.NETWORK_PROVIDER,
                            signal,
                            ContextCompat.getMainExecutor(context)
                        ) { loc -> if (cont.isActive) cont.resume(loc) }
                    } catch (e: Exception) {
                        if (cont.isActive) cont.resume(null)
                    }
                }
            }
        }
        return best
    }

    @Suppress("DEPRECATION")
    private fun reverseGeocode(context: Context, loc: Location): String = try {
        if (!Geocoder.isPresent()) {
            ""
        } else {
            Geocoder(context, Locale.getDefault())
                .getFromLocation(loc.latitude, loc.longitude, 1)
                ?.firstOrNull()
                ?.let { it.locality ?: it.subAdminArea ?: it.adminArea }
                .orEmpty()
        }
    } catch (e: Exception) {
        ""
    }

    private fun httpGet(urlStr: String): String {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("User-Agent", "NovaOSLauncher/1.0")
            if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
