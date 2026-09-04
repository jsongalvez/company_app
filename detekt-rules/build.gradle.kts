plugins {
    kotlin("jvm")
}

dependencies {
    compileOnly(libs.detekt.api)
    testImplementation(libs.detekt.test)
    testImplementation(libs.kotlin.testJunit)
    testImplementation(libs.junit)
}

tasks.withType<Test>().configureEach {
    useJUnit()
}
