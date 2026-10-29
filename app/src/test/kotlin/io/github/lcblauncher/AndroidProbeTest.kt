package io.github.lcblauncher

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.net.Uri
import io.github.lcblauncher.core.ArchiveRejected
import io.github.lcblauncher.core.ProbePolicy
import io.github.lcblauncher.data.LocalArchiveStore
import io.github.lcblauncher.probe.DeviceProbe
import io.github.lcblauncher.probe.ProbeReport
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AndroidProbeTest {
    private lateinit var context: Context

    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
    }

    @Test fun missingGameIsReportedWithoutClaimingRuntimeCompatibility() {
        val snapshot = DeviceProbe(context).capture()
        assertEquals(36, snapshot.device.api)
        assertFalse(snapshot.game.installed)
        val report = JSONObject(ProbeReport.create(snapshot, null, null, "not_tested"))
        assertFalse(report.getJSONObject("runtime").getBoolean("translationImplemented"))
        assertEquals("original-system-application", report.getJSONObject("runtime").getString("launchMode"))
        assertTrue(report.isNull("archive"))
        assertFalse(report.has("androidId"))
        assertFalse(report.has("serial"))
    }

    @Test fun installedGameWithUnreadableApkStillReportsItsVersion() {
        val packageInfo = PackageInfo().apply {
            packageName = ProbePolicy.GAME_PACKAGE
            versionName = "test-fixture"
            longVersionCode = 123
            applicationInfo = ApplicationInfo().apply {
                packageName = ProbePolicy.GAME_PACKAGE
                sourceDir = File(context.cacheDir, "missing-game.apk").path
                splitSourceDirs = arrayOf(File(context.cacheDir, "missing-split.apk").path)
            }
        }
        Shadows.shadowOf(context.packageManager).installPackage(packageInfo)
        val game = DeviceProbe(context).capture().game
        assertTrue(game.installed)
        assertEquals("test-fixture", game.versionName)
        assertEquals(123L, game.versionCode)
        assertEquals(2, game.apks.size)
        assertTrue(game.apks.none { it.readable })
        assertNull(game.error)
    }

    @Test fun rejectedImportPreservesLastAuditedPackageAcrossStoreInstances() {
        val valid = createZip("text.json", """{"name":"本地测试译文"}""")
        val invalid = createZip("../outside.json", """{"name":"bad"}""")
        val store = LocalArchiveStore(context)
        val original = store.importArchive(Uri.fromFile(valid))
        try {
            store.importArchive(Uri.fromFile(invalid))
            fail("Must reject unsafe archive")
        } catch (_: ArchiveRejected) { /* expected */ }
        assertEquals(original.sha256, LocalArchiveStore(context).load()?.sha256)
        assertFalse(File(context.filesDir, "outside.json").exists())
        assertTrue(context.cacheDir.listFiles().orEmpty().none { it.name.startsWith("archive-") })
    }

    @Test fun tamperedCacheIsRejectedInsteadOfDisplayedAsValidated() {
        val valid = createZip("text.json", """{"name":"original"}""")
        val audit = LocalArchiveStore(context).importArchive(Uri.fromFile(valid))
        val cached = File(context.filesDir, "translation-audit/${audit.sha256}.zip")
        val altered = createZip("text.json", """{"name":"changed"}""")
        altered.copyTo(cached, overwrite = true)
        try { LocalArchiveStore(context).load(); fail("Must reject hash mismatch") }
        catch (_: java.io.IOException) { /* expected */ }
    }

    @Test fun diagnosticsContainAuditStatisticsButNoTranslationTextOrFileUris() {
        val file = createZip("text.json", """{"name":"不会出现在诊断的译文"}""")
        val audit = LocalArchiveStore(context).importArchive(Uri.fromFile(file))
        val report = ProbeReport.create(DeviceProbe(context).capture(), audit, null, "original_opened")
        assertEquals(1, JSONObject(report).getJSONObject("archive").getInt("displayFields"))
        assertFalse(report.contains("不会出现在诊断的译文"))
        assertFalse(report.contains("file://"))
        assertFalse(report.contains(context.filesDir.path))
    }

    @Test fun mainActivityCanCreateAndRecreateOnAndroid16() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        assertFalse(controller.get().isFinishing)
        controller.recreate()
        assertFalse(controller.get().isFinishing)
        controller.pause().stop().destroy()
    }

    private fun createZip(path: String, contents: String): File {
        val file = File.createTempFile("fixture-", ".zip", context.cacheDir)
        ZipOutputStream(file.outputStream()).use {
            it.putNextEntry(ZipEntry(path))
            it.write(contents.toByteArray(Charsets.UTF_8))
            it.closeEntry()
        }
        return file
    }
}
