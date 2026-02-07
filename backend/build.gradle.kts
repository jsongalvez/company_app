plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":shared"))

    // Javalin
    implementation(libs.javalin)
}
