package io.github.lcblauncher.core

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class TranslationArchiveTest {
    @Test fun auditsDisplayStringsWithoutCountingIdentifiersOrChangingContent() = withZip(
        "jp/JP_Test.json" to """{"dataList":[{"id":"身份编号","name":"你好","voicefile":"语音路径","desc":"<color=#fff>描述</color>"}]}""".toByteArray(),
    ) { file ->
        val original = file.readBytes()
        val report = TranslationArchiveInspector().inspect(file)
        assertEquals(2, report.displayFields)
        assertEquals("jp/JP_Test.json", report.files.single().path)
        assertEquals(64, report.sha256.length)
        assertArrayEquals(original, file.readBytes())
    }

    @Test fun rejectsTraversalAbsoluteWindowsAndControlPaths() {
        for (path in listOf("../escape.json", "/escape.json", "C:/escape.json", "a\\escape.json", "a/./b.json", "a//b.json", "a\u0000.json")) {
            withZip(path to """{"name":"test"}""".toByteArray()) { file ->
                assertRejected { TranslationArchiveInspector().inspect(file) }
            }
        }
    }

    @Test fun rejectsDirectoryFilePathCollisions() = withZip(
        "jp/" to byteArrayOf(), "jp" to byteArrayOf(), "file.json" to """{"name":"test"}""".toByteArray(),
    ) { file -> assertRejected { TranslationArchiveInspector().inspect(file) } }

    @Test fun rejectsExpandedZipBombAndOversizedIgnoredFiles() = withZip(
        "ignored.bin" to ByteArray(5000), "text.json" to """{"name":"test"}""".toByteArray(),
    ) { file ->
        assertRejected { TranslationArchiveInspector(ArchiveLimits(entryBytes = 1024)).inspect(file) }
        assertRejected { TranslationArchiveInspector(ArchiveLimits(expandedBytes = 1024)).inspect(file) }
    }

    @Test fun rejectsTooManyEntries() = withZip(
        "a.json" to """{"name":"test"}""".toByteArray(), "b.json" to "{}".toByteArray(),
    ) { file -> assertRejected { TranslationArchiveInspector(ArchiveLimits(entries = 1)).inspect(file) } }

    @Test fun rejectsInvalidUtf8AndMalformedJson() {
        for (bytes in listOf(byteArrayOf(0xc3.toByte(), 0x28), "{".toByteArray(), "true".toByteArray())) {
            withZip("text.json" to bytes) { file -> assertRejected { TranslationArchiveInspector().inspect(file) } }
        }
    }

    @Test fun rejectsDeepJsonButIgnoresBracketsInsideStrings() {
        withZip("text.json" to ("[".repeat(50) + "{}" + "]".repeat(50)).toByteArray()) { file ->
            assertRejected { TranslationArchiveInspector().inspect(file) }
        }
        withZip("text.json" to """{"name":"[[[[[\\\""}""".toByteArray()) { file ->
            assertEquals(1, TranslationArchiveInspector(ArchiveLimits(jsonDepth = 2)).inspect(file).displayFields)
        }
    }

    @Test fun rejectsUnrelatedZipAndAcceptsUtf8Bom() {
        withZip("metadata.json" to """{"id":"test"}""".toByteArray()) { file ->
            assertRejected { TranslationArchiveInspector().inspect(file) }
        }
        withZip("text.json" to "\uFEFF{\"name\":\"中文\"}".toByteArray()) { file ->
            assertEquals(1, TranslationArchiveInspector().inspect(file).displayFields)
        }
    }

    @Test fun boundedCopyRejectsProviderWhoseReportedSizeCannotBeTrusted() {
        val output = ByteArrayOutputStream()
        assertRejected { copyBounded(ByteArrayInputStream(ByteArray(9000)), output, 100) }
        assertEquals(0, output.size())
    }

    @Test fun compatibleHardwareNeverClaimsTranslationIsImplemented() {
        assertFalse(ProbePolicy.translationRuntime().available)
    }

    private fun assertRejected(block: () -> Unit) {
        try { block(); fail("Expected ArchiveRejected") } catch (_: ArchiveRejected) { /* expected */ }
    }

    private fun withZip(vararg entries: Pair<String, ByteArray>, block: (File) -> Unit) {
        val file = File.createTempFile("lcb-archive-", ".zip")
        try {
            ZipOutputStream(file.outputStream()).use { zip ->
                entries.forEach { (path, bytes) ->
                    zip.putNextEntry(ZipEntry(path))
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
            block(file)
        } finally { file.delete() }
    }
}
