import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")
}

val keystorePropertiesFile = rootProject.file("local.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

android {
    namespace = "com.viteacher.toolkit"
    compileSdk = 35

    signingConfigs {
        create("release") {
            keyAlias = keystoreProperties["signing.key.alias"] as String? ?: ""
            keyPassword = keystoreProperties["signing.key.password"] as String? ?: ""
            storeFile = keystoreProperties["signing.keystore.path"]?.let { file(it as String) }
            storePassword = keystoreProperties["signing.keystore.password"] as String? ?: ""
        }
    }

    defaultConfig {
        applicationId = "com.viteacher.toolkit"
        minSdk = 26
        targetSdk = 35
        versionCode = 21
        versionName = "2.5"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            val isSigningConfigured = keystorePropertiesFile.exists() && 
                    keystoreProperties.containsKey("signing.keystore.path")
            signingConfig = if (isSigningConfigured) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
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

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // Room database
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    // Apache POI for Excel import and export
    implementation("org.apache.poi:poi:5.2.5")
    implementation("org.apache.poi:poi-ooxml:5.2.5")

    // Security for encrypted storage
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Biometric authentication
    implementation("androidx.biometric:biometric:1.1.0")

    // Lifecycle and ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}