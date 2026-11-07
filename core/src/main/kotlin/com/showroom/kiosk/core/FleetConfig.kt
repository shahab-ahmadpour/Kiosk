package com.showroom.kiosk.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ConfigException(message: String) : Exception(message)

@Serializable
data class RemoteApk(
    val packageName: String,
    val url: String,
    val sha256: String,
    val versionCode: Int,
)

@Serializable
data class FleetConfig(
    val allowedPackages: List<String>? = null,
    val chromeStartUrl: String? = null,
    val chromeUrlAllowlist: List<String>? = null,
    val autoLaunchPackage: String? = null,
    val samsungHardening: Boolean? = null,
    val showroomTitle: String? = null,
    val kioskEnabled: Boolean? = null,
    val chromeSignInAllowed: Boolean? = null,
    val brightness: Int? = null,
    val configRevision: Int? = null,
    val reboot: Boolean? = null,
    val versionCode: Int? = null,
    val apkUrl: String? = null,
    val apkSha256: String? = null,
    val extraApks: List<RemoteApk>? = null,
) {
    fun validate(selfPackage: String) {
        allowedPackages?.let { PackageRules.checkAll(it, selfPackage) }
        chromeStartUrl?.let { url ->
            if (url.isNotEmpty() && !UrlRules.isHttpUrl(url)) {
                throw ConfigException("chromeStartUrl must be an http(s) URL")
            }
        }
        chromeUrlAllowlist?.let { patterns ->
            if (patterns.size > PackageRules.MAX_ALLOWLIST) {
                throw ConfigException("chromeUrlAllowlist has more than ${PackageRules.MAX_ALLOWLIST} entries")
            }
            patterns.forEach { UrlRules.checkChromePattern(it) }
        }
        autoLaunchPackage?.let { pkg ->
            if (pkg.isNotEmpty()) {
                PackageRules.checkName(pkg, selfPackage)
                val allowed = allowedPackages
                if (allowed != null && pkg !in allowed) {
                    throw ConfigException("autoLaunchPackage is not in allowedPackages")
                }
            }
        }
        showroomTitle?.let { title ->
            if (title.isBlank() || title.length > 40 || title.any { it == '\n' || it == '\r' }) {
                throw ConfigException("showroomTitle must be 1..40 characters on one line")
            }
        }
        brightness?.let { value ->
            if (value !in 10..255) {
                throw ConfigException("brightness must be from 10 to 255")
            }
        }
        configRevision?.let { revision ->
            if (revision < 0) throw ConfigException("configRevision must be >= 0")
        }
        val publishesApk = !apkUrl.isNullOrEmpty() || !apkSha256.isNullOrEmpty() || versionCode != null
        if (publishesApk) {
            val code = versionCode ?: throw ConfigException("versionCode is required when publishing an apk")
            if (code < 1) throw ConfigException("versionCode must be >= 1")
            val url = apkUrl ?: throw ConfigException("apkUrl is required when publishing an apk")
            if (!UrlRules.isHttpsUrl(url)) throw ConfigException("apkUrl must be https")
            val hash = apkSha256 ?: throw ConfigException("apkSha256 is required when publishing an apk")
            UrlRules.checkSha256(hash)
        }
        extraApks?.let { apks ->
            if (apks.size > PackageRules.MAX_APPS) {
                throw ConfigException("extraApks supports at most ${PackageRules.MAX_APPS} packages")
            }
            apks.forEach { apk ->
                PackageRules.checkName(apk.packageName, selfPackage)
                if (!UrlRules.isHttpsUrl(apk.url)) throw ConfigException("extra apk url must be https")
                UrlRules.checkSha256(apk.sha256)
                if (apk.versionCode < 1) throw ConfigException("extra apk versionCode must be >= 1")
            }
        }
    }

    fun toPrettyJson(): String = exportJson.encodeToString(this)

    companion object {
        private val parser = Json {
            ignoreUnknownKeys = true
        }
        private val exportJson = Json {
            prettyPrint = true
            explicitNulls = false
            encodeDefaults = true
        }

        fun parse(raw: String, selfPackage: String): FleetConfig {
            val config = try {
                parser.decodeFromString<FleetConfig>(raw)
            } catch (error: Exception) {
                throw ConfigException("JSON: ${error.message}")
            }
            config.validate(selfPackage)
            return config
        }
    }
}
