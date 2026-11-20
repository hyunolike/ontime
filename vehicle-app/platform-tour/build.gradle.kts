plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":platform-api"))
    // 플러그인 없이 JSON 트리만 읽는다(Json.parseToJsonElement).
    implementation(libs.serialization.json)
    testImplementation(libs.coroutines.test)
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
