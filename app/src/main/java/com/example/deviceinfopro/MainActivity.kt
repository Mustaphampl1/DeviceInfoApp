package com.example.deviceinfopro

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.*
import android.util.DisplayMetrics
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebSettings
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.text.DecimalFormat
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private val TAG = "DeviceInfoPro"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        setContentView(webView)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            cacheMode = WebSettings.LOAD_NO_CACHE
        }

        webView.webViewClient = WebViewClient()

        // MUHIMU: addJavascriptInterface KABLA ya loadUrl
        webView.addJavascriptInterface(DeviceBridge(this), "AndroidDevice")
        webView.loadUrl("file:///android_asset/index.html")
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    inner class DeviceBridge(private val ctx: Context) {

        @JavascriptInterface
        fun getDeviceInfo(): String {
            val json = JSONObject()
            try {
                // ===== KIFAA =====
                json.put("brand", Build.MANUFACTURER.replaceFirstChar { it.uppercase() })
                json.put("model", Build.MODEL)
                json.put("device", Build.DEVICE)
                json.put("product", Build.PRODUCT)
                json.put("hardware", Build.HARDWARE)
                json.put("board", Build.BOARD)
                json.put("androidVersion", Build.VERSION.RELEASE)
                json.put("sdkInt", Build.VERSION.SDK_INT)
                json.put("securityPatch", if (Build.VERSION.SDK_INT >= 23) Build.VERSION.SECURITY_PATCH else "N/A")
                json.put("fingerprint", Build.FINGERPRINT)
                json.put("isEmulator", isEmulator())

                // ===== RAM =====
                val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val memInfo = ActivityManager.MemoryInfo()
                am.getMemoryInfo(memInfo)
                json.put("ramTotal", memInfo.totalMem)
                json.put("ramAvail", memInfo.availMem)
                json.put("ramTotalGBText", formatBytes(memInfo.totalMem))
                json.put("ramAvailText", formatBytes(memInfo.availMem))

                // ===== STORAGE INTERNAL =====
                val dataStat = StatFs(Environment.getDataDirectory().path)
                val totalInternal = dataStat.blockCountLong * dataStat.blockSizeLong
                val freeInternal = dataStat.availableBlocksLong * dataStat.blockSizeLong
                json.put("storageInternalTotal", totalInternal)
                json.put("storageInternalFree", freeInternal)
                json.put("storageInternalUsed", totalInternal - freeInternal)
                json.put("storageInternalTotalText", formatBytes(totalInternal))
                json.put("storageInternalFreeText", formatBytes(freeInternal))
                json.put("storageInternalUsedText", formatBytes(totalInternal - freeInternal))
                json.put("storageInternalPercent", ((totalInternal - freeInternal).toDouble() / totalInternal * 100))

                // ===== CPU =====
                json.put("cpuCores", Runtime.getRuntime().availableProcessors())
                json.put("cpuAbiPrimary", Build.SUPPORTED_ABIS.firstOrNull() ?: "N/A")
                try {
                    val cpuInfo = java.io.File("/proc/cpuinfo").readText()
                    val hw = cpuInfo.lines().firstOrNull { it.startsWith("Hardware") }
                        ?: cpuInfo.lines().firstOrNull { it.startsWith("model name") }
                    json.put("cpuModel", hw?.substringAfter(":")?.trim() ?: "N/A")
                } catch (e: Exception) {
                    json.put("cpuModel", "N/A")
                }

                // ===== BETRI =====
                try {
                    val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                    json.put("batteryLevel", bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY))
                    val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
                    val batteryStatus = ctx.registerReceiver(null, ifilter)
                    if (batteryStatus != null) {
                        val status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                        json.put("batteryCharging", status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL)
                        json.put("batteryTemperature", batteryStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10.0)
                        val health = batteryStatus.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)
                        json.put("batteryHealth", when (health) {
                            BatteryManager.BATTERY_HEALTH_GOOD -> "Nzuri"
                            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Inachomeka"
                            BatteryManager.BATTERY_HEALTH_DEAD -> "Imekufa"
                            else -> "Haijulikani"
                        })
                    }
                } catch (e: Exception) {
                    json.put("batteryLevel", -1)
                }

                // ===== SKRINI =====
                val metrics = DisplayMetrics()
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay.getMetrics(metrics)
                json.put("screenWidth", metrics.widthPixels)
                json.put("screenHeight", metrics.heightPixels)
                json.put("screenDensityDpi", metrics.densityDpi)
                json.put("screenInches", Math.round(Math.sqrt(
                    Math.pow(metrics.widthPixels / metrics.xdpi.toDouble(), 2.0) +
                    Math.pow(metrics.heightPixels / metrics.ydpi.toDouble(), 2.0)
                ) * 10.0) / 10.0)
                @Suppress("DEPRECATION")
                val rr = windowManager.defaultDisplay.refreshRate
                json.put("screenRefreshRate", Math.round(rr * 100.0) / 100.0)

                // ===== MTANDAO =====
                try {
                    val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                    val caps = cm.getNetworkCapabilities(cm.activeNetwork)
                    json.put("networkType", when {
                        caps == null -> "Hakuna"
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
                        else -> "Nyingine"
                    })
                    try {
                        val wifi = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                        @Suppress("DEPRECATION")
                        val info = wifi.connectionInfo
                        json.put("wifiSSID", info?.ssid ?: "N/A")
                        json.put("wifiRssi", info?.rssi ?: 0)
                    } catch (e: Exception) {}
                } catch (e: Exception) {}

                // ===== MFUMO =====
                json.put("uptimeText", formatUptime(SystemClock.elapsedRealtime()))
                json.put("timezone", java.util.TimeZone.getDefault().id)
                json.put("totalApps", ctx.packageManager.getInstalledPackages(0).size)

            } catch (e: Exception) {
                json.put("error", e.message ?: "Kosa")
            }
            return json.toString()
        }

        private fun isEmulator(): Boolean {
            return (Build.FINGERPRINT.startsWith("generic")
                    || Build.FINGERPRINT.startsWith("unknown")
                    || Build.MODEL.contains("google_sdk")
                    || Build.MODEL.contains("Emulator")
                    || Build.MODEL.contains("Android SDK built for x86")
                    || Build.MANUFACTURER.contains("Genymotion")
                    || "google_sdk" == Build.PRODUCT)
        }

        private fun formatBytes(bytes: Long): String {
            if (bytes <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            val g = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
            return "${DecimalFormat("#,##0.##").format(bytes / Math.pow(1024.0, g.toDouble()))} ${units[g]}"
        }

        private fun formatUptime(ms: Long): String {
            val d = TimeUnit.MILLISECONDS.toDays(ms)
            val h = TimeUnit.MILLISECONDS.toHours(ms) % 24
            val m = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
            return "${d}d ${h}h ${m}m"
        }
    }
}
