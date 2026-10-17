plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Backend origin, e.g. http://10.0.2.2:8000 (emulator -> host) or https://photos.example.com
val apiUrl: String = (findProperty("bigshelf.apiUrl") as String?)?.trimEnd('/') ?: "http://10.0.2.2:8000"

android {
    namespace = "com.bigshelf.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.bigshelf.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 15
        versionName = "1.10.0"
        buildConfigField("String", "API_URL", "\"$apiUrl\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    // Automatic backup runs as a periodic background job
    implementation(libs.androidx.work.runtime)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    // Same icon set as the web app (@phosphor-icons/react)
    implementation(libs.phosphor.icons)
    // Map of where photos were taken: OpenStreetMap tiles, no API key
    implementation(libs.osmdroid)
}
