import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// 키스토어 정보는 저장소에 넣지 않는다. 파일이 없으면 release는 서명 없이 빌드된다.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use(::load)
}

android {
    namespace = "io.github.hyunolike.ontime.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "io.github.hyunolike.ontime"
        minSdk = 28 // Pleos Connect 요건: 사이닝 키 로테이션 지원
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        if (keystoreProperties.containsKey("storeFile")) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }

    // fake: 시나리오를 재생한다. Pleos 이미지가 없는 에뮬레이터에서도 돈다.
    // pleos: Pleos Connect SDK를 호출한다. Pleos Connect 에뮬레이터나 실차에서만 돈다.
    flavorDimensions += "platform"
    productFlavors {
        create("fake") {
            dimension = "platform"
        }
        create("pleos") {
            dimension = "platform"
            applicationIdSuffix = ".pleos"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core-session"))
    implementation(project(":design-system"))
    "fakeImplementation"(project(":platform-fake"))
    "pleosImplementation"(project(":platform-pleos"))
    // pleos 빌드도 위치는 재생한다. 에뮬레이터의 내비 차가 움직이지 않아서다(docs/features/live-docent).
    "pleosImplementation"(project(":platform-fake"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.core.ktx)
    implementation(libs.coroutines.android)
}
