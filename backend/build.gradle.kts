import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.jvm.tasks.Jar

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
    annotationProcessor(libs.javalin.openapi.processor)

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

    // PDF export
    implementation(libs.openpdf)

    // Testing
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.testJunit)
    testImplementation(libs.junit)
    testImplementation(libs.javalin.testtools)

    // JMH
    jmh(libs.jmh.core)
    jmhAnnotationProcessor(libs.jmh.annprocess)
}

tasks.named<JavaCompile>("compileJava") {
    doFirst {
        options.compilerArgs.removeAll { it == "-proc:none" }
        options.compilerArgs.addAll(
            listOf("-processor", "io.javalin.openapi.processor.OpenApiAnnotationProcessor"),
        )
    }
}

tasks.named<Jar>("jar") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
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
