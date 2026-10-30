package io.github.lcblauncher.core

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipFile

class NativeImportRejected(message: String) : IOException(message)
data class NativeLibraryLimits(val entryBytes: Long = 1024L * 1024 * 1024,
    val totalBytes: Long = 2L * 1024 * 1024 * 1024, val entries: Int = 256)
data class NativeLibrary(val name: String, val bytes: Long, val sha256: String)
data class NativeLibraryPlan(val expandedBytes: Long, val entries: Int)

/** A streaming native-library importer. Limits apply to expanded bytes, never to the copy buffer. */
object NativeLibraries {
    fun plan(apks: List<File>, abi: String, limits: NativeLibraryLimits = NativeLibraryLimits()): NativeLibraryPlan {
        var total = 0L
        var count = 0
        apks.forEach { apk -> ZipFile(apk).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.isDirectory || !entry.name.startsWith("lib/$abi/")) continue
                val name = libraryName(entry.name, abi)
                if (entry.size <= 0 || entry.size > limits.entryBytes)
                    throw NativeImportRejected("原生库 $name：${size(entry.size)}，单库上限 ${size(limits.entryBytes)}")
                if (++count > limits.entries) throw NativeImportRejected("原生库数量 $count，超过 ${limits.entries} 个上限")
                if (entry.size > limits.totalBytes - total)
                    throw NativeImportRejected("原生库展开总量超过 ${size(limits.totalBytes)}，当前文件 $name（${size(entry.size)}）")
                total += entry.size
            }
        } }
        return NativeLibraryPlan(total, count)
    }

    fun extract(apks: List<File>, abi: String, output: File, limits: NativeLibraryLimits = NativeLibraryLimits(),
        onLibrary: (String) -> Unit = {}): List<NativeLibrary> {
        plan(apks, abi, limits)
        check(output.isDirectory || output.mkdirs())
        val imported = linkedMapOf<String, NativeLibrary>()
        apks.forEach { apk -> ZipFile(apk).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.isDirectory || !entry.name.startsWith("lib/$abi/")) continue
                val name = libraryName(entry.name, abi)
                onLibrary("正在提取 $name · ${size(entry.size)}")
                val temp = File(output, "extract-${UUID.randomUUID()}")
                try {
                    val digest = MessageDigest.getInstance("SHA-256")
                    var bytes = 0L
                    zip.getInputStream(entry).use { input -> FileOutputStream(temp).use { stream ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            bytes += read
                            if (bytes > entry.size || bytes > limits.entryBytes)
                                throw NativeImportRejected("原生库 $name 的实际展开大小超过记录或上限（${size(bytes)}）")
                            stream.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                        }
                        if (bytes != entry.size) throw NativeImportRejected("原生库 $name 大小不完整：${size(bytes)} / ${size(entry.size)}")
                        stream.fd.sync()
                    } }
                    val hash = digest.digest().joinToString("") { "%02x".format(it) }
                    val previous = imported[name]
                    if (previous != null && previous.sha256 != hash)
                        throw NativeImportRejected("分包包含内容冲突的同名原生库：$name")
                    if (previous == null) {
                        check(temp.setReadOnly())
                        Files.move(temp.toPath(), File(output, name).toPath(), StandardCopyOption.ATOMIC_MOVE)
                        imported[name] = NativeLibrary(name, bytes, hash)
                    }
                } finally { temp.delete() }
            }
        } }
        return imported.values.toList()
    }

    private fun libraryName(path: String, abi: String): String {
        val name = path.removePrefix("lib/$abi/")
        if (!name.matches(Regex("lib[A-Za-z0-9_.+-]+\\.so"))) throw NativeImportRejected("安装包包含无效的原生库路径")
        return name
    }
    fun size(bytes: Long): String = when {
        bytes < 0 -> "未知大小"
        bytes >= 1024L * 1024 * 1024 -> String.format(Locale.ROOT, "%.2f GiB", bytes / (1024.0 * 1024 * 1024))
        bytes >= 1024 * 1024 -> String.format(Locale.ROOT, "%.1f MiB", bytes / (1024.0 * 1024))
        bytes >= 1024 -> String.format(Locale.ROOT, "%.1f KiB", bytes / 1024.0)
        else -> "$bytes B"
    }
}
