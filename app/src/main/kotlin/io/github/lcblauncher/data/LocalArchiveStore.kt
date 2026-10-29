package io.github.lcblauncher.data

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import io.github.lcblauncher.core.ArchiveLimits
import io.github.lcblauncher.core.ArchiveRejected
import io.github.lcblauncher.core.TranslationArchive
import io.github.lcblauncher.core.TranslationArchiveInspector
import io.github.lcblauncher.core.copyBounded
import io.github.lcblauncher.core.sha256
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class LocalArchiveStore(private val context: Context) {
    private val directory = File(context.filesDir, "translation-audit")
    private val active = File(directory, "active-sha256")
    private val inspector = TranslationArchiveInspector()

    fun load(): TranslationArchive? {
        if (!active.exists()) return null
        if (active.length() != 64L) throw IOException("缓存记录无效，请重新导入")
        val hash = active.readText(Charsets.US_ASCII)
        if (!hash.matches(Regex("[0-9a-f]{64}"))) throw IOException("缓存记录无效，请重新导入")
        val file = File(directory, "$hash.zip")
        if (!file.isFile) throw IOException("缓存文件缺失，请重新导入")
        return inspector.inspect(file).also {
            if (it.sha256 != hash) throw IOException("缓存校验失败，请重新导入")
        }
    }

    fun importArchive(uri: Uri): TranslationArchive {
        val staging = File.createTempFile("archive-", ".zip", context.cacheDir)
        try {
            val input = context.contentResolver.openInputStream(uri) ?: throw IOException("无法打开所选文件")
            input.use { source -> staging.outputStream().use { output ->
                copyBounded(source, output, ArchiveLimits().compressedBytes)
                output.fd.sync()
            } }
            val audit = inspector.inspect(staging)
            if (!directory.isDirectory && !directory.mkdirs()) throw IOException("无法创建缓存目录")
            val destination = AtomicFile(File(directory, "${audit.sha256}.zip"))
            val stream = destination.startWrite()
            try {
                staging.inputStream().use { it.copyTo(stream) }
                destination.finishWrite(stream)
            } catch (e: Exception) {
                destination.failWrite(stream)
                throw e
            }
            if (!destination.baseFile.isFile || destination.baseFile.length() != audit.compressedBytes ||
                destination.baseFile.inputStream().use { sha256(it) } != audit.sha256) {
                throw IOException("缓存文件写入校验失败")
            }
            // Rename is the single commit point. SharedPreferences.commit() can update its in-memory
            // value even when its disk write fails, so it must not select the active package.
            val pointer = File.createTempFile("active-", ".tmp", directory)
            try {
                pointer.outputStream().use {
                    it.write(audit.sha256.toByteArray(Charsets.US_ASCII))
                    it.fd.sync()
                }
                Files.move(pointer.toPath(), active.toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } finally {
                pointer.delete()
            }
            // Only one audited package is retained; no game files are touched.
            directory.listFiles()?.filter { it.name.matches(Regex("[0-9a-f]{64}\\.zip")) && it.name != "${audit.sha256}.zip" }
                ?.forEach { it.delete() }
            return audit
        } catch (e: ArchiveRejected) {
            throw e
        } finally {
            staging.delete()
        }
    }
}
