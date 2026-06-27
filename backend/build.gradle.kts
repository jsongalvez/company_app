plugins {
    kotlin("jvm")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":shared"))

    // Javalin
    implementation(libs.javalin)

    // Database
    implementation(libs.postgresql)
    implementation(libs.flyway)
    implementation(libs.flyway.postgresql)
    implementation(libs.hikaricp)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Authentication
    implementation(libs.jwt)

    // Logging
    implementation(libs.kotlin.logging)
    implementation(libs.logback.classic)

    // Environment
    implementation(libs.dotenv)

    // Exposed
    implementation(libs.exposed)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.java.time)

    // BCrypt
    implementation(libs.bcrypt)

    // Testing
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.testJunit)
    testImplementation(libs.junit)
}

tasks.test {
    useJUnit()
}
