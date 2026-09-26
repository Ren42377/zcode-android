// Root build file. Shared Android configuration lives in the build-logic included build.

buildscript {
    repositories {
        mavenCentral()
    }

    dependencies {
        // AGP 9 ships built-in Kotlin support with Kotlin Gradle Plugin 2.2.10.
        // Raise it to the catalog Kotlin version, as documented in the AGP 9.0
        // release notes. The version catalog is not available in this block.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.20")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
