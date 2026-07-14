plugins {
    kotlin("jvm")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.jmh)
    application
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

    // JMH
    jmh(libs.jmh.core)
    jmhAnnotationProcessor(libs.jmh.annprocess)
}

application {
    mainClass = "com.companyb.companyapp.MainKt"
}

tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
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
