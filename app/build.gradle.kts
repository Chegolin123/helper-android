import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Читаем секреты из local.properties (не попадает в git).
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val deepseekKey: String = localProps.getProperty("DEEPSEEK_API_KEY", "")
val deepseekUrl: String = localProps.getProperty("DEEPSEEK_BASE_URL", "https://api.deepseek.com/v1")
val deepseekModel: String = localProps.getProperty("DEEPSEEK_MODEL", "deepseek-v4-flash")

android {
    namespace = "com.helper.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.helper.app"
        minSdk = 23
        targetSdk = 35
        versionCode = 20000
        versionName = "2.0.0"

        buildConfigField("String", "DEEPSEEK_API_KEY", "\"$deepseekKey\"")
        buildConfigField("String", "DEEPSEEK_BASE_URL", "\"$deepseekUrl\"")
        buildConfigField("String", "DEEPSEEK_MODEL", "\"$deepseekModel\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)
    // Фича 2: геолокация
    implementation(libs.google.play.services.location)
    implementation(libs.kotlinx.coroutines.play.services)
}
