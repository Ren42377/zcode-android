plugins {
    id("zcode.android.library")
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.zcode.android.feature.settings"

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core:agent"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:engine"))
    implementation(project(":core:mcp"))
    implementation(project(":core:storage"))
    implementation(project(":core:terminal"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.hilt.android)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)

    ksp(libs.hilt.compiler)
}
