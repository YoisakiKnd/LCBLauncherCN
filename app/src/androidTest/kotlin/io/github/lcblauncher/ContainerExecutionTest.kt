package io.github.lcblauncher

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Process
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import io.github.lcblauncher.container.ContainerActivity
import io.github.lcblauncher.container.ContainerJournal
import io.github.lcblauncher.container.ContainerStore
import io.github.lcblauncher.container.ContainerProcessObservation
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ContainerExecutionTest {
    @Test fun uninstalledApkExecutesInAnotherProcessAndKeepsGuestDataAcrossCodeImport() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        val store = ContainerStore(context)
        fun assertNotInstalled() {
            try {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(ContainerStore.FIXTURE_PACKAGE, 0)
                fail("Guest must not be installed as a system application")
            } catch (_: PackageManager.NameNotFoundException) { /* expected */ }
        }
        assertNotInstalled()
        // Capture the actual app for visual review; these images are not game compatibility evidence.
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        assertTrue(device.wait(Until.hasObject(By.textContains("导入原版游戏")), 15_000))
        device.waitForIdle()
        assertTrue(device.takeScreenshot(File(context.cacheDir, "ui-home.png")))
        device.findObject(By.desc("导航：资源")).click()
        assertTrue(device.wait(Until.hasObject(By.text("资源档案")), 10_000))
        device.waitForIdle()
        assertTrue(device.takeScreenshot(File(context.cacheDir, "ui-archive.png")))
        device.findObject(By.desc("导航：工具")).click()
        assertTrue(device.wait(Until.hasObject(By.text("工具与诊断")), 10_000))
        device.waitForIdle()
        assertTrue(device.takeScreenshot(File(context.cacheDir, "ui-tools.png")))
        device.findObject(By.desc("导航：启程")).click()
        assertTrue(device.wait(Until.hasObject(By.textContains("导入原版游戏")), 10_000))
        device.waitForIdle()
        device.setOrientationLeft()
        device.waitForIdle()
        // Accessibility can report idle before the recreated window's first frame reaches SurfaceFlinger.
        SystemClock.sleep(1_000)
        assertTrue(device.displayWidth > device.displayHeight)
        assertTrue(device.wait(Until.hasObject(By.textContains("导入原版游戏")), 10_000))
        assertTrue(device.takeScreenshot(File(context.cacheDir, "ui-home-landscape.png")))
        device.setOrientationNatural()
        device.unfreezeRotation()
        device.waitForIdle()
        File(context.filesDir, "container/${ContainerStore.FIXTURE_PACKAGE}").deleteRecursively()
        var previousPid = Process.myPid()
        for (count in 1..2) {
            store.importFixture()
            context.startActivity(Intent(context, ContainerActivity::class.java)
                .putExtra(ContainerActivity.EXTRA_PACKAGE, ContainerStore.FIXTURE_PACKAGE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            assertTrue("Guest UI absent; journal=${ContainerJournal(context).read()}",
                device.wait(Until.hasObject(By.textContains("GUEST_COUNT=$count")), 20_000))
            val text = device.findObject(By.textContains("GUEST_COUNT=$count")).text
            assertTrue(text.contains("GUEST_RESOURCE_OK"))
            assertTrue(text.contains("GUEST_ASSET_OK"))
            val session = requireNotNull(ContainerJournal(context).read())
            assertEquals("guest_resumed", session.getString("stage"))
            assertEquals("${ContainerStore.FIXTURE_PACKAGE}.FixtureApplication", session.getString("applicationClass"))
            assertTrue(session.getJSONArray("events").toString().contains("calling_application_on_create"))
            assertNotEquals(Process.myPid(), session.getInt("pid"))
            assertNotEquals(previousPid, session.getInt("pid"))
            assertEquals(Process.myUid(), session.getInt("uid"))
            previousPid = session.getInt("pid")
            device.pressBack()
            assertTrue(device.wait(Until.gone(By.textContains("GUEST_COUNT=$count")), 10_000))
            device.waitForIdle()
        }
        val guestFiles = File(context.filesDir, "container/${ContainerStore.FIXTURE_PACKAGE}/data/files").apply { mkdirs() }
        fun launch() = context.startActivity(Intent(context, ContainerActivity::class.java)
            .putExtra(ContainerActivity.EXTRA_PACKAGE, ContainerStore.FIXTURE_PACKAGE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val exitMarker = File(guestFiles, "exit-during-attach")
        val throwMarker = File(guestFiles, "throw-during-create")
        try {
            exitMarker.writeText("test")
            launch()
            val deadline = SystemClock.uptimeMillis() + 15_000
            var observed = org.json.JSONObject()
            var session = org.json.JSONObject()
            while (SystemClock.uptimeMillis() < deadline) {
                session = ContainerJournal(context).read() ?: org.json.JSONObject()
                observed = ContainerProcessObservation.capture(context, session)
                if (session.optString("stage") == "constructing_attaching_application" && observed.optString("state") == "exited") break
                SystemClock.sleep(100)
            }
            assertEquals("Exit record not matched: $observed / $session", "exited", observed.getString("state"))
            assertEquals("constructing_attaching_application", session.getString("stage"))
            assertEquals("exit_self", observed.getJSONObject("exit").getString("reason"))
            assertEquals(23, observed.getJSONObject("exit").getInt("status"))
            assertEquals("system_session_marker", observed.getJSONObject("exit").getString("matchedBy"))
            assertFalse(observed.getBoolean("hostRequestedClose"))
            assertTrue(session.isNull("failureType"))

            throwMarker.writeText("test")
            launch()
            assertTrue(device.wait(Until.hasObject(By.textContains("容器执行失败")), 15_000))
            val failed = requireNotNull(ContainerJournal(context).read())
            assertEquals("failed_calling_application_on_create", failed.getString("stage"))
            assertEquals("java.lang.IllegalStateException", failed.getString("failureType"))
            assertFalse(failed.toString().contains("fixture-sensitive-message-must-not-be-exported"))
            assertEquals("pid_present", ContainerProcessObservation.capture(context, failed).getString("state"))
            device.pressBack()
            device.waitForIdle()
        } finally { exitMarker.delete(); throwMarker.delete() }
        assertNotInstalled()
    }
}
