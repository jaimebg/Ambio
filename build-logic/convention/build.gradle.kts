import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
}

group = "com.jbgsoft.ambio.buildlogic"

// Deliberately no `toolchain { languageVersion = 17 }` here. A toolchain block
// demands an exact JDK 17 *installation*, and F-Droid's build server ships only
// openjdk-21 with auto-provisioning disabled, so resolving it was impossible
// there: the build died at `Cannot find a Java installation matching
// {languageVersion=17}` before compiling a line.
//
// Pinning the output bytecode instead gives the same artifact on any JDK 17 or
// newer. Both halves are required and must agree — Gradle fails the build on an
// inconsistent JVM-target between Java and Kotlin.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
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
        register("androidApplication") {
            id = "ambio.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "ambio.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "ambio.android.compose"
            implementationClass = "AndroidComposeConventionPlugin"
        }
        register("androidHilt") {
            id = "ambio.android.hilt"
            implementationClass = "AndroidHiltConventionPlugin"
        }
    }
}
