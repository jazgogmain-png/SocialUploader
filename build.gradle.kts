// Project-level build file
buildscript {
    repositories {
        google()
        mavenCentral()
        // Essential for TikTok SDK
        maven { url = uri("https://artifact.bytedance.com/repository/AwemeOpenSDK") }
    }
    dependencies {
        // These are the core tools for Android and Kotlin 1.9.22
        classpath("com.android.tools.build:gradle:8.3.0")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.22")
    }
}

plugins {
    // We define them here but don't "apply" them to the root project
    id("com.android.application") version "8.3.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
}
