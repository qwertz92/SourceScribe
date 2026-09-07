plugins { alias(libs.plugins.android.library) }
android {
    namespace = "app.sourcescribe.extractor"
    compileSdk = 37
    defaultConfig {
        minSdk = 29
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    packaging {
        resources.merges += listOf("META-INF/LICENSE.md", "META-INF/NOTICE.md")
        jniLibs {
            useLegacyPackaging = true
            // These audited upstream files include ZIP containers, not strippable libraries.
            keepDebugSymbols += listOf("**/libpython.so", "**/libpython.zip.so", "**/libqjs.so", "**/libffmpeg.so", "**/libffprobe.so", "**/libffmpeg.zip.so")
        }
    }
    lint { warningsAsErrors = true; abortOnError = true; checkTestSources = true }
}
kotlin { compilerOptions { allWarningsAsErrors.set(true) } }
dependencies {
    implementation(project(":core"))
    implementation(libs.coroutines.android)
    implementation(libs.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.extractor.library)
    implementation(libs.extractor.ffmpeg)
    androidTestImplementation(libs.android.test.runner)
    androidTestImplementation(libs.android.test.junit)
}
