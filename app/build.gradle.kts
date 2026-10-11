import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val keystoreProperties = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.advanceai.calculator"
    compileSdk = 34

    // "Continue with Google" only works for a build whose signing certificate is
    // registered in the Google Cloud console, so the project signs every build
    // with the same committed key. Its SHA-1 is in app/signing/fingerprints.txt.
    val bundledKeystore = rootProject.file("app/signing/mcalculator.p12")
    val telemetryUrl: String = (findProperty("telemetryUrl") as String?) ?: ""
    val geminiKey: String = (findProperty("geminiApiKey") as String?) ?: ""
    val geminiModelName: String = (findProperty("geminiModel") as String?) ?: ""

    defaultConfig {
        applicationId = "com.advanceai.calculator"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
        resourceConfigurations += listOf("en")
        vectorDrawables { useSupportLibrary = true }
        // Where the anonymous usage counts go (the Cloudflare Worker in
        // admin/worker). Empty = the counters are never sent anywhere.
        buildConfigField("String", "TELEMETRY_URL", "\"$telemetryUrl\"")
        // A Google AI Studio key turns Ask AI into the native Gemini screen.
        buildConfigField("String", "GEMINI_API_KEY", "\"$geminiKey\"")
        // Defaults to Gemini 3.6 Flash when the build does not name a model.
        buildConfigField(
            "String",
            "GEMINI_MODEL",
            "\"" + geminiModelName.ifBlank { "gemini-3.6-flash" } + "\""
        )
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
            // No applicationIdSuffix: the package name must match the OAuth
            // client registered for Google Sign-In.
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

    kotlinOptions {
        jvmTarget = "17"
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

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
}
