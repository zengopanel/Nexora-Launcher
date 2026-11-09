package com.nexora.launcher

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.os.UserHandle
import java.util.Locale

data class AppInfo(
    val label: String,
    val pkg: String,
    val cls: String,
    val user: UserHandle,
    val info: LauncherActivityInfo,
) {
    val key: String get() = "$pkg/$cls/${user.hashCode()}"
}

class AppRepository(context: Context) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)

    fun load(): List<AppInfo> =
        launcherApps.profiles.flatMap { user ->
            launcherApps.getActivityList(null, user).map {
                AppInfo(
                    label = it.label.toString(),
                    pkg = it.componentName.packageName,
                    cls = it.componentName.className,
                    user = user,
                    info = it,
                )
            }
        }.sortedBy { it.label.lowercase(Locale.getDefault()) }

    fun launch(app: AppInfo) {
        launcherApps.startMainActivity(ComponentName(app.pkg, app.cls), app.user, null, null)
    }

    fun register(onChange: () -> Unit): LauncherApps.Callback {
        val cb = object : LauncherApps.Callback() {
            override fun onPackageAdded(packageName: String?, user: UserHandle?) = onChange()
            override fun onPackageRemoved(packageName: String?, user: UserHandle?) = onChange()
            override fun onPackageChanged(packageName: String?, user: UserHandle?) = onChange()
            override fun onPackagesAvailable(
                packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean,
            ) = onChange()

            override fun onPackagesUnavailable(
                packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean,
            ) = onChange()
        }
        launcherApps.registerCallback(cb)
        return cb
    }

    fun unregister(cb: LauncherApps.Callback) = launcherApps.unregisterCallback(cb)
}
