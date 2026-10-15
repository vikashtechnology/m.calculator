import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// AdMob test units published by Google. They always return a test ad, so they
// are safe to build and tap on, but they must be replaced with the publisher's
// own app ID and banner unit ID before the app goes to Google Play:
//   gradle.properties  ->  admobAppId=…  admobBannerUnitId=…
// or one build:  ./gradlew assembleRelease -PadmobAppId=… -PadmobBannerUnitId=…
val testAdmobAppId = "ca-app-pub-3940256099942544~3347511713"
val testAdmobBannerUnitId = "ca-app-pub-3940256099942544/9214589741"

val keystoreProperties = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.advanceai.calculator"
    // Google Play requires new apps and updates to target API 36 (Android 16)
    // since 31 August 2026. compileSdk follows targetSdk so the APIs the app is
    // written against are the ones it declares.
    compileSdk = 36

    // Every build is signed with the committed key so its fingerprint stays
    // stable (Google Sign-In registration and Play upload both care about that).
    // Its SHA-1 is in app/signing/fingerprints.txt.
    val bundledKeystore = rootProject.file("app/signing/mcalculator.p12")
    val telemetryUrl: String = (findProperty("telemetryUrl") as String?) ?: ""
    // AdMob: the publisher's own IDs when gradle.properties carries them,
    // Google's test IDs otherwise.
    val admobAppId: String = (findProperty("admobAppId") as String?) ?: testAdmobAppId
    val admobBannerUnitId: String =
        (findProperty("admobBannerUnitId") as String?) ?: testAdmobBannerUnitId

    defaultConfig {
        applicationId = "com.advanceai.calculator"
        minSdk = 24
        targetSdk = 36
        // Play needs a versionCode that goes *up* with every upload. CI passes
        // -PversionCode=… so a release can bump it without editing files.
        versionCode = (findProperty("versionCode") as String?)?.toIntOrNull() ?: 1
        versionName = (findProperty("versionName") as String?) ?: "1.1.0"
        resourceConfigurations += listOf("en")
        vectorDrawables { useSupportLibrary = true }
        // UMP (the consent SDK) still reads the app ID from the manifest even
        // though the ads SDK itself is given it programmatically; both come
        // from this one value so they cannot drift apart.
        manifestPlaceholders["admobAppId"] = admobAppId
        // Where the anonymous usage counts go (the Cloudflare Worker in
        // admin/worker). Empty = the counters are never sent anywhere.
        buildConfigField("String", "TELEMETRY_URL", "\"$telemetryUrl\"")
        // AdMob IDs the ads code reads at runtime.
        buildConfigField("String", "ADMOB_APP_ID", "\"$admobAppId\"")
        buildConfigField("String", "ADMOB_BANNER_UNIT_ID", "\"$admobBannerUnitId\"")
    }

    signingConfigs {
        create("bundled") {
            storeFile = bundledKeystore
            storePassword = "mcalculator"
            keyAlias = "mcalculator"
            keyPassword = "mcalculator"
        }
        if (keystoreProperties.getProperty("storeFile") != null) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // No applicationIdSuffix: debug and release share one package name,
            // so a debug build installs straight over the released app.
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("bundled")
        }
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Uses the committed key so the fingerprint stays stable; provide
            // keystore.properties to sign with your own release key instead.
            signingConfig = if (keystoreProperties.getProperty("storeFile") != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("bundled")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // java.time (used by the age calculator) on Android 7 and below.
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

// The kotlinOptions block this replaced was removed in Kotlin 2.4.
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.2")
    // Backport of java.time for minSdk 24.
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // AdMob. The Next-Gen SDK is the one Google recommends for new work; the
    // older play-services-ads line is in maintenance mode and retires in 2028.
    // The banner lives in a Compose AndroidView, so no extra UI dependency.
    implementation("com.google.android.libraries.ads.mobile.sdk:ads-mobile-sdk:1.5.0")
    // User Messaging Platform: the consent form shown before any ad is loaded
    // in the EEA, the UK and Switzerland.
    implementation("com.google.android.ump:user-messaging-platform:4.0.0")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
}
