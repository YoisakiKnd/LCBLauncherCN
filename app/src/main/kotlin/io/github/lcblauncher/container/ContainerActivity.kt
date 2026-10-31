package io.github.lcblauncher.container

import android.app.Activity
import android.app.Application
import android.app.Instrumentation
import android.content.ComponentName
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.Process
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import io.github.lcblauncher.core.ProbePolicy

/**
 * Independent first runtime: the OS launches this proxy in :container, then we instantiate an ordinary
 * guest Activity through Android's public Instrumentation API and embed its Window. No system-original
 * launch fallback. This does not yet implement a virtual ActivityManager, providers, services or JNI IO.
 */
@Suppress("DEPRECATION")
class ContainerActivity : ComponentActivity() {
    private val instrumentation = Instrumentation()
    private var guest: Activity? = null
    private var guestStarted = false
    private var guestResumed = false
    private var resumed = false
    private var attempted = false
    private lateinit var surface: FrameLayout
    private lateinit var status: TextView
    private lateinit var journal: ContainerJournal
    private var guestPackage = ProbePolicy.GAME_PACKAGE
    @Volatile private var stage = "process_started"

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { finish() }
        })
        guestPackage = intent.getStringExtra(EXTRA_PACKAGE) ?: ProbePolicy.GAME_PACKAGE
        require(guestPackage == ProbePolicy.GAME_PACKAGE || guestPackage == ContainerStore.FIXTURE_PACKAGE)
        journal = ContainerJournal(this)
        val systemCrashHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { journal.write(guestPackage, "failed_$stage", error) }
            if (systemCrashHandler != null) systemCrashHandler.uncaughtException(thread, error)
            else Process.killProcess(Process.myPid())
        }
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(0xff101113.toInt()) }
        status = TextView(this).apply {
            text = "独立容器 · 实验运行中（尚未接入汉化）"
            setTextColor(0xffe6b767.toInt()); textSize = 12f; setPadding(16, 12, 16, 12)
        }
        surface = FrameLayout(this)
        layout.addView(status)
        layout.addView(surface, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(layout)
        record("process_started")
        // Wait for the host window's genuine application token before attaching the guest window.
        surface.post { if (!attempted && !isFinishing && !isDestroyed) launchGuest() }
    }

    private fun launchGuest() {
        attempted = true
        record("validating_snapshot")
        Thread({
            try {
                val descriptor = ContainerStore(this).load(guestPackage)
                runOnUiThread { if (!isFinishing && !isDestroyed) executeGuest(descriptor) }
            } catch (error: Exception) {
                runOnUiThread { if (!isDestroyed) guarded { throw error } }
            }
        }, "container-integrity").start()
    }

    private fun executeGuest(descriptor: GuestPackage) {
        guarded {
            val applicationClass = descriptor.info.className ?: Application::class.java.name
            val activityClassName = descriptor.activityInfo.targetActivity ?: descriptor.activityInfo.name
            journal.identify(applicationClass, activityClassName)
            record("creating_classloader")
            val context = GuestContext(this, descriptor)
            Thread.currentThread().contextClassLoader = context.classLoader
            record("loading_application_class")
            context.classLoader.loadClass(applicationClass).asSubclass(Application::class.java)
            record("constructing_attaching_application")
            val app = instrumentation.newApplication(context.classLoader, applicationClass, context)
            context.application = app
            record("calling_application_on_create")
            instrumentation.callApplicationOnCreate(app)
            record("creating_activity")
            val className = descriptor.activityInfo.targetActivity ?: descriptor.activityInfo.name
            val activityClass = context.classLoader.loadClass(className).asSubclass(Activity::class.java)
            val token = window.decorView.applicationWindowToken ?: error("Host window not attached")
            val guestIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(ComponentName(guestPackage, descriptor.activityInfo.name))
            guest = instrumentation.newActivity(activityClass, context, token, app, guestIntent,
                descriptor.activityInfo, "Limbus container", this, "guest", null)
            val activity = requireNotNull(guest)
            if (descriptor.activityInfo.themeResource != 0) activity.setTheme(descriptor.activityInfo.themeResource)
            record("calling_on_create")
            instrumentation.callActivityOnCreate(activity, null)
            val content = activity.window.decorView
            surface.addView(content, FrameLayout.LayoutParams(-1, -1))
            record("calling_on_start")
            instrumentation.callActivityOnStart(activity)
            guestStarted = true
            if (resumed) resumeGuest()
        }
    }

    override fun onStart() {
        super.onStart()
        if (guest != null && !guestStarted) guarded {
            instrumentation.callActivityOnStart(requireNotNull(guest)); guestStarted = true
        }
    }
    override fun onResume() { super.onResume(); resumed = true; if (guestStarted && !guestResumed) guarded { resumeGuest() } }
    private fun resumeGuest() {
        record("calling_on_resume")
        instrumentation.callActivityOnResume(requireNotNull(guest))
        guestResumed = true
        record("guest_resumed")
        status.text = "独立容器 · 客体 Activity 已执行（尚未验证游戏兼容／汉化）"
    }
    override fun onPause() {
        resumed = false
        if (guestResumed) guarded { guestResumed = false; instrumentation.callActivityOnPause(requireNotNull(guest)); record("guest_paused") }
        super.onPause()
    }
    override fun onStop() {
        if (guestStarted) guarded { guestStarted = false; instrumentation.callActivityOnStop(requireNotNull(guest)); record("guest_stopped") }
        super.onStop()
    }
    override fun onDestroy() {
        guest?.let { activity -> guarded { instrumentation.callActivityOnDestroy(activity); record("guest_destroyed") } }
        guest = null
        runCatching { journal.markHostRequestedClose() }
        super.onDestroy()
        // Unity and other native runtimes cannot reliably be unloaded. Every next launch uses a fresh process.
        Process.killProcess(Process.myPid())
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        guest?.let { guarded { it.onWindowFocusChanged(hasFocus) } }
    }
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        guest?.let { guarded { it.onConfigurationChanged(newConfig) } }
    }

    private fun record(value: String) { stage = value; journal.write(guestPackage, value) }
    private inline fun guarded(block: () -> Unit) {
        try { block() }
        catch (error: Throwable) {
            if (error is VirtualMachineError || error is ThreadDeath) throw error
            val failure = "failed_$stage"
            runCatching { journal.write(guestPackage, failure, error) }
            status.text = "容器执行失败：$stage\n${error.javaClass.simpleName}\n返回启动器可导出诊断。未启动系统原版。"
            surface.removeAllViews()
            guest = null; guestStarted = false; guestResumed = false
        }
    }
    companion object { const val EXTRA_PACKAGE = "guest_package" }
}
