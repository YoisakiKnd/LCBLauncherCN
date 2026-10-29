package io.github.lcblauncher.probe

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.os.StatFs
import android.system.Os
import android.system.OsConstants
import io.github.lcblauncher.core.ProbePolicy
import io.github.lcblauncher.core.sha256
import java.io.File
import java.util.zip.ZipFile

data class DeviceSnapshot(
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val api: Int,
    val abis: List<String>,
    val process64Bit: Boolean,
    val pageBytes: Long?,
    val totalRam: Long,
    val lowRam: Boolean,
    val availableStorage: Long,
    val batteryUnrestricted: Boolean,
    val googleServicesInstalled: Boolean,
    val playStoreInstalled: Boolean,
)

data class ApkSnapshot(
    val name: String,
    val bytes: Long,
    val readable: Boolean,
    val nativeAbis: List<String>,
    val runtimeLibraries: List<String>,
    val localizationHints: List<String>,
)

data class GameSnapshot(
    val installed: Boolean,
    val versionName: String? = null,
    val versionCode: Long? = null,
    val installer: String? = null,
    val launchable: Boolean = false,
    val certificateSha256: List<String> = emptyList(),
    val apks: List<ApkSnapshot> = emptyList(),
    val error: String? = null,
)

data class ProbeSnapshot(val capturedAt: String, val device: DeviceSnapshot, val game: GameSnapshot)

class DeviceProbe(private val context: Context) {
    private val pm = context.packageManager

    fun capture(): ProbeSnapshot {
        val am = context.getSystemService(ActivityManager::class.java)
        val memory = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val device = DeviceSnapshot(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            androidVersion = Build.VERSION.RELEASE,
            api = Build.VERSION.SDK_INT,
            abis = Build.SUPPORTED_ABIS.toList(),
            process64Bit = Process.is64Bit(),
            pageBytes = runCatching { Os.sysconf(OsConstants._SC_PAGESIZE) }.getOrNull(),
            totalRam = memory.totalMem,
            lowRam = am.isLowRamDevice,
            availableStorage = StatFs(context.filesDir.path).availableBytes,
            batteryUnrestricted = context.getSystemService(PowerManager::class.java)
                .isIgnoringBatteryOptimizations(context.packageName),
            googleServicesInstalled = isInstalled("com.google.android.gms"),
            playStoreInstalled = isInstalled("com.android.vending"),
        )
        return ProbeSnapshot(java.time.Instant.now().toString(), device, inspectGame())
    }

    @Suppress("DEPRECATION")
    private fun inspectGame(): GameSnapshot {
        return try {
            val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
            val info = pm.getPackageInfo(ProbePolicy.GAME_PACKAGE, flags)
            val app = info.applicationInfo ?: return GameSnapshot(installed = true, error = "无法读取游戏应用信息")
            val signatures = if (Build.VERSION.SDK_INT >= 28) {
                val signing = info.signingInfo
                if (signing?.hasMultipleSigners() == true) signing.apkContentsSigners
                else signing?.signingCertificateHistory
            } else info.signatures
            val installer = runCatching {
                if (Build.VERSION.SDK_INT >= 30) pm.getInstallSourceInfo(ProbePolicy.GAME_PACKAGE).installingPackageName
                else pm.getInstallerPackageName(ProbePolicy.GAME_PACKAGE)
            }.getOrNull()
            GameSnapshot(
                installed = true,
                versionName = info.versionName,
                versionCode = versionCode(info),
                installer = installer,
                launchable = pm.getLaunchIntentForPackage(ProbePolicy.GAME_PACKAGE) != null,
                certificateSha256 = signatures.orEmpty().map { sha256(it.toByteArray().inputStream()) },
                apks = (listOf(app.sourceDir) + app.splitSourceDirs.orEmpty()).map { inspectApk(File(it)) },
            )
        } catch (_: PackageManager.NameNotFoundException) {
            GameSnapshot(installed = false)
        } catch (_: SecurityException) {
            GameSnapshot(installed = false, error = "系统拒绝读取游戏安装信息")
        } catch (_: Exception) {
            GameSnapshot(installed = false, error = "读取游戏安装信息失败，请导出诊断")
        }
    }

    private fun inspectApk(file: File): ApkSnapshot {
        val abis = sortedSetOf<String>()
        val libraries = sortedSetOf<String>()
        val hints = mutableListOf<String>()
        val readable = runCatching {
            ZipFile(file).use { zip ->
                val entries = zip.entries()
                var count = 0
                while (entries.hasMoreElements() && count++ < 100_000) {
                    val entry = entries.nextElement()
                    val parts = entry.name.split('/')
                    if (parts.size == 3 && parts[0] == "lib" && parts[2].endsWith(".so")) {
                        abis += parts[1]
                        if (parts[2] in RUNTIME_LIBRARIES) libraries += parts[2]
                    }
                    if (hints.size < 12 && entry.name.length < 200 && entry.name.startsWith("assets/") &&
                        entry.name.contains("localiz", ignoreCase = true)) hints += entry.name
                }
            }
        }.isSuccess
        return ApkSnapshot(file.name, file.length(), readable, abis.toList(), libraries.toList(), hints)
    }

    @Suppress("DEPRECATION")
    private fun isInstalled(name: String): Boolean = try {
        pm.getPackageInfo(name, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) { false }
      catch (_: SecurityException) { false }

    @Suppress("DEPRECATION")
    private fun versionCode(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()

    private companion object {
        val RUNTIME_LIBRARIES = setOf("libunity.so", "libil2cpp.so", "libmain.so", "libcovault-appsec.so")
    }
}
