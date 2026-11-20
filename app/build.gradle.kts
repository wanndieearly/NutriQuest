
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)            // F6: generates Room database code
}

android {
    namespace = "com.nutriquest.app"

    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.nutriquest.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    // Enable Jetpack Compose
    // Robolectric tests need Android resources available on the computer
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {

    // Existing dependencies
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)

    // Compose BOM: manages compatible Compose library versions
    implementation(platform(libs.androidx.compose.bom))

    // Compose UI
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)

    // Material 3
    implementation(libs.androidx.compose.material3)

    // Activity integration
    implementation(libs.androidx.activity.compose)

    // Compose debugging tools
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    // Camera
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.litert)
    implementation(libs.litertlm)

    // F6: meal storage (Room database)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    // F6: lets MealStorageTest run Android + Room code on the computer (tests only)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)

    // F9: daily reminder (WorkManager)
    implementation(libs.work.runtime.ktx)
    testImplementation(libs.work.testing)
}
