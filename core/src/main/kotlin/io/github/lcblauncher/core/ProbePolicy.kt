package io.github.lcblauncher.core

data class RuntimeReadiness(val available: Boolean, val explanation: String)

object ProbePolicy {
    const val GAME_PACKAGE = "com.ProjectMoon.LimbusCompany"
    const val REPORT_SCHEMA = 1

    // No runtime adapter has been implemented or verified. Hardware detection must never enable it.
    fun translationRuntime(): RuntimeReadiness = RuntimeReadiness(
        available = false,
        explanation = "汉化运行接入尚未实现；当前可测试自主容器的 APK 导入和 Activity 执行，游戏原生路径、登录与中文显示仍待验证。",
    )
}
