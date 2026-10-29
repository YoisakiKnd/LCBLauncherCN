# 边狱启程 · LCBLauncher

自主开发的《Limbus Company》Android 中文启动器项目，当前为 **0.1.0-probe 真机诊断版**。

**这一版尚未实现游戏内汉化。** 当前启动按钮明确打开系统里安装的原版游戏；导入的汉化包只用于结构检查和本地缓存，不会写入游戏目录。没有容器、进程注入、Root 或 Shizuku 功能。

第一轮测试目标：**vivo V2458A / Android 16 / 已安装原版 Limbus Company**。该组合尚待用户真机验证，不能视为已兼容。

## 云端构建

进入 [GitHub Actions](https://github.com/YoisakiKnd/LCBLauncherCN/actions/workflows/android.yml) 查看 `Android APK` 工作流。推送到 `main`、提交 PR 或手动运行时，GitHub 托管 runner 会执行构建、Android 16 主机测试、静态检查，以及 APK 签名和对齐检查。当前配置仅验证构建，下载产物的上传步骤尚未启用。

服务器不再用于编译。工作流设置了超时、并发去重和资源上限；`scripts/ci-build.sh` 拒绝在 GitHub Actions 之外运行。云端 `versionCode` 使用工作流运行编号。

## 当前功能

- Kotlin + Jetpack Compose 原生界面，支持横竖屏和宽屏布局。
- 读取设备型号、Android 版本、ABI、进程位数、实际内存页大小、内存与存储空间。
- 仅查询游戏、Google Play 服务和 Google Play 商店三个指定应用。
- 检测游戏版本、安装来源、签名证书摘要、base/split APK 文件及原生运行库名称。
- 通过明确标识的入口启动原版游戏；记录用户手动确认的启动结果。
- 通过系统文件选择器导出诊断 JSON，无需电脑或 adb。
- 离线检查含 JSON 文本的 ZIP 汉化包，计算 SHA-256，并将通过检查的包保存在启动器私有目录。
- 限制压缩大小、展开大小、条目数量、单文件大小与 JSON 深度，拒绝危险路径和无效 UTF-8。

本版无需网络权限、存储权限或安装应用权限。诊断不包含账号、存档、设备唯一标识、游戏 APK、翻译正文或所选文件的 URI。文件哈希用于完整性复核，不证明资源作者身份。

## 构建

推荐使用上述 Actions。以下命令仅供开发电脑使用，不在当前服务器执行。

需要 JDK 21（Android 16 主机测试要求）、Android SDK Platform 36、Build Tools 35.0.0。工程使用 Gradle 8.13、AGP 8.13.2、Kotlin 2.2.21，Gradle Wrapper 已附带发行包 SHA-256。应用 JVM 字节码目标为 17。

Android Studio 可直接打开项目。在 `local.properties` 设置 `sdk.dir` 后运行：

```bash
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

本机已安装的独立构建工具位于忽略的 `.tools/` 目录，可运行：

```bash
LCB_ALLOW_LOCAL_BUILD=1 bash scripts/dev.sh
```

APK 输出：`app/build/outputs/apk/debug/app-debug.apk`。测试交付文件另放在 `dist/`，不提交构建产物、SDK、缓存或签名私钥。

本机调试签名保存在 `.tools/android-user-home/debug.keystore`。保留该文件，以便后续测试 APK 同签名覆盖升级。它不用于正式发布。

## 真机测试

见 [vivo Android 16 测试步骤](docs/testing-vivo-android16.md)。无需导入汉化包即可完成第一轮测试。

导出 JSON 是为了确定后续运行接入路线。原版能启动、APK 目录可读、或汉化包结构有效，都不能证明游戏内汉化可用。

## 项目结构

```text
app/       Android 界面、系统检测、原版启动、本地缓存和诊断导出
core/      独立于 Android 的汉化包检查与运行能力声明
docs/      真机验证、架构和开发进度
scripts/   构建与校验入口
```

后续工作和验收条件见 [开发路线](docs/roadmap.md)。当前没有复制现有 Limbus 汉化启动器或容器项目的源码，也没有附带游戏、译文或游戏美术素材；界面图形为代码绘制。使用通用开源构建与 UI 依赖，其许可见 [第三方说明](THIRD_PARTY_NOTICES.md)。

这是非官方社区项目，与 Project Moon 无隶属、授权或合作关系。
