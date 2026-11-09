package com.showroom.kiosk

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_LOCKED_BOOT_COMPLETED) return
        val store = KioskStore(context)
        if (store.maintenanceMode) store.maintenanceMode = false
        PolicyApplier(context).apply(store)
        try {
            context.startActivity(
                Intent(context, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                fullscreenOptions(),
            )
        } catch (error: Exception) {
            Log.w(TAG, "boot start", error)
        }
    }

    private companion object {
        const val TAG = "ShowroomKiosk"
    }
}
