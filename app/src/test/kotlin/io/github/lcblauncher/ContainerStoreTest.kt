package io.github.lcblauncher

import android.content.Context
import io.github.lcblauncher.container.ContainerStore
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ContainerStoreTest {
    private lateinit var context: Context
    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        File(context.filesDir, "container").deleteRecursively()
    }
    @Test fun unknownPackageCannotSelectAStoragePath() {
        try { ContainerStore(context).load("../../outside"); fail("Must reject package") }
        catch (_: IllegalArgumentException) { }
    }
    @Test fun tamperedGuestCodeIsRejectedBeforeClassLoading() {
        val store = ContainerStore(context)
        store.importFixture()
        val apk = File(context.filesDir, "container/${ContainerStore.FIXTURE_PACKAGE}")
            .walkTopDown().first { it.name == "base.apk" }
        check(apk.setWritable(true))
        apk.appendBytes(byteArrayOf(0))
        try { store.load(ContainerStore.FIXTURE_PACKAGE); fail("Must reject corrupted snapshot") }
        catch (_: IllegalArgumentException) { }
    }
    @Test fun failedGameImportPreservesExistingGuestSnapshot() {
        val store = ContainerStore(context)
        val previous = store.importFixture()
        try { store.importGame(); fail("No original game is installed in this test") }
        catch (_: android.content.pm.PackageManager.NameNotFoundException) { }
        assertEquals(previous, store.installed(ContainerStore.FIXTURE_PACKAGE))
        assertNull(store.installed())
    }
}
