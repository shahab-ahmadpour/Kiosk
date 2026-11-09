package com.showroom.kiosk

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.UserManager
import android.provider.Settings
import android.util.Log
import com.showroom.kiosk.core.ChromePolicies

class PolicyApplier(private val context: Context) {
    private val dpm = context.getSystemService(DevicePolicyManager::class.java)
    private val admin = ComponentName(context, AdminReceiver::class.java)
    val warnings = mutableListOf<String>()

    fun isDeviceOwner(): Boolean = dpm.isDeviceOwnerApp(context.packageName)

    fun apply(store: KioskStore) {
        warnings.clear()
        if (!isDeviceOwner()) {
            store.lastPolicyWarning = ""
            return
        }
        val locked = store.kioskEnabled && !store.maintenanceMode
        step("support") {
            dpm.setShortSupportMessage(admin, context.getString(R.string.support_short))
            dpm.setLongSupportMessage(admin, context.getString(R.string.support_long))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            step("organization") { dpm.setOrganizationName(admin, context.getString(R.string.organization_name)) }
        }
        step("backup") { dpm.setBackupServiceEnabled(admin, false) }
        step("keyguard") { dpm.setKeyguardDisabled(admin, true) }
        step("stay-awake") {
            val plugged = BatteryManager.BATTERY_PLUGGED_AC or
                BatteryManager.BATTERY_PLUGGED_USB or
                BatteryManager.BATTERY_PLUGGED_WIRELESS
            dpm.setGlobalSetting(admin, Settings.Global.STAY_ON_WHILE_PLUGGED_IN, plugged.toString())
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            step("screen-timeout") {
                dpm.setSystemSetting(admin, Settings.System.SCREEN_OFF_TIMEOUT, "300000")
            }
            step("auto-rotate") {
                dpm.setSystemSetting(admin, Settings.System.ACCELEROMETER_ROTATION, "1")
            }
        }
        if (store.brightness in 10..255 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            step("brightness") {
                dpm.setSystemSetting(admin, Settings.System.SCREEN_BRIGHTNESS_MODE, "0")
                dpm.setSystemSetting(admin, Settings.System.SCREEN_BRIGHTNESS, store.brightness.toString())
            }
        }
        step("home") {
            val filter = IntentFilter(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addCategory(Intent.CATEGORY_DEFAULT)
            }
            dpm.addPersistentPreferredActivity(
                admin,
                filter,
                ComponentName(context, HomeActivity::class.java),
            )
        }
        applyRestrictions(store, locked)
        step("lock-packages") {
            if (locked) {
                val packages = (listOf(context.packageName) + store.allowedPackages).distinct().toTypedArray()
                dpm.setLockTaskPackages(admin, packages)
            } else {
                dpm.setLockTaskPackages(admin, emptyArray())
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            step("lock-features") {
                val features = if (locked && !store.statusBarDisabled) {
                    DevicePolicyManager.LOCK_TASK_FEATURE_HOME or DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO
                } else if (locked) {
                    DevicePolicyManager.LOCK_TASK_FEATURE_HOME
                } else {
                    DevicePolicyManager.LOCK_TASK_FEATURE_NONE
                }
                dpm.setLockTaskFeatures(admin, features)
            }
        }
        step("status-bar") { dpm.setStatusBarDisabled(admin, locked && store.statusBarDisabled) }
        step("screenshots") { dpm.setScreenCaptureDisabled(admin, locked && store.blockScreenshots) }
        applyChrome(store)
        syncUninstallBlock(store)
        syncSamsungPackages(store, hide = locked && store.samsungHardening)
        if (locked) {
            step("navigation-buttons") { dpm.setSecureSetting(admin, "navigation_mode", "0") }
            step("edge-panels") { dpm.setGlobalSetting(admin, "edge_enable", "0") }
        }
        store.lastPolicyWarning = warnings.joinToString("\n")
    }

    private fun applyRestrictions(store: KioskStore, locked: Boolean) {
        val desired = desiredRestrictions(store, locked)
        for (restriction in universe()) {
            if (restriction in desired) {
                step("restrict") { dpm.addUserRestriction(admin, restriction) }
            } else {
                step("clear-restrict") { dpm.clearUserRestriction(admin, restriction) }
            }
        }
    }

    private fun desiredRestrictions(store: KioskStore, locked: Boolean): Set<String> {
        val set = linkedSetOf(
            UserManager.DISALLOW_SAFE_BOOT,
            UserManager.DISALLOW_FACTORY_RESET,
            UserManager.DISALLOW_ADD_USER,
        )
        if (!locked) return set
        set += listOf(
            UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA,
            UserManager.DISALLOW_USB_FILE_TRANSFER,
            UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES,
            UserManager.DISALLOW_UNINSTALL_APPS,
            UserManager.DISALLOW_APPS_CONTROL,
            UserManager.DISALLOW_MODIFY_ACCOUNTS,
            UserManager.DISALLOW_FUN,
            UserManager.DISALLOW_CONFIG_BLUETOOTH,
            UserManager.DISALLOW_SET_WALLPAPER,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            set += UserManager.DISALLOW_CONFIG_DATE_TIME
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            set += UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY
        }
        if (!store.allowDebugging) {
            set += UserManager.DISALLOW_DEBUGGING_FEATURES
        }
        return set
    }

    private fun universe(): List<String> {
        val list = mutableListOf(
            UserManager.DISALLOW_SAFE_BOOT,
            UserManager.DISALLOW_FACTORY_RESET,
            UserManager.DISALLOW_ADD_USER,
            UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA,
            UserManager.DISALLOW_USB_FILE_TRANSFER,
            UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES,
            UserManager.DISALLOW_UNINSTALL_APPS,
            UserManager.DISALLOW_APPS_CONTROL,
            UserManager.DISALLOW_MODIFY_ACCOUNTS,
            UserManager.DISALLOW_FUN,
            UserManager.DISALLOW_CONFIG_BLUETOOTH,
            UserManager.DISALLOW_SET_WALLPAPER,
            UserManager.DISALLOW_DEBUGGING_FEATURES,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            list += UserManager.DISALLOW_CONFIG_DATE_TIME
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            list += UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY
        }
        return list
    }

    private fun applyChrome(store: KioskStore) {
        step("chrome") {
            val values = ChromePolicies.restrictions(
                startUrl = store.chromeStartUrl,
                allowlist = store.chromeAllowlist,
                signInAllowed = store.chromeSignInAllowed,
            )
            val bundle = Bundle()
            for ((key, value) in values) {
                when (value) {
                    is String -> bundle.putString(key, value)
                    is Int -> bundle.putInt(key, value)
                    is Boolean -> bundle.putBoolean(key, value)
                }
            }
            dpm.setApplicationRestrictions(admin, ChromePolicies.PACKAGE, bundle)
        }
    }

    private fun syncUninstallBlock(store: KioskStore) {
        val desired = (store.allowedPackages + context.packageName).distinct()
        for (packageName in store.uninstallBlocked - desired.toSet()) {
            step("unblock-uninstall") { dpm.setUninstallBlocked(admin, packageName, false) }
        }
        for (packageName in desired) {
            step("block-uninstall") { dpm.setUninstallBlocked(admin, packageName, true) }
        }
        store.uninstallBlocked = desired
    }

    private fun syncSamsungPackages(store: KioskStore, hide: Boolean) {
        val desired = if (hide) SAMSUNG_PACKAGES.filter { installed(it) } else emptyList()
        for (packageName in store.hiddenPackages.filter { it !in desired }) {
            step("unhide") { dpm.setApplicationHidden(admin, packageName, false) }
        }
        val hidden = mutableListOf<String>()
        for (packageName in desired) {
            step("hide") {
                if (dpm.setApplicationHidden(admin, packageName, true)) {
                    hidden += packageName
                }
            }
        }
        store.hiddenPackages = hidden
    }

    private fun installed(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun step(name: String, block: () -> Unit) {
        try {
            block()
        } catch (error: Throwable) {
            warnings += name
            Log.w(TAG, name, error)
        }
    }

    companion object {
        private const val TAG = "ShowroomKiosk"

        val SAMSUNG_PACKAGES = listOf(
            "com.samsung.android.app.cocktailbarservice",
            "com.samsung.android.game.gametools",
            "com.samsung.android.game.gamehome",
            "com.samsung.android.bixby.agent",
            "com.samsung.android.bixby.wakeup",
            "com.samsung.android.app.spage",
            "com.samsung.android.smartmirroring",
            "com.sec.android.desktopmode.uiservice",
            "com.sec.android.app.desktoplauncher",
        )
    }
}
