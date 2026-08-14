plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.locationalarm"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.locationalarm"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // 从 local.properties 读取高德 Key（CI 会将 AMAP_KEY secret 写入此文件）
        val props = java.util.Properties()
        val lpFile = project.rootProject.file("local.properties")
        if (lpFile.exists()) {
            java.io.FileInputStream(lpFile).use { props.load(it) }
        }
        val amapKey = props.getProperty("AMAP_KEY")
            ?: providers.gradleProperty("AMAP_KEY").getOrElse("YOUR_AMAP_KEY_HERE")
        buildConfigField("String", "AMAP_KEY", "\"$amapKey\"")
        manifestPlaceholders["AMAP_KEY"] = amapKey
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

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.lifecycle:lifecycle-service:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // 高德地图合并包：3dmap + location + search（固定版本避免 latest.integration 漂移；11.1.200 含 search 9.7.4，RegeocodeSearch 可用）
    implementation("com.amap.api:3dmap-location-search:11.1.200_loc11.1.200_sea9.7.4")
}
