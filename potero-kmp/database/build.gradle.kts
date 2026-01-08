plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.sqldelight)
}

kotlin {
    jvm()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.bundles.sqldelight)
                implementation(libs.kotlinx.coroutines.core)
            }
        }

        val jvmMain by getting {
            dependencies {
                implementation(libs.sqldelight.driver.jvm)
            }
        }
    }
}

sqldelight {
    databases {
        create("PoteroDatabase") {
            packageName.set("com.potero.db")
            schemaOutputDirectory.set(file("build/schema"))
            verifyMigrations.set(true)
        }
    }
}
