import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections
import java.util.Locale
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("kotlin-parcelize")
    id("kotlin-kapt")
}

// Function to get local IP address on macOS
fun getLocalIpAddress(): String {
    return try {
        val process = ProcessBuilder("sh", "-c", "ipconfig getifaddr $(route -n get default | grep 'interface:' | awk '{print $2}')")
            .redirectErrorStream(true)
            .start()
        val output = ByteArrayOutputStream()
        process.inputStream.copyTo(output)
        val ip = output.toString().trim()
        if (ip.isNotEmpty() && !ip.contains("0.0.0.0")) {
            ip
        } else {
            "10.0.2.2" // Fallback for emulator
        }
    } catch (_: Exception) {
        "10.0.2.2" // Fallback for emulator
    }
}

fun pickHostIp(): String {
    return try {
        val interfaces = NetworkInterface.getNetworkInterfaces() ?: return ""
        val candidates = Collections.list(interfaces)
            .filter { iface ->
                iface.isUp &&
                    !iface.isLoopback &&
                    !iface.displayName.lowercase(Locale.US).contains("awdl") &&
                    !iface.displayName.lowercase(Locale.US).contains("utun") &&
                    !iface.displayName.lowercase(Locale.US).contains("docker") &&
                    !iface.displayName.lowercase(Locale.US).contains("vmnet")
            }
            .flatMap { iface -> Collections.list(iface.inetAddresses).map { iface to it } }
            .firstOrNull { (_, address) -> address is Inet4Address }
            ?: return ""
        (candidates.second as Inet4Address).hostAddress ?: ""
    } catch (_: Exception) {
        ""
    }
}

data class DebugServerUrls(val lan: String, val emulator: String)

fun resolveDebugServerUrls(localProperties: Properties): DebugServerUrls {
    val tag = "[SonicWave]"
    val override = localProperties.getProperty("SERVER_BASE_URL")?.trim()?.takeIf { it.isNotEmpty() }
    if (override != null) {
        val sanitized = override.trimEnd('/')
        println("$tag Debug server URL -> $sanitized (from local.properties override)")
        return DebugServerUrls(lan = sanitized, emulator = sanitized)
    }

    val emulatorOverride = localProperties.getProperty("SERVER_BASE_URL_EMULATOR")?.trim()?.takeIf { it.isNotEmpty() }
    val emulatorUrl = if (emulatorOverride != null) {
        val sanitized = emulatorOverride.trimEnd('/')
        println("$tag Emulator server URL -> $sanitized (from local.properties override)")
        sanitized
    } else {
        val fallback = "http://10.0.2.2:3000"
        println("$tag Emulator server URL -> $fallback (default emulator loopback)")
        fallback
    }

    val networkIp = pickHostIp()
    if (networkIp.isNotEmpty()) {
        val url = "http://$networkIp:3000"
        println("$tag LAN server URL -> $url (from NetworkInterface)")
        return DebugServerUrls(lan = url, emulator = emulatorUrl)
    }

    val shellIp = getLocalIpAddress()
    if (shellIp.isNotEmpty() && shellIp != "10.0.2.2") {
        val url = "http://$shellIp:3000"
        println("$tag LAN server URL -> $url (from shell fallback)")
        return DebugServerUrls(lan = url, emulator = emulatorUrl)
    }

    val fallback = "http://10.0.2.2:3000"
    println("$tag LAN server URL -> $fallback (fallback to emulator loopback)")
    return DebugServerUrls(lan = fallback, emulator = emulatorUrl)
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
        buildConfigField("String", "SERVER_BASE_URL_RELEASE", "\"http://47.107.66.156:3000\"")
        buildConfigField("String", "SERVER_BASE_URL_LAN", "\"http://10.0.2.2:3000\"")
        buildConfigField("String", "SERVER_BASE_URL_EMULATOR", "\"http://10.0.2.2:3000\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("String", "SERVER_BASE_URL_RELEASE", "\"http://47.107.66.156:3000\"")
            buildConfigField("String", "SERVER_BASE_URL_LAN", "\"http://47.107.66.156:3000\"")
            buildConfigField("String", "SERVER_BASE_URL_EMULATOR", "\"http://47.107.66.156:3000\"")
            // Release：只需要云端地址
            buildConfigField(
                "String",
                "BACKEND_BASE_URL_CLOUD",
                "\"http://47.107.66.156:3000\""
            )

            // 为了兼容旧代码，可以让 BACKEND_BASE_URL 继续等于 CLOUD
            buildConfigField(
                "String",
                "BACKEND_BASE_URL",
                "\"http://47.107.66.156:3000\""
            )
            // 环境标签：prod（正式包）
            buildConfigField("String", "ENVIRONMENT", "\"prod\"")
        }
        debug {
            val serverUrls = resolveDebugServerUrls(localProperties)
            // 旧的音频 / 其他接口仍然使用 SERVER_BASE_URL_* 逻辑，暂不动
            buildConfigField("String", "SERVER_BASE_URL_RELEASE", "\"http://47.107.66.156:3000\"")
            buildConfigField("String", "SERVER_BASE_URL_LAN", "\"${serverUrls.lan}\"")
            buildConfigField("String", "SERVER_BASE_URL_EMULATOR", "\"${serverUrls.emulator}\"")

            // Debug：本地 + 云端两个后端地址
            buildConfigField(
                "String",
                "BACKEND_BASE_URL_LOCAL",
                "\"${serverUrls.lan}\""
            )
            buildConfigField(
                "String",
                "BACKEND_BASE_URL_CLOUD",
                "\"http://47.107.66.156:3000\""
            )

            // 旧的 BACKEND_BASE_URL 为了兼容，可以暂时仍指向 CLOUD（后续会用新的环境管理类来替代）
            buildConfigField(
                "String",
                "BACKEND_BASE_URL",
                "\"http://47.107.66.156:3000\""
            )

            // 环境标签：test（调试包指向“测试环境”）
            buildConfigField("String", "ENVIRONMENT", "\"test\"")
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
    implementation("androidx.lifecycle:lifecycle-process:2.8.6")
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
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    implementation(files("libs/CH341PARV1.1.jar"))

    // Room 持久化：用于保存用户自设模式
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.core:core-splashscreen:1.0.1")
}
