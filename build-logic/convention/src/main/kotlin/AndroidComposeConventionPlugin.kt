import com.android.build.api.dsl.ApplicationExtension
import com.android.build.gradle.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

        if (pluginManager.hasPlugin("com.android.application")) {
            extensions.configure<ApplicationExtension> {
                buildFeatures {
                    compose = true
                }
            }
        } else {
            extensions.configure<LibraryExtension> {
                buildFeatures {
                    compose = true
                }
            }
        }
    }
}
