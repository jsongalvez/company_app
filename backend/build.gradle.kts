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

// #568 — dedicated development source set for DevMain/DevSeeder. `./gradlew
// :backend:run` used the whole test runtimeClasspath; the dev server now rides
// main+dev only, so starting it never compiles unrelated test sources and the
// production artifact (installDist/Docker, main-only) holds no dev fixtures.
sourceSets {
    create("dev") {
        java.srcDir("src/dev/kotlin")
        compileClasspath += sourceSets["main"].output
        runtimeClasspath += sourceSets["main"].output
    }
}

configurations {
    named("devImplementation") { extendsFrom(configurations["implementation"]) }
    named("devRuntimeOnly") { extendsFrom(configurations["runtimeOnly"]) }
}

kotlin {
    target {
        compilations {
            // Friend relationships, not widened visibility: dev reads main
            // internals (internal stores the seeder writes through) while those
            // stores stay internal to production; test reads dev internals
            // (fixture IDs) through the same mechanism.
            maybeCreate("dev").associateWith(getByName("main"))
            getByName("test").associateWith(getByName("dev"))
        }
    }
}

dependencies {
    // Explicit test wiring so seeder tests exercise dev seeding code.
    testImplementation(sourceSets["dev"].output)
}

// Detekt analyzes main+test by default; cover the dev source set with the same rules.
tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    source(files("src/dev/kotlin"))
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
    classpath = sourceSets["dev"].runtimeClasspath
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
