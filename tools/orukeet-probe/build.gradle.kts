plugins { id("com.android.application") version "9.0.0" }

android {
    namespace = "nl.bartvandermeeren.ownkey.probe"
    compileSdk = 36
    buildToolsVersion = "36.0.0"
    defaultConfig {
        applicationId = "nl.bartvandermeeren.ownkey.probe"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1-probe"
        ndk { abiFilters += listOf("x86_64", "arm64-v8a") }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging { jniLibs { useLegacyPackaging = false } }
}

dependencies {
    implementation(files("libs/sherpa-onnx-1.13.4.aar"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.3.10")
}
