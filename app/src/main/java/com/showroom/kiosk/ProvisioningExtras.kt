package com.showroom.kiosk

import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.os.Build
import android.os.PersistableBundle
import android.util.Log
import com.showroom.kiosk.core.ChromePolicies
import com.showroom.kiosk.core.ConfigException
import com.showroom.kiosk.core.PackageRules
import com.showroom.kiosk.core.PinGuard
import com.showroom.kiosk.core.UrlRules

object ProvisioningExtras {
    private const val TAG = "ShowroomKiosk"

    fun from(intent: Intent): PersistableBundle? {
        return if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(
                DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE,
                PersistableBundle::class.java,
            )
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE)
        }
    }

    fun apply(store: KioskStore, bundle: PersistableBundle?) {
        if (bundle == null) return
        readPackages(store, bundle)
        readUrl(store, bundle.getString("chrome_start_url")) { store.chromeStartUrl = it }
        readAllowlist(store, bundle.getString("chrome_allowlist"))
        readTitle(store, bundle.getString("showroom_title"))
        readConfig(store, bundle)
        readAutoLaunch(store, bundle.getString("auto_launch"))
        readFlag(bundle.getString("samsung_hardening"))?.let { store.samsungHardening = it }
        readFlag(bundle.getString("chrome_sign_in"))?.let { store.chromeSignInAllowed = it }
        bundle.getString("brightness")?.toIntOrNull()?.takeIf { it in 10..255 }?.let {
            store.brightness = it
        }
        val pinApplied = readPin(store, bundle.getString("initial_pin"))
        val lock = bundle.getString("kiosk_enabled")
        if (pinApplied && lock != "false" && store.allowedPackages.isNotEmpty()) {
            store.kioskEnabled = true
            store.maintenanceMode = false
        }
    }

    private fun readPackages(store: KioskStore, bundle: PersistableBundle) {
        val raw = bundle.getString("allowed_packages") ?: return
        val parts = raw.split(',', ';', ' ', '\n').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) return
        try {
            PackageRules.checkAll(parts, store.selfPackage)
            store.allowedPackages = parts
        } catch (error: ConfigException) {
            Log.w(TAG, "ignored allowed_packages", error)
        }
    }

    private fun readUrl(store: KioskStore, raw: String?, write: (String) -> Unit) {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return
        if (UrlRules.isHttpUrl(value)) write(value) else Log.w(TAG, "ignored url")
    }

    private fun readAllowlist(store: KioskStore, raw: String?) {
        if (raw.isNullOrBlank()) return
        val patterns = raw.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }
        try {
            patterns.forEach { UrlRules.checkChromePattern(it) }
            store.chromeAllowlist = patterns
        } catch (error: ConfigException) {
            Log.w(TAG, "ignored chrome_allowlist", error)
        }
    }

    private fun readTitle(store: KioskStore, raw: String?) {
        val title = raw?.trim().orEmpty()
        if (title.isNotEmpty() && title.length <= 40 && !title.contains('\n')) {
            store.showroomTitle = title
        }
    }

    private fun readConfig(store: KioskStore, bundle: PersistableBundle) {
        val url = bundle.getString("config_url")?.trim().orEmpty()
        if (url.isNotEmpty() && UrlRules.isHttpsUrl(url)) {
            store.configUrl = url
        }
        val token = bundle.getString("config_token")?.trim().orEmpty()
        if (token.isNotEmpty() && token.length <= 200) {
            store.configToken = token
        }
    }

    private fun readAutoLaunch(store: KioskStore, raw: String?) {
        when (raw?.trim()) {
            null -> Unit
            "", "false" -> store.autoLaunchPackage = ""
            "true" -> store.autoLaunchPackage = ChromePolicies.PACKAGE
            else -> {
                try {
                    PackageRules.checkName(raw.trim(), store.selfPackage)
                    store.autoLaunchPackage = raw.trim()
                } catch (error: ConfigException) {
                    Log.w(TAG, "ignored auto_launch", error)
                }
            }
        }
    }

    private fun readPin(store: KioskStore, raw: String?): Boolean {
        val pin = raw?.trim().orEmpty()
        if (pin.isEmpty() || store.hasPin() || !PinGuard.isFormatValid(pin)) return store.hasPin() && pin.isNotEmpty()
        return try {
            store.setPin(pin)
            true
        } catch (error: ConfigException) {
            Log.w(TAG, "ignored initial_pin", error)
            false
        }
    }

    private fun readFlag(raw: String?): Boolean? = when (raw?.trim()?.lowercase()) {
        "true", "1", "yes" -> true
        "false", "0", "no" -> false
        else -> null
    }
}
