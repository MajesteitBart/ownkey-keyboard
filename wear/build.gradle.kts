plugins {
    alias(libs.plugins.agp.application)
    alias(libs.plugins.kotlin.plugin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val projectTargetSdk: String by project
val projectCompileSdk: String by project
val projectVersionCode: String by project
val projectVersionName: String by project

android {
    namespace = "nl.bartvandermeeren.ownkey.wear"
    compileSdk = projectCompileSdk.toInt()

    defaultConfig {
        // Must match phone app applicationId for Wear Data Layer message delivery.
        applicationId = "nl.bartvandermeeren.ownkey"
        minSdk = 30
        targetSdk = projectTargetSdk.toInt()
        versionCode = projectVersionCode.toInt()
        versionName = projectVersionName.substringBefore("-")
    }

    buildTypes {
        named("debug") {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.security.crypto)
    implementation(libs.kotlinx.coroutines)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.play.services.wearable)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
