import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// ── Versioning ────────────────────────────────────────────────────────────────────────────────
// versionName/channel live in version.properties (one line to bump). versionCode is the git commit
// count so every push to main produces a strictly higher code and can upgrade the previous install.
// A shallow clone would undercount, so CI must fetch full history; -PversionCode overrides anyway.
val versionProps = Properties().apply {
    rootProject.file("version.properties").inputStream().use { load(it) }
}
val appVersionName = versionProps.getProperty("versionName", "1.0.0")
val appChannel = versionProps.getProperty("channel", "beta")

val gitCommitCount: Int = runCatching {
    providers.exec { commandLine("git", "rev-list", "--count", "HEAD") }
        .standardOutput.asText.get().trim().toInt()
}.getOrDefault(1)
val appVersionCode = (findProperty("versionCode") as String?)?.toInt() ?: gitCommitCount

// ── Signing ───────────────────────────────────────────────────────────────────────────────────
// Credentials come from keystore.properties (gitignored) or, in CI, from environment variables.
// When neither is present the release build stays unsigned instead of failing every local build.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signingValue(key: String, env: String): String? =
    keystoreProps.getProperty(key) ?: System.getenv(env)

val storeFilePath = signingValue("storeFile", "KEYSTORE_FILE")
val storePasswordValue = signingValue("storePassword", "KEYSTORE_PASSWORD")
val keyAliasValue = signingValue("keyAlias", "KEY_ALIAS")
val keyPasswordValue = signingValue("keyPassword", "KEY_PASSWORD")
val hasSigningKey = storeFilePath != null && storePasswordValue != null && keyAliasValue != null

android {
    namespace = "com.vegasol.brainstorm"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.vegasol.brainstorm"
        minSdk = 29
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
    }
    flavorDimensions += "enforcement"
    productFlavors {
        create("consumer") {
            dimension = "enforcement"
            applicationIdSuffix = ".consumer"
            buildConfigField("String", "ENFORCEMENT_MODE", "\"CONSUMER\"")
            buildConfigField("String", "CHANNEL", "\"$appChannel\"")
        }
        create("managed") {
            dimension = "enforcement"
            applicationIdSuffix = ".managed"
            buildConfigField("String", "ENFORCEMENT_MODE", "\"MANAGED\"")
            buildConfigField("String", "CHANNEL", "\"$appChannel\"")
        }
    }
    signingConfigs {
        if (hasSigningKey) {
            create("release") {
                storeFile = rootProject.file(storeFilePath!!)
                storePassword = storePasswordValue
                keyAlias = keyAliasValue
                keyPassword = keyPasswordValue ?: storePasswordValue
            }
        }
    }
    buildTypes {
        debug {
            // A debug install lives beside the release build it is testing against, so enabling
            // the accessibility service for the probe never replaces the user's real installation.
            applicationIdSuffix = ".debug"
        }
        release {
            // No shrinking yet: debug and release stay behaviourally identical until an R8 pass
            // has been verified on a device.
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.findByName("release")
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.13.1")
implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    testImplementation("junit:junit:4.13.2")
}
