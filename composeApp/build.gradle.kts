plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.application")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.21"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.21"
    id("app.cash.sqldelight")
}

kotlin {
    androidTarget()
    jvmToolchain(17)

    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.materialIconsExtended)
            implementation(project.dependencies.platform("io.github.jan-tennert.supabase:bom:3.2.6"))
            implementation("io.github.jan-tennert.supabase:postgrest-kt")
            implementation("app.cash.sqldelight:runtime:2.1.0")
        }
        androidMain.dependencies {
            implementation("androidx.activity:activity-compose:1.10.1")
            implementation("io.ktor:ktor-client-android:3.3.0")
            implementation("app.cash.sqldelight:android-driver:2.1.0")
        }
        iosMain.dependencies {
            implementation("io.ktor:ktor-client-darwin:3.3.0")
            implementation("app.cash.sqldelight:native-driver:2.1.0")
        }
    }
}

sqldelight {
    databases {
        create("NutriDatabase") {
            packageName.set("com.reus.nutri.db")
        }
    }
}

android {
    namespace = "com.reus.nutri"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.reus.nutri"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }
}
