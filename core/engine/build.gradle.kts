plugins {
    id("zcode.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.zcode.android.core.engine"
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
