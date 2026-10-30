package io.github.lcblauncher.core

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class NativeLibrariesTest {
    @get:Rule val folder = TemporaryFolder()
    private val abi = "arm64-v8a"

    @Test fun libraryLargerThanOld128MiBCapIsImportedWithSmallStreamingBuffer() {
        val bytes = 129L * 1024 * 1024
        val archive = folder.newFile("large.apk")
        ZipOutputStream(archive.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("lib/$abi/libil2cpp.so"))
            val buffer = ByteArray(64 * 1024)
            repeat((bytes / buffer.size).toInt()) { zip.write(buffer) }
            zip.closeEntry()
        }
        val out = folder.newFolder("large-out")
        val result = NativeLibraries.extract(listOf(archive), abi, out).single()
        assertEquals(bytes, result.bytes)
        assertEquals(bytes, File(out, "libil2cpp.so").length())
        assertEquals(64, result.sha256.length)
        assertEquals(listOf("libil2cpp.so"), out.listFiles()!!.map { it.name })
    }

    @Test fun rejectedSizeIdentifiesLibraryActualBytesAndLimit() {
        val zip = archive("size.apk", mapOf("lib/$abi/libunity.so" to ByteArray(33)))
        try { NativeLibraries.plan(listOf(zip), abi, NativeLibraryLimits(32, 64)); fail("Must reject") }
        catch (e: NativeImportRejected) {
            assertTrue(e.message!!.contains("libunity.so"))
            assertTrue(e.message!!.contains("33 B"))
            assertTrue(e.message!!.contains("32 B"))
        }
    }

    @Test fun expandedTotalIsCheckedBeforeExtraction() {
        val zip = archive("total.apk", mapOf("lib/$abi/libone.so" to ByteArray(24), "lib/$abi/libtwo.so" to ByteArray(24)))
        try { NativeLibraries.plan(listOf(zip), abi, NativeLibraryLimits(32, 40)); fail("Must reject") }
        catch (e: NativeImportRejected) { assertTrue(e.message!!.contains("40 B")) }
    }

    @Test fun unsafeNativePathCannotEscapeStagingDirectory() {
        val zip = archive("unsafe.apk", mapOf("lib/$abi/../liboutside.so" to byteArrayOf(1)))
        val out = folder.newFolder("unsafe-out")
        try { NativeLibraries.extract(listOf(zip), abi, out); fail("Must reject") }
        catch (_: NativeImportRejected) { }
        assertTrue(out.listFiles()!!.isEmpty())
        assertFalse(File(folder.root, "liboutside.so").exists())
    }

    @Test fun identicalSplitLibrariesAreDeduplicatedButConflictingCopiesAreRejected() {
        val one = archive("one.apk", mapOf("lib/$abi/libmain.so" to byteArrayOf(1, 2)))
        val same = archive("same.apk", mapOf("lib/$abi/libmain.so" to byteArrayOf(1, 2)))
        assertEquals(1, NativeLibraries.extract(listOf(one, same), abi, folder.newFolder("same-out")).size)
        val different = archive("different.apk", mapOf("lib/$abi/libmain.so" to byteArrayOf(3, 4)))
        val out = folder.newFolder("different-out")
        try { NativeLibraries.extract(listOf(one, different), abi, out); fail("Must reject conflict") }
        catch (e: NativeImportRejected) { assertTrue(e.message!!.contains("libmain.so")) }
        assertArrayEquals(byteArrayOf(1, 2), File(out, "libmain.so").readBytes())
        assertTrue(out.listFiles()!!.none { it.name.startsWith("extract-") })
    }

    @Test fun unrelatedAbiIsNotExpandedOrCounted() {
        val zip = archive("abi.apk", mapOf("lib/x86_64/libtest.so" to ByteArray(50)))
        assertEquals(NativeLibraryPlan(0, 0), NativeLibraries.plan(listOf(zip), abi, NativeLibraryLimits(8, 8)))
    }

    private fun archive(name: String, entries: Map<String, ByteArray>): File = folder.newFile(name).also { file ->
        ZipOutputStream(file.outputStream()).use { zip -> entries.forEach { (name, bytes) ->
            zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry()
        } }
    }
}
