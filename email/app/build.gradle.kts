import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Firebase (notifications) is switched on by dropping the project's
// google-services.json next to this file. Without it the app builds and runs,
// just without notifications.
if (file("google-services.json").exists()) apply(plugin = "com.google.gms.google-services")

// Signing details live in email/keystore.properties (not committed), with
// storeFile relative to email/. Without it the release build is left unsigned.
val signingDir = file("..")
val keystoreProps = Properties().apply {
    val f = signingDir.resolve("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

// The server the app talks to; -PinboxUrl=http://10.0.2.2:5173 points a
// build at a local `npm run dev` from the emulator.
val inboxUrl = (findProperty("inboxUrl") as String?) ?: "https://mail.doughmination.gay"

android {
    namespace = "gay.doughmination.mail"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "gay.doughmination.mail"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        // CI sets these from appVersion in gradle.properties (docs/releasing.md);
        // local builds use the fallbacks.
        versionCode = (System.getenv("VERSION_CODE") ?: "1").toInt()
        versionName = System.getenv("VERSION_NAME") ?: "${project.property("appVersion")}.0"
        buildConfigField("String", "INBOX_URL", "\"$inboxUrl\"")
    }

    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = signingDir.resolve(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.browser)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.kotlinx.coroutines.play.services)

    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
}
