plugins {
    id("zcode.android.library")
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.zcode.android.core.designsystem"

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)

    testImplementation(libs.junit)
}
