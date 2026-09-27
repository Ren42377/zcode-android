plugins {
    id("zcode.android.library")
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.zcode.android.feature.chat"

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core:agent"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:engine"))
    implementation(project(":core:mcp"))
    implementation(project(":core:tools"))
    implementation(project(":core:storage"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.hilt.android)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.markdown.renderer)
    implementation(libs.markdown.renderer.m3)

    ksp(libs.hilt.compiler)
}
