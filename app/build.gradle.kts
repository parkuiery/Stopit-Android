import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.dagger.hilt)
    alias(libs.plugins.gms.google.services)
    alias(libs.plugins.google.devtools.ksp)
    id("androidx.room")
    id("com.google.firebase.crashlytics")
}

val properties = Properties()
properties.load(rootProject.file("./local.properties").inputStream())

android {
    namespace = "com.uiery.keep"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.uiery.keep"
        minSdk = 33 // 28
        targetSdk = 35
        versionCode = 19
        versionName = "1.5.7"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        buildConfigField(
            type = "String",
            name = "DATA_DOG_CLIENT_TOKEN",
            value = properties.getProperty("DATA_DOG_CLIENT_TOKEN")
        )
        buildConfigField(
            type = "String",
            name = "DATA_DOG_APPLICATION_ID",
            value = properties.getProperty("DATA_DOG_APPLICATION_ID")
        )
    }
    flavorDimensions += "server"
    productFlavors {
        create("dev") {
            dimension = "server"
            //applicationIdSuffix = ".dev"
            buildConfigField(
                type = "String",
                name = "BASE_URL",
                value = properties.getProperty("BASE_URL_DEV","\"\"")
            )
        }
        create("prod") {
            dimension = "server"
            buildConfigField(
                type = "String",
                name = "BASE_URL",
                value = properties.getProperty("BASE_URL_PROD","\"\"")
            )
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }

        debug {
            isDebuggable = true
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
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    room {
        schemaDirectory("$projectDir/schemas")
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
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.kotlinx.serialization.json)

    implementation(libs.accompanist.systemuicontroller)

    implementation(libs.orbit.core)
    implementation(libs.orbit.viewmodel)
    implementation(libs.orbit.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.lottie.compose)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.messaging)
    implementation("com.google.firebase:firebase-crashlytics-ndk")

    implementation(libs.utilcodex)

    implementation(libs.dd.sdk.android.rum)
    implementation("com.datadoghq:dd-sdk-android-session-replay:2.19.2")
    implementation("com.datadoghq:dd-sdk-android-session-replay-material:2.19.2")
    implementation("com.datadoghq:dd-sdk-android-session-replay-compose:2.19.2")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation(platform("com.squareup.okhttp3:okhttp-bom:4.12.0"))
    implementation("com.squareup.okhttp3:logging-interceptor")
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-gson:3.0.0")

    implementation("androidx.room:room-runtime:2.7.1")
    ksp("androidx.room:room-compiler:2.7.1")

    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")

    implementation("com.google.android.gms:play-services-ads:23.0.0")

    implementation(project(":core:kds"))
}
