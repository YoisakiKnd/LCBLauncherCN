package io.github.lcblauncher

import android.content.Context
import android.os.Process
import io.github.lcblauncher.container.ContainerJournal
import io.github.lcblauncher.container.ContainerProcessObservation
import io.github.lcblauncher.container.ProcessExitEvidence
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ContainerObservationTest {
    private val name = "io.github.lcblauncher:container"
    private val uid = 12345
    private fun session() = JSONObject().put("pid", 23456).put("startedAt", "2026-10-31T00:00:00Z")
    private val start = java.time.Instant.parse("2026-10-31T00:00:00Z").toEpochMilli()
    private fun evidence(reason: Int = 5, status: Int = 11, time: Long = start + 1000, match: Boolean? = true) =
        ProcessExitEvidence(23456, uid, name, time, reason, status, match)

    @Test fun nativeCrashRequiresMatchingSystemEvidence() {
        val json = ContainerProcessObservation.describe(session(), name, uid, listOf(evidence()), false)
        assertEquals("exited", json.getString("state"))
        assertEquals("native_crash", json.getJSONObject("exit").getString("reason"))
        assertEquals("system_session_marker", json.getJSONObject("exit").getString("matchedBy"))
    }
    @Test fun staleOrForeignProcessHistoryIsNeverAttributedToThisLaunch() {
        val exits = listOf(evidence(time = start - 1), evidence().copy(uid = uid + 1),
            evidence().copy(processName = "other"), evidence(match = false))
        val json = ContainerProcessObservation.describe(session(), name, uid, exits, false)
        assertEquals("not_observed", json.getString("state"))
        assertFalse(json.has("exit"))
    }
    @Test fun missingExitRecordDoesNotBecomeACrashClaim() {
        val absent = ContainerProcessObservation.describe(session(), name, uid, emptyList(), false)
        val running = ContainerProcessObservation.describe(session(), name, uid, emptyList(), true)
        assertEquals("not_observed", absent.getString("state"))
        assertEquals("pid_present", running.getString("state"))
        assertFalse(absent.toString().contains("native_crash"))
        assertFalse(running.has("exit"))
    }
    @Test fun controlledHostCloseRemainsDistinctFromUnexpectedExit() {
        val json = ContainerProcessObservation.describe(session().put("hostRequestedClose", true), name, uid,
            listOf(evidence(reason = 2, status = 9)), false)
        assertTrue(json.getBoolean("hostRequestedClose"))
        assertEquals("signal", json.getJSONObject("exit").getString("reason"))
        assertEquals(9, json.getJSONObject("exit").getInt("status"))
    }
    @Test fun oldReportsCanUseExplicitPidAndTimestampFallback() {
        val legacy = JSONObject().put("pid", 23456).put("at", "2026-10-31T00:00:00Z")
        val json = ContainerProcessObservation.describe(legacy, name, uid, listOf(evidence(match = null)), false)
        assertEquals("pid_uid_name_time", json.getJSONObject("exit").getString("matchedBy"))
    }
    @Test fun journalKeepsPhaseHistoryAndTypesWithoutExceptionText() {
        val context: Context = RuntimeEnvironment.getApplication()
        val journal = ContainerJournal(context)
        journal.identify("example.GameApplication", "example.GameActivity")
        journal.write("com.ProjectMoon.LimbusCompany", "constructing_attaching_application")
        journal.write("com.ProjectMoon.LimbusCompany", "failed_calling_application_on_create", IllegalStateException("secret-account-token"))
        journal.markHostRequestedClose()
        val json = requireNotNull(journal.read())
        assertEquals(2, json.getJSONArray("events").length())
        assertEquals("example.GameApplication", json.getString("applicationClass"))
        assertEquals(Process.myPid(), json.getInt("pid"))
        assertTrue(json.getBoolean("hostRequestedClose"))
        assertFalse(json.toString().contains("secret-account-token"))
        assertFalse(json.toString().contains(context.filesDir.path))
    }
}
