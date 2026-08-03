import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        // AGP 9 applies Kotlin support implicitly (android.builtInKotlin) as part of
        // registering the "com.android.application" plugin, which is what makes the
        // KotlinAndroidProjectExtension below resolvable even though no Kotlin plugin
        // is applied anywhere in this file. Do NOT add an explicit
        // pluginManager.apply("org.jetbrains.kotlin.android") call: with AGP 9, applying
        // it explicitly on top of the built-in one is a build error, not a no-op. The
        // order here — Android plugin first, then configuring KotlinAndroidProjectExtension
        // — is load-bearing; do not reorder these two blocks.
        pluginManager.apply("com.android.application")

        extensions.configure<ApplicationExtension> {
            compileSdk = 37

            defaultConfig {
                minSdk = 31
                targetSdk = 37
            }

            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
        }

        extensions.configure<KotlinAndroidProjectExtension> {
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_17)
            }
        }
    }
}
