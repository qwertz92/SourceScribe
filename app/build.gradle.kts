plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}
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
    sourceSets.getByName("androidTest").assets.directories.add("$projectDir/schemas")
    buildTypes { debug { applicationIdSuffix = ".debug"; versionNameSuffix = "-dev" } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    packaging {
        resources.merges += listOf("META-INF/LICENSE.md", "META-INF/NOTICE.md")
        jniLibs {
            useLegacyPackaging = true
            // Preserve the audited upstream executables and ZIP containers byte for byte.
            keepDebugSymbols += listOf("**/libpython.so", "**/libpython.zip.so", "**/libqjs.so", "**/libffmpeg.so", "**/libffprobe.so", "**/libffmpeg.zip.so", "**/libandroidx.graphics.path.so")
            // Audited 16-KB binaries, no .debug sections; retain upstream bytes and their small symbol tables.
            // See the r50 native inspection in docs/reports/2026-09-07-integration.md.
            keepDebugSymbols += "**/libdatastore_shared_counter.so"
        }
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
    lint { warningsAsErrors = true; abortOnError = true; checkTestSources = true }
}
kotlin { compilerOptions { allWarningsAsErrors.set(true) } }
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
dependencies {
    implementation(project(":core"))
    implementation(project(":extractor"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.preview)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.appcompat)
    implementation(libs.lifecycle.compose)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.coroutines.android)
    implementation(libs.serialization.json)
    implementation(libs.core.ktx)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.hilt.android)
    implementation(libs.hilt.work)
    implementation(libs.work.runtime)
    implementation(libs.datastore)
    ksp(libs.room.compiler)
    ksp(libs.hilt.compiler)
    ksp(libs.hilt.work.compiler)
    debugImplementation(libs.compose.tooling)
    androidTestImplementation(libs.android.test.runner)
    androidTestImplementation(libs.android.test.junit)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.work.testing)
    androidTestImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(libs.okhttp.tls)
}
