import java.security.SecureRandom
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val keystoreProperties = loadOrCreateKeystore()

android {
    namespace = "com.showroom.kiosk"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.showroom.kiosk"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "1.1.2"
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
            storePassword = keystoreProperties.getProperty("storePassword")
            keyAlias = keystoreProperties.getProperty("keyAlias")
            keyPassword = keystoreProperties.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    lint {
        abortOnError = true
    }
}

dependencies {
    implementation(project(":core"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
}

fun loadOrCreateKeystore(): Properties {
    val propsFile = rootProject.file("keystore.properties")
    if (propsFile.exists()) {
        val existing = Properties().apply { propsFile.inputStream().use { load(it) } }
        val configured = rootProject.file(existing.getProperty("storeFile"))
        if (!configured.exists()) {
            error(
                "keystore.properties points at ${configured.path} but that file is missing. " +
                    "Restore the backup. A new key cannot update tablets that are already enrolled.",
            )
        }
        return existing
    }

    val store = rootProject.file("keystore/showroom-kiosk.jks")
    if (store.exists()) {
        error("Found ${store.path} without keystore.properties. Refusing to overwrite it.")
    }
    store.parentFile.mkdirs()

    val alphabet = "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    val random = SecureRandom()
    fun secret(length: Int) = buildString(length) {
        repeat(length) { append(alphabet[random.nextInt(alphabet.length)]) }
    }
    val storePass = secret(24)
    val keytoolHome = File(System.getProperty("java.home"), "bin/keytool")
    val keytool = if (keytoolHome.exists()) keytoolHome.absolutePath else "keytool"
    exec {
        commandLine(
            keytool,
            "-genkeypair",
            "-alias", "showroom",
            "-keyalg", "RSA",
            "-keysize", "2048",
            "-validity", "10000",
            "-keystore", store.absolutePath,
            "-storepass", storePass,
            "-keypass", storePass,
            "-dname", "CN=Showroom Kiosk, O=Showroom, C=IR",
        )
    }
    val created = Properties()
    created.setProperty("storeFile", "keystore/showroom-kiosk.jks")
    created.setProperty("storePassword", storePass)
    created.setProperty("keyAlias", "showroom")
    created.setProperty("keyPassword", storePass)
    propsFile.outputStream().use { created.store(it, "Showroom Kiosk release key. Keep this private.") }
    logger.lifecycle(
        "Created ${store.absolutePath}. Back up this file and keystore.properties outside the repo. " +
            "Losing them means every enrolled tablet must be factory reset before the app can be updated.",
    )
    return created
}
