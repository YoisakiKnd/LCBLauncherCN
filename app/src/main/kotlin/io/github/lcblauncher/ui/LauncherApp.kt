package io.github.lcblauncher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.lcblauncher.BuildConfig
import io.github.lcblauncher.LauncherState
import io.github.lcblauncher.core.ProbePolicy
import io.github.lcblauncher.probe.GameSnapshot
import java.util.Locale

private val Background = Color(0xFF101113)
private val Surface = Color(0xFF1B1D21)
private val Border = Color(0xFF303237)
private val Gold = Color(0xFFE6B767)
private val Muted = Color(0xFFA5A5AA)
private val Green = Color(0xFF9CC6AD)
private val Ink = Color(0xFFF0EDE7)

private enum class Page(val title: String, val subtitle: String) {
    HOME("启程", "你的巴士，即将出发"),
    ARCHIVE("汉化包", "让每一份文本都有迹可循"),
    DIAGNOSTICS("诊断", "从你的设备开始，逐步验证"),
}

@Composable
fun LauncherApp(
    state: LauncherState,
    onRefresh: () -> Unit,
    onLaunchOriginal: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onAppSettings: () -> Unit,
    onFeedback: (String) -> Unit,
    onDismissMessage: () -> Unit,
) {
    var selectedPage by rememberSaveable { mutableStateOf(Page.HOME.name) }
    var showLaunchDialog by rememberSaveable { mutableStateOf(false) }
    val page = Page.valueOf(selectedPage)
    val snackbar = remember { SnackbarHostState() }
    val theme = darkColorScheme(
        primary = Gold, onPrimary = Background, background = Background, onBackground = Ink,
        surface = Surface, onSurface = Ink, onSurfaceVariant = Muted,
        secondary = Green, outline = Border, surfaceContainer = Surface,
    )
    MaterialTheme(colorScheme = theme) {
        LaunchedEffect(state.message) {
            state.message?.let {
                snackbar.showSnackbar(it, actionLabel = "知道了", duration = SnackbarDuration.Long)
                onDismissMessage()
            }
        }
        Scaffold(
            containerColor = Background,
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                NavigationBar(containerColor = Background, tonalElevation = 0.dp) {
                    Page.entries.forEach { item ->
                        NavigationBarItem(
                            selected = page == item,
                            onClick = { selectedPage = item.name },
                            icon = { LineIcon(item, if (page == item) Gold else Muted) },
                            label = { Text(item.title, fontSize = 12.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Gold, selectedTextColor = Gold,
                                indicatorColor = Gold.copy(alpha = .09f), unselectedTextColor = Muted,
                            ),
                        )
                    }
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
                Column(
                    Modifier.widthIn(max = 1040.dp).fillMaxSize().verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp).padding(top = 20.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Header(page)
                    if (state.busy) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            LinearProgressIndicator(Modifier.fillMaxWidth(), color = Gold, trackColor = Border)
                            Text(state.busyLabel, color = Muted, fontSize = 12.sp)
                        }
                    }
                    when (page) {
                        Page.HOME -> Home(state, { showLaunchDialog = true }, { selectedPage = Page.ARCHIVE.name },
                            { selectedPage = Page.DIAGNOSTICS.name }, onRefresh)
                        Page.ARCHIVE -> ArchivePage(state, onImport)
                        Page.DIAGNOSTICS -> DiagnosticsPage(state, onRefresh, onExport, onAppSettings, onFeedback)
                    }
                    Text("非官方社区项目 · 与 Project Moon 无隶属关系", color = Muted, fontSize = 11.sp,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                }
            }
        }
        if (showLaunchDialog) {
            AlertDialog(
                onDismissRequest = { showLaunchDialog = false },
                title = { Text("启动原版游戏") },
                text = { Text("当前版本尚未接入游戏内汉化。此次会打开你已安装的原版，不会应用缓存的汉化包。返回后可在「诊断」中记录启动结果。") },
                confirmButton = { TextButton(onClick = { showLaunchDialog = false; onLaunchOriginal() }) { Text("打开原版") } },
                dismissButton = { TextButton(onClick = { showLaunchDialog = false }) { Text("取消") } },
            )
        }
    }
}

@Composable
private fun Header(page: Page) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).background(Gold, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
            LineIcon(Page.HOME, Background)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("边狱启程", fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Text(page.subtitle, fontSize = 11.sp, color = Muted)
        }
        Text("PROBE / 01", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = Gold)
    }
}

@Composable
private fun Home(state: LauncherState, onLaunch: () -> Unit, onArchive: () -> Unit, onDiagnostics: () -> Unit, onRefresh: () -> Unit) {
    Hero()
    BoxWithConstraints {
        if (maxWidth >= 720.dp) {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(Modifier.weight(1.15f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    GameCard(state, onLaunch, onRefresh)
                    RuntimeCard()
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    QuickDeviceCard(state, onDiagnostics)
                    NextSteps(onArchive, onDiagnostics)
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                GameCard(state, onLaunch, onRefresh)
                RuntimeCard()
                QuickDeviceCard(state, onDiagnostics)
                NextSteps(onArchive, onDiagnostics)
            }
        }
    }
}

@Composable
private fun Hero() {
    Box(Modifier.fillMaxWidth().heightIn(min = 240.dp)
        .background(Brush.linearGradient(listOf(Color(0xFF49262B), Color(0xFF211C21))), RoundedCornerShape(20.dp))) {
        CityDrawing(Modifier.matchParentSize())
        Column(Modifier.padding(24.dp).fillMaxWidth(.8f), verticalArrangement = Arrangement.spacedBy(13.dp)) {
            Text("LCB LAUNCHER  /  自主开发", color = Gold, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
            Text("下一站，\n中文世界。", fontSize = 34.sp, lineHeight = 45.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text("从一次可靠的启动开始。", color = Ink.copy(alpha = .65f), fontSize = 13.sp)
            StatusPill("开发测试版 · 汉化接入待实现", Gold)
        }
    }
}

@Composable
private fun GameCard(state: LauncherState, onLaunch: () -> Unit, onRefresh: () -> Unit) {
    val game = state.snapshot?.game
    Panel {
        SectionHeading("01", "游戏", if (game?.installed == true) "已检测到" else "待检查")
        Text("Limbus Company", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Text(when {
            game == null -> "正在识别当前用户空间中的游戏"
            game.error != null -> game.error
            !game.installed -> "未检测到游戏，请确认原版安装在当前用户空间。"
            else -> "版本 ${game.versionName ?: "未知"} · ${game.apks.size} 个安装包文件"
        }, color = Muted, fontSize = 13.sp)
        if (game?.installed == true) {
            Text("安装来源：${installerLabel(game.installer)}", color = Muted, fontSize = 12.sp)
        }
        Button(
            onClick = onLaunch, enabled = !state.busy && game?.launchable == true,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(12.dp),
        ) { Text("启动原版游戏  ↗", fontWeight = FontWeight.SemiBold, fontSize = 15.sp) }
        Text("当前入口打开系统原版，尚不应用汉化。", color = Muted, fontSize = 12.sp)
        if (game != null && !game.installed) TextButton(onClick = onRefresh, enabled = !state.busy) { Text("重新检测") }
    }
}

@Composable
private fun RuntimeCard() {
    Panel {
        SectionHeading("02", "游戏内汉化", "待接入")
        Text(ProbePolicy.translationRuntime().explanation, color = Muted, fontSize = 13.sp, lineHeight = 21.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusPill("设备诊断可用", Green)
            StatusPill("汉化包检查可用", Green)
        }
    }
}

@Composable
private fun QuickDeviceCard(state: LauncherState, onDiagnostics: () -> Unit) {
    Panel {
        SectionHeading("03", "设备概览", "只读检测")
        val device = state.snapshot?.device
        if (device == null) Text("等待检测结果", color = Muted, fontSize = 13.sp)
        else {
            KeyValue("设备", "${device.manufacturer} ${device.model}")
            KeyValue("系统", "Android ${device.androidVersion} · API ${device.api}")
            KeyValue("内存页", pageLabel(device.pageBytes))
            KeyValue("可用空间", formatBytes(device.availableStorage))
        }
        TextButton(onClick = onDiagnostics, contentPadding = PaddingValues(0.dp)) { Text("查看诊断与测试步骤  →") }
    }
}

@Composable
private fun NextSteps(onArchive: () -> Unit, onDiagnostics: () -> Unit) {
    Panel {
        Text("第一次测试", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Text("检测原版 → 尝试启动 → 返回记录结果 → 导出诊断", color = Muted, fontSize = 13.sp, lineHeight = 22.sp)
        OutlinedButton(onClick = onDiagnostics, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) { Text("开始真机验证") }
        TextButton(onClick = onArchive, modifier = Modifier.fillMaxWidth()) { Text("我有 ZIP 汉化包，检查结构") }
    }
}

@Composable
private fun ArchivePage(state: LauncherState, onImport: () -> Unit) {
    Text("先检查，再接入。", fontSize = 28.sp, fontWeight = FontWeight.Bold)
    Text("检查你选择的本地汉化包，识别 JSON 文本结构并计算 SHA-256。", fontSize = 14.sp, color = Muted, lineHeight = 23.sp)
    Panel {
        SectionHeading("ZIP", "离线汉化包检查", "本地处理")
        Text("支持包含 JSON 文本的 ZIP 文件；暂不支持 7z。结构检查不代表与当前游戏版本匹配，也不会安装到游戏。",
            color = Muted, fontSize = 13.sp, lineHeight = 22.sp)
        Button(onClick = onImport, enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
            shape = RoundedCornerShape(12.dp)) { Text(if (state.archive == null) "选择 ZIP 汉化包" else "检查另一个 ZIP 包") }
        Text("通过系统文件选择器读取，无需存储权限。上限：压缩包 32 MiB，展开 64 MiB，单文件 4 MiB。",
            fontSize = 12.sp, color = Muted, lineHeight = 20.sp)
    }
    val audit = state.archive
    if (audit != null) {
        Panel {
            StatusPill("结构检查通过 · 尚未应用", Green)
            KeyValue("JSON 文件", audit.files.size.toString())
            KeyValue("显示文本字段", audit.displayFields.toString())
            KeyValue("压缩大小", formatBytes(audit.compressedBytes))
            KeyValue("展开大小", formatBytes(audit.expandedBytes))
            HorizontalDivider(color = Border)
            Text("SHA-256", fontSize = 11.sp, color = Gold, fontFamily = FontFamily.Monospace)
            Text(audit.sha256, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Muted)
            Text("此摘要用于识别与复核文件，不能证明作者身份或补丁可信。", color = Muted, fontSize = 12.sp)
        }
        Panel {
            Text("资源概览", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            audit.files.take(8).forEach { file ->
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(file.path, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${file.displayFields} 个显示字段 · ${formatBytes(file.bytes)}", color = Muted, fontSize = 11.sp)
                }
            }
            if (audit.files.size > 8) Text("另有 ${audit.files.size - 8} 个 JSON 文件", color = Muted, fontSize = 12.sp)
        }
    } else {
        Panel {
            Text("还没有检查过汉化包", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text("无需为首次设备测试准备汉化包，可以先前往「诊断」完成原版启动验证。", color = Muted, fontSize = 13.sp, lineHeight = 22.sp)
        }
    }
    Text("翻译资源保留原作者权利，使用时请遵守对应许可。", color = Muted, fontSize = 12.sp)
}

@Composable
private fun DiagnosticsPage(state: LauncherState, onRefresh: () -> Unit, onExport: () -> Unit,
    onAppSettings: () -> Unit, onFeedback: (String) -> Unit) {
    Text("把问题，变成线索。", fontSize = 28.sp, fontWeight = FontWeight.Bold)
    Text("第一轮重点验证设备识别和原版启动，不需要修改游戏数据。", color = Muted, fontSize = 14.sp, lineHeight = 23.sp)
    Panel {
        SectionHeading("TEST", "真机测试清单", "手动确认")
        Text("1. 在首页确认游戏版本与安装包数量。\n2. 点击「启动原版游戏」，观察能否进入游戏。\n3. 返回这里记录结果，再导出诊断 JSON。",
            color = Muted, fontSize = 13.sp, lineHeight = 24.sp)
        Text("原版启动结果", fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Column {
            listOf("original_opened" to "已成功打开原版", "original_failed" to "未打开或发生闪退", "not_tested" to "尚未确认").forEach { (value, label) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = state.feedback == value, onClick = { onFeedback(value) })
                    TextButton(onClick = { onFeedback(value) }, contentPadding = PaddingValues(0.dp)) { Text(label, color = Ink) }
                }
            }
        }
        Text("结果由你确认；启动器无法读取游戏的登录或运行状态。", color = Muted, fontSize = 12.sp, lineHeight = 20.sp)
    }
    val snapshot = state.snapshot
    if (snapshot != null) {
        val device = snapshot.device
        Panel {
            SectionHeading("DEVICE", "设备与系统", "已读取")
            KeyValue("厂商 / 型号", "${device.manufacturer} / ${device.model}")
            KeyValue("Android", "${device.androidVersion}（API ${device.api}）")
            KeyValue("CPU 架构", device.abis.joinToString(", "))
            KeyValue("当前进程", if (device.process64Bit) "64 位" else "32 位")
            KeyValue("内存页大小", pageLabel(device.pageBytes))
            KeyValue("总内存", formatBytes(device.totalRam))
            KeyValue("低内存模式", if (device.lowRam) "是" else "否")
            KeyValue("可用空间", formatBytes(device.availableStorage))
            KeyValue("电池优化", if (device.batteryUnrestricted) "已豁免" else "系统默认限制")
            KeyValue("Google Play 服务", if (device.googleServicesInstalled) "已检测到" else "未检测到")
            KeyValue("Google Play 商店", if (device.playStoreInstalled) "已检测到" else "未检测到")
            Text("检测结果用于确定后续接入方式，不能作为汉化运行兼容证明。", color = Muted, fontSize = 12.sp, lineHeight = 20.sp)
            TextButton(onClick = onAppSettings, contentPadding = PaddingValues(0.dp)) { Text("打开启动器系统设置  ↗") }
        }
        GameDiagnostics(snapshot.game)
    }
    Panel {
        SectionHeading("EXPORT", "反馈与诊断", "仅手动导出")
        Text("包含设备型号、系统、游戏版本、安装包结构与手动测试结果。不包含账号、存档、设备唯一标识、游戏 APK 或翻译正文。",
            color = Muted, fontSize = 13.sp, lineHeight = 22.sp)
        Button(onClick = onExport, enabled = !state.busy && snapshot != null,
            modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp), shape = RoundedCornerShape(12.dp)) { Text("导出诊断 JSON") }
        OutlinedButton(onClick = onRefresh, enabled = !state.busy, modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)) { Text("刷新设备与游戏信息") }
        Text("启动器版本 ${BuildConfig.VERSION_NAME}", color = Muted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun GameDiagnostics(game: GameSnapshot) {
    Panel {
        SectionHeading("GAME", "游戏安装信息", if (game.installed) "已检测到" else "未检测到")
        Text(ProbePolicy.GAME_PACKAGE, fontSize = 11.sp, color = Muted, fontFamily = FontFamily.Monospace)
        if (game.installed) {
            KeyValue("版本", "${game.versionName ?: "未知"}（${game.versionCode ?: "未知"}）")
            KeyValue("安装来源", installerLabel(game.installer))
            KeyValue("启动入口", if (game.launchable) "已找到" else "未找到")
            KeyValue("安装包数量", game.apks.size.toString())
            game.apks.forEach { apk ->
                HorizontalDivider(color = Border)
                Text(apk.name, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Text("${formatBytes(apk.bytes)} · ${if (apk.readable) "目录可读" else "目录不可读"}", color = Muted, fontSize = 12.sp)
                if (apk.nativeAbis.isNotEmpty()) Text(apk.nativeAbis.joinToString(", "), color = Muted, fontSize = 12.sp)
                if (apk.runtimeLibraries.isNotEmpty()) Text(apk.runtimeLibraries.joinToString(" · "), color = Muted, fontSize = 11.sp)
            }
        } else Text(game.error ?: "请确认游戏安装在当前用户空间，而非另一个系统分身或工作资料。", color = Muted, fontSize = 13.sp, lineHeight = 21.sp)
    }
}

@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().background(Surface, RoundedCornerShape(16.dp))
        .border(1.dp, Border, RoundedCornerShape(16.dp)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}

@Composable
private fun SectionHeading(number: String, title: String, status: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(number, color = Gold, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
        Spacer(Modifier.width(10.dp))
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Text(status, color = Muted, fontSize = 10.sp)
    }
}

@Composable
private fun KeyValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, color = Muted, fontSize = 12.sp, modifier = Modifier.weight(.42f))
        Text(value, fontSize = 12.sp, modifier = Modifier.weight(.58f), lineHeight = 19.sp)
    }
}

@Composable
private fun StatusPill(text: String, color: Color) {
    Text(text, color = color, fontSize = 10.sp, modifier = Modifier.background(color.copy(alpha = .1f), RoundedCornerShape(6.dp))
        .padding(horizontal = 9.dp, vertical = 6.dp))
}

@Composable
private fun LineIcon(page: Page, color: Color) {
    Canvas(Modifier.size(23.dp)) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = 1.7.dp.toPx(), cap = StrokeCap.Round)
        when (page) {
            Page.HOME -> {
                drawRoundRect(color, Offset(w * .18f, h * .1f), Size(w * .64f, h * .7f), CornerRadius(w * .1f), style = stroke)
                drawLine(color, Offset(w * .2f, h * .48f), Offset(w * .8f, h * .48f), stroke.width)
                drawLine(color, Offset(w * .29f, h * .8f), Offset(w * .29f, h * .92f), stroke.width, StrokeCap.Round)
                drawLine(color, Offset(w * .71f, h * .8f), Offset(w * .71f, h * .92f), stroke.width, StrokeCap.Round)
                drawCircle(color, w * .045f, Offset(w * .32f, h * .65f))
                drawCircle(color, w * .045f, Offset(w * .68f, h * .65f))
            }
            Page.ARCHIVE -> {
                val path = Path().apply {
                    moveTo(w * .12f, h * .3f); lineTo(w * .5f, h * .1f); lineTo(w * .88f, h * .3f)
                    lineTo(w * .88f, h * .75f); lineTo(w * .5f, h * .95f); lineTo(w * .12f, h * .75f); close()
                    moveTo(w * .12f, h * .3f); lineTo(w * .5f, h * .5f); lineTo(w * .88f, h * .3f)
                    moveTo(w * .5f, h * .5f); lineTo(w * .5f, h * .95f)
                }
                drawPath(path, color, style = stroke)
            }
            Page.DIAGNOSTICS -> {
                val path = Path().apply {
                    moveTo(w * .04f, h * .52f); lineTo(w * .27f, h * .52f); lineTo(w * .4f, h * .17f)
                    lineTo(w * .58f, h * .87f); lineTo(w * .72f, h * .45f); lineTo(w * .96f, h * .45f)
                }
                drawPath(path, color, style = stroke)
            }
        }
    }
}

@Composable
private fun CityDrawing(modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val base = h * .9f
        for (i in 0..11) {
            val x = w * (.50f + i * .046f)
            val height = h * (.12f + ((i * 7) % 9) * .025f)
            drawRect(Color(0xFF17171C).copy(alpha = .55f), Offset(x, base - height), Size(w * .034f, height))
        }
        drawLine(Gold.copy(alpha = .2f), Offset(w * .52f, base), Offset(w, base), 1.dp.toPx())
        val bx = w * .74f
        val by = h * .65f
        drawRoundRect(Gold.copy(alpha = .65f), Offset(bx, by), Size(w * .19f, h * .18f), CornerRadius(4.dp.toPx()))
        for (i in 0..3) {
            drawRect(Color(0xFF2A2025), Offset(bx + w * (.018f + i * .042f), by + h * .025f), Size(w * .031f, h * .063f))
        }
        drawCircle(Color(0xFF17171C), h * .022f, Offset(bx + w * .038f, by + h * .18f))
        drawCircle(Color(0xFF17171C), h * .022f, Offset(bx + w * .153f, by + h * .18f))
    }
}

private fun installerLabel(value: String?): String = when (value) {
    "com.android.vending" -> "Google Play"
    null -> "系统未提供"
    else -> value
}

private fun pageLabel(bytes: Long?): String = bytes?.takeIf { it > 0 }?.let { "${it / 1024} KiB" } ?: "未能读取"

private fun formatBytes(bytes: Long): String {
    val gib = 1024.0 * 1024 * 1024
    val mib = 1024.0 * 1024
    return when {
        bytes >= gib -> String.format(Locale.ROOT, "%.1f GiB", bytes / gib)
        bytes >= mib -> String.format(Locale.ROOT, "%.1f MiB", bytes / mib)
        bytes >= 1024 -> String.format(Locale.ROOT, "%.1f KiB", bytes / 1024.0)
        else -> "$bytes B"
    }
}
