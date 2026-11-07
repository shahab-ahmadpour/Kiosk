package com.showroom.kiosk

import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import com.showroom.kiosk.core.FleetConfig
import com.showroom.kiosk.core.RemoteApk
import java.io.File
import java.net.URL
import java.security.MessageDigest

object UpdateInstaller {
    private const val TAG = "ShowroomKiosk"

    fun installNeeded(
        context: android.content.Context,
        config: FleetConfig,
        configUrl: String,
        token: String,
    ): Boolean {
        val jobs = mutableListOf<RemoteApk>()
        val selfUrl = config.apkUrl
        val selfHash = config.apkSha256
        val selfVersion = config.versionCode
        if (!selfUrl.isNullOrEmpty() && !selfHash.isNullOrEmpty() && selfVersion != null) {
            if (selfVersion > AppCatalog.versionCode(context, context.packageName)) {
                jobs += RemoteApk(context.packageName, selfUrl, selfHash, selfVersion)
            }
        }
        for (apk in config.extraApks.orEmpty()) {
            if (apk.versionCode > AppCatalog.versionCode(context, apk.packageName)) {
                jobs += apk
            }
        }
        if (jobs.isEmpty()) return false
        val app = context.applicationContext as KioskApp
        if (app.installInProgress) return true
        app.installInProgress = true
        try {
            val ordered = jobs.partition { it.packageName != context.packageName }
            for (job in ordered.first + ordered.second) {
                installOne(context, job, tokenFor(configUrl, job.url, token))
            }
        } finally {
            app.installInProgress = false
        }
        return true
    }

    private fun tokenFor(configUrl: String, apkUrl: String, token: String): String {
        return try {
            if (token.isNotBlank() && URL(configUrl).host.equals(URL(apkUrl).host, ignoreCase = true)) {
                token
            } else {
                ""
            }
        } catch (_: Exception) {
            ""
        }
    }

    private fun installOne(context: android.content.Context, job: RemoteApk, token: String) {
        val file = File(context.cacheDir, "update-${job.packageName}.apk")
        HttpsFiles.download(job.url, token, file, Limits.MAX_DOWNLOAD_BYTES)
        val actual = sha256(file)
        if (!actual.equals(job.sha256, ignoreCase = false)) {
            file.delete()
            throw com.showroom.kiosk.core.ConfigException("sha256 mismatch for ${job.packageName}")
        }
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(job.packageName)
            if (Build.VERSION.SDK_INT >= 31) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        val session = installer.openSession(sessionId)
        try {
            session.openWrite("base.apk", 0, file.length()).use { output ->
                file.inputStream().use { it.copyTo(output) }
                session.fsync(output)
            }
            var flags = PendingIntent.FLAG_UPDATE_CURRENT
            if (Build.VERSION.SDK_INT >= 31) flags = flags or PendingIntent.FLAG_MUTABLE
            val callback = Intent(context, InstallResultReceiver::class.java)
                .putExtra(InstallResultReceiver.EXTRA_PACKAGE, job.packageName)
            val pending = PendingIntent.getBroadcast(context, sessionId, callback, flags)
            session.commit(pending.intentSender)
            Log.i(TAG, "committed install ${job.packageName}")
        } finally {
            session.close()
            file.delete()
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
