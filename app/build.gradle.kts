plugins { alias(libs.plugins.android.application); alias(libs.plugins.kotlin.compose); alias(libs.plugins.kotlin.serialization) }
android {
    namespace = "app.sourcescribe"
    compileSdk = 37
    defaultConfig {
        applicationId = "app.sourcescribe"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    packaging {
        jniLibs {
            useLegacyPackaging = true
            // Preserve the audited upstream executables and ZIP containers byte for byte.
            keepDebugSymbols += listOf("**/libpython.so", "**/libpython.zip.so", "**/libqjs.so", "**/libffmpeg.so", "**/libffprobe.so", "**/libffmpeg.zip.so", "**/libandroidx.graphics.path.so")
        }
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
    lint { warningsAsErrors = true; abortOnError = true; checkTestSources = true }
}
kotlin { compilerOptions { allWarningsAsErrors.set(true) } }
dependencies {
    implementation(project(":core"))
    implementation(project(":extractor"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.preview)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.compose)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.coroutines.android)
    implementation(libs.serialization.json)
    debugImplementation(libs.compose.tooling)
    androidTestImplementation(libs.android.test.runner)
    androidTestImplementation(libs.android.test.junit)
}
