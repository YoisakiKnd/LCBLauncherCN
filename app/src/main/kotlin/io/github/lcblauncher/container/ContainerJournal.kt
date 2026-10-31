package io.github.lcblauncher.container

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Process
import android.util.AtomicFile
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.util.UUID

/** Bounded metadata only: exception types and method names, never messages, traces or game text. */
class ContainerJournal(private val context: Context) {
    private val record = AtomicFile(File(context.filesDir, "container-session.json"))
    private val sessionId = UUID.randomUUID().toString()
    private val startedAt = Instant.now().toString()
    private val events = ArrayDeque<JSONObject>()
    private var applicationClass: String? = null
    private var activityClass: String? = null
    private var markerAttempted = false
    private var markerInstalled = false

    @Synchronized fun identify(application: String, activity: String) {
        applicationClass = application
        activityClass = activity
    }

    @Synchronized fun write(packageName: String, stage: String, failure: Throwable? = null) {
        if (!markerAttempted) {
            markerAttempted = true
            if (Build.VERSION.SDK_INT >= 30) markerInstalled = runCatching {
                context.getSystemService(ActivityManager::class.java)?.setProcessStateSummary(sessionId.toByteArray(Charsets.US_ASCII))
                    ?: return@runCatching false
                true
            }.getOrDefault(false)
        }
        val at = Instant.now().toString()
        if (events.size == 24) events.removeFirst()
        events.addLast(JSONObject().put("stage", stage).put("at", at))
        val json = JSONObject().put("at", at).put("startedAt", startedAt).put("sessionId", sessionId)
            .put("package", packageName).put("stage", stage).put("pid", Process.myPid()).put("uid", Process.myUid())
            .put("systemMarkerInstalled", markerInstalled).put("hostRequestedClose", false)
            .put("applicationClass", applicationClass ?: JSONObject.NULL).put("activityClass", activityClass ?: JSONObject.NULL)
            .put("failureType", failure?.javaClass?.name ?: JSONObject.NULL).put("translationImplemented", false)
            .put("events", JSONArray(events.toList()))
        val causes = generateSequence(failure) { it.cause }.take(8).toList()
        json.put("failureChainTypes", JSONArray(causes.map { it.javaClass.name }))
        json.put("failureFrames", JSONArray(causes.lastOrNull()?.stackTrace.orEmpty().take(8).map { "${it.className}.${it.methodName}" }))
        save(json)
        Log.i("LCBContainer", "stage=$stage guest=$packageName failure=${failure?.javaClass?.name ?: "none"}")
    }

    @Synchronized fun markHostRequestedClose() {
        val json = read() ?: return
        if (json.optInt("pid") != Process.myPid() || json.optString("sessionId") != sessionId) return
        save(json.put("hostRequestedClose", true).put("hostClosedAt", Instant.now().toString()))
    }

    private fun save(json: JSONObject) {
        val stream = record.startWrite()
        try { stream.write(json.toString().toByteArray()); record.finishWrite(stream) }
        catch (error: Throwable) { record.failWrite(stream); throw error }
    }
    @Synchronized fun read(): JSONObject? = try {
        if (record.baseFile.exists()) JSONObject(record.openRead().bufferedReader().use { it.readText() }) else null
    } catch (_: Exception) { null }
}
