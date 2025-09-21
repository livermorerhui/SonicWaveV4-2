import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("kotlin-parcelize")
}

// Read properties from local.properties
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(FileInputStream(localPropertiesFile))
}

android {
    namespace = "com.example.sonicwavev4"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.sonicwavev4"
        minSdk = 24
        //noinspection OldTargetApi
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("String", "SERVER_BASE_URL", "\"http://8.148.182.90:3000\"")
        }
        debug {
            // Read server URL from local.properties or use a default value
            val serverUrl = localProperties.getProperty("SERVER_BASE_URL") ?: "http://10.0.2.2:3000"
            buildConfigField("String", "SERVER_BASE_URL", "\"$serverUrl\"")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.gridlayout) // Added for GridLayout support
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    // 添加 ViewModel KTX 依赖，以便使用 by viewModels()
    implementation("androidx.fragment:fragment-ktx:1.6.2") // 请使用最新稳定版

    // Retrofit for networking
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")// For JSON serialization
    implementation("com.squareup.okhttp3:okhttp:4.10.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.10.0") // For logging requests

    implementation("com.google.code.gson:gson:2.10.1")

    // WorkManager for background tasks
    implementation("androidx.work:work-runtime-ktx:2.10.4")

    implementation("androidx.window:window:1.3.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
}