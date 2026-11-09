package com.showroom.kiosk.core

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object ChromePolicies {
    const val PACKAGE = "com.android.chrome"

    private val json = Json

    fun restrictions(
        startUrl: String?,
        allowlist: List<String>,
        signInAllowed: Boolean,
    ): Map<String, Any> {
        val out = linkedMapOf<String, Any>()
        out["IncognitoModeAvailability"] = 1
        out["PasswordManagerEnabled"] = false
        out["ContextualSearchEnabled"] = false
        out["EditBookmarksEnabled"] = false
        if (!signInAllowed) {
            out["BrowserSignin"] = 0
        }
        val url = startUrl?.trim().orEmpty()
        if (url.isNotEmpty()) {
            out["HomepageLocation"] = url
            out["HomepageIsNewTabPage"] = false
            out["ShowHomeButton"] = true
            out["RestoreOnStartup"] = 4
            out["RestoreOnStartupURLs"] = json.encodeToString(listOf(url))
        }
        if (allowlist.isNotEmpty()) {
            val encoded = json.encodeToString(allowlist)
            val blockAll = json.encodeToString(listOf("*"))
            out["URLAllowlist"] = encoded
            out["URLWhitelist"] = encoded
            out["URLBlocklist"] = blockAll
            out["URLBlacklist"] = blockAll
            out["IncognitoModeUrlAllowlist"] = encoded
            out["IncognitoModeUrlBlocklist"] = blockAll
        }
        return out
    }
}
