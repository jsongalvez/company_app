plugins {
    kotlin("jvm")
    kotlin("kapt")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.jmh)
    application
}

dependencies {
    implementation(project(":shared"))

    // Javalin
    implementation(libs.javalin)
    implementation(libs.javalin.openapi.plugin)
    implementation(libs.javalin.swagger.plugin)
    kapt(libs.javalin.openapi.processor)

    // Database
    implementation(libs.postgresql)
    implementation(libs.flyway)
    implementation(libs.flyway.postgresql)
    implementation(libs.hikaricp)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Authentication
    implementation(libs.jwt)

    // Password-reset email delivery
    implementation(libs.jakarta.mail)

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

    // PDF export
    implementation(libs.openpdf)

    // Testing
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.testJunit)
    testImplementation(libs.junit)
    testImplementation(libs.javalin.testtools)
    // #572 — Detekt's PSI parser for declaration-aware architecture checks (test scope only).
    testImplementation(libs.detekt.parser)

    // JMH
    jmh(libs.jmh.core)
    jmhAnnotationProcessor(libs.jmh.annprocess)
}

// #495 — canonical contract export + verification. Reads the kapt-generated classpath
// resource (the same document production serves) and applies OpenApiCanonical.
// Deliberately NOT wired into compile/installDist: Docker builders have no Node,
// and this path is JVM-only (#372).
tasks.register<JavaExec>("exportOpenApiSpec") {
    dependsOn("compileKotlin")
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "com.companyb.companyapp.http.openapi.ExportOpenApiSpecKt"
    workingDir = rootProject.projectDir
    args = listOf(layout.buildDirectory.file("openapi/openapi-canonical.json").get().asFile.path)
}

tasks.register<Test>("verifyOpenApiContract") {
    useJUnit()
    workingDir = rootProject.projectDir
    filter { includeTestsMatching("*OpenApiContractTest") }
    dependsOn("compileKotlin")
}

application {
    mainClass = "com.companyb.companyapp.MainKt"
}

tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
    classpath = sourceSets["test"].runtimeClasspath
    mainClass = "com.companyb.companyapp.seeding.DevMainKt"
}

tasks.test {
    useJUnit()
    workingDir = rootProject.projectDir
}

jmh {
    warmupIterations = 3
    iterations = 5
    fork = 1
    threads = 1
}

tasks.register<JavaExec>("runWithJfr") {
    group = "profiling"
    description = "Runs the backend with JDK Flight Recorder enabled"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = application.mainClass
    workingDir = rootProject.projectDir
    jvmArgs = listOf(
        "-XX:StartFlightRecording=filename=${rootProject.projectDir}/logs/recording.jfr",
        "-XX:FlightRecorderOptions=stackdepth=256",
    )
}

tasks.register<JavaExec>("runWithJfrAllocation") {
    group = "profiling"
    description = "Runs the backend with JFR + allocation profiling"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = application.mainClass
    workingDir = rootProject.projectDir
    jvmArgs = listOf(
        "-XX:StartFlightRecording=filename=${rootProject.projectDir}/logs/recording-alloc.jfr,settings=profile",
        "-XX:FlightRecorderOptions=stackdepth=256",
    )
}
