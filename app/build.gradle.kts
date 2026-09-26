plugins {
    id("zcode.android.application")
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// The release workflow passes -Papp.versionName=<tag> so APK metadata matches the tag.
val appVersionName: String = providers.gradleProperty("app.versionName").orElse("0.1.0").get()
val versionParts = appVersionName.split(".").map { it.toInt() }
require(versionParts.size == 3) { "app.versionName must have the form major.minor.patch, got $appVersionName" }
val appVersionCode: Int = versionParts[0] * 10_000 + versionParts[1] * 100 + versionParts[2]

android {
    namespace = "com.zcode.android"

    defaultConfig {
        applicationId = "com.zcode.android"
        versionCode = appVersionCode
        versionName = appVersionName
    }

    signingConfigs {
        create("release") {
            val storeFilePath = System.getenv("KEYSTORE_FILE") ?: return@create
            storeFile = file(storeFilePath)
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS")
            keyPassword = System.getenv("KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig =
                if (System.getenv("KEYSTORE_FILE") != null) {
                    signingConfigs.getByName("release")
                } else {
                    signingConfigs.getByName("debug")
                }
        }
    }

    buildFeatures {
        compose = true
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a")
            isUniversalApk = true
        }
    }
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:agent"))
    implementation(project(":core:engine"))
    implementation(project(":core:tools"))
    implementation(project(":core:terminal"))
    implementation(project(":core:mcp"))
    implementation(project(":core:storage"))
    implementation(project(":core:config"))
    implementation(project(":feature:chat"))
    implementation(project(":feature:terminal"))
    implementation(project(":feature:workspace"))
    implementation(project(":feature:settings"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.android)

    ksp(libs.hilt.compiler)
}
