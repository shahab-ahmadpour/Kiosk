package com.showroom.kiosk.core

object PackageRules {
    const val MAX_APPS = 3
    const val MAX_ALLOWLIST = 30

    private val blockedPrefixes = listOf(
        "com.android.settings",
        "com.android.systemui",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.samsung.android.packageinstaller",
        "com.android.shell",
        "com.android.vpndialogs",
        "com.google.android.permissioncontroller",
        "com.android.vending",
        "com.samsung.android.lool",
    )

    private val nameRegex = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$")

    fun checkName(packageName: String, selfPackage: String) {
        if (packageName == selfPackage) {
            throw ConfigException("the kiosk package cannot be listed as an allowed app")
        }
        if (packageName.length !in 3..120 || !nameRegex.matches(packageName)) {
            throw ConfigException("invalid package name: $packageName")
        }
        val blocked = blockedPrefixes.any { prefix ->
            packageName == prefix || packageName.startsWith("$prefix.")
        }
        if (blocked) {
            throw ConfigException("package is blocked: $packageName")
        }
    }

    fun checkAll(packages: List<String>, selfPackage: String) {
        if (packages.isEmpty()) {
            throw ConfigException("allowedPackages must not be empty")
        }
        if (packages.size > MAX_APPS) {
            throw ConfigException("at most $MAX_APPS apps can be allowed")
        }
        if (packages.distinct().size != packages.size) {
            throw ConfigException("duplicate package in allowedPackages")
        }
        packages.forEach { checkName(it, selfPackage) }
    }
}

object UrlRules {
    private val sha256 = Regex("^[a-f0-9]{64}$")

    fun isHttpUrl(value: String): Boolean {
        if (value.length !in 8..500) return false
        if (value.any { it.isISOControl() || it == ' ' }) return false
        val uri = try {
            java.net.URI(value)
        } catch (_: Exception) {
            return false
        }
        if (uri.scheme != "http" && uri.scheme != "https") return false
        return !uri.host.isNullOrBlank()
    }

    fun isHttpsUrl(value: String): Boolean = isHttpUrl(value) && value.startsWith("https://")

    fun checkChromePattern(pattern: String) {
        if (pattern.isBlank() || pattern.length > 200 || pattern == "*") {
            throw ConfigException(
                "invalid Chrome URL pattern. Leave the list empty to allow every site; do not use *",
            )
        }
        val illegal = pattern.any { ch ->
            ch.isISOControl() || ch == '"' || ch == '\\' || ch == ' ' || ch == '<' || ch == '>'
        }
        if (illegal) {
            throw ConfigException("invalid Chrome URL pattern: $pattern")
        }
    }

    fun checkSha256(value: String) {
        if (!sha256.matches(value)) {
            throw ConfigException("sha256 must be 64 lowercase hex characters")
        }
    }
}
