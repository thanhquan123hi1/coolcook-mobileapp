import com.google.gms.googleservices.GoogleServicesTask
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
}

tasks.withType<GoogleServicesTask>().configureEach {
    // Avoid the plugin's default generated/res/<taskName> path, which can stay locked on Windows.
    outputDirectory.set(layout.buildDirectory.dir("generated/google-services/$name"))
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

val localProperties = Properties().apply {
    val localFile = rootProject.file("local.properties")
    if (localFile.exists()) {
        localFile.inputStream().use { load(it) }
    }
}

fun envValue(name: String): String =
    (envProperties.getProperty(name) ?: "").replace("\"", "\\\"")

fun secretValue(name: String): String =
    (envProperties.getProperty(name)
        ?: localProperties.getProperty(name)
        ?: "").replace("\"", "\\\"")

fun fbLoginScheme(appId: String): String {
    if (appId.isBlank()) return ""
    return if (appId.startsWith("fb", ignoreCase = true)) appId else "fb$appId"
}

// layout.buildDirectory.set(rootProject.layout.projectDirectory.dir(".gradle-build/app"))
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
        buildConfigField("String", "GEMINI_API_KEY", "\"${secretValue("GEMINI_API_KEY")}\"")
        buildConfigField("String", "GROQ_API_KEY", "\"${secretValue("GROQ_API_KEY")}\"")

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

    packaging {
        resources {
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/LICENSE"
            excludes += "META-INF/LICENSE.txt"
            excludes += "META-INF/NOTICE"
            excludes += "META-INF/NOTICE.txt"
        }
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.9.4")
    implementation("androidx.lifecycle:lifecycle-livedata:2.9.4")
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    implementation(platform("com.google.firebase:firebase-bom:34.12.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-auth")
    implementation("androidx.credentials:credentials:1.5.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.5.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation("com.facebook.android:facebook-login:18.2.3")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("androidx.camera:camera-core:1.4.2")
    implementation("androidx.camera:camera-camera2:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    implementation("androidx.camera:camera-view:1.4.2")
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    // Thư viện Cloudinary cho Android
    implementation("com.cloudinary:cloudinary-android:3.1.2") // Vui lòng dùng version mới nhất
    // Thư viện Firebase Firestore
    implementation("com.google.firebase:firebase-firestore")
    // Thư viện Glide để load ảnh siêu mượt từ URL
    implementation("com.github.bumptech.glide:glide:4.16.0")
}
