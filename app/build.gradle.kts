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

val releaseKeystorePath = System.getenv("ANDROID_KEYSTORE_PATH")
val releaseKeystorePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
val releaseKeyAlias = System.getenv("ANDROID_KEY_ALIAS")
val releaseKeyPassword = System.getenv("ANDROID_KEY_PASSWORD")
val hasReleaseSigning = listOf(
    releaseKeystorePath,
    releaseKeystorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { !it.isNullOrBlank() }

fun com.android.build.api.dsl.ProductFlavor.setAdMobConfig(
    applicationId: String,
    blockTop: String,
    homeBottom: String,
    lockBottom: String,
    menuBottom: String,
    routineListBottom: String,
    routineEmptyBottom: String,
) {
    manifestPlaceholders["adMobApplicationId"] = applicationId
    buildConfigField("String", "ADMOB_APPLICATION_ID", "\"$applicationId\"")
    buildConfigField("String", "ADMOB_BLOCK_TOP_AD_UNIT_ID", "\"$blockTop\"")
    buildConfigField("String", "ADMOB_HOME_BOTTOM_AD_UNIT_ID", "\"$homeBottom\"")
    buildConfigField("String", "ADMOB_LOCK_BOTTOM_AD_UNIT_ID", "\"$lockBottom\"")
    buildConfigField("String", "ADMOB_MENU_BOTTOM_AD_UNIT_ID", "\"$menuBottom\"")
    buildConfigField("String", "ADMOB_ROUTINE_LIST_BOTTOM_AD_UNIT_ID", "\"$routineListBottom\"")
    buildConfigField("String", "ADMOB_ROUTINE_EMPTY_BOTTOM_AD_UNIT_ID", "\"$routineEmptyBottom\"")
}

android {
    namespace = "com.uiery.keep"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.uiery.keep"
        minSdk = 33 // 28
        targetSdk = 35
        versionCode = 27
        versionName = "1.7.5"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }
    flavorDimensions += "server"
    productFlavors {
        create("dev") {
            dimension = "server"
            applicationIdSuffix = ".dev"
            setAdMobConfig(
                applicationId = "ca-app-pub-3940256099942544~3347511713",
                blockTop = "ca-app-pub-3940256099942544/6300978111",
                homeBottom = "ca-app-pub-3940256099942544/6300978111",
                lockBottom = "ca-app-pub-3940256099942544/6300978111",
                menuBottom = "ca-app-pub-3940256099942544/6300978111",
                routineListBottom = "ca-app-pub-3940256099942544/6300978111",
                routineEmptyBottom = "ca-app-pub-3940256099942544/6300978111",
            )
        }
        create("prod") {
            dimension = "server"
            setAdMobConfig(
                applicationId = "ca-app-pub-1537867411423705~6734784292",
                blockTop = "ca-app-pub-1537867411423705/5467753282",
                homeBottom = "ca-app-pub-1537867411423705/5120253017",
                lockBottom = "ca-app-pub-1537867411423705/7892727021",
                menuBottom = "ca-app-pub-1537867411423705/3270829732",
                routineListBottom = "ca-app-pub-1537867411423705/7750072748",
                routineEmptyBottom = "ca-app-pub-1537867411423705/9271028233",
            )
        }
    }

    signingConfigs {
        create("release") {
            if (hasReleaseSigning) {
                storeFile = file(requireNotNull(releaseKeystorePath))
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName(if (hasReleaseSigning) "release" else "debug")
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
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    room {
        schemaDirectory("$projectDir/schemas")
    }
    lint {
        checkTestSources = false
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
    testImplementation(libs.mockito.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.test.uiautomator)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.kotlinx.serialization.json)

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
    implementation(libs.firebase.crashlytics.ndk)
    implementation(libs.firebase.config)

    implementation(libs.play.review.ktx)

    implementation(libs.utilcodex)

    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    androidTestImplementation(libs.androidx.room.testing)

    implementation(libs.kotlinx.datetime)

    implementation(libs.google.play.services.ads)

    implementation(project(":core:kds"))
}
