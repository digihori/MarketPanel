plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.digihori.marketpanel"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.digihori.marketpanel"
        minSdk = 21
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "MARKET_API_BASE_URL", "\"https://marketpanel-api.yo6987.workers.dev/\"")
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-demo"
            buildConfigField("boolean", "USE_DEMO_DATA", "true")
        }
        create("liveDebug") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".live"
            versionNameSuffix = "-live"
            buildConfigField("boolean", "USE_DEMO_DATA", "false")
            matchingFallbacks += listOf("debug")
        }
        release {
            buildConfigField("boolean", "USE_DEMO_DATA", "false")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
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
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    ksp("androidx.room:room-compiler:2.7.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
