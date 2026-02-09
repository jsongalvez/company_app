plugins {
    kotlin("jvm")
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
}
