// Top-level build file. Plugin versions are declared here and applied per-module.
plugins {
    id("com.android.application") version "8.5.2" apply false
    // Kotlin 2.4 is the first line that can read the AdMob SDK's metadata: the
    // Google Mobile Ads Next-Gen (and even the legacy play-services-ads) jars
    // are built with Kotlin 2.3+, which Kotlin 2.0 refuses to load. This pair
    // is inside Google's supported window — Kotlin 2.4.0-2.4.10 works with
    // Gradle 7.6.3-9.5.0 and AGP 8.5.2-9.1.0, and requires AGP 8.5.2.
    id("org.jetbrains.kotlin.android") version "2.4.10" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10" apply false
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
