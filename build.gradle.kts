import io.gitlab.arturbosch.detekt.Detekt
import org.gradle.api.tasks.compile.JavaCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false

    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

subprojects {
    apply(plugin = rootProject.libs.plugins.ktlint.get().pluginId)
    apply(plugin = rootProject.libs.plugins.detekt.get().pluginId)

    ktlint {
        version.set("1.8.0")
        outputToConsole.set(true)
        ignoreFailures.set(false)
    }

    detekt {
        buildUponDefaultConfig = true
        allRules = false
        config.setFrom(
            files(
                "$rootDir/config/detekt/detekt.yml",
                "$rootDir/config/detekt/detekt-anti-slop.yml",
            ),
        )
        // #465: custom-rule activation only where the plugin is on the classpath.
        if (path != ":detekt-rules") {
            config.from(files("$rootDir/config/detekt/detekt-custom.yml"))
        }
    }

    dependencies {
        add("detektPlugins", "io.gitlab.arturbosch.detekt:detekt-formatting:${rootProject.libs.versions.detekt.get()}")
        if (path != ":detekt-rules") {
            add("detektPlugins", project(":detekt-rules"))
        }
    }

    if (path != ":detekt-rules") {
        tasks.withType<Detekt>().configureEach {
            dependsOn(":detekt-rules:assemble")
        }
    }

    val warningsAsErrors = providers.gradleProperty("warningsAsErrors").map(String::toBoolean).orElse(false)
    tasks.withType<KotlinCompilationTask<*>>().configureEach {
        compilerOptions.allWarningsAsErrors.set(warningsAsErrors)
    }

    tasks.withType<JavaCompile>().configureEach {
        options.isWarnings = true
        if (warningsAsErrors.get()) {
            options.compilerArgs.add("-Werror")
        }
    }
}

// Semantic dead-code gate (map #529 Phase A, ref #530): one whole-project
// compiler analysis over the listed sources. CI runs this task verbatim;
// local reproduction is the same command. Never wired into git hooks (#329).
val deadCodeTool by configurations.creating {
    isCanBeResolved = true
    isCanBeConsumed = false
}

dependencies {
    deadCodeTool(project(":detekt-rules"))
}

// Union of compile+test classpaths, wired lazily (child configurations do
// not exist while the root script evaluates). Library receivers (Compose,
// Ktor, coroutines) must resolve or whole call chains go blind; project
// build outputs are excluded so cross-module references resolve to sources,
// never to binaries.
val deadCodeTargetCoordinates =
    listOf(
        ":backend" to "compileClasspath",
        ":backend" to "testCompileClasspath",
        ":composeApp" to "desktopMainCompileClasspath",
        ":composeApp" to "desktopTestCompileClasspath",
        ":shared" to "jvmMainCompileClasspath",
        ":shared" to "jvmTestCompileClasspath",
    )
val deadCodeTargetFiles = files()
deadCodeTargetCoordinates.forEach { (projectPath, configurationName) ->
    project(projectPath).afterEvaluate {
        deadCodeTargetFiles.from(configurations.getByName(configurationName))
    }
}

fun resolveDeadCodeTargetJars(): List<java.io.File> =
    deadCodeTargetFiles
        .filter { it.extension == "jar" && "/build/" !in it.absolutePath }
        .files
        .distinct()

tasks.register<JavaExec>("deadCodeCheck") {
    group = "verification"
    description = "Semantic unused-declaration gate (map #529 Phase A, ref #530)."
    // Cross-project classpath union resolves dynamically at execution time;
    // that is incompatible with the configuration cache by design. The task
    // runs in CI (cold cache) and on demand locally, so nothing is lost.
    notCompatibleWithConfigurationCache("resolves cross-project compile classpaths at execution time")
    classpath = deadCodeTool
    mainClass.set("com.companyb.detekt.deadcode.DeadCodeMainKt")
    workingDir = rootDir
    inputs.files(deadCodeTargetFiles)
    val writeBaseline = providers.gradleProperty("deadCodeWriteBaseline").orElse("")
    args(
        listOf(
            "--candidate", "shared/src/commonMain",
            "--candidate", "composeApp/src/commonMain",
            "--candidate", "composeApp/src/desktopMain",
            "--candidate", "backend/src/main",
            "--consumer", "backend/src/test",
            "--consumer", "backend/src/dev",
            "--consumer", "composeApp/src/commonTest",
            "--consumer", "composeApp/src/desktopTest",
            "--consumer", "shared/src/commonTest",
            "--baseline", "config/deadcode/baseline.txt",
            "--entry-points", "config/deadcode/entry-points.txt",
            "--known-unanalyzed", "config/deadcode/known-unanalyzed.txt",
        ) + (if (writeBaseline.get().isNotEmpty()) listOf("--write-baseline", writeBaseline.get()) else emptyList()),
    )
    doFirst {
        args(resolveDeadCodeTargetJars().flatMap { listOf("--classpath", it.absolutePath) })
    }
}
