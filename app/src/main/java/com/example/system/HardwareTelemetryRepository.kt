package com.example.system

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Debug
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

private val Context.simDataStore by preferencesDataStore(name = "i17_sim_prefs")

data class RamTelemetry(
    val virtualTotalGb: Double = 16.0,
    val virtualAllocatedGb: Double = 6.8,
    val actualTotalBytes: Long = 0L,
    val actualAvailableBytes: Long = 0L,
    val actualUsedBytes: Long = 0L,
    val actualLowMemory: Boolean = false,
    val jvmHeapUsedBytes: Long = 0L,
    val jvmHeapMaxBytes: Long = 0L,
    val appTotalPssKb: Int = 0,
    val thermalStatusLabel: String = "Nominal",
    val cpuCores: Int = Runtime.getRuntime().availableProcessors(),
    val deviceModel: String = "${Build.MANUFACTURER} ${Build.MODEL}",
    val androidVersion: String = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
) {
    val actualUsageFraction: Float
        get() = if (actualTotalBytes > 0L) {
            (actualUsedBytes.toFloat() / actualTotalBytes.toFloat()).coerceIn(0f, 1f)
        } else 0f

    val virtualUsageFraction: Float
        get() = (virtualAllocatedGb / virtualTotalGb).toFloat().coerceIn(0f, 1f)
}

data class StorageCategoryBreakdown(
    val photosGb: Double,
    val videosGb: Double,
    val appsGb: Double,
    val downloadsGb: Double,
    val documentsGb: Double,
    val systemGb: Double
) {
    val totalUsedGb: Double
        get() = photosGb + videosGb + appsGb + downloadsGb + documentsGb + systemGb
}

data class StorageTelemetry(
    val virtualTotalGb: Double = 1024.0,
    val virtualBreakdown: StorageCategoryBreakdown = StorageCategoryBreakdown(
        photosGb = 18.4,
        videosGb = 42.6,
        appsGb = 36.2,
        downloadsGb = 4.8,
        documentsGb = 3.1,
        systemGb = 24.5
    ),
    val actualTotalBytes: Long = 0L,
    val actualAvailableBytes: Long = 0L,
    val actualUsedBytes: Long = 0L,
    val appVaultBytes: Long = 0L,
    val appCacheBytes: Long = 0L,
    val realPhotosCount: Int = 0,
    val realVideosCount: Int = 0,
    val realVaultFilesCount: Int = 0
) {
    val virtualUsedGb: Double get() = virtualBreakdown.totalUsedGb.coerceAtMost(virtualTotalGb)
    val virtualFreeGb: Double get() = (virtualTotalGb - virtualUsedGb).coerceAtLeast(0.0)
    val actualUsageFraction: Float
        get() = if (actualTotalBytes > 0L) {
            (actualUsedBytes.toFloat() / actualTotalBytes.toFloat()).coerceIn(0f, 1f)
        } else 0f
}

data class SystemStatusSnapshot(
    val batteryPercent: Int = 100,
    val isCharging: Boolean = false,
    val networkLabel: String = "Wi-Fi",
    val isOnline: Boolean = true,
    val hasFlashlight: Boolean = false
)

data class SimPreferences(
    val storageWarningThresholdPercent: Int = 85,
    val performanceMode: String = "Balanced",
    val cloudEndpointUrl: String = "",
    val cloudSyncEnabled: Boolean = false,
    val showDynamicIsland: Boolean = true
)

class HardwareTelemetryRepository(private val context: Context) {

    private object Keys {
        val STORAGE_WARN_THRESHOLD = intPreferencesKey("storage_warn_threshold")
        val PERFORMANCE_MODE = stringPreferencesKey("performance_mode")
        val CLOUD_ENDPOINT = stringPreferencesKey("cloud_endpoint")
        val CLOUD_ENABLED = booleanPreferencesKey("cloud_enabled")
        val SHOW_ISLAND = booleanPreferencesKey("show_island")
    }

    val preferencesFlow: Flow<SimPreferences> = context.simDataStore.data.map { prefs ->
        SimPreferences(
            storageWarningThresholdPercent = prefs[Keys.STORAGE_WARN_THRESHOLD] ?: 85,
            performanceMode = prefs[Keys.PERFORMANCE_MODE] ?: "Balanced",
            cloudEndpointUrl = prefs[Keys.CLOUD_ENDPOINT] ?: "",
            cloudSyncEnabled = prefs[Keys.CLOUD_ENABLED] ?: false,
            showDynamicIsland = prefs[Keys.SHOW_ISLAND] ?: true
        )
    }

    suspend fun updatePerformanceMode(mode: String) {
        context.simDataStore.edit { it[Keys.PERFORMANCE_MODE] = mode }
    }

    suspend fun updateStorageWarningThreshold(percent: Int) {
        context.simDataStore.edit { it[Keys.STORAGE_WARN_THRESHOLD] = percent.coerceIn(50, 98) }
    }

    suspend fun updateCloudConfig(endpointUrl: String, enabled: Boolean) {
        context.simDataStore.edit {
            it[Keys.CLOUD_ENDPOINT] = endpointUrl.trim()
            it[Keys.CLOUD_ENABLED] = enabled && endpointUrl.trim().startsWith("https://")
        }
    }

    suspend fun readRamTelemetry(): RamTelemetry = withContext(Dispatchers.IO) {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)

        val runtime = Runtime.getRuntime()
        val jvmUsed = (runtime.totalMemory() - runtime.freeMemory()).coerceAtLeast(0L)
        val jvmMax = runtime.maxMemory()

        val pssKb = runCatching {
            val debugInfo = Debug.MemoryInfo()
            Debug.getMemoryInfo(debugInfo)
            debugInfo.totalPss
        }.getOrDefault(0)

        val thermalLabel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            when (powerManager.currentThermalStatus) {
                PowerManager.THERMAL_STATUS_NONE -> "Nominal"
                PowerManager.THERMAL_STATUS_LIGHT -> "Light"
                PowerManager.THERMAL_STATUS_MODERATE -> "Moderate"
                PowerManager.THERMAL_STATUS_SEVERE -> "Warm"
                PowerManager.THERMAL_STATUS_CRITICAL,
                PowerManager.THERMAL_STATUS_EMERGENCY,
                PowerManager.THERMAL_STATUS_SHUTDOWN -> "Critical"
                else -> "Nominal"
            }
        } else {
            "Nominal"
        }

        val totalMem = memInfo.totalMem.coerceAtLeast(1L)
        val availMem = memInfo.availMem.coerceIn(0L, totalMem)
        val usedMem = (totalMem - availMem).coerceAtLeast(0L)

        // Map real physical memory pressure proportionally onto the simulated 16GB virtual display
        val physicalFraction = usedMem.toDouble() / totalMem.toDouble()
        val virtualUsedGb = ((4.2 + physicalFraction * 6.4) * 10.0).roundToInt() / 10.0

        RamTelemetry(
            virtualTotalGb = 16.0,
            virtualAllocatedGb = virtualUsedGb.coerceIn(2.0, 14.8),
            actualTotalBytes = totalMem,
            actualAvailableBytes = availMem,
            actualUsedBytes = usedMem,
            actualLowMemory = memInfo.lowMemory,
            jvmHeapUsedBytes = jvmUsed,
            jvmHeapMaxBytes = jvmMax,
            appTotalPssKb = pssKb,
            thermalStatusLabel = thermalLabel
        )
    }

    suspend fun readStorageTelemetry(
        realPhotosCount: Int,
        realPhotosBytes: Long,
        realVideosCount: Int,
        realVideosBytes: Long
    ): StorageTelemetry = withContext(Dispatchers.IO) {
        val statFs = StatFs(Environment.getDataDirectory().path)
        val totalBytes = statFs.totalBytes
        val availBytes = statFs.availableBytes
        val usedBytes = (totalBytes - availBytes).coerceAtLeast(0L)

        val vaultDir = File(context.filesDir, "i17_vault")
        if (!vaultDir.exists()) vaultDir.mkdirs()

        val vaultStats = measureDirectory(vaultDir)
        val cacheBytes = measureDirectory(context.cacheDir).first

        val realPhotosGb = realPhotosBytes / (1024.0 * 1024.0 * 1024.0)
        val realVideosGb = realVideosBytes / (1024.0 * 1024.0 * 1024.0)
        val vaultGb = vaultStats.first / (1024.0 * 1024.0 * 1024.0)

        // Combine simulated baseline OS/App allocations with real measured media/vault bytes
        val breakdown = StorageCategoryBreakdown(
            photosGb = 18.4 + realPhotosGb,
            videosGb = 42.6 + realVideosGb,
            appsGb = 36.2,
            downloadsGb = 4.8 + (vaultGb * 0.4),
            documentsGb = 3.1 + (vaultGb * 0.6),
            systemGb = 24.5
        )

        StorageTelemetry(
            virtualTotalGb = 1024.0,
            virtualBreakdown = breakdown,
            actualTotalBytes = totalBytes,
            actualAvailableBytes = availBytes,
            actualUsedBytes = usedBytes,
            appVaultBytes = vaultStats.first,
            appCacheBytes = cacheBytes,
            realPhotosCount = realPhotosCount,
            realVideosCount = realVideosCount,
            realVaultFilesCount = vaultStats.second
        )
    }

    suspend fun optimizeMemoryAndClearCache(): Long = withContext(Dispatchers.IO) {
        val before = measureDirectory(context.cacheDir).first
        runCatching {
            context.cacheDir.listFiles()?.forEach { child ->
                if (child.name != "audio") {
                    child.deleteRecursively()
                }
            }
        }
        System.gc()
        val after = measureDirectory(context.cacheDir).first
        (before - after).coerceAtLeast(0L)
    }

    fun readSystemStatus(): SystemStatusSnapshot {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: 100
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val pct = if (level >= 0 && scale > 0) (level * 100) / scale else 100
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = cm.activeNetwork
        val caps = cm.getNetworkCapabilities(activeNetwork)
        val online = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val netLabel = when {
            caps == null || !online -> "Offline"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "5G"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "Online"
        }

        val hasFlash = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)

        return SystemStatusSnapshot(
            batteryPercent = pct,
            isCharging = charging,
            networkLabel = netLabel,
            isOnline = online,
            hasFlashlight = hasFlash
        )
    }

    fun setSystemTorch(enabled: Boolean): Result<Boolean> {
        return runCatching {
            val camManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val backCameraId = camManager.cameraIdList.firstOrNull { id ->
                val chars = camManager.getCameraCharacteristics(id)
                chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK &&
                    chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: camManager.cameraIdList.firstOrNull { id ->
                camManager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: error("No hardware flash unit detected on this device")

            camManager.setTorchMode(backCameraId, enabled)
            enabled
        }
    }

    private fun measureDirectory(dir: File): Pair<Long, Int> {
        if (!dir.exists()) return 0L to 0
        var totalBytes = 0L
        var fileCount = 0
        dir.walkTopDown().forEach { f ->
            if (f.isFile) {
                totalBytes += f.length()
                fileCount++
            }
        }
        return totalBytes to fileCount
    }
}

fun formatBytesHuman(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format("%.2f GB", gb)
        mb >= 1.0 -> String.format("%.1f MB", mb)
        kb >= 1.0 -> String.format("%.0f KB", kb)
        else -> "$bytes B"
    }
}
