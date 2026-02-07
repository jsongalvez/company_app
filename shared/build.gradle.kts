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
