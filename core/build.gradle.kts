plugins { alias(libs.plugins.kotlin.jvm); alias(libs.plugins.kotlin.serialization) }
kotlin { jvmToolchain(17); compilerOptions { allWarningsAsErrors.set(true) } }
dependencies {
    implementation(libs.serialization.json)
    implementation(libs.coroutines.core)
    implementation(libs.okhttp)
    implementation(libs.bcpg)
    implementation(libs.bcprov)
    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
}
tasks.test { systemProperty("sourcescribe.engineFixture", rootProject.file("extractor/src/main/res/raw/ytdlp").absolutePath) }
