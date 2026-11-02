plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Where the Manager reads what's available: the manifest CI attaches to the
// rolling `latest` release (docs/manifest.md). -PmanifestUrl=https://... points
// a build elsewhere, e.g. for testing a manifest by hand.
val manifestUrl = (findProperty("manifestUrl") as String?)
    ?: "https://github.com/doughmination/apps/releases/latest/download/manifest.json"

android {
    namespace = "gay.doughmination.manager"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        // Released: this ID (and the signing key) must never change, or the
        // Manager can't update itself over what people have.
        applicationId = "gay.doughmination.manager"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        // CI sets these from appVersion in gradle.properties (docs/releasing.md);
        // local builds use the fallbacks.
        versionCode = System.getenv("VERSION_CODE")?.toInt() ?: 1
        versionName = System.getenv("VERSION_NAME") ?: "${project.property("appVersion")}.0"
        buildConfigField("String", "MANIFEST_URL", "\"$manifestUrl\"")
    }

    // Same convention as Device Reporter: CI passes the key through SIGNING_*
    // environment variables; without them release builds use the debug key.
    val storeFilePath = System.getenv("SIGNING_STORE_FILE")
    signingConfigs {
        if (storeFilePath != null) {
            create("release") {
                storeFile = file(storeFilePath)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
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
    implementation(project(":design"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)

    testImplementation(libs.junit)
}
