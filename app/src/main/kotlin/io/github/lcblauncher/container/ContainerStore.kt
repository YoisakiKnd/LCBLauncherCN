package io.github.lcblauncher.container

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.util.AtomicFile
import io.github.lcblauncher.core.ProbePolicy
import io.github.lcblauncher.core.NativeLibraries
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID

data class ContainerInstall(val packageName: String, val version: String, val versionCode: Long,
    val apkCount: Int, val bytes: Long, val activity: String)

data class GuestPackage(val install: ContainerInstall, val info: ApplicationInfo, val activityInfo: ActivityInfo,
    val apks: List<File>, val data: File, val libraries: File)

/** Imports immutable code snapshots. Guest data lives outside snapshots, including across game updates. */
class ContainerStore(private val context: Context) {
    private val root = File(context.filesDir, "container")

    fun installed(packageName: String = ProbePolicy.GAME_PACKAGE): ContainerInstall? {
        return try { readRecord(packageName)?.let(::installInfo) } catch (_: Exception) { null }
    }

    fun importGame(onProgress: (String) -> Unit = {}): ContainerInstall {
        onProgress("正在检查安装包与原生库大小…")
        if (Build.VERSION.SDK_INT < 28) throw IOException("实验容器导入暂需 Android 9 或以上")
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val pkg = pm.getPackageInfo(ProbePolicy.GAME_PACKAGE,
            PackageManager.GET_ACTIVITIES or PackageManager.GET_META_DATA or PackageManager.GET_SIGNING_CERTIFICATES)
        val info = requireNotNull(pkg.applicationInfo) { "无法读取游戏应用信息" }
        val launch = pm.getLaunchIntentForPackage(ProbePolicy.GAME_PACKAGE)?.component
            ?: throw IOException("原版没有可识别的启动 Activity")
        val activity = pkg.activities.orEmpty().singleOrNull { it.name == launch.className }
            ?: throw IOException("启动 Activity 不在原版清单中")
        val sources = listOf(File(info.sourceDir)) + info.splitSourceDirs.orEmpty().map(::File)
        require(sources.size in 1..64 && sources.all { it.isFile && it.canRead() }) { "无法读取完整的 base/split APK" }
        val total = sources.sumOf { it.length() }
        require(total in 1..MAX_APK_TOTAL && sources.all { it.length() in 1..MAX_APK_BYTES }) { "安装包大小超出导入上限" }
        val versionCode = if (Build.VERSION.SDK_INT >= 28) pkg.longVersionCode else @Suppress("DEPRECATION") pkg.versionCode.toLong()
        val signers = pkg.signingInfo?.apkContentsSigners.orEmpty().map { sha(it.toByteArray()) }.sorted()
        require(signers.isNotEmpty()) { "无法读取原版签名，拒绝导入" }
        return stage(ProbePolicy.GAME_PACKAGE, pkg.versionName ?: "unknown", versionCode, activity.name,
            sources, signers, info.splitNames?.toList().orEmpty(), onProgress) { snapshots ->
            // Android already verified the installed split set. Rehash every OS-owned source after
            // copying; parse the copied base certificate separately (PackageManager rejects standalone splits).
            sources.zip(snapshots).forEach { (source, snapshot) ->
                require(sha(source) == sha(snapshot)) { "复制后的 APK 与系统安装内容不一致" }
            }
            @Suppress("DEPRECATION")
            val parsed = pm.getPackageArchiveInfo(snapshots.first().path, PackageManager.GET_SIGNING_CERTIFICATES)
                ?: throw IOException("无法解析复制后的 base APK")
            val copiedSigners = parsed.signingInfo?.apkContentsSigners.orEmpty().map { sha(it.toByteArray()) }.sorted()
            require(parsed.packageName == pkg.packageName && copiedSigners == signers && parsed.longVersionCode == versionCode) {
                "复制后的 base APK 签名、包名或版本不一致"
            }
            @Suppress("DEPRECATION")
            val latest = pm.getPackageInfo(pkg.packageName, 0).applicationInfo
            require(latest?.sourceDir == info.sourceDir && latest.splitSourceDirs.orEmpty().toList() == info.splitSourceDirs.orEmpty().toList()) {
                "游戏在导入过程中更新了，请重试"
            }
        }
    }

    fun importFixture(): ContainerInstall {
        val input = File(context.cacheDir, "container-fixture-${UUID.randomUUID()}.apk")
        try {
            context.assets.open("container-fixture.apk").use { source -> input.outputStream().use { source.copyTo(it) } }
            return stage(FIXTURE_PACKAGE, "1", 1, "$FIXTURE_PACKAGE.FixtureActivity", listOf(input), emptyList(), emptyList()) { }
        } finally { input.delete() }
    }

    private fun stage(packageName: String, version: String, versionCode: Long, activity: String,
        sources: List<File>, signers: List<String>, splits: List<String>, onProgress: (String) -> Unit = {},
        verify: (List<File>) -> Unit): ContainerInstall {
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: throw IOException("未知 CPU 架构")
        val nativePlan = NativeLibraries.plan(sources, abi)
        val needed = sources.sumOf { it.length() } + nativePlan.expandedBytes + 64L * 1024 * 1024
        val available = root.apply { mkdirs() }.usableSpace
        require(available > needed) { "空间不足：本次导入需 ${NativeLibraries.size(needed)}，当前可用 ${NativeLibraries.size(available)}" }
        val home = home(packageName).apply { mkdirs() }
        val staging = File(home, "staging-${UUID.randomUUID()}").apply { check(mkdirs()) }
        try {
            val records = JSONArray()
            val copied = sources.mapIndexed { index, source ->
                onProgress("正在复制游戏安装包（${index + 1}/${sources.size}）…")
                val name = if (index == 0) "base.apk" else "split-%03d.apk".format(java.util.Locale.ROOT, index)
                val destination = File(staging, name)
                val before = source.length()
                copyReadOnly(source, destination, MAX_APK_BYTES)
                require(destination.length() == before && source.length() == before) { "安装包在复制中变化" }
                records.put(JSONObject().put("name", name).put("bytes", destination.length()).put("sha256", sha(destination)))
                destination
            }
            onProgress("正在校验安装包完整性…")
            verify(copied)
            val libraries = NativeLibraries.extract(copied, abi, File(staging, "lib"), onLibrary = onProgress)
            val native = JSONArray().apply { libraries.forEach { library ->
                put(JSONObject().put("name", library.name).put("bytes", library.bytes).put("sha256", library.sha256))
            } }
            onProgress("正在保存容器安装信息…")
            val json = JSONObject().put("schema", 1).put("package", packageName).put("version", version)
                .put("versionCode", versionCode).put("activity", activity).put("apks", records)
                .put("signers", JSONArray(signers)).put("splits", JSONArray(splits)).put("libraries", native)
            val generation = "code-${UUID.randomUUID()}"
            val committed = File(home, generation)
            Files.move(staging.toPath(), committed.toPath(), StandardCopyOption.ATOMIC_MOVE)
            try {
                json.put("generation", generation)
                val record = AtomicFile(File(home, "active.json"))
                val output = record.startWrite()
                try { output.write(json.toString().toByteArray()); record.finishWrite(output) }
                catch (error: Throwable) { record.failWrite(output); throw error }
            } catch (error: Throwable) { committed.deleteRecursively(); throw error }
            // Keep older code snapshots until explicit maintenance, so an executing process never loses its APK.
            return installInfo(json)
        } finally { staging.deleteRecursively() }
    }

    fun load(packageName: String): GuestPackage {
        val json = readRecord(packageName) ?: throw IOException("请先导入容器")
        val install = installInfo(json)
        val generation = json.getString("generation")
        require(generation.matches(Regex("code-[a-f0-9-]{36}"))) { "损坏的容器版本记录" }
        val code = File(home(packageName), generation)
        val apks = json.getJSONArray("apks").let { list -> (0 until list.length()).map { index ->
            val record = list.getJSONObject(index)
            val name = record.getString("name")
            require(name.matches(Regex("base\\.apk|split-[0-9]{3}\\.apk"))) { "无效的容器文件名" }
            File(code, name).also { file ->
                require(file.isFile && file.length() == record.getLong("bytes") && sha(file) == record.getString("sha256")) { "容器 APK 完整性检查失败，请重新导入" }
            }
        } }
        require(apks.isNotEmpty() && apks.first().name == "base.apk") { "容器缺少 base.apk" }
        val data = File(home(packageName), "data").apply { mkdirs() }
        val libraries = File(code, "lib")
        val nativeRecords = json.getJSONArray("libraries")
        for (i in 0 until nativeRecords.length()) {
            val entry = nativeRecords.getJSONObject(i)
            val name = entry.getString("name")
            require(name.matches(Regex("lib[A-Za-z0-9_.+-]+\\.so")))
            require(sha(File(libraries, name)) == entry.getString("sha256")) { "容器原生库完整性检查失败" }
        }
        @Suppress("DEPRECATION")
        val parsed = context.packageManager.getPackageArchiveInfo(apks.first().path, PackageManager.GET_ACTIVITIES or PackageManager.GET_META_DATA)
            ?: throw IOException("无法解析容器 APK 清单")
        val info = ApplicationInfo(requireNotNull(parsed.applicationInfo)).apply {
            sourceDir = apks.first().path
            publicSourceDir = sourceDir
            splitSourceDirs = apks.drop(1).map { it.path }.toTypedArray()
            splitPublicSourceDirs = splitSourceDirs
            splitNames = json.getJSONArray("splits").let { names -> Array(names.length()) { names.getString(it) } }
            nativeLibraryDir = libraries.path
            dataDir = data.path
            deviceProtectedDataDir = File(data, "device-protected").apply { mkdirs() }.path
            uid = Process.myUid()
        }
        val activity = parsed.activities.orEmpty().singleOrNull { it.name == install.activity }
            ?: throw IOException("容器清单中找不到启动 Activity")
        val resolved = ActivityInfo(activity).apply { applicationInfo = info }
        // activity-alias resolves to its implementation class; retain the alias's manifest metadata.
        return GuestPackage(install, info, resolved, apks, data, libraries)
    }

    private fun readRecord(packageName: String): JSONObject? {
        val file = AtomicFile(File(home(packageName), "active.json"))
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return null
        return JSONObject(file.openRead().bufferedReader().use { it.readText() }).also {
            require(it.getInt("schema") == 1 && it.getString("package") == packageName)
        }
    }

    private fun home(packageName: String): File {
        require(packageName == ProbePolicy.GAME_PACKAGE || packageName == FIXTURE_PACKAGE) { "仅支持指定游戏和自检客体" }
        return File(root, packageName)
    }

    private fun installInfo(json: JSONObject) = ContainerInstall(json.getString("package"), json.getString("version"),
        json.getLong("versionCode"), json.getJSONArray("apks").length(),
        json.getJSONArray("apks").let { list -> (0 until list.length()).sumOf { list.getJSONObject(it).getLong("bytes") } },
        json.getString("activity"))

    companion object {
        const val FIXTURE_PACKAGE = "io.github.lcblauncher.fixture"
        private const val MAX_APK_BYTES = 4L * 1024 * 1024 * 1024
        private const val MAX_APK_TOTAL = 8L * 1024 * 1024 * 1024
        private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        private fun sha(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered().use { stream ->
                val buffer = ByteArray(64 * 1024)
                while (true) { val n = stream.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
        private fun copyReadOnly(source: File, target: File, limit: Long) {
            // Android 14+ requires dynamically loaded APKs to be read-only before code is written.
            FileOutputStream(target).use { output ->
                check(target.setReadOnly())
                var size = 0L
                source.inputStream().use { input ->
                    val buffer = ByteArray(128 * 1024)
                    while (true) {
                        val n = input.read(buffer); if (n < 0) break
                        size += n; require(size <= limit) { "APK 实际大小超出限制" }
                        output.write(buffer, 0, n)
                    }
                }
                output.fd.sync()
            }
        }
    }
}
