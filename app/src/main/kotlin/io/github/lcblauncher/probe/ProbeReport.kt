package io.github.lcblauncher.probe

import io.github.lcblauncher.BuildConfig
import io.github.lcblauncher.core.ProbePolicy
import io.github.lcblauncher.core.TranslationArchive
import org.json.JSONArray
import org.json.JSONObject

object ProbeReport {
    fun create(snapshot: ProbeSnapshot, archive: TranslationArchive?, launchRequestedAt: String?, feedback: String): String {
        val device = snapshot.device
        val game = snapshot.game
        return JSONObject().apply {
            put("schema", ProbePolicy.REPORT_SCHEMA)
            put("capturedAt", snapshot.capturedAt)
            put("application", JSONObject().apply {
                put("package", BuildConfig.APPLICATION_ID)
                put("version", BuildConfig.VERSION_NAME)
                put("versionCode", BuildConfig.VERSION_CODE)
            })
            put("runtime", JSONObject().apply {
                put("translationImplemented", false)
                put("launchMode", "original-system-application")
                put("archiveAuditMeansCompatible", false)
            })
            put("device", JSONObject().apply {
                put("manufacturer", device.manufacturer)
                put("model", device.model)
                put("android", device.androidVersion)
                put("api", device.api)
                put("abis", JSONArray(device.abis))
                put("process64Bit", device.process64Bit)
                put("pageBytes", device.pageBytes ?: JSONObject.NULL)
                put("totalRamBytes", device.totalRam)
                put("lowRam", device.lowRam)
                put("availableStorageBytes", device.availableStorage)
                put("batteryUnrestricted", device.batteryUnrestricted)
                put("googleServicesInstalled", device.googleServicesInstalled)
                put("playStoreInstalled", device.playStoreInstalled)
            })
            put("game", JSONObject().apply {
                put("package", ProbePolicy.GAME_PACKAGE)
                put("installed", game.installed)
                put("version", game.versionName ?: JSONObject.NULL)
                put("versionCode", game.versionCode ?: JSONObject.NULL)
                put("installer", game.installer ?: JSONObject.NULL)
                put("launchable", game.launchable)
                put("error", game.error ?: JSONObject.NULL)
                put("signingCertificateSha256", JSONArray(game.certificateSha256))
                put("apks", JSONArray().apply {
                    game.apks.forEach { apk -> put(JSONObject().apply {
                        put("name", apk.name)
                        put("bytes", apk.bytes)
                        put("readable", apk.readable)
                        put("nativeAbis", JSONArray(apk.nativeAbis))
                        put("runtimeLibraries", JSONArray(apk.runtimeLibraries))
                        put("localizationHints", JSONArray(apk.localizationHints))
                    }) }
                })
            })
            put("archive", archive?.let { audit -> JSONObject().apply {
                put("sha256", audit.sha256)
                put("compressedBytes", audit.compressedBytes)
                put("expandedBytes", audit.expandedBytes)
                put("entries", audit.entries)
                put("jsonFiles", audit.files.size)
                put("displayFields", audit.displayFields)
            } } ?: JSONObject.NULL)
            put("manualTest", JSONObject().apply {
                put("originalLaunchRequestedAt", launchRequestedAt ?: JSONObject.NULL)
                put("result", feedback)
                put("resultSource", "user-reported")
            })
            put("privacy", "No account data, device identifiers, game saves, translation text, document URIs or APK contents collected.")
        }.toString(2)
    }
}
