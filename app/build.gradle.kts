plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.ftpserverapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.ftpserverapp"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

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
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes.add("META-INF/DEPENDENCIES")
            // It's also common practice to exclude other potential duplicates:
            excludes.add("META-INF/LICENSE")
            excludes.add("META-INF/LICENSE.txt")
            excludes.add("META-INF/license.txt")
            excludes.add("META-INF/NOTICE")
            excludes.add("META-INF/NOTICE.txt")
            excludes.add("META-INF/notice.txt")
            excludes.add("META-INF/ASL2.0")
            excludes.add("META-INF/*.kotlin_module") // If using Kotlin libraries
        }
    }

}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)


    // *** ADD THESE FOR APPCOMPAT ACTIVITY AND VIEWS ***
    implementation("androidx.appcompat:appcompat:1.6.1") // Or latest stable version
    implementation("androidx.activity:activity-ktx:1.8.2") // Or latest stable version (ktx often brings base activity too)
    implementation("com.google.android.material:material:1.11.0") // Or latest stable version

    // Corrected FTP Server and SLF4J dependencies
    implementation("org.apache.ftpserver:ftpserver-core:1.1.1") // Check for the latest version
    implementation("org.slf4j:slf4j-api:1.7.32") // Or newer - use the latest stable
    implementation("org.slf4j:slf4j-simple:1.7.32")
    implementation(libs.androidx.appcompat) // Simple logger implementation for Android

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}