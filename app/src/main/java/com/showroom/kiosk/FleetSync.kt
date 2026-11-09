package com.showroom.kiosk

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.SystemClock
import com.showroom.kiosk.core.FleetConfig

object FleetSync {
    fun start(context: Context, onDone: (String) -> Unit) {
        val app = context.applicationContext as KioskApp
        val store = KioskStore(app)
        if (store.configUrl.isBlank()) {
            val message = app.getString(R.string.sync_skipped)
            store.lastConfigMessage = message
            app.main.post { onDone(message) }
            return
        }
        if (app.syncRunning) {
            app.main.post { onDone(store.lastConfigMessage) }
            return
        }
        app.syncRunning = true
        app.lastSyncElapsed = SystemClock.elapsedRealtime()
        app.io.execute {
            val message = try {
                pull(app, store)
            } catch (error: Exception) {
                app.getString(R.string.sync_failed, error.message ?: error.javaClass.simpleName)
            }
            store.lastConfigMessage = message
            app.syncRunning = false
            app.main.post { onDone(message) }
        }
    }

    private fun pull(context: Context, store: KioskStore): String {
        val body = HttpsFiles.getText(store.configUrl, store.configToken, 65_536)
        val config = FleetConfig.parse(body, context.packageName)
        val reboot = store.applyRemote(config)
        PolicyApplier(context).apply(store)
        val installing = UpdateInstaller.installNeeded(context, config, store.configUrl, store.configToken)
        if (reboot && !installing && PolicyApplier(context).isDeviceOwner()) {
            val dpm = context.getSystemService(DevicePolicyManager::class.java)
            dpm.reboot(ComponentName(context, AdminReceiver::class.java))
        }
        return context.getString(R.string.sync_ok)
    }
}
