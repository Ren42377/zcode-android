plugins {
    id("zcode.android.library")
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.zcode.android.core.storage"
}

dependencies {
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.hilt.android)
    implementation(libs.kotlinx.coroutines.android)

    ksp(libs.androidx.room.compiler)
    ksp(libs.hilt.compiler)
}
