package io.github.lcblauncher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.lcblauncher.BuildConfig
import io.github.lcblauncher.LauncherState
import io.github.lcblauncher.probe.GameSnapshot

private val Paper = Color(0xFFF4F1EA)
private val White = Color(0xFFFFFEFA)
private val Ink = Color(0xFF242724)
private val Accent = Color(0xFFB7362C)
private val Muted = Color(0xFF656A61)
private val Rule = Color(0xFFDFE0D7)
private val Green = Color(0xFF37664C)

private enum class Page(val title: String, val code: String) {
    HOME("启程", "DEPARTURE"), ARCHIVE("资源", "ARCHIVE"), DIAGNOSTICS("工具", "WORKSHOP")
}

@Composable
fun LauncherApp(
    state: LauncherState,
    onRefresh: () -> Unit,
    onLaunchOriginal: () -> Unit,
    onImportGame: () -> Unit,
    onLaunchContainer: () -> Unit,
    onContainerSelfTest: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onAppSettings: () -> Unit,
    onFeedback: (String) -> Unit,
    onDismissMessage: () -> Unit,
) {
    var selectedPage by rememberSaveable { mutableStateOf(Page.HOME.name) }
    var showOriginal by rememberSaveable { mutableStateOf(false) }
    var showContainer by rememberSaveable { mutableStateOf(false) }
    val page = Page.valueOf(selectedPage)
    val snackbar = remember { SnackbarHostState() }
    MaterialTheme(colorScheme = lightColorScheme(
        primary = Accent, onPrimary = White, secondary = Ink, background = Paper, onBackground = Ink,
        surface = White, onSurface = Ink, onSurfaceVariant = Muted, outline = Rule, surfaceContainer = Paper,
    )) {
        LaunchedEffect(state.message) {
            state.message?.let {
                snackbar.showSnackbar(it, actionLabel = "关闭", duration = SnackbarDuration.Long)
                onDismissMessage()
            }
        }
        Scaffold(containerColor = Paper, snackbarHost = { SnackbarHost(snackbar) }, bottomBar = {
            Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                Row(Modifier.widthIn(max = 520.dp).fillMaxWidth().background(Ink, RoundedCornerShape(18.dp)).padding(5.dp)) {
                    Page.entries.forEach { tab ->
                        val active = page == tab
                        Row(Modifier.weight(1f).height(54.dp).clip(RoundedCornerShape(13.dp))
                            .background(if (active) Accent else Color.Transparent)
                            .semantics { contentDescription = "导航：${tab.title}"; selected = active }
                            .clickable(role = Role.Tab) { selectedPage = tab.name },
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                            LineIcon(tab, if (active) White else Color(0xFFB3B6AD))
                            Spacer(Modifier.width(8.dp))
                            Text(tab.title, color = if (active) White else Color(0xFFB3B6AD), fontSize = 13.sp,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            }
        }) { insets ->
            Box(Modifier.fillMaxSize().padding(insets), contentAlignment = Alignment.TopCenter) {
                key(page) {
                    Column(Modifier.widthIn(max = 1040.dp).fillMaxSize().verticalScroll(rememberScrollState())
                        .padding(horizontal = 22.dp).padding(top = 18.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                        Masthead(page)
                        if (state.busy) ProgressStrip(state.busyLabel)
                        when (page) {
                            Page.HOME -> Departure(state, onImportGame, { showContainer = true }, onContainerSelfTest,
                                { selectedPage = Page.ARCHIVE.name }, { selectedPage = Page.DIAGNOSTICS.name }, onRefresh)
                            Page.ARCHIVE -> Archive(state, onImport)
                            Page.DIAGNOSTICS -> Workshop(state, onRefresh, onExport, onAppSettings, onFeedback, { showOriginal = true }, onContainerSelfTest)
                        }
                        Text("非官方社区项目 · 与 Project Moon 无隶属关系", fontSize = 10.sp, color = Muted,
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                    }
                }
            }
        }
        if (showContainer) AlertDialog(
            onDismissRequest = { showContainer = false }, title = { Text("测试容器启动") },
            text = { Text("将在独立容器中运行已导入的游戏。游戏兼容、Google 登录与汉化仍待完成，可能失败或闪退。原版账号与资源暂不迁移。") },
            confirmButton = { TextButton(onClick = { showContainer = false; onLaunchContainer() }) { Text("开始测试") } },
            dismissButton = { TextButton(onClick = { showContainer = false }) { Text("取消") } },
        )
        if (showOriginal) AlertDialog(
            onDismissRequest = { showOriginal = false }, title = { Text("打开系统原版") },
            text = { Text("此入口打开你安装的原版游戏，不应用汉化。返回后可在工具页记录结果。") },
            confirmButton = { TextButton(onClick = { showOriginal = false; onLaunchOriginal() }) { Text("打开原版") } },
            dismissButton = { TextButton(onClick = { showOriginal = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun Masthead(page: Page) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(30.dp).background(Ink, RoundedCornerShape(7.dp)), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(18.dp)) {
                val p = Path().apply { moveTo(0f, size.height * .22f); lineTo(size.width * .7f, size.height * .22f)
                    lineTo(size.width * .7f, size.height * .05f); lineTo(size.width, size.height * .38f)
                    lineTo(size.width * .7f, size.height * .7f); lineTo(size.width * .7f, size.height * .53f); lineTo(0f, size.height * .53f); close() }
                drawPath(p, White)
                drawRect(Accent, Offset(0f, size.height * .8f), Size(size.width * .45f, size.height * .15f))
            }
        }
        Spacer(Modifier.width(10.dp))
        Text("边狱启程", fontWeight = FontWeight.Bold, fontSize = 17.sp, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
        Label("${page.code} / α", color = Muted)
    }
}

@Composable
private fun Departure(state: LauncherState, onImport: () -> Unit, onLaunch: () -> Unit,
    onSelfTest: () -> Unit, onArchive: () -> Unit, onTools: () -> Unit, onRefresh: () -> Unit) {
    val shortScreen = LocalConfiguration.current.screenHeightDp < 500
    BoxWithConstraints {
        if (maxWidth >= 560.dp) Row(horizontalArrangement = Arrangement.spacedBy(26.dp)) {
            Column(Modifier.weight(1.12f)) {
                if (shortScreen) CompactBoardingPass(state, onImport, onLaunch) else BoardingPass(state, onImport, onLaunch)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                Preparation(state, onRefresh)
                Shortcuts(state, onSelfTest, onArchive, onTools)
            }
        } else Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
            BoardingPass(state, onImport, onLaunch)
            Preparation(state, onRefresh)
            Shortcuts(state, onSelfTest, onArchive, onTools)
        }
    }
}

@Composable
private fun BoardingPass(state: LauncherState, onImport: () -> Unit, onLaunch: () -> Unit) {
    val imported = state.container != null
    Column {
        Box(Modifier.fillMaxWidth().heightIn(min = 218.dp).clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)).background(Ink)) {
            TransitDrawing(Modifier.matchParentSize())
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Label("LIMBUS COMPANY  /  ANDROID", Color(0xFFBFC2B5))
                Text(if (imported) "下一站，\n容器之内。" else "准备好了，\n就启程。", color = White,
                    fontSize = 34.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp)

            }
        }
        Column(Modifier.fillMaxWidth().background(White, RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Label(if (imported) "READY  /  游戏已导入" else "IMPORT  /  等待导入", Muted)
                    Text("Limbus Company", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                }
                Text(if (imported) "02" else "01", fontSize = 34.sp, fontWeight = FontWeight.Light, color = Rule, fontFamily = FontFamily.Monospace)
            }
            HorizontalDivider(color = Rule)
            PrimaryAction(if (imported) "测试容器启动" else "导入原版游戏", !state.busy &&
                (imported || state.snapshot?.game?.installed == true), if (imported) onLaunch else onImport)
            if (imported) TextButton(onClick = onImport, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("重新导入原版", color = Muted, fontSize = 12.sp) }
            Text("实验版 · 尚未提供游戏内汉化", fontSize = 11.sp, color = Accent, modifier = Modifier.fillMaxWidth())
        }
        state.containerError?.let { error ->
            Spacer(Modifier.height(12.dp))
            Column(Modifier.fillMaxWidth().background(Color(0xFFF7E5DF), RoundedCornerShape(10.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("导入未完成", color = Accent, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(error, color = Ink, fontSize = 13.sp, lineHeight = 21.sp)
                Text("原版游戏与已有容器数据已保留。", color = Muted, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun CompactBoardingPass(state: LauncherState, onImport: () -> Unit, onLaunch: () -> Unit) {
    val imported = state.container != null
    Column(Modifier.fillMaxWidth().background(Ink, RoundedCornerShape(14.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Limbus Company", fontSize = 16.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold, color = White, modifier = Modifier.weight(1f))
            Text("汉化待接入", fontSize = 9.sp, lineHeight = 13.sp, color = Color(0xFFF0AAA0))
        }
        PrimaryAction(if (imported) "测试容器启动" else "导入原版游戏", !state.busy &&
            (imported || state.snapshot?.game?.installed == true), if (imported) onLaunch else onImport)
        if (imported) TextButton(onClick = onImport, enabled = !state.busy) { Text("重新导入原版", color = White) }
        state.containerError?.let { Text(it, color = Color(0xFFF0AAA0), fontSize = 12.sp, lineHeight = 20.sp) }
    }
}

@Composable
private fun Preparation(state: LauncherState, onRefresh: () -> Unit) {
    val game = state.snapshot?.game
    val installed = game?.installed == true
    Column(verticalArrangement = Arrangement.spacedBy(15.dp)) {
        SectionTitle("01", "游戏准备", if (state.container != null) "已导入" else "待导入")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Step(Modifier.weight(1f), "01", "检测原版", installed)
            Step(Modifier.weight(1f), "02", "导入游戏", state.container != null)
            Step(Modifier.weight(1f), "03", "测试启动", false)
        }
        Text(when {
            state.container != null -> "容器版本 ${state.container.version} · ${state.container.apkCount} 个分包。可开始测试启动。"
            installed -> "已发现原版 ${game?.versionName ?: ""} · ${game?.apks?.size ?: 0} 个分包。导入后即可测试容器启动。"
            else -> "请先在当前用户空间安装原版游戏，再刷新检测。"
        }, color = Muted, fontSize = 13.sp, lineHeight = 21.sp)
        if (!installed) TextButton(onClick = onRefresh, enabled = !state.busy, contentPadding = PaddingValues(0.dp)) { Text("刷新检测  ↻") }
    }
}

@Composable
private fun Step(modifier: Modifier, number: String, label: String, done: Boolean) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(9.dp)) {
        HorizontalDivider(thickness = 2.dp, color = if (done) Accent else Rule)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Label(if (done) "✓" else number, if (done) Accent else Muted)
            Text(label, fontSize = 12.sp, color = if (done) Ink else Muted)
        }
    }
}

@Composable
private fun Shortcuts(state: LauncherState, onSelfTest: () -> Unit, onArchive: () -> Unit, onTools: () -> Unit) {
    Column {
        SectionTitle("02", "随行工具")
        Spacer(Modifier.height(8.dp))
        ActionRow("↗", "运行容器自检", "确认设备上的客体执行能力", !state.busy, onSelfTest)
        ActionRow("文", "汉化资源", if (state.archive == null) "导入 ZIP，检查文本结构" else "已有通过结构检查的资源包", true, onArchive)
        ActionRow("＋", "诊断与设置", "导出运行记录，查看设备信息", true, onTools)
    }
}

@Composable
private fun Archive(state: LauncherState, onImport: () -> Unit) {
    PageHeading("资源档案", "让每一份文本，都有迹可循。")
    Panel {
        Label("LOCAL ARCHIVE", Accent)
        Text(if (state.archive == null) "带上你的汉化包。" else "资源已归档。", fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        Text("导入 ZIP，检查 JSON 结构与完整性。当前只检查资源，不会应用到游戏。", color = Muted, fontSize = 14.sp, lineHeight = 23.sp)
        PrimaryAction(if (state.archive == null) "选择 ZIP 汉化包" else "检查另一个资源包", !state.busy, onImport)
        Text("压缩包 ≤ 32 MiB · 展开 ≤ 64 MiB · 单文件 ≤ 4 MiB", color = Muted, fontSize = 11.sp)
    }
    state.archive?.let { audit ->
        SectionTitle("01", "检查结果", "结构通过 · 尚未应用")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Metric(Modifier.weight(1f), audit.files.size.toString(), "JSON 文件")
            Metric(Modifier.weight(1f), audit.displayFields.toString(), "显示文本字段")
        }
        Panel {
            KeyValue("压缩 / 展开", "${formatBytes(audit.compressedBytes)} / ${formatBytes(audit.expandedBytes)}")
            Label("SHA-256", Muted)
            Text(audit.sha256, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Muted)
            Text("摘要用于识别文件，不证明作者身份或补丁可信。", fontSize = 11.sp, color = Muted)
        }
        Expandable("资源目录", "${audit.files.size} 个 JSON 文件") {
            audit.files.take(12).forEach { file ->
                Text(file.path, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${file.displayFields} 个显示字段 · ${formatBytes(file.bytes)}", color = Muted, fontSize = 11.sp)
            }
            if (audit.files.size > 12) Text("另有 ${audit.files.size - 12} 个文件", fontSize = 12.sp, color = Muted)
        }
    }
    Text("翻译资源保留原作者权利，请遵守对应许可。", color = Muted, fontSize = 12.sp)
}

@Composable
private fun Workshop(state: LauncherState, onRefresh: () -> Unit, onExport: () -> Unit, onSettings: () -> Unit,
    onFeedback: (String) -> Unit, onOriginal: () -> Unit, onSelfTest: () -> Unit) {
    PageHeading("工具与诊断", "记录每一次启程，找到下一步的线索。")
    Panel {
        Label("RUN LOG  /  运行记录", Accent)
        Text(state.containerStage?.let(::stageLabel) ?: "还没有容器运行记录", fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
        state.containerStage?.let { Text(it, fontFamily = FontFamily.Monospace, color = Muted, fontSize = 11.sp) }
        Text("导出设备、游戏版本和容器阶段。不含账号、存档、游戏 APK 或译文正文。", color = Muted, fontSize = 13.sp, lineHeight = 22.sp)
        PrimaryAction("导出诊断 JSON", !state.busy && state.snapshot != null, onExport)
    }
    Column {
        ActionRow("↗", "运行容器自检", "打开项目自带的测试客体", !state.busy, onSelfTest)
        ActionRow("↻", "刷新检测", "重新读取设备与游戏信息", !state.busy, onRefresh)
        ActionRow("⚙", "系统设置", "后台运行、电池与系统权限", true, onSettings)
    }
    state.snapshot?.let { snapshot ->
        val d = snapshot.device
        Expandable("设备信息", "${d.manufacturer} ${d.model} · Android ${d.androidVersion}") {
            KeyValue("Android / API", "${d.androidVersion} / ${d.api}")
            KeyValue("CPU 架构", d.abis.joinToString(", "))
            KeyValue("当前进程", if (d.process64Bit) "64 位" else "32 位")
            KeyValue("内存页", d.pageBytes?.let { "${it / 1024} KiB" } ?: "未读取")
            KeyValue("总内存", formatBytes(d.totalRam))
            KeyValue("可用空间", formatBytes(d.availableStorage))
            KeyValue("电池优化", if (d.batteryUnrestricted) "已豁免" else "系统默认")
            KeyValue("Google 服务", if (d.googleServicesInstalled) "已安装" else "未检测到")
            KeyValue("Play 商店", if (d.playStoreInstalled) "已安装" else "未检测到")
        }
        Expandable("原版安装", snapshot.game.versionName ?: "未检测到游戏") { GameDetails(snapshot.game) }
    }
    Expandable("原版启动与反馈", "仅打开系统原版，不应用汉化") {
        OutlinedButton(onClick = onOriginal, enabled = !state.busy && state.snapshot?.game?.launchable == true,
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(9.dp)) { Text("打开系统原版  ↗") }
        listOf("original_opened" to "已成功打开原版", "original_failed" to "未打开或发生闪退", "not_tested" to "尚未确认").forEach { (value, label) ->
            Row(Modifier.fillMaxWidth().clickable { onFeedback(value) }, verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = state.feedback == value, onClick = { onFeedback(value) })
                Text(label, fontSize = 13.sp)
            }
        }
        Text("结果由你确认，不代表容器登录或游戏运行通过。", color = Muted, fontSize = 11.sp)
    }
    Text("${BuildConfig.VERSION_NAME}\n自主容器实验版 · 游戏兼容与汉化待完成", color = Muted, fontSize = 11.sp, lineHeight = 18.sp)
}

@Composable
private fun GameDetails(game: GameSnapshot) {
    if (!game.installed) { Text(game.error ?: "请确认原版安装在当前用户空间。", fontSize = 13.sp, color = Muted); return }
    KeyValue("游戏版本", "${game.versionName ?: "未知"} / ${game.versionCode ?: "未知"}")
    KeyValue("安装来源", if (game.installer == "com.android.vending") "Google Play" else game.installer ?: "未知")
    game.apks.forEach { apk ->
        HorizontalDivider(color = Rule)
        Text(apk.name, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        Text("${formatBytes(apk.bytes)} · ${if (apk.readable) "可读取" else "不可读取"}", color = Muted, fontSize = 12.sp)
        if (apk.runtimeLibraries.isNotEmpty()) Text(apk.runtimeLibraries.joinToString(" · "), color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun PageHeading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.5).sp)
        Text(subtitle, color = Muted, fontSize = 13.sp, lineHeight = 21.sp)
    }
}

@Composable
private fun PrimaryAction(label: String, enabled: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
        shape = RoundedCornerShape(9.dp), colors = ButtonDefaults.buttonColors(disabledContainerColor = Rule, disabledContentColor = Muted)) {
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text("↗", fontSize = 20.sp)
    }
}

@Composable
private fun ProgressStrip(label: String) {
    Column(Modifier.fillMaxWidth().background(White, RoundedCornerShape(10.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(label, fontSize = 12.sp, color = Ink, lineHeight = 19.sp)
        LinearProgressIndicator(Modifier.fillMaxWidth().height(3.dp), color = Accent, trackColor = Rule)
    }
}

@Composable
private fun ActionRow(symbol: String, title: String, subtitle: String, enabled: Boolean, onClick: () -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 15.dp, horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).background(White, RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
                Text(symbol, color = Accent, fontSize = 19.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (enabled) Ink else Muted)
                Text(subtitle, fontSize = 11.sp, color = Muted, lineHeight = 17.sp)
            }
            Text("›", color = Muted, fontSize = 25.sp)
        }
        HorizontalDivider(color = Rule)
    }
}

@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().background(White, RoundedCornerShape(13.dp)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
}

@Composable
private fun Expandable(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().background(White, RoundedCornerShape(12.dp))) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { open = !open }.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(subtitle, fontSize = 11.sp, color = Muted, lineHeight = 18.sp)
            }
            Text(if (open) "−" else "+", fontSize = 22.sp, color = Muted)
        }
        if (open) Column(Modifier.padding(start = 18.dp, end = 18.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun SectionTitle(number: String, title: String, status: String? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Label(number, Accent)
        Spacer(Modifier.width(10.dp))
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        status?.let { Text(it, fontSize = 11.sp, color = Muted) }
    }
}

@Composable
private fun Metric(modifier: Modifier, value: String, label: String) {
    Column(modifier.background(White, RoundedCornerShape(12.dp)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(value, fontSize = 27.sp, fontWeight = FontWeight.SemiBold)
        Text(label, fontSize = 11.sp, color = Muted)
    }
}

@Composable
private fun KeyValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, color = Muted, fontSize = 12.sp, modifier = Modifier.weight(.4f))
        Text(value, fontSize = 12.sp, modifier = Modifier.weight(.6f), lineHeight = 19.sp)
    }
}

@Composable
private fun Label(value: String, color: Color) {
    Text(value, color = color, fontSize = 9.sp, fontFamily = FontFamily.Monospace, lineHeight = 12.sp, letterSpacing = .8.sp)
}

@Composable
private fun LineIcon(page: Page, color: Color) {
    Canvas(Modifier.size(19.dp)) {
        val w = size.width; val h = size.height
        val stroke = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
        when (page) {
            Page.HOME -> {
                drawRoundRect(color, Offset(w * .15f, h * .1f), Size(w * .7f, h * .7f), CornerRadius(3.dp.toPx()), style = stroke)
                drawLine(color, Offset(w * .15f, h * .48f), Offset(w * .85f, h * .48f), stroke.width)
                drawLine(color, Offset(w * .27f, h * .8f), Offset(w * .27f, h * .95f), stroke.width, StrokeCap.Round)
                drawLine(color, Offset(w * .73f, h * .8f), Offset(w * .73f, h * .95f), stroke.width, StrokeCap.Round)
            }
            Page.ARCHIVE -> {
                val p = Path().apply { moveTo(w * .1f, h * .3f); lineTo(w * .5f, h * .08f); lineTo(w * .9f, h * .3f)
                    lineTo(w * .9f, h * .75f); lineTo(w * .5f, h * .95f); lineTo(w * .1f, h * .75f); close()
                    moveTo(w * .1f, h * .3f); lineTo(w * .5f, h * .53f); lineTo(w * .9f, h * .3f)
                    moveTo(w * .5f, h * .53f); lineTo(w * .5f, h * .95f) }
                drawPath(p, color, style = stroke)
            }
            Page.DIAGNOSTICS -> {
                drawCircle(color, w * .34f, style = stroke)
                drawLine(color, Offset(w * .38f, h * .5f), Offset(w * .62f, h * .5f), stroke.width)
                drawLine(color, Offset(w * .5f, h * .38f), Offset(w * .5f, h * .62f), stroke.width)
            }
        }
    }
}

@Composable
private fun TransitDrawing(modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val subtle = Color(0xFF41473E)
        for (i in 0..7) drawLine(subtle.copy(alpha = .45f), Offset(w * .62f + i * 22.dp.toPx(), 0f), Offset(w * .34f + i * 22.dp.toPx(), h), 1.dp.toPx())
        val x = w * .61f; val y = h * .65f
        drawCircle(Accent.copy(alpha = .23f), h * .24f, Offset(w * .85f, h * .75f))
        drawLine(Color(0xFF989E89), Offset(w * .60f, h * .87f), Offset(w * .95f, h * .87f), 1.dp.toPx())
        drawRoundRect(Color(0xFFDADAC9), Offset(x, y), Size(w * .28f, h * .20f), CornerRadius(5.dp.toPx()))
        drawRect(Accent, Offset(x, y + h * .135f), Size(w * .28f, h * .035f))
        for (i in 0..3) drawRoundRect(Ink, Offset(x + w * (.017f + i * .064f), y + h * .023f), Size(w * .048f, h * .067f), CornerRadius(1.dp.toPx()))
        drawCircle(Ink, h * .028f, Offset(x + w * .055f, y + h * .20f))
        drawCircle(Ink, h * .028f, Offset(x + w * .22f, y + h * .20f))
        drawCircle(Color(0xFFDADAC9), h * .010f, Offset(x + w * .055f, y + h * .20f))
        drawCircle(Color(0xFFDADAC9), h * .010f, Offset(x + w * .22f, y + h * .20f))
    }
}

private fun stageLabel(stage: String): String = when {
    stage.startsWith("failed_") -> "上次容器运行未完成"
    stage == "guest_resumed" -> "客体界面已执行"
    stage == "guest_destroyed" -> "上次客体已正常关闭"
    stage == "guest_paused" || stage == "guest_stopped" -> "客体已退至后台"
    else -> "上次运行停在准备阶段"
}
private fun formatBytes(bytes: Long): String = io.github.lcblauncher.core.NativeLibraries.size(bytes)
