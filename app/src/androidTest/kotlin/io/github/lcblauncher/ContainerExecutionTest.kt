package io.github.lcblauncher

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import io.github.lcblauncher.container.ContainerActivity
import io.github.lcblauncher.container.ContainerJournal
import io.github.lcblauncher.container.ContainerStore
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
            assertNotEquals(Process.myPid(), session.getInt("pid"))
            assertNotEquals(previousPid, session.getInt("pid"))
            assertEquals(Process.myUid(), session.getInt("uid"))
            previousPid = session.getInt("pid")
            device.pressBack()
            assertTrue(device.wait(Until.gone(By.textContains("GUEST_COUNT=$count")), 10_000))
            device.waitForIdle()
        }
        assertNotInstalled()
    }
}
