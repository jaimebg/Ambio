plugins {
    `kotlin-dsl`
}

group = "com.jbgsoft.ambio.buildlogic"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
    compileOnly(libs.hilt.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidLibrary") {
            id = "ambio.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
    }
}
