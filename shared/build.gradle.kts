plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvm()
    androidTarget()
    iosArm64()

    sourceSets {
        androidMain.dependencies {
        }
        commonMain.dependencies {
            // Serialization
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
        }
    }
}

android {
    namespace = "com.companyb.companyapp.shared"
    compileSdk =
        libs.versions.android.compileSdk
            .get()
            .toInt()
}
