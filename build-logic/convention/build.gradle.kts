plugins {
    `kotlin-dsl`
}

group = "com.zcode.android.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    compileOnly(libs.android.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "zcode.android.application"
            implementationClass = "com.zcode.android.buildlogic.AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "zcode.android.library"
            implementationClass = "com.zcode.android.buildlogic.AndroidLibraryConventionPlugin"
        }
    }
}
