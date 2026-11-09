package com.showroom.kiosk

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

class AdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        PolicyApplier(context).apply(KioskStore(context))
    }

    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        val store = KioskStore(context)
        ProvisioningExtras.apply(store, ProvisioningExtras.from(intent))
        PolicyApplier(context).apply(store)
        context.startActivity(
            Intent(context, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            fullscreenOptions(),
        )
    }
}
