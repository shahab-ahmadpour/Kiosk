package com.showroom.kiosk

import android.app.Activity
import android.content.Intent
import android.os.Bundle

class PolicyComplianceActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = KioskStore(this)
        ProvisioningExtras.apply(store, ProvisioningExtras.from(intent))
        PolicyApplier(this).apply(store)
        startActivity(Intent(this, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        setResult(RESULT_OK)
        finish()
    }
}
