package io.github.lcblauncher.container

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Process
import org.json.JSONObject
import java.time.Instant

internal data class ProcessExitEvidence(val pid: Int, val uid: Int, val processName: String,
    val timestamp: Long, val reason: Int, val status: Int, val markerMatches: Boolean?)

/** Only observes this launcher's own container. Never opens traces, descriptions or other apps' logs. */
object ContainerProcessObservation {
    fun capture(context: Context, session: JSONObject?): JSONObject {
        if (session == null) return JSONObject().put("state", "no_session")
        val pid = session.optInt("pid", -1)
        if (pid <= 0 || session.optInt("uid", -1) != Process.myUid())
            return JSONObject().put("state", "unavailable").put("detail", "invalid_session_identity")
        val processName = "${context.packageName}:container"
        val manager = context.getSystemService(ActivityManager::class.java)
            ?: return JSONObject().put("state", "unavailable")
        return try {
            val marker = session.optString("sessionId").takeIf { it.isNotEmpty() }?.toByteArray(Charsets.US_ASCII)
            val exits = if (Build.VERSION.SDK_INT >= 30) {
                manager.getHistoricalProcessExitReasons(context.packageName, pid, 8).map { info ->
                    val summary = info.processStateSummary
                    ProcessExitEvidence(info.pid, info.realUid, info.processName, info.timestamp,
                        info.reason, info.status,
                        if (marker == null || summary == null) null else summary.contentEquals(marker))
                }
            } else emptyList()
            val running = manager.runningAppProcesses.orEmpty().any {
                it.pid == pid && it.uid == Process.myUid() && it.processName == processName
            }
            describe(session, processName, Process.myUid(), exits, running).apply {
                put("historySupported", Build.VERSION.SDK_INT >= 30)
            }
        } catch (error: RuntimeException) {
            JSONObject().put("state", "unavailable").put("failureType", error.javaClass.name)
        }
    }

    internal fun describe(session: JSONObject, processName: String, uid: Int,
        exits: List<ProcessExitEvidence>, running: Boolean): JSONObject {
        val start = runCatching { Instant.parse(session.optString("startedAt", session.optString("at"))).toEpochMilli() }.getOrNull()
        val match = if (start == null) null else exits.filter {
            it.pid == session.optInt("pid", -1) && it.uid == uid && it.processName == processName &&
                it.timestamp >= start && it.markerMatches != false
        }.sortedWith(compareByDescending<ProcessExitEvidence> { it.markerMatches == true }.thenBy { it.timestamp }).firstOrNull()
        return JSONObject().put("observedAt", Instant.now().toString()).apply {
            put("state", when { match != null -> "exited"; running -> "pid_present"; else -> "not_observed" })
            put("hostRequestedClose", session.optBoolean("hostRequestedClose", false))
            if (match != null) {
                put("exit", JSONObject().put("timestamp", match.timestamp).put("reasonCode", match.reason)
                    .put("reason", reasonLabel(match.reason)).put("status", match.status)
                    .put("matchedBy", if (match.markerMatches == true) "system_session_marker" else "pid_uid_name_time"))
            }
            // Absence of a process/exit record is not evidence of either a Java or a native crash.
        }
    }

    private fun reasonLabel(reason: Int): String = when (reason) {
        1 -> "exit_self"
        2 -> "signal"
        3 -> "low_memory"
        4 -> "java_crash"
        5 -> "native_crash"
        6 -> "anr"
        7 -> "initialization_failure"
        9 -> "resource_usage"
        10 -> "user_requested"
        11 -> "user_stopped"
        12 -> "dependency_died"
        else -> "other"
    }
}
