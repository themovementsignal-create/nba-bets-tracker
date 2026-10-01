import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

// Release signing comes from environment variables set by the GitHub Actions workflow.
// The keystore and passwords are never stored in the repo.
val keystoreFile: String? = System.getenv("KEYSTORE_FILE")

android {
    // Package name is permanent: changing it makes Android treat the app as a different app.
    namespace = "io.github.themovementsignal.training"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.themovementsignal.training"
        minSdk = 28
        targetSdk = 37
        // CI passes -PversionCode=<run number>; local builds fall back to 1.
        versionCode = providers.gradleProperty("versionCode").orElse("1").get().toInt()
        versionName = "0.1.$versionCode"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Phones (arm64) and the CI emulator (x86_64) only; keeps the snore model's native code small.
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }

    signingConfigs {
        if (keystoreFile != null) {
            create("release") {
                storeFile = file(keystoreFile)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // The YAMNet model is memory-mapped from assets, so it must stay uncompressed.
    androidResources { noCompress += "tflite" }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

room {
    // Schema history, needed to write and test migrations. Uploaded by CI as an artifact.
    schemaDirectory("$projectDir/schemas")
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    // On-device sound classification (YAMNet) for snore detection.
    implementation(libs.mediapipe.tasks.audio)
    testImplementation(libs.junit)

    // Test-only: emulator UI tour run by CI (not shipped in the app).
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
