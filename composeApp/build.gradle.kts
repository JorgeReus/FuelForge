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
        val generatedSupabaseDir = layout.buildDirectory.dir("generated/supabase/commonMain/kotlin")
        val generateSupabaseConfig by tasks.registering {
            val supabaseUrl = providers.environmentVariable("SUPABASE_URL")
                .orElse("")
            val supabasePublishableKey = providers.environmentVariable("SUPABASE_PUBLISHABLE_KEY")
                .orElse("")
            inputs.property("supabaseUrl", supabaseUrl)
            inputs.property("supabasePublishableKey", supabasePublishableKey)
            outputs.dir(generatedSupabaseDir)
            doLast {
                val url = supabaseUrl.get()
                val key = supabasePublishableKey.get()
                check(url.isNotBlank()) { "SUPABASE_URL is required; load nix/env/<APP_ENV>.env via direnv" }
                check(key.startsWith("sb_publishable_")) {
                    "SUPABASE_PUBLISHABLE_KEY must be a publishable key"
                }
                val output = generatedSupabaseDir.get().file("com/reus/nutri/GeneratedSupabaseConfig.kt").asFile
                output.parentFile.mkdirs()
                output.writeText(
                    """
                    package com.reus.nutri

                    internal object GeneratedSupabaseConfig {
                        const val url = ${url.quoteKotlin()}
                        const val publishableKey = ${key.quoteKotlin()}
                    }
                    """.trimIndent(),
                )
            }
        }

        commonMain {
            kotlin.srcDir(generatedSupabaseDir)
            kotlin.srcDir(generateSupabaseConfig)
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation(compose.materialIconsExtended)
                implementation(project.dependencies.platform("io.github.jan-tennert.supabase:bom:3.2.6"))
                implementation("io.github.jan-tennert.supabase:auth-kt")
                implementation("io.github.jan-tennert.supabase:postgrest-kt")
                implementation("app.cash.sqldelight:runtime:2.1.0")
            }
        }
        androidMain.dependencies {
            implementation("androidx.activity:activity-compose:1.10.1")
            // ActivityIntensityRecord is available starting with 1.2.0-alpha06.
            // compileSdk/targetSdk 36 are required for this client generation.
            implementation("androidx.health.connect:connect-client:1.2.0-alpha06")
            implementation("io.ktor:ktor-client-android:3.3.0")
            implementation("app.cash.sqldelight:android-driver:2.1.0")
        }
        iosMain.dependencies {
            implementation("io.ktor:ktor-client-darwin:3.3.0")
            implementation("app.cash.sqldelight:native-driver:2.1.0")
        }
        androidUnitTest.dependencies {
            implementation("app.cash.sqldelight:sqlite-driver:2.1.0")
        }
    }

}

fun String.quoteKotlin(): String = "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

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
