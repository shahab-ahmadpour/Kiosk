package com.showroom.kiosk

import android.app.ActivityManager
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import com.google.android.material.button.MaterialButton

class HomeActivity : AppCompatActivity() {
    private lateinit var store: KioskStore
    private lateinit var policy: PolicyApplier
    private lateinit var subtitleView: TextView
    private lateinit var bannerView: TextView
    private lateinit var hintView: TextView
    private lateinit var setupButton: MaterialButton
    private lateinit var resumeButton: MaterialButton
    private lateinit var appList: LinearLayout
    private lateinit var versionView: TextView
    private lateinit var status: HomeStatus
    private var taps = 0
    private var lastTap = 0L
    private var gateOpen = false
    private val holdGate = Runnable { openStaffGate() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        store = KioskStore(this)
        policy = PolicyApplier(this)
        val (scroll, column) = screenScroll()
        scroll.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        subtitleView = mutedText().apply {
            setTextColor(getColor(R.color.text))
            setShadowLayer(12f, 0f, 2f, android.graphics.Color.BLACK)
        }
        watchStaffGesture(subtitleView)
        bannerView = bodyText().apply {
            background = cardBackground()
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        hintView = mutedText(getString(R.string.setup_hint))
        setupButton = actionButton(getString(R.string.setup_button)).apply {
            setOnClickListener {
                Dialogs.askPin(this@HomeActivity, store) {
                    startActivity(android.content.Intent(this@HomeActivity, AdminActivity::class.java))
                }
            }
        }
        resumeButton = actionButton(getString(R.string.resume_kiosk), outlined = true).apply {
            setOnClickListener {
                store.maintenanceMode = false
                policy.apply(store)
                render()
                enterLockTask()
            }
        }
        appList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        versionView = mutedText()
        column.full(subtitleView, 28)
        column.full(bannerView, 16)
        column.full(hintView, 8)
        column.full(setupButton, 16)
        column.full(resumeButton, 8)
        column.full(appList, 8)
        column.full(versionView, 18)
        val wallpaper = ImageView(this).apply {
            setImageResource(R.drawable.home_background)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(android.graphics.Color.BLACK)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        status = HomeStatus(this)
        val root = FrameLayout(this).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
            addView(wallpaper, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            addView(scroll, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            addView(
                status.view,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                ).apply { gravity = Gravity.TOP or Gravity.RIGHT },
            )
        }
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(status.view) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val params = view.layoutParams as FrameLayout.LayoutParams
            params.topMargin = bars.top + dp(12)
            params.rightMargin = bars.right + dp(20)
            view.layoutParams = params
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
    }

    override fun onStart() {
        super.onStart()
        status.start()
    }

    override fun onStop() {
        status.stop()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        if (store.maintenanceMode && store.kioskEnabled && System.currentTimeMillis() >= store.maintenanceUntil) {
            store.maintenanceMode = false
        }
        if (policy.isDeviceOwner()) policy.apply(store)
        render()
        enterLockTask()
        syncThenMaybeLaunch()
    }

    private fun render() {
        val installed = store.allowedPackages.filter { AppCatalog.isInstalled(this, it) }
        val missing = store.allowedPackages.filter { it !in installed }
        subtitleView.text = when {
            installed.size > 1 -> getString(R.string.home_subtitle_many)
            installed.size == 1 -> getString(R.string.home_subtitle_one)
            else -> getString(R.string.home_subtitle_none)
        }
        val banner = when {
            !policy.isDeviceOwner() -> getString(R.string.banner_not_owner)
            store.maintenanceMode && store.kioskEnabled -> getString(R.string.banner_maintenance)
            !store.kioskEnabled -> getString(R.string.banner_not_locked)
            missing.isNotEmpty() -> getString(R.string.banner_missing, missing.joinToString())
            else -> ""
        }
        bannerView.text = banner
        bannerView.visibility = if (banner.isEmpty()) View.GONE else View.VISIBLE
        val locked = store.kioskEnabled && !store.maintenanceMode
        hintView.visibility = if (locked) View.GONE else View.VISIBLE
        setupButton.visibility = if (locked) View.GONE else View.VISIBLE
        resumeButton.visibility = if (store.maintenanceMode && store.kioskEnabled) View.VISIBLE else View.GONE
        versionView.visibility = if (locked) View.GONE else View.VISIBLE
        if (!locked) {
            val info = packageManager.getPackageInfo(packageName, 0)
            val code = if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode.toInt() else {
                @Suppress("DEPRECATION")
                info.versionCode
            }
            versionView.text = getString(R.string.version_line, info.versionName ?: "", code)
        }
        appList.removeAllViews()
        for (packageName in store.allowedPackages) {
            appList.full(tile(packageName), 10)
        }
    }

    private fun tile(packageName: String): LinearLayout {
        val installed = AppCatalog.isInstalled(this, packageName)
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            background = cardBackground()
            setPadding(dp(18), dp(16), dp(18), dp(16))
            minimumHeight = dp(88)
            alpha = if (installed) 1f else 0.45f
            val icon = ImageView(context).apply {
                val size = dp(56)
                layoutParams = LinearLayout.LayoutParams(size, size).apply { marginEnd = dp(16) }
                setImageDrawable(AppCatalog.icon(context, packageName))
            }
            val label = TextView(context).apply {
                text = AppCatalog.label(context, packageName)
                setTextColor(getColor(R.color.text))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            addView(icon)
            addView(label)
            setOnClickListener {
                (application as KioskApp).autoLaunchConsumed = true
                if (!AppCatalog.launch(this@HomeActivity, packageName, store.chromeStartUrl)) {
                    toast(getString(R.string.app_not_installed))
                }
            }
        }
    }

    private fun watchStaffGesture(view: View) {
        view.isClickable = true
        view.minimumHeight = dp(72)
        view.setOnTouchListener { target, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    target.parent?.requestDisallowInterceptTouchEvent(true)
                    target.removeCallbacks(holdGate)
                    target.postDelayed(holdGate, Limits.HOLD_MS)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    target.removeCallbacks(holdGate)
                    target.parent?.requestDisallowInterceptTouchEvent(false)
                    onTitleTap()
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    target.removeCallbacks(holdGate)
                    target.parent?.requestDisallowInterceptTouchEvent(false)
                    true
                }
                else -> false
            }
        }
    }

    private fun onTitleTap() {
        if (gateOpen) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastTap > Limits.TAP_GAP_MS) taps = 0
        lastTap = now
        taps += 1
        if (taps < Limits.TAP_COUNT) return
        openStaffGate()
    }

    private fun openStaffGate() {
        if (gateOpen || isFinishing) return
        gateOpen = true
        taps = 0
        Dialogs.askPin(this, store) {
            startActivity(android.content.Intent(this, AdminActivity::class.java))
        }
        subtitleView.postDelayed({ gateOpen = false }, 1_500)
    }

    private fun enterLockTask() {
        val manager = getSystemService(ActivityManager::class.java)
        val shouldLock = policy.isDeviceOwner() && store.kioskEnabled && !store.maintenanceMode
        try {
            if (shouldLock) {
                if (manager.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_NONE) startLockTask()
            } else if (manager.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE) {
                stopLockTask()
            }
        } catch (error: RuntimeException) {
            Log.w(TAG, "lock task", error)
        }
    }

    private fun syncThenMaybeLaunch() {
        val app = application as KioskApp
        val hasConfig = store.configUrl.isNotBlank()
        if (hasConfig && !app.syncGatePassed) {
            FleetSync.start(this) {
                app.syncGatePassed = true
                if (isDestroyed) return@start
                render()
                enterLockTask()
                maybeAutoLaunch()
            }
            return
        }
        if (hasConfig && SystemClock.elapsedRealtime() - app.lastSyncElapsed > Limits.SYNC_INTERVAL_MS) {
            FleetSync.start(this) {
                if (!isDestroyed) render()
            }
        }
        maybeAutoLaunch()
    }

    private fun maybeAutoLaunch() {
        val app = application as KioskApp
        if (app.autoLaunchConsumed || !store.kioskEnabled || store.maintenanceMode) return
        val packageName = store.autoLaunchPackage
        if (packageName.isBlank() || packageName !in store.allowedPackages) return
        app.autoLaunchConsumed = true
        AppCatalog.launch(this, packageName, store.chromeStartUrl)
    }

    private companion object {
        const val TAG = "ShowroomKiosk"
    }
}
