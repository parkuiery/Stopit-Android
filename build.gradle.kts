// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.jetbrains.kotlin.android) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.jetbrains.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.dagger.hilt) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.gms.google.services) apply false
    alias(libs.plugins.google.devtools.ksp) apply false
    alias(libs.plugins.dd.sdk) apply false
    id("androidx.room") version "2.7.1" apply false
    id("com.google.firebase.crashlytics") version "3.0.6" apply false
}