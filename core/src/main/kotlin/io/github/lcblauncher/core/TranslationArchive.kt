package io.github.lcblauncher.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/** A structural audit only. A valid archive is never evidence of game compatibility. */
data class TranslationFile(
    val path: String,
    val bytes: Long,
    val sha256: String,
    val displayFields: Int,
)

data class TranslationArchive(
    val sha256: String,
    val compressedBytes: Long,
    val expandedBytes: Long,
    val entries: Int,
    val files: List<TranslationFile>,
) {
    val displayFields: Int get() = files.sumOf { it.displayFields }
}

data class ArchiveLimits(
    val compressedBytes: Long = 32L * 1024 * 1024,
    val expandedBytes: Long = 64L * 1024 * 1024,
    val entryBytes: Long = 4L * 1024 * 1024,
    val entries: Int = 4096,
    val jsonDepth: Int = 40,
)

class ArchiveRejected(message: String) : Exception(message)

/** Does not extract paths, execute content, or access the game's files. */
class TranslationArchiveInspector(private val limits: ArchiveLimits = ArchiveLimits()) {
    private val json = Json { isLenient = false; allowSpecialFloatingPointValues = false }

    fun inspect(file: File): TranslationArchive {
        if (file.length() > limits.compressedBytes) reject("压缩包超过大小限制")
        val digest = file.inputStream().use { sha256(it) }
        val paths = mutableSetOf<String>()
        val files = mutableListOf<TranslationFile>()
        var expanded = 0L
        var count = 0
        try {
            ZipInputStream(file.inputStream().buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (++count > limits.entries) reject("压缩包文件数量过多")
                    val path = checkedPath(entry.name)
                    if (!paths.add(path)) reject("压缩包包含重复路径")
                    val buffer = ByteArray(8192)
                    val capture = if (!entry.isDirectory && path.endsWith(".json", true)) {
                        ByteArrayOutputStream()
                    } else null
                    var entryBytes = 0L
                    while (true) {
                        val read = zip.read(buffer)
                        if (read < 0) break
                        entryBytes += read
                        expanded += read
                        if (entryBytes > limits.entryBytes) reject("单个文件超过大小限制")
                        if (expanded > limits.expandedBytes) reject("解压后内容超过大小限制")
                        capture?.write(buffer, 0, read)
                    }
                    if (capture != null) {
                        val bytes = capture.toByteArray()
                        val text = decodeUtf8(bytes).removePrefix("\uFEFF")
                        checkDepth(text)
                        val tree = try {
                            json.parseToJsonElement(text)
                        } catch (_: IllegalArgumentException) {
                            reject("压缩包包含无效 JSON")
                        }
                        if (tree !is JsonObject && tree !is JsonArray) reject("JSON 根节点必须是对象或数组")
                        files += TranslationFile(path, entryBytes, sha256(bytes.inputStream()), countDisplayFields(tree))
                    }
                    zip.closeEntry()
                }
            }
        } catch (e: ArchiveRejected) {
            throw e
        } catch (_: java.io.IOException) {
            reject("ZIP 损坏或读取失败")
        } catch (_: IllegalArgumentException) {
            reject("ZIP 编码无效")
        }
        if (files.isEmpty()) reject("没有找到 JSON 文本资源，请选择 ZIP 格式的汉化包")
        if (files.sumOf { it.displayFields } == 0) reject("没有找到可识别的显示文本字段")
        return TranslationArchive(digest, file.length(), expanded, count, files.sortedBy { it.path })
    }

    private fun checkedPath(raw: String): String {
        if (raw.length > 240 || raw.startsWith('/') || raw.contains('\\') || raw.contains(':') ||
            raw.any { it.code < 32 || it.code == 127 }) reject("压缩包包含不安全路径")
        val path = raw.removeSuffix("/")
        if (path.isEmpty() || path.split('/').any { it.isEmpty() || it == "." || it == ".." }) {
            reject("压缩包包含不安全路径")
        }
        return path
    }

    private fun decodeUtf8(bytes: ByteArray): String = try {
        Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
    } catch (_: CharacterCodingException) {
        reject("JSON 必须使用有效的 UTF-8 编码")
    }

    private fun checkDepth(text: String) {
        var depth = 0
        var inString = false
        var escaped = false
        for (c in text) {
            if (inString) {
                if (escaped) escaped = false
                else if (c == '\\') escaped = true
                else if (c == '"') inString = false
            } else when (c) {
                '"' -> inString = true
                '{', '[' -> if (++depth > limits.jsonDepth) reject("JSON 嵌套层数过多")
                '}', ']' -> depth--
            }
        }
    }

    private fun countDisplayFields(element: JsonElement): Int = when (element) {
        is JsonArray -> element.sumOf { countDisplayFields(it) }
        is JsonObject -> element.entries.sumOf { (key, value) ->
            if (key in DISPLAY_FIELDS && value is JsonPrimitive && value.isString && value.content.isNotBlank()) 1
            else countDisplayFields(value)
        }
        else -> 0
    }

    private fun reject(message: String): Nothing = throw ArchiveRejected(message)

    companion object {
        // These fields are counted, never substituted. IDs, resource paths and voice keys are excluded.
        private val DISPLAY_FIELDS = setOf("name", "title", "desc", "description", "content", "dialog", "dlg",
            "teller", "nameWithTitle", "summary", "flavor", "place", "story")
    }
}

fun sha256(input: InputStream): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(8192)
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        digest.update(buffer, 0, count)
    }
    return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

fun copyBounded(input: InputStream, output: java.io.OutputStream, limit: Long): Long {
    val buffer = ByteArray(8192)
    var total = 0L
    while (true) {
        val count = input.read(buffer)
        if (count < 0) return total
        total += count
        if (total > limit) throw ArchiveRejected("压缩包超过大小限制")
        output.write(buffer, 0, count)
    }
}
