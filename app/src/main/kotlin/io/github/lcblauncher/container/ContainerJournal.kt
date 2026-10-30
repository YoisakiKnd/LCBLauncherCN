package io.github.lcblauncher.container

import android.content.Context
import android.os.Process
import android.util.AtomicFile
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.time.Instant

/** A single bounded status record, no exception messages, paths, stacks, tokens or game text. */
class ContainerJournal(context: Context) {
    private val record = AtomicFile(File(context.filesDir, "container-session.json"))
    fun write(packageName: String, stage: String, failure: Throwable? = null) {
        val json = JSONObject().put("at", Instant.now().toString()).put("package", packageName)
            .put("stage", stage).put("pid", Process.myPid()).put("uid", Process.myUid())
            .put("failureType", failure?.javaClass?.name ?: JSONObject.NULL)
            .put("translationImplemented", false)
        val stream = record.startWrite()
        try { stream.write(json.toString().toByteArray()); record.finishWrite(stream) }
        catch (error: Throwable) { record.failWrite(stream); throw error }
        Log.i("LCBContainer", "stage=$stage guest=$packageName failure=${failure?.javaClass?.name ?: "none"}")
    }
    fun read(): JSONObject? = try {
        if (record.baseFile.exists()) JSONObject(record.openRead().bufferedReader().use { it.readText() }) else null
    } catch (_: Exception) { null }
}
