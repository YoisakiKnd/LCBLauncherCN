package io.github.lcblauncher

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.lcblauncher.container.ContainerInstall
import io.github.lcblauncher.container.ContainerStore
import io.github.lcblauncher.container.ContainerJournal
import io.github.lcblauncher.core.ArchiveRejected
import io.github.lcblauncher.core.TranslationArchive
import io.github.lcblauncher.data.LocalArchiveStore
import io.github.lcblauncher.probe.DeviceProbe
import io.github.lcblauncher.probe.ProbeReport
import io.github.lcblauncher.probe.ProbeSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Instant

data class LauncherState(
    val snapshot: ProbeSnapshot? = null,
    val container: ContainerInstall? = null,
    val containerStage: String? = null,
    val archive: TranslationArchive? = null,
    val busy: Boolean = true,
    val busyLabel: String = "正在读取设备信息…",
    val message: String? = null,
    val feedback: String = "not_tested",
    val lastLaunchAt: String? = null,
)

class LauncherViewModel(application: Application) : AndroidViewModel(application) {
    private val probe = DeviceProbe(application)
    private val archives = LocalArchiveStore(application)
    private val preferences = application.getSharedPreferences("test-session", 0)
    private val mutable = MutableStateFlow(LauncherState(
        feedback = preferences.getString("feedback", "not_tested") ?: "not_tested",
        lastLaunchAt = preferences.getString("lastLaunchAt", null),
    ))
    val state = mutable.asStateFlow()

    init { refresh(initial = true) }

    fun refresh(initial: Boolean = false) {
        if (!initial && mutable.value.busy) return
        mutable.value = mutable.value.copy(busy = true, busyLabel = "正在检查设备与游戏…")
        viewModelScope.launch {
            try {
                val snapshot = withContext(Dispatchers.IO) { probe.capture() }
                val container = withContext(Dispatchers.IO) { ContainerStore(getApplication()).installed() }
                val session = withContext(Dispatchers.IO) { ContainerJournal(getApplication()).read() }
                var cacheMessage: String? = null
                val archive = if (initial) withContext(Dispatchers.IO) {
                    try { archives.load() } catch (_: Exception) {
                        cacheMessage = "上次的汉化包缓存不可用，请重新导入检查。"
                        null
                    }
                } else mutable.value.archive
                mutable.value = mutable.value.copy(snapshot = snapshot, archive = archive, container = container,
                    containerStage = session?.optString("stage"), busy = false,
                    message = cacheMessage ?: if (initial) null else "检测信息已刷新")
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                mutable.value = mutable.value.copy(busy = false, message = "设备检测失败，请重试")
            }
        }
    }


    fun importContainer(fixture: Boolean, onReady: () -> Unit = {}) {
        if (mutable.value.busy) return
        mutable.value = mutable.value.copy(busy = true,
            busyLabel = if (fixture) "正在准备容器自检客体…" else "正在复制和校验原版分包，请保持本页打开…", message = null)
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val store = ContainerStore(getApplication())
                    if (fixture) store.importFixture() else store.importGame()
                }
                mutable.value = mutable.value.copy(busy = false,
                    container = if (fixture) mutable.value.container else result,
                    message = if (fixture) null else "原版 APK 已导入容器。系统原版数据不会迁移；游戏兼容与汉化仍待验证。")
                onReady()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                mutable.value = mutable.value.copy(busy = false,
                    message = "容器导入失败：${if (e is IllegalArgumentException || e is IOException) e.message else e.javaClass.simpleName}")
            }
        }
    }

    fun importArchive(uri: Uri) {
        if (mutable.value.busy) return
        mutable.value = mutable.value.copy(busy = true, busyLabel = "正在校验 ZIP 和文本结构…", message = null)
        viewModelScope.launch {
            try {
                val audit = withContext(Dispatchers.IO) { archives.importArchive(uri) }
                mutable.value = mutable.value.copy(archive = audit, busy = false,
                    message = "汉化包结构检查通过，已保存在启动器内。尚未应用到游戏。")
            } catch (e: CancellationException) {
                throw e
            } catch (e: ArchiveRejected) {
                mutable.value = mutable.value.copy(busy = false, message = "导入失败：${e.message}。原有缓存保留。")
            } catch (_: Exception) {
                mutable.value = mutable.value.copy(busy = false, message = "无法读取或保存所选文件，请检查空间后重试。原有缓存保留。")
            }
        }
    }

    fun exportReport(uri: Uri) {
        val snapshot = mutable.value.snapshot ?: return
        if (mutable.value.busy) return
        val captured = mutable.value
        mutable.value = captured.copy(busy = true, busyLabel = "正在导出诊断…", message = null)
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val report = org.json.JSONObject(ProbeReport.create(snapshot, captured.archive, captured.lastLaunchAt, captured.feedback)).apply {
                        put("container", org.json.JSONObject().apply {
                            put("engine", "independent-activity-alpha")
                            put("gameImported", captured.container != null)
                            put("gameVersion", captured.container?.version ?: org.json.JSONObject.NULL)
                            put("lastSession", ContainerJournal(getApplication()).read() ?: org.json.JSONObject.NULL)
                            put("nativeIoVirtualized", false)
                            put("googleLoginVerified", false)
                        })
                    }.toString(2)
                    val output = getApplication<Application>().contentResolver.openOutputStream(uri, "wt")
                        ?: throw IOException("No output stream")
                    output.use { it.write(report.toByteArray(Charsets.UTF_8)) }
                }
                mutable.value = mutable.value.copy(busy = false, message = "诊断已保存，可将 JSON 文件发送给开发者。")
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                mutable.value = mutable.value.copy(busy = false, message = "诊断保存失败，请重新选择保存位置")
            }
        }
    }

    fun recordOriginalLaunch() {
        val time = Instant.now().toString()
        preferences.edit().putString("lastLaunchAt", time).putString("feedback", "not_tested").apply()
        mutable.value = mutable.value.copy(lastLaunchAt = time, feedback = "not_tested")
    }

    fun setFeedback(value: String) {
        if (value !in setOf("not_tested", "original_opened", "original_failed")) return
        preferences.edit().putString("feedback", value).apply()
        mutable.value = mutable.value.copy(feedback = value, message = "测试结果已记录，导出诊断时会一并保存。")
    }

    fun showMessage(message: String) { mutable.value = mutable.value.copy(message = message) }
    fun dismissMessage() { mutable.value = mutable.value.copy(message = null) }
}
