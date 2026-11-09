package com.showroom.kiosk

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log

class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
        val packageName = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME)
            ?: intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
        val line = "$packageName $status $message".trim()
        KioskStore(context).lastInstallMessage = line
        Log.i(TAG, line)
    }

    companion object {
        const val EXTRA_PACKAGE = "com.showroom.kiosk.extra.PACKAGE"
        private const val TAG = "ShowroomKiosk"
    }
}
