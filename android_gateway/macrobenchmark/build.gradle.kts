plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.smsforwarder.gateway.macrobenchmark"
    compileSdk = 34

    defaultConfig {
        minSdk = 28
        targetSdk = 34

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Macrobenchmark requires a non-debuggable target build (the app never runs
    // ahead-of-time-compiled while debuggable - see baselineprofile/build.gradle.kts
    // and spec 0025's finding that JIT compilation on the main thread, not app code,
    // is the dominant cost being measured here) - targets :app's "release" variant,
    // same as :baselineprofile. com.android.test only instantiates a "debug" variant
    // by default, so "release" is declared explicitly below to match app's release
    // build type by name (AGP's default same-name variant matching) - without this,
    // `connectedDebugAndroidTest` silently runs against app's debuggable build, which
    // Macrobenchmark refuses to measure ("Failed to grant permissions").
    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildTypes {
        // Declared explicitly so this module gets a "release" variant at all (com.android.test
        // only instantiates "debug" by default) - matches :app's "release" build type by name.
        // This is the TEST APK's own signing (must be valid to install at all), unrelated to
        // :app's release signing - the default debug keystore is fine for a local test APK.
        create("release") {
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.espresso.core)
    implementation(libs.uiautomator)
    implementation(libs.benchmark.macro.junit4)
}
