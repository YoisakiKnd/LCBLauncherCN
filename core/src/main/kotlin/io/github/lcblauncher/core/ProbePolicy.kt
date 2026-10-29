package io.github.lcblauncher.core

data class RuntimeReadiness(val available: Boolean, val explanation: String)

object ProbePolicy {
    const val GAME_PACKAGE = "com.ProjectMoon.LimbusCompany"
    const val REPORT_SCHEMA = 1

    // No runtime adapter has been implemented or verified. Hardware detection must never enable it.
    fun translationRuntime(): RuntimeReadiness = RuntimeReadiness(
        available = false,
        explanation = "汉化运行接入尚未实现；当前仅支持原版启动、设备诊断和离线汉化包检查。",
    )
}
