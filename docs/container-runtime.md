# 自主容器第一版：APK 与 Activity 执行

用户已选择容器作为主线；不再把系统文件管理器、Root 或无线调试授权当作前置步骤。代码独立编写，没有引入其他启动器、VirtualApp、BlackBox 或第三方容器内核。

## 已实现的执行路径

1. 从系统 PackageManager 读取指定原版游戏的 base/split 安装路径、启动 Activity 和签名。
2. 在手机上流式复制完整分包集到启动器私有目录；复制过程中设置 APK 只读，符合 Android 14 以后的动态代码加载约束。
3. 对全部分包比较来源与副本 SHA-256，并验证复制后的 base 包名、版本和签名。分包签名由系统安装过程验证；不宣称本项目独立实现了 split APK 签名校验器。
4. 有界提取当前 ABI 的原生库，拒绝危险文件名和冲突的同名库；原子启用代码快照，客体数据单独保留。
5. 系统启动宿主声明的 `:container` 进程；用 DexClassLoader 加载复制后的代码，客体 Resources 包含 base 与 splits。
6. 通过公开的 Instrumentation API 创建客体 Application 和普通 Activity，嵌入客体 Window 并转发生命周期。这条路径实际执行客体 DEX，不通过系统 Intent 打开原版。
7. Java Context 的文件、数据库、缓存和外部目录 API 指向容器目录；SharedPreferences 使用独立的宿主前缀。原版游戏数据不迁移。
8. 启动阶段写入有界状态记录；诊断只包含阶段、客体包名、进程身份和异常类型，不收集异常正文或游戏资源。

它是应用内客体运行时的第一个实现，不是完整 Android 虚拟系统，也不是恶意代码安全沙箱。客体与启动器使用同一 UID，独立进程只提供崩溃隔离。只允许固定游戏包与本项目自检包。

## 当前不能承诺的部分

- libc/JNI 路径重定向尚未实现。Unity 原生代码直接访问真实目录时，Java Context 映射不足以解决权限。
- PackageManager、ActivityManager、ContentProvider、Service、广播和多 Activity 调度尚未虚拟化。公开 Instrumentation 创建的 Activity 没有完整系统调度环境，调用依赖该环境的 API 可能失败。
- 未实现游戏保护组件兼容、IL2CPP 文本接入、字体加载或 Google 登录容器环境；不承诺游戏能到标题页或登录。
- `INTERNET` 和 `ACCESS_NETWORK_STATE` 由宿主声明以供客体联网。游戏不自动继承系统原版权限、登录或资源。
- 自检 APK 成功只证明基础客体执行，不证明游戏兼容，更不证明中文已生效。

## Actions 验证与真机验收

`.github/workflows/android.yml` 在 GitHub 托管机器运行编译、单元测试、lint、签名及对齐校验，还会启动 API 36 x86_64 模拟器执行仪器测试。当前服务器不运行 Java、Android 构建或模拟器。

自检客体是单独的普通 Android 应用模块 `container-fixture`，只依赖平台 API，不依赖宿主 SDK。它只作为 APK 资源打包，测试不会把它安装进系统。验证包含：

- 未安装 APK 中 Activity 的代码、独立资源表和 assets 真正被加载；
- 客体执行 PID 与宿主不同，UID 与宿主相同；
- 第二次导入代码不清除客体数据，计数递增；
- 退出后下一次启动使用新进程。

2026-10-30，[Actions 41034682899](https://github.com/YoisakiKnd/LCBLauncherCN/actions/runs/41034682899) 已通过全部检查。构建提交 `6792382`，版本 `0.2.0-container-alpha` / versionCode 14。19 项 JUnit/Robolectric 测试、4 项 Python 测试、1 项 API 36 x86_64 仪器测试通过；lint、APK 签名和对齐校验通过。仪器测试两次客体进程 PID 分别为 2534、2950，均实际走到 `guest_resumed`，退出走到 `guest_destroyed`。测试客体未安装到系统。

vivo Android 16 / ARM64 的容器自检和 Limbus 实验启动仍需真机测试。若游戏失败，按最后执行阶段修复容器；不会把启动系统原版记成容器成功。

## 后续实现顺序

1. 先根据真实游戏首个失败点完成 Activity 与 Application 绑定、分包/asset pack 加载和必要组件调度，验收到 Unity 标题页。
2. 实现原生目录访问映射并验证下载资源写入容器、重启保留；兼容游戏本身的保护运行环境。
3. 单独验证登录、后台切换和资源下载；不能由系统 Google 服务已安装推断容器登录可用。
4. 实现可复现的原创中文文本与字体测试，再接汉化资源版本管理及恢复。

## 设计依据

- [Android Instrumentation API](https://developer.android.com/reference/android/app/Instrumentation)：公开的 Application/Activity 构造及生命周期接口。
- [Android 16 Instrumentation 实现](https://android.googlesource.com/platform/frameworks/base/+/android-16.0.0_r1/core/java/android/app/Instrumentation.java)：核对公开接口的实际绑定边界，未复制实现。
- [Android 14 动态代码加载要求](https://developer.android.com/about/versions/14/behavior-changes-14#safer-dynamic-code-loading)：加载的代码必须只读。
- [DexClassLoader](https://developer.android.com/reference/dalvik/system/DexClassLoader)：APK/DEX 与原生库搜索路径。
