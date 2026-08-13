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

        // 从 local.properties 读取高德 Key（未配置时使用占位符，可编译但地图不可用）
        val amapKey = providers.gradleProperty("AMAP_KEY").getOrElse("YOUR_AMAP_KEY_HERE")
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

    // 高德地图三件套：3dmap + search + location（版本已在阿里云 Maven 验证）
    implementation("com.amap.api:3dmap:9.8.2")
    implementation("com.amap.api:search:9.7.0")
    implementation("com.amap.api:location:6.4.9")
}
