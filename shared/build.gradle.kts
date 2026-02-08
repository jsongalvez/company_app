plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
}

kotlin {
    jvm()
    androidTarget()
    iosArm64()

    sourceSets {
        androidMain.dependencies {
        }
        commonMain.dependencies {
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
