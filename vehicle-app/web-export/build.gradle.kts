plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

kotlin {
    jvmToolchain(17)
}

// 앱의 판단 코드(DocentSession, AheadTracker, TrackPlayer)를 그대로 돌려 웹 체험판이 재생할 주행 기록을 만든다.
application {
    mainClass.set("io.github.hyunolike.ontime.webexport.ExportKt")
}

dependencies {
    implementation(project(":core-session"))
    implementation(project(":platform-fake"))
    // 가상 시간으로 빠르게 돌리기 위해 쓴다.
    implementation(libs.coroutines.test)
    implementation(libs.serialization.json)
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir.parentFile
    args = listOf("web/data")
}
