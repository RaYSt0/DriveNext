import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "ru.student.drivenext"
    compileSdk = 35

    defaultConfig {
        applicationId = "ru.student.drivenext"
        minSdk = 31
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        val local = Properties().apply {
            rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
        }
        buildConfigField("String", "SUPABASE_URL", "\"${local.getProperty("supabase.url", "")}\"")
        buildConfigField("String", "SUPABASE_KEY", "\"${local.getProperty("supabase.publishableKey", "")}\"")
    }

    buildFeatures { buildConfig = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.core:core-splashscreen:1.0.1")
}
