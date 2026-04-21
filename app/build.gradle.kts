import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
}

val envProperties = Properties().apply {
    val envFile = rootProject.file(".env")
    if (envFile.exists()) {
        envFile.readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains("=") }
            .forEach { line ->
                val splitIndex = line.indexOf('=')
                val key = line.substring(0, splitIndex).trim()
                val value = line.substring(splitIndex + 1).trim().removeSurrounding("\"")
                put(key, value)
            }
    }
}

fun envValue(name: String): String =
    (envProperties.getProperty(name) ?: "").replace("\"", "\\\"")

fun fbLoginScheme(appId: String): String {
    if (appId.isBlank()) return ""
    return if (appId.startsWith("fb", ignoreCase = true)) appId else "fb$appId"
}

android {
    namespace = "com.coolcook.app"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.coolcook.app"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        val facebookAppId = envValue("FACEBOOK_APP_ID")
        val facebookClientToken = envValue("FACEBOOK_CLIENT_TOKEN")
        val facebookLoginScheme = fbLoginScheme(facebookAppId)

        buildConfigField("String", "CLOUDINARY_CLOUD_NAME", "\"${envValue("CLOUDINARY_CLOUD_NAME")}\"")
        buildConfigField("String", "CLOUDINARY_API_KEY", "\"${envValue("CLOUDINARY_API_KEY")}\"")
        buildConfigField("String", "CLOUDINARY_API_SECRET", "\"${envValue("CLOUDINARY_API_SECRET")}\"")
        buildConfigField("String", "CLOUDINARY_UPLOAD_PRESET", "\"${envValue("CLOUDINARY_UPLOAD_PRESET")}\"")

        resValue("string", "facebook_app_id", "\"$facebookAppId\"")
        resValue("string", "facebook_client_token", "\"$facebookClientToken\"")
        resValue("string", "fb_login_protocol_scheme", "\"$facebookLoginScheme\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        buildConfig = true
        resValues = true
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    implementation(platform("com.google.firebase:firebase-bom:34.12.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.android.gms:play-services-auth:21.2.0")
    implementation("com.facebook.android:facebook-login:18.2.3")

    // Thư viện Cloudinary cho Android
    implementation("com.cloudinary:cloudinary-android:3.1.2") // Vui lòng dùng version mới nhất
    // Thư viện Firebase Firestore
    implementation("com.google.firebase:firebase-firestore")
    // Thư viện Glide để load ảnh siêu mượt từ URL
    implementation("com.github.bumptech.glide:glide:4.16.0")
}
