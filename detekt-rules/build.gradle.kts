plugins {
    kotlin("jvm")
}

dependencies {
    compileOnly(libs.detekt.api)
    // #530: semantic dead-code engine binds sources with the repo's own Kotlin
    // compiler frontend (single whole-project compilation, not per-file PSI).
    implementation(libs.kotlin.compiler.embeddable)
    testImplementation(libs.detekt.test)
    testImplementation(libs.kotlin.testJunit)
    testImplementation(libs.junit)
}

tasks.withType<Test>().configureEach {
    useJUnit()
}
