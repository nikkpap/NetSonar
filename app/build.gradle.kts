import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// ---- Release signing (same key every time = OTA updates install over the old app) ----
// Local:  put keystore.properties in the project root (see README).
// GitHub: secrets KEYSTORE_BASE64 / KEYSTORE_PASSWORD / KEY_ALIAS / KEY_PASSWORD.
val ksProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun ks(key: String, env: String): String? =
    (ksProps.getProperty(key) ?: System.getenv(env))?.takeIf { it.isNotBlank() }

android {
    namespace = "gr.nikkpap.netsonar"
    compileSdk = 35

    defaultConfig {
        applicationId = "gr.nikkpap.netsonar"
        minSdk = 24
        targetSdk = 29 // 29 = Android lets the app read the neighbour (ARP) table → all devices + MACs
        versionCode = 7
        versionName = "1.0.6"
    }

    signingConfigs {
        // Shared debug key (not secret) → every debug APK installs over the previous one.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        val store = ks("storeFile", "NS_STORE_FILE")
        if (store != null) {
            create("release") {
                storeFile = rootProject.file(store)
                storePassword = ks("storePassword", "NS_STORE_PASSWORD")
                keyAlias = ks("keyAlias", "NS_KEY_ALIAS")
                keyPassword = ks("keyPassword", "NS_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // APK file name carries the version: NetSonar_v1.0.0.apk
    applicationVariants.all {
        val vName = versionName
        outputs.all {
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName =
                "NetSonar_v$vName.apk"
        }
    }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(bom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
