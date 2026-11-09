package com.showroom.kiosk.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FleetConfigTest {
    private val self = "com.showroom.kiosk"
    private val json = Json

    @Test
    fun parsesAShowroomFile() {
        val raw = """
            {
              "showroomTitle": "شوروم خودرو",
              "allowedPackages": ["com.android.chrome", "com.example.catalog"],
              "chromeStartUrl": "https://dealer.example/cars",
              "chromeUrlAllowlist": ["dealer.example", "maps.google.com"],
              "autoLaunchPackage": "com.android.chrome",
              "samsungHardening": true,
              "chromeSignInAllowed": false,
              "brightness": 180
            }
        """.trimIndent()
        val config = FleetConfig.parse(raw, self)
        assertEquals(listOf("com.android.chrome", "com.example.catalog"), config.allowedPackages)
        assertEquals("شوروم خودرو", config.showroomTitle)
        assertEquals(180, config.brightness)
    }

    @Test
    fun exportOmitsSecretsAndApkFields() {
        val text = FleetConfig(
            allowedPackages = listOf(ChromePolicies.PACKAGE),
            showroomTitle = "شوروم",
            chromeSignInAllowed = false,
        ).toPrettyJson()
        assertTrue(text.contains("شوروم"))
        assertTrue(text.contains(ChromePolicies.PACKAGE))
        assertFalse(text.contains("apkUrl"))
        assertFalse(text.contains("apkSha256"))
    }

    @Test
    fun rejectsSettingsPlayStoreAndAFourthApp() {
        assertRejected("""{"allowedPackages":["com.android.settings"]}""")
        assertRejected("""{"allowedPackages":["com.android.vending"]}""")
        assertRejected(
            """{"allowedPackages":["com.android.chrome","a.b","c.d","e.f"]}""",
        )
        assertRejected("""{"allowedPackages":["$self"]}""")
    }

    @Test
    fun rejectsOpenEndedOrUnsafeUrls() {
        assertRejected("""{"chromeUrlAllowlist":["*"]}""")
        assertRejected("""{"chromeStartUrl":"javascript:alert(1)"}""")
        assertRejected(
            """{"versionCode":2,"apkUrl":"http://dealer.example/app.apk","apkSha256":"${"ab".repeat(32)}"}""",
        )
    }

    @Test
    fun chromeAllowlistIsAJsonStringAndIncognitoIsDisabled() {
        val policies = ChromePolicies.restrictions(
            startUrl = "https://dealer.example/cars",
            allowlist = listOf("dealer.example"),
            signInAllowed = false,
        )
        assertEquals(1, policies["IncognitoModeAvailability"])
        assertEquals(0, policies["BrowserSignin"])
        assertTrue(policies["URLAllowlist"] is String)
        val allow = json.parseToJsonElement(policies["URLAllowlist"] as String).jsonArray
        assertEquals("dealer.example", allow[0].toString().trim('"'))
        val startup = json.parseToJsonElement(policies["RestoreOnStartupURLs"] as String) as JsonArray
        assertEquals("https://dealer.example/cars", startup[0].toString().trim('"'))
    }

    @Test
    fun emptyAllowlistDoesNotBlockTheWeb() {
        val policies = ChromePolicies.restrictions(
            startUrl = null,
            allowlist = emptyList(),
            signInAllowed = true,
        )
        assertFalse(policies.containsKey("URLBlocklist"))
        assertFalse(policies.containsKey("BrowserSignin"))
    }

    private fun assertRejected(raw: String) {
        try {
            FleetConfig.parse(raw, self)
            throw AssertionError("expected rejection for $raw")
        } catch (_: ConfigException) {
            // expected
        }
    }
}
