plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "io.github.hyunolike.ontime.pleos"
    compileSdk = 34

    defaultConfig {
        minSdk = 28
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    api(project(":platform-api"))
    implementation(project(":core-voice"))
    implementation(libs.coroutines.android)

    implementation(libs.pleos.navi)
    implementation(libs.pleos.vehicle)
    implementation(libs.pleos.tts)
    implementation(libs.pleos.stt)
    implementation(libs.pleos.location)
}
