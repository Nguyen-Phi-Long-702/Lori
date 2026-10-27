import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

val localProperties = Properties().apply {
    val localFile = rootProject.file("local.properties")
    if (localFile.exists()) {
        localFile.inputStream().use { load(it) }
    }
}
val loriApiKey: String = localProperties.getProperty("LORI_API_KEY", "").trim()
val googleWebClientId: String = localProperties.getProperty("GOOGLE_WEB_CLIENT_ID", "").trim()

android {
    namespace = "com.example.lori"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.lori"
        minSdk = 24
        targetSdk = 36
        versionCode = 4
        versionName = "1.3-alpha"

        //URL Backend trên Render (có dấu / ở cuối — Retrofit bắt buộc)
        buildConfigField("String", "BASE_URL", "\"https://lori-25ej.onrender.com/\"")
        //API_KEY lấy từ local.properties; thiếu thì rỗng → Backend sẽ trả 403
        buildConfigField("String", "API_KEY", "\"$loriApiKey\"")
        //Web client ID của Google OAuth (lấy từ local.properties, KHÔNG commit); dùng làm serverClientId khi đăng nhập Google
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleWebClientId\"")

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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    androidResources {
        noCompress += "db"
    }
    androidResources {
        noCompress += "db"
    }
    buildFeatures {
        buildConfig = true
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
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:core:1.6.1")
    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    annotationProcessor("androidx.room:room-compiler:2.6.1")

    // Lifecycle (ViewModel + LiveData) — khai báo tường minh cho MVVM
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.8.7")
    implementation("androidx.lifecycle:lifecycle-livedata:2.8.7")

    // Retrofit + OkHttp
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Gson
    implementation("com.google.code.gson:gson:2.11.0")

    // Glide
    implementation("com.github.bumptech.glide:glide:4.16.0")
    annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")

    // Navigation Component
    implementation("androidx.navigation:navigation-fragment:2.8.0")
    implementation("androidx.navigation:navigation-ui:2.8.0")

    // ViewPager2 (dùng cho OnboardingActivity)
    implementation("androidx.viewpager2:viewpager2:1.1.0")

    // AndroidX Media3 (thay thế ExoPlayer2 đã deprecated)
    implementation("androidx.media3:media3-exoplayer:1.10.0")
    implementation("androidx.media3:media3-ui:1.10.0")
    implementation("androidx.media3:media3-common:1.10.0")

    // CameraX
    implementation("androidx.camera:camera-core:1.4.0")
    implementation("androidx.camera:camera-camera2:1.4.0")
    implementation("androidx.camera:camera-lifecycle:1.4.0")
    implementation("androidx.camera:camera-view:1.4.0")

    // WorkManager
    implementation("androidx.work:work-runtime:2.9.1")

    // Kéo để làm mới + khung xương khi tải (bảng xếp hạng)
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("com.facebook.shimmer:shimmer:0.5.0")

    // Lưu JWT mã hóa (EncryptedSharedPreferences) – Tuần 6 Ngày 3-4
    implementation("androidx.security:security-crypto:1.1.0")

    // Đăng nhập Google qua Credential Manager – Tuần 6 Ngày 3-4
    implementation("androidx.credentials:credentials:1.6.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.6.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    // Hiển thị Markdown trong câu trả lời của AI Tutor
    implementation("io.noties.markwon:core:4.6.2")
}