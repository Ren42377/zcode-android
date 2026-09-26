plugins {
    id("zcode.android.library")
    alias(libs.plugins.kotlin.compose)
}

android {
    // The namespace matches the vendored termlib package so its generated R class
    // resolves inside the vendored sources. See VENDORED.md.
    namespace = "org.connectbot.terminal"

    defaultConfig {
        ndk {
            // arm64-v8a is the primary target; x86_64 keeps emulator QA possible.
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.core.ktx)
}
