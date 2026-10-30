package io.github.lcblauncher

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lcblauncher.container.ContainerActivity
import io.github.lcblauncher.container.ContainerStore
import io.github.lcblauncher.core.ProbePolicy
import io.github.lcblauncher.ui.LauncherApp
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    private val model: LauncherViewModel by viewModels()
    private val importArchive = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(model::importArchive)
    }
    private val exportReport = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(model::exportReport)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        setContent {
            LauncherApp(
                state = model.state.collectAsStateWithLifecycle().value,
                onRefresh = { model.refresh() },
                onLaunchOriginal = ::launchOriginal,
                onImportGame = { model.importContainer(false) },
                onLaunchContainer = { launchContainer(ProbePolicy.GAME_PACKAGE) },
                onContainerSelfTest = { model.importContainer(true) { launchContainer(ContainerStore.FIXTURE_PACKAGE) } },
                onImport = { importArchive.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) },
                onExport = { exportReport.launch("LCBLauncher-diagnostic-${LocalDate.now()}.json") },
                onAppSettings = ::openAppSettings,
                onFeedback = model::setFeedback,
                onDismissMessage = model::dismissMessage,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        model.refresh(announce = false)
    }

    private fun launchContainer(guest: String) {
        startActivity(Intent(this, ContainerActivity::class.java).putExtra(ContainerActivity.EXTRA_PACKAGE, guest))
    }

    private fun launchOriginal() {
        try {
            val intent = packageManager.getLaunchIntentForPackage(ProbePolicy.GAME_PACKAGE)
            if (intent == null) {
                model.showMessage("找不到游戏启动入口，请确认原版安装在当前用户空间。")
                return
            }
            startActivity(intent)
            model.recordOriginalLaunch()
        } catch (_: ActivityNotFoundException) {
            model.showMessage("游戏启动入口已变化，请刷新检测后重试。")
        } catch (_: SecurityException) {
            model.showMessage("系统阻止了启动请求，请在系统设置中检查相关权限。")
        }
    }

    private fun openAppSettings() {
        try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.parse("package:$packageName")))
        } catch (_: ActivityNotFoundException) {
            model.showMessage("当前系统没有提供应用设置入口，请手动打开系统设置。")
        } catch (_: SecurityException) {
            model.showMessage("无法打开应用设置，请手动打开系统设置。")
        }
    }
}
