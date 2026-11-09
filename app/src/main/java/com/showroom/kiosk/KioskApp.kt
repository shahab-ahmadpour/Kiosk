package com.showroom.kiosk

import android.app.Application
import android.content.Intent
import android.os.Handler
import android.os.Looper
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class KioskApp : Application() {
    lateinit var main: Handler
        private set

    val io: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "kiosk-io").apply { isDaemon = true }
    }

    @Volatile var autoLaunchConsumed: Boolean = false
    @Volatile var syncRunning: Boolean = false
    @Volatile var syncGatePassed: Boolean = false
    @Volatile var installInProgress: Boolean = false
    @Volatile var lastSyncElapsed: Long = 0L
    @Volatile var fullscreenRelaunchTried: Boolean = false

    private val relock = Runnable { forceEndMaintenance() }

    override fun onCreate() {
        super.onCreate()
        main = Handler(Looper.getMainLooper())
        val store = KioskStore(this)
        if (store.maintenanceMode && store.kioskEnabled) {
            scheduleMaintenance(store.maintenanceUntil)
        }
    }

    fun scheduleMaintenance(deadlineEpochMs: Long) {
        main.removeCallbacks(relock)
        val delay = deadlineEpochMs - System.currentTimeMillis()
        if (delay <= 0L) {
            val store = KioskStore(this)
            if (store.maintenanceMode && store.kioskEnabled) {
                store.maintenanceMode = false
                PolicyApplier(this).apply(store)
            }
            return
        }
        main.postDelayed(relock, delay)
    }

    fun forceEndMaintenance() {
        val store = KioskStore(this)
        if (!store.maintenanceMode || !store.kioskEnabled) return
        store.maintenanceMode = false
        PolicyApplier(this).apply(store)
        startActivity(
            Intent(this, HomeActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            fullscreenOptions(),
        )
    }
}
