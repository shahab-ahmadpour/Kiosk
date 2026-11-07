package com.showroom.kiosk

import android.annotation.SuppressLint
import android.content.Context
import com.showroom.kiosk.core.ChromePolicies
import com.showroom.kiosk.core.ConfigException
import com.showroom.kiosk.core.FleetConfig
import com.showroom.kiosk.core.PinGuard
import com.showroom.kiosk.core.PinState

class KioskStore(context: Context) {
    private val prefs = context.createDeviceProtectedStorageContext()
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    val selfPackage: String = context.packageName

    var showroomTitle: String
        get() = prefs.getString(KEY_TITLE, "").orEmpty()
        set(value) = edit { putString(KEY_TITLE, value.trim().take(40)) }

    fun title(context: Context): String {
        return showroomTitle.ifBlank { context.getString(R.string.default_title) }
    }

    var allowedPackages: List<String>
        get() = prefs.getString(KEY_PACKAGES, ChromePolicies.PACKAGE).orEmpty().csv()
        set(value) = edit { putString(KEY_PACKAGES, value.joinToString(",")) }

    var chromeStartUrl: String
        get() = prefs.getString(KEY_URL, "").orEmpty()
        set(value) = edit { putString(KEY_URL, value.trim()) }

    var chromeAllowlist: List<String>
        get() = prefs.getString(KEY_ALLOW, "").orEmpty().lines().map { it.trim() }.filter { it.isNotEmpty() }
        set(value) = edit { putString(KEY_ALLOW, value.joinToString("\n")) }

    var autoLaunchPackage: String
        get() = prefs.getString(KEY_AUTO, ChromePolicies.PACKAGE).orEmpty()
        set(value) = edit { putString(KEY_AUTO, value.trim()) }

    var samsungHardening: Boolean
        get() = prefs.getBoolean(KEY_SAMSUNG, true)
        set(value) = edit { putBoolean(KEY_SAMSUNG, value) }

    var statusBarDisabled: Boolean
        get() = prefs.getBoolean(KEY_STATUS, true)
        set(value) = edit { putBoolean(KEY_STATUS, value) }

    var blockScreenshots: Boolean
        get() = prefs.getBoolean(KEY_SHOT, true)
        set(value) = edit { putBoolean(KEY_SHOT, value) }

    var allowDebugging: Boolean
        get() = prefs.getBoolean(KEY_ADB, false)
        set(value) = edit { putBoolean(KEY_ADB, value) }

    var chromeSignInAllowed: Boolean
        get() = prefs.getBoolean(KEY_SIGNIN, false)
        set(value) = edit { putBoolean(KEY_SIGNIN, value) }

    var kioskEnabled: Boolean
        get() = prefs.getBoolean(KEY_KIOSK, false)
        set(value) = edit { putBoolean(KEY_KIOSK, value) }

    var maintenanceMode: Boolean
        get() = prefs.getBoolean(KEY_MAINT, false)
        set(value) = edit { putBoolean(KEY_MAINT, value) }

    var maintenanceUntil: Long
        get() = prefs.getLong(KEY_MAINT_UNTIL, 0L)
        set(value) = edit { putLong(KEY_MAINT_UNTIL, value) }

    var brightness: Int
        get() = prefs.getInt(KEY_BRIGHT, -1)
        set(value) = edit { putInt(KEY_BRIGHT, value) }

    var configUrl: String
        get() = prefs.getString(KEY_CONFIG_URL, "").orEmpty()
        set(value) = edit { putString(KEY_CONFIG_URL, value.trim()) }

    var configToken: String
        get() = prefs.getString(KEY_TOKEN, "").orEmpty()
        set(value) = edit { putString(KEY_TOKEN, value.take(200)) }

    var lastConfigMessage: String
        get() = prefs.getString(KEY_CONFIG_MSG, "").orEmpty()
        set(value) = edit { putString(KEY_CONFIG_MSG, value.take(500)) }

    var lastInstallMessage: String
        get() = prefs.getString(KEY_INSTALL_MSG, "").orEmpty()
        set(value) = edit { putString(KEY_INSTALL_MSG, value.take(500)) }

    var lastPolicyWarning: String
        get() = prefs.getString(KEY_POLICY_MSG, "").orEmpty()
        set(value) = edit { putString(KEY_POLICY_MSG, value.take(800)) }

    var hiddenPackages: List<String>
        get() = prefs.getString(KEY_HIDDEN, "").orEmpty().csv()
        set(value) = edit { putString(KEY_HIDDEN, value.joinToString(",")) }

    var uninstallBlocked: List<String>
        get() = prefs.getString(KEY_UNINSTALL, "").orEmpty().csv()
        set(value) = edit { putString(KEY_UNINSTALL, value.joinToString(",")) }

    var appliedRevision: Int
        get() = prefs.getInt(KEY_REV, 0)
        set(value) = edit { putInt(KEY_REV, value) }

    fun hasPin(): Boolean = pinState() != null

    fun pinState(): PinState? {
        val salt = prefs.getString(KEY_SALT, null) ?: return null
        val hash = prefs.getString(KEY_HASH, null) ?: return null
        return PinState(salt, hash, prefs.getInt(KEY_FAILS, 0), prefs.getLong(KEY_LOCKOUT, 0L))
    }

    fun savePinState(state: PinState) {
        edit {
            putString(KEY_SALT, state.saltB64)
            putString(KEY_HASH, state.hashB64)
            putInt(KEY_FAILS, state.failures)
            putLong(KEY_LOCKOUT, state.lockoutUntilEpochMs)
        }
    }

    fun setPin(pin: String) {
        savePinState(PinGuard.create(pin))
    }

    fun exportConfig(): FleetConfig {
        return FleetConfig(
            allowedPackages = allowedPackages,
            chromeStartUrl = chromeStartUrl,
            chromeUrlAllowlist = chromeAllowlist,
            autoLaunchPackage = autoLaunchPackage,
            samsungHardening = samsungHardening,
            showroomTitle = showroomTitle.ifBlank { null },
            kioskEnabled = kioskEnabled,
            chromeSignInAllowed = chromeSignInAllowed,
            brightness = brightness.takeIf { it in 10..255 },
        )
    }

    fun applyRemote(config: FleetConfig): Boolean {
        val nextAllowed = config.allowedPackages ?: allowedPackages
        val nextLaunch = config.autoLaunchPackage ?: autoLaunchPackage
        if (nextLaunch.isNotEmpty() && nextLaunch !in nextAllowed) {
            throw ConfigException("autoLaunchPackage is not in allowedPackages")
        }
        val editor = prefs.edit()
        config.allowedPackages?.let { editor.putString(KEY_PACKAGES, it.joinToString(",")) }
        config.chromeStartUrl?.let { editor.putString(KEY_URL, it.trim()) }
        config.chromeUrlAllowlist?.let { editor.putString(KEY_ALLOW, it.joinToString("\n")) }
        config.autoLaunchPackage?.let { editor.putString(KEY_AUTO, it.trim()) }
        config.samsungHardening?.let { editor.putBoolean(KEY_SAMSUNG, it) }
        config.showroomTitle?.let { editor.putString(KEY_TITLE, it.trim()) }
        config.chromeSignInAllowed?.let { editor.putBoolean(KEY_SIGNIN, it) }
        config.brightness?.let { editor.putInt(KEY_BRIGHT, it) }
        if (config.kioskEnabled == true) {
            if (!hasPin()) throw ConfigException("refusing to lock a tablet that has no staff PIN")
            editor.putBoolean(KEY_KIOSK, true)
            editor.putBoolean(KEY_MAINT, false)
        } else if (config.kioskEnabled == false) {
            editor.putBoolean(KEY_KIOSK, false)
        }
        val revision = config.configRevision
        val reboot = revision != null && revision > appliedRevision && config.reboot == true
        if (revision != null && revision > appliedRevision) {
            editor.putInt(KEY_REV, revision)
        }
        if (!editor.commit()) {
            throw ConfigException("could not save the fleet file on this tablet")
        }
        return reboot
    }

    @SuppressLint("ApplySharedPref")
    private fun edit(block: android.content.SharedPreferences.Editor.() -> Unit) {
        val editor = prefs.edit()
        editor.block()
        editor.commit()
    }

    private fun String.csv(): List<String> = split(',').map { it.trim() }.filter { it.isNotEmpty() }

    private companion object {
        const val PREFS = "kiosk"
        const val KEY_TITLE = "title"
        const val KEY_PACKAGES = "packages"
        const val KEY_URL = "url"
        const val KEY_ALLOW = "allow"
        const val KEY_AUTO = "auto"
        const val KEY_SAMSUNG = "samsung"
        const val KEY_STATUS = "status_bar"
        const val KEY_SHOT = "shots"
        const val KEY_ADB = "adb"
        const val KEY_SIGNIN = "signin"
        const val KEY_KIOSK = "kiosk"
        const val KEY_MAINT = "maintenance"
        const val KEY_MAINT_UNTIL = "maintenance_until"
        const val KEY_BRIGHT = "brightness"
        const val KEY_CONFIG_URL = "config_url"
        const val KEY_TOKEN = "config_token"
        const val KEY_CONFIG_MSG = "config_msg"
        const val KEY_INSTALL_MSG = "install_msg"
        const val KEY_POLICY_MSG = "policy_msg"
        const val KEY_HIDDEN = "hidden"
        const val KEY_UNINSTALL = "uninstall_blocked"
        const val KEY_REV = "revision"
        const val KEY_SALT = "pin_salt"
        const val KEY_HASH = "pin_hash"
        const val KEY_FAILS = "pin_fails"
        const val KEY_LOCKOUT = "pin_lockout"
    }
}
