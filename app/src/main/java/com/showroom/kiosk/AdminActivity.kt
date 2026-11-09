package com.showroom.kiosk

import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Log
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.showroom.kiosk.core.ChromePolicies
import com.showroom.kiosk.core.ConfigException
import com.showroom.kiosk.core.PackageRules
import com.showroom.kiosk.core.UrlRules

class AdminActivity : AppCompatActivity() {
    private lateinit var store: KioskStore
    private lateinit var policy: PolicyApplier
    private lateinit var statusView: TextView
    private lateinit var urlInput: EditText
    private lateinit var allowInput: EditText
    private lateinit var configInput: EditText
    private lateinit var tokenInput: EditText
    private lateinit var appsSummary: TextView
    private lateinit var brightnessLabel: TextView
    private lateinit var brightnessSeek: SeekBar
    private lateinit var autoLaunch: SwitchCompat
    private lateinit var hardening: SwitchCompat
    private lateinit var statusBar: SwitchCompat
    private lateinit var screenshots: SwitchCompat
    private lateinit var debugging: SwitchCompat
    private lateinit var signIn: SwitchCompat
    private val selected = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        store = KioskStore(this)
        policy = PolicyApplier(this)
        val (scroll, column) = screenScroll()
        statusView = bodyText()
        urlInput = field(getString(R.string.chrome_url), InputType.TYPE_TEXT_VARIATION_URI)
        allowInput = field(getString(R.string.chrome_allowlist), InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE, 4000)
        allowInput.minLines = 4
        configInput = field(getString(R.string.config_url), InputType.TYPE_TEXT_VARIATION_URI)
        tokenInput = field(
            getString(R.string.config_token),
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            200,
            password = true,
        )
        appsSummary = mutedText()
        brightnessLabel = mutedText()
        brightnessSeek = SeekBar(this).apply { max = 245 }
        autoLaunch = toggle(getString(R.string.auto_launch))
        hardening = toggle(getString(R.string.samsung_hardening))
        statusBar = toggle(getString(R.string.hide_status_bar))
        screenshots = toggle(getString(R.string.block_screenshots))
        debugging = toggle(getString(R.string.allow_debugging))
        signIn = toggle(getString(R.string.chrome_signin))

        column.full(heading(getString(R.string.admin_title)), 0)
        column.full(sectionLabel(getString(R.string.section_status)), 8)
        column.full(statusView, 0)
        column.full(actionButton(getString(R.string.save)) { if (saveForm()) toast(getString(R.string.saved)) })
        column.full(sectionLabel(getString(R.string.section_pin)))
        column.full(actionButton(getString(R.string.change_pin), outlined = true) { Dialogs.changePin(this, store) { refreshStatus() } })
        column.full(sectionLabel(getString(R.string.section_apps)))
        column.full(appsSummary, 0)
        column.full(actionButton(getString(R.string.choose_apps), outlined = true) { chooseApps() })
        column.full(sectionLabel(getString(R.string.section_chrome)))
        column.full(urlInput)
        column.full(allowInput)
        column.full(mutedText(getString(R.string.chrome_allowlist_help)), 4)
        column.full(autoLaunch)
        column.full(signIn)
        column.full(sectionLabel(getString(R.string.section_device)))
        column.full(hardening)
        column.full(statusBar)
        column.full(screenshots)
        column.full(debugging)
        column.full(brightnessLabel)
        column.full(brightnessSeek, 4)
        column.full(sectionLabel(getString(R.string.section_fleet)))
        column.full(configInput)
        column.full(tokenInput)
        column.full(actionButton(getString(R.string.sync_now), outlined = true) { sync() })
        column.full(actionButton(getString(R.string.export_json), outlined = true) { exportJson() })
        column.full(sectionLabel(getString(R.string.section_power)))
        column.full(actionButton(getString(R.string.enable_kiosk)) { enableKiosk() })
        column.full(actionButton(getString(R.string.enter_maintenance), outlined = true) { enterMaintenance() })
        column.full(actionButton(getString(R.string.extend_maintenance), outlined = true) { extendMaintenance() })
        column.full(actionButton(getString(R.string.open_settings), outlined = true) { openSystem(Settings.ACTION_SETTINGS) })
        column.full(actionButton(getString(R.string.open_wifi), outlined = true) { openSystem(Settings.ACTION_WIFI_SETTINGS) })
        column.full(actionButton(getString(R.string.reboot), outlined = true) { confirmReboot() })
        column.full(actionButton(getString(R.string.factory_reset), danger = true) { confirmWipe() })
        brightnessSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                brightnessLabel.text = getString(R.string.brightness) + " " + (progress + 10)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
        setContentView(scroll)
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        fill()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun fill() {
        selected.clear()
        selected += store.allowedPackages
        urlInput.setText(store.chromeStartUrl)
        allowInput.setText(store.chromeAllowlist.joinToString("\n"))
        configInput.setText(store.configUrl)
        tokenInput.setText(store.configToken)
        autoLaunch.isChecked = store.autoLaunchPackage.isNotBlank()
        hardening.isChecked = store.samsungHardening
        statusBar.isChecked = store.statusBarDisabled
        screenshots.isChecked = store.blockScreenshots
        debugging.isChecked = store.allowDebugging
        signIn.isChecked = store.chromeSignInAllowed
        val level = if (store.brightness in 10..255) store.brightness else 180
        brightnessSeek.progress = level - 10
        brightnessLabel.text = getString(R.string.brightness) + " " + level
        appsSummary.text = selected.joinToString().ifBlank { getString(R.string.none_yet) }
        refreshStatus()
    }

    private fun saveForm(): Boolean {
        val startUrl = urlInput.text.toString().trim()
        if (startUrl.isNotEmpty() && !UrlRules.isHttpUrl(startUrl)) {
            toast(getString(R.string.invalid_url))
            return false
        }
        val patterns = allowInput.text.toString().lines().map { it.trim() }.filter { it.isNotEmpty() }
        try {
            patterns.forEach { UrlRules.checkChromePattern(it) }
        } catch (_: ConfigException) {
            toast(getString(R.string.invalid_pattern))
            return false
        }
        val configUrl = configInput.text.toString().trim()
        if (configUrl.isNotEmpty() && !UrlRules.isHttpsUrl(configUrl)) {
            toast(getString(R.string.invalid_config_url))
            return false
        }
        if (autoLaunch.isChecked && ChromePolicies.PACKAGE !in selected) {
            toast(getString(R.string.need_chrome))
            return false
        }
        store.chromeStartUrl = startUrl
        store.chromeAllowlist = patterns
        store.configUrl = configUrl
        store.configToken = tokenInput.text.toString().trim()
        store.allowedPackages = selected.toList()
        store.autoLaunchPackage = if (autoLaunch.isChecked) ChromePolicies.PACKAGE else ""
        store.samsungHardening = hardening.isChecked
        store.statusBarDisabled = statusBar.isChecked
        store.blockScreenshots = screenshots.isChecked
        store.allowDebugging = debugging.isChecked
        store.chromeSignInAllowed = signIn.isChecked
        store.brightness = brightnessSeek.progress + 10
        if (policy.isDeviceOwner()) policy.apply(store)
        refreshStatus()
        return true
    }

    private fun refreshStatus() {
        val owner = policy.isDeviceOwner()
        val lines = mutableListOf(
            getString(R.string.owner_line, if (owner) getString(R.string.yes) else getString(R.string.no)),
            getString(R.string.kiosk_line, if (store.kioskEnabled) getString(R.string.on) else getString(R.string.off)),
            getString(R.string.maintenance_line, if (store.maintenanceMode) getString(R.string.on) else getString(R.string.off)),
            getString(R.string.apps_line, store.allowedPackages.joinToString().ifBlank { getString(R.string.none_yet) }),
        )
        if (store.maintenanceMode) {
            val minutes = ((store.maintenanceUntil - System.currentTimeMillis()) / 60_000L).coerceAtLeast(0L)
            lines += getString(R.string.maintenance_left, minutes.toInt())
        }
        if (store.lastPolicyWarning.isNotBlank()) lines += getString(R.string.policy_line, store.lastPolicyWarning)
        if (store.lastConfigMessage.isNotBlank()) lines += getString(R.string.config_line, store.lastConfigMessage)
        if (store.lastInstallMessage.isNotBlank()) lines += getString(R.string.install_line, store.lastInstallMessage)
        val info = packageManager.getPackageInfo(packageName, 0)
        val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode.toInt() else {
            @Suppress("DEPRECATION")
            info.versionCode
        }
        lines += getString(R.string.version_line, info.versionName ?: "", code)
        if (!owner) {
            lines += getString(R.string.adb_help)
            lines += "adb shell dpm set-device-owner $packageName/.AdminReceiver"
        }
        statusView.text = lines.joinToString("\n")
    }

    private fun chooseApps() {
        val apps = AppCatalog.launchable(this)
        val filter = field(getString(R.string.filter_apps))
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroller = ScrollView(this)
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), 0)
            full(filter, 0)
            full(scroller)
        }
        scroller.addView(list)
        fun rebuild() {
            list.removeAllViews()
            val query = filter.text.toString().trim().lowercase()
            for (app in apps) {
                val haystack = (app.label + " " + app.packageName).lowercase()
                if (query.isNotEmpty() && query !in haystack) continue
                val box = CheckBox(this).apply {
                    text = app.label + "\n" + app.packageName
                    setTextColor(getColor(R.color.text))
                    isChecked = app.packageName in selected
                    setOnCheckedChangeListener { _, checked ->
                        if (checked) {
                            if (app.packageName !in selected && selected.size >= PackageRules.MAX_APPS) {
                                isChecked = false
                                toast(getString(R.string.too_many_apps))
                                return@setOnCheckedChangeListener
                            }
                            if (app.packageName !in selected) selected += app.packageName
                        } else {
                            selected.remove(app.packageName)
                        }
                        appsSummary.text = getString(R.string.apps_count, selected.size)
                    }
                }
                list.full(box, 4)
            }
        }
        filter.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = rebuild()
        })
        rebuild()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.choose_apps)
            .setView(form)
            .setPositiveButton(R.string.save) { _, _ ->
                store.allowedPackages = selected.toList()
                appsSummary.text = selected.joinToString().ifBlank { getString(R.string.none_yet) }
                if (policy.isDeviceOwner()) policy.apply(store)
            }
            .setNegativeButton(R.string.cancel) { _, _ ->
                selected.clear()
                selected += store.allowedPackages
                appsSummary.text = selected.joinToString().ifBlank { getString(R.string.none_yet) }
            }
            .show()
    }

    private fun sync() {
        if (!saveForm()) return
        FleetSync.start(this) {
            if (isDestroyed) return@start
            fill()
            toast(it)
        }
    }

    private fun exportJson() {
        if (!saveForm()) return
        val text = store.exportConfig().toPrettyJson()
        val clipboard = getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("fleet-config", text))
        val view = bodyText(text).apply { setTextIsSelectable(true) }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.export_json)
            .setView(pad(view))
            .setPositiveButton(android.R.string.ok, null)
            .show()
        toast(getString(R.string.copied))
    }

    private fun enableKiosk() {
        if (!saveForm()) return
        if (!policy.isDeviceOwner()) {
            toast(getString(R.string.need_owner))
            return
        }
        if (!store.hasPin()) {
            toast(getString(R.string.need_pin))
            return
        }
        if (store.allowedPackages.isEmpty()) {
            toast(getString(R.string.need_app))
            return
        }
        val missing = store.allowedPackages.filter { !AppCatalog.isInstalled(this, it) }
        if (missing.isNotEmpty()) {
            toast(getString(R.string.need_installed, missing.joinToString()))
            return
        }
        store.maintenanceMode = false
        store.kioskEnabled = true
        policy.apply(store)
        finish()
    }

    private fun enterMaintenance() {
        if (!saveForm()) return
        if (!store.kioskEnabled) {
            toast(getString(R.string.banner_not_locked))
            return
        }
        store.maintenanceMode = true
        store.maintenanceUntil = System.currentTimeMillis() + Limits.MAINTENANCE_MS
        (application as KioskApp).scheduleMaintenance(store.maintenanceUntil)
        leaveLockTask()
        policy.apply(store)
        refreshStatus()
    }

    private fun extendMaintenance() {
        if (!store.maintenanceMode) {
            toast(getString(R.string.banner_not_locked))
            return
        }
        store.maintenanceUntil = System.currentTimeMillis() + Limits.MAINTENANCE_EXTEND_MS
        (application as KioskApp).scheduleMaintenance(store.maintenanceUntil)
        refreshStatus()
    }

    private fun openSystem(action: String) {
        if (store.kioskEnabled && !store.maintenanceMode) {
            toast(getString(R.string.maintenance_settings_blocked))
            return
        }
        leaveLockTask()
        startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun confirmReboot() {
        MaterialAlertDialogBuilder(this)
            .setMessage(R.string.reboot_confirm)
            .setPositiveButton(R.string.reboot) { _, _ ->
                if (!policy.isDeviceOwner()) {
                    toast(getString(R.string.need_owner))
                    return@setPositiveButton
                }
                try {
                    val dpm = getSystemService(DevicePolicyManager::class.java)
                    dpm.reboot(ComponentName(this, AdminReceiver::class.java))
                } catch (error: Exception) {
                    Log.w(TAG, "reboot", error)
                    toast(getString(R.string.reboot_failed, error.javaClass.simpleName))
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmWipe() {
        if (!policy.isDeviceOwner()) {
            toast(getString(R.string.need_owner))
            return
        }
        val input = field(getString(R.string.factory_reset_hint), max = 12)
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.factory_reset)
            .setMessage(R.string.factory_reset_body)
            .setView(pad(input))
            .setPositiveButton(R.string.factory_reset, null)
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (input.text.toString() != getString(R.string.factory_reset_hint)) return@setOnClickListener
                val dpm = getSystemService(DevicePolicyManager::class.java)
                dpm.wipeData(0)
            }
        }
        dialog.show()
    }

    private fun leaveLockTask() {
        val manager = getSystemService(ActivityManager::class.java)
        if (manager.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE) {
            try {
                stopLockTask()
            } catch (error: RuntimeException) {
                Log.w(TAG, "stopLockTask", error)
            }
        }
    }

    private fun actionButton(
        label: String,
        outlined: Boolean = false,
        danger: Boolean = false,
        onClick: () -> Unit,
    ) = actionButton(label, outlined, danger).apply { setOnClickListener { onClick() } }

    private companion object {
        const val TAG = "ShowroomKiosk"
    }
}
