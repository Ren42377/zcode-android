plugins {
    id("zcode.android.library")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.zcode.android.core.mcp"
}

dependencies {
    implementation(project(":core:engine"))
    implementation(project(":core:tools"))
    implementation(project(":core:terminal"))

    implementation(libs.hilt.android)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)

    ksp(libs.hilt.compiler)
}
