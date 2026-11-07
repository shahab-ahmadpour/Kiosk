package com.showroom.kiosk

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.util.Log
import com.showroom.kiosk.core.ChromePolicies
import com.showroom.kiosk.core.ConfigException
import com.showroom.kiosk.core.PackageRules
import com.showroom.kiosk.core.UrlRules

data class ListedApp(
    val packageName: String,
    val label: String,
    val icon: Drawable,
)

object AppCatalog {
    private const val TAG = "ShowroomKiosk"

    fun isInstalled(context: Context, packageName: String): Boolean {
        return versionCode(context, packageName) >= 0L
    }

    fun versionCode(context: Context, packageName: String): Long {
        return try {
            val info = context.packageManager.getPackageInfo(packageName, 0)
            if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
        } catch (_: PackageManager.NameNotFoundException) {
            -1L
        }
    }

    fun label(context: Context, packageName: String): String {
        return try {
            val info = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(info).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            packageName
        }
    }

    fun icon(context: Context, packageName: String): Drawable? {
        return try {
            context.packageManager.getApplicationIcon(packageName)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    fun launchable(context: Context): List<ListedApp> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val infos = context.packageManager.queryIntentActivities(intent, 0)
        return infos.mapNotNull { info ->
            val packageName = info.activityInfo.packageName
            try {
                PackageRules.checkName(packageName, context.packageName)
            } catch (_: ConfigException) {
                return@mapNotNull null
            }
            ListedApp(
                packageName = packageName,
                label = info.loadLabel(context.packageManager).toString(),
                icon = info.loadIcon(context.packageManager),
            )
        }.distinctBy { it.packageName }
            .sortedWith(compareBy<ListedApp> { it.packageName != ChromePolicies.PACKAGE }.thenBy { it.label.lowercase() })
    }

    fun isChrome(packageName: String): Boolean {
        return packageName == ChromePolicies.PACKAGE || packageName.startsWith("com.chrome.")
    }

    fun launch(context: Context, packageName: String, chromeStartUrl: String): Boolean {
        return try {
            val intent = if (isChrome(packageName) && UrlRules.isHttpUrl(chromeStartUrl)) {
                Intent(Intent.ACTION_VIEW, Uri.parse(chromeStartUrl)).setPackage(packageName)
            } else {
                context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (error: ActivityNotFoundException) {
            Log.w(TAG, "launch $packageName", error)
            false
        } catch (error: SecurityException) {
            Log.w(TAG, "launch $packageName", error)
            false
        }
    }
}
