import java.io.File

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Release signing config is read from ~/.gradle/gradle.properties or env vars.
// All four properties must be set to enable release signing; otherwise the
// release build falls back to debug signing (so local builds still work)
// and a warning prints. Play Console rejects debug-signed AABs.
fun releaseSigningSource(name: String): String? =
    (project.findProperty(name) as String?)
        ?: System.getenv(name)
val releaseKeystorePath = releaseSigningSource("SLEEPSOUND_KEYSTORE_PATH")
val releaseKeystorePassword = releaseSigningSource("SLEEPSOUND_KEYSTORE_PASSWORD")
val releaseKeyAlias = releaseSigningSource("SLEEPSOUND_KEY_ALIAS")
val releaseKeyPassword = releaseSigningSource("SLEEPSOUND_KEY_PASSWORD")
val hasReleaseSigning = listOf(
    releaseKeystorePath,
    releaseKeystorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { !it.isNullOrBlank() } &&
    releaseKeystorePath?.let { File(it).exists() } == true

android {
    namespace = "com.sleepsound"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.drediger.sleepsoundly"
        minSdk = 29
        targetSdk = 35
        versionCode = 3
        versionName = "1.0.0-rc1"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseKeystorePath!!)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                logger.warn(
                    "SleepSound: release signing properties not set; " +
                        "falling back to debug signing. Play Console will reject this AAB. " +
                        "See BUILDING.md §4.",
                )
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    androidResources {
        // Keep audio sample assets uncompressed so MediaExtractor can openFd()
        noCompress += listOf("ogg", "opus", "wav", "mp3", "flac")
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.billing.ktx)
    implementation(libs.androidx.media)
    debugImplementation(libs.androidx.ui.tooling)
    testImplementation(libs.junit)
}
