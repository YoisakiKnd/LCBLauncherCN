package io.github.lcblauncher.container

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.res.AssetManager
import android.content.res.Resources
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.view.LayoutInflater
import dalvik.system.DexClassLoader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/** Java Context routing only. Native libc paths and system Binder identities are not virtualized yet. */
class GuestContext(host: Context, val guest: GuestPackage) : ContextWrapper(host) {
    private val loader = DexClassLoader(guest.apks.joinToString(File.pathSeparator) { it.path },
        File(host.codeCacheDir, "guest-${guest.install.packageName}").apply { mkdirs() }.path,
        guest.libraries.path, host.classLoader.parent)
    private val guestResources = host.packageManager.getResourcesForApplication(guest.info)
    var application: Application? = null

    override fun getPackageName(): String = guest.install.packageName
    // AppOps and system services must still use the real host identity. This is not a virtual PackageManager.
    override fun getOpPackageName(): String = if (android.os.Build.VERSION.SDK_INT >= 29) baseContext.opPackageName else baseContext.packageName
    override fun getApplicationInfo(): ApplicationInfo = guest.info
    override fun getApplicationContext(): Context = application ?: this
    override fun getClassLoader(): ClassLoader = loader
    override fun getResources(): Resources = guestResources
    override fun getAssets(): AssetManager = guestResources.assets
    override fun getPackageCodePath(): String = guest.apks.first().path
    override fun getPackageResourcePath(): String = guest.apks.first().path
    override fun getDataDir(): File = guest.data
    override fun getFilesDir(): File = directory("files")
    override fun getCacheDir(): File = directory("cache")
    override fun getCodeCacheDir(): File = directory("code_cache")
    override fun getNoBackupFilesDir(): File = directory("no_backup")
    override fun getFileStreamPath(name: String): File = named(filesDir, name)
    override fun fileList(): Array<String> = filesDir.list().orEmpty().map { it }.toTypedArray()
    override fun openFileInput(name: String): FileInputStream = FileInputStream(getFileStreamPath(name))
    override fun openFileOutput(name: String, mode: Int): FileOutputStream {
        require(mode == MODE_PRIVATE || mode == MODE_APPEND) { "不支持共享客体私有文件" }
        return FileOutputStream(getFileStreamPath(name), mode == MODE_APPEND)
    }
    override fun deleteFile(name: String): Boolean = getFileStreamPath(name).delete()
    override fun getDir(name: String, mode: Int): File = named(guest.data, "app_${safeName(name)}").apply { mkdirs() }
    override fun getDatabasePath(name: String): File = named(directory("databases"), name)
    override fun databaseList(): Array<String> = directory("databases").list().orEmpty().map { it }.toTypedArray()
    override fun deleteDatabase(name: String): Boolean = SQLiteDatabase.deleteDatabase(getDatabasePath(name))
    override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?): SQLiteDatabase =
        SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name), factory)
    override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?, handler: DatabaseErrorHandler?): SQLiteDatabase =
        SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name).path, factory, handler)
    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
        require(mode == MODE_PRIVATE) { "客体只支持私有首选项" }
        // Android's SharedPreferences constructor is not public; isolate via host-managed names.
        return baseContext.getSharedPreferences("guest.${guest.install.packageName}.${safeName(name)}", MODE_PRIVATE)
    }
    override fun deleteSharedPreferences(name: String): Boolean =
        baseContext.deleteSharedPreferences("guest.${guest.install.packageName}.${safeName(name)}")
    override fun getExternalFilesDir(type: String?): File =
        if (type == null) directory("external/files") else named(directory("external/files"), type).apply { mkdirs() }
    override fun getExternalFilesDirs(type: String?): Array<File> = arrayOf(getExternalFilesDir(type))
    override fun getExternalCacheDir(): File = directory("external/cache")
    override fun getExternalCacheDirs(): Array<File> = arrayOf(externalCacheDir)
    override fun getObbDir(): File = directory("external/obb")
    override fun getObbDirs(): Array<File> = arrayOf(obbDir)
    override fun getSystemService(name: String): Any? =
        if (name == LAYOUT_INFLATER_SERVICE) LayoutInflater.from(baseContext).cloneInContext(this)
        else super.getSystemService(name)

    private fun directory(name: String): File = File(guest.data, name).apply { check(isDirectory || mkdirs()) }
    private fun named(parent: File, name: String): File = File(parent, safeName(name))
    private fun safeName(name: String): String {
        require(name.isNotEmpty() && name != "." && name != ".." && '/' !in name && '\\' !in name && '\u0000' !in name) { "无效的客体文件名" }
        return name
    }
}
