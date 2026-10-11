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
    val webClientId: String = (findProperty("googleWebClientId") as String?) ?: ""
    val telemetryUrl: String = (findProperty("telemetryUrl") as String?) ?: ""
    val geminiKey: String = (findProperty("geminiApiKey") as String?) ?: ""
    val geminiModelName: String = (findProperty("geminiModel") as String?) ?: ""
    val aiProxy: String = (findProperty("aiProxyUrl") as String?) ?: ""

    defaultConfig {
        applicationId = "com.advanceai.calculator"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
        resourceConfigurations += listOf("en")
        vectorDrawables { useSupportLibrary = true }
        // Empty means Google Sign-In is hidden in the app; the Ask AI screen
        // then uses Puter's own sign-in, which also offers Google.
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$webClientId\"")
        // Where the anonymous usage counts go (the Cloudflare Worker in
        // admin/worker). Empty = the counters are never sent anywhere.
        buildConfigField("String", "TELEMETRY_URL", "\"$telemetryUrl\"")
        // A Google AI Studio key turns Ask AI into the native Gemini screen.
        buildConfigField("String", "GEMINI_API_KEY", "\"$geminiKey\"")
        buildConfigField("String", "GEMINI_MODEL", "\"$geminiModelName\"")
        // The stats Worker that holds the API key and checks Google sign-ins.
        buildConfigField("String", "AI_PROXY_URL", "\"$aiProxy\"")
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
    // WebViewAssetLoader serves the bundled "Ask AI" page over an https origin,
    // which Puter needs for sign-in (a file:// page has no origin to sign in).
    implementation("androidx.webkit:webkit:1.11.0")
    // "Continue with Google": Credential Manager plus the Google ID helper.
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.0")
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
