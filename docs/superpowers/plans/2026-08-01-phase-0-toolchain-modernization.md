# Fase 0 — Modernización del toolchain + CI: Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Poner Ambio en AGP 9.3.1 / Kotlin 2.3.21 con dependencias actuales, eliminar la duplicación de configuración entre los 8 módulos mediante convention plugins, y añadir CI que valide cada cambio.

**Architecture:** Se extraen convention plugins **antes** de subir versiones. Esto no es preferencia estética: AGP 9 elimina el bloque `kotlinOptions`, que hoy está repetido en 8 ficheros. Al extraerlo primero a `build-logic/`, se escribe directamente en la forma nueva (`compilerOptions`, que Kotlin 2.0.21 ya soporta), y la subida de AGP posterior no tiene nada que arreglar. Después, las dependencias suben por grupos de riesgo creciente, verificando entre cada uno.

**Tech Stack:** Gradle 9.6.1, AGP 9.3.1, Kotlin 2.3.21, KSP 2.3.10, Hilt 2.60.1, Compose BOM 2026.06.01, Media3 1.10.1, Room 2.8.4, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-08-01-toolchain-modernization-design.md`

## Global Constraints

- `compileSdk = 36` y `targetSdk = 36` **no cambian** en esta fase.
- `minSdk = 31` no cambia.
- Java 17 (`sourceCompatibility`, `targetCompatibility`, `jvmTarget`) en todos los módulos.
- **Ningún cambio de comportamiento de la app.** Sí están permitidos los cambios de
  configuración de build aunque vivan en ficheros `.kt` de producción — el caso concreto es
  `exportSchema` en `AmbioDatabase.kt` (Tarea 8). Lo prohibido es alterar lo que la app hace.
  Si una subida de versión obliga a modificar lógica, se toca lo mínimo para **restaurar el
  comportamiento existente**, y se reporta.
- El número de tests que pasan nunca debe bajar de la línea base registrada en la Tarea 1.
- Cada tarea termina con el build en verde y un commit propio.
- Rama de trabajo: `chore/phase-0-toolchain-modernization` (ya creada, contiene el spec).

## File Structure

| Fichero | Responsabilidad | Tarea |
|---|---|---|
| `.github/workflows/ci.yml` | Validación en push/PR | 1 |
| `build-logic/settings.gradle.kts` | Build incluido; expone el catálogo `libs` | 2 |
| `build-logic/convention/build.gradle.kts` | Declara los 4 plugins y sus dependencias | 2 |
| `build-logic/convention/src/main/kotlin/AndroidLibraryConventionPlugin.kt` | SDK, Java 17, jvmTarget para librerías | 2 |
| `build-logic/convention/src/main/kotlin/AndroidComposeConventionPlugin.kt` | `buildFeatures.compose` + plugin de Compose | 3 |
| `build-logic/convention/src/main/kotlin/AndroidHiltConventionPlugin.kt` | KSP + Hilt + sus dependencias | 3 |
| `build-logic/convention/src/main/kotlin/AndroidApplicationConventionPlugin.kt` | Equivalente de librería para `app` | 4 |
| `settings.gradle.kts` | `includeBuild("build-logic")` | 2 |
| `gradle/libs.versions.toml` | Versiones; se toca en casi todas las tareas | 2,6,7,8,9,10,11 |
| Los 8 `build.gradle.kts` de módulo | Se vacían de configuración duplicada | 2,3,4 |
| `core/data/.../db/AmbioDatabase.kt` | `exportSchema = true` | 8 |
| `core/data/schemas/` | Esquemas de Room versionados | 8 |

---

### Task 1: CI y línea base

Sin CI no hay forma de saber si los pasos siguientes rompen algo. Esta tarea se hace sobre
el código actual, sin tocar ninguna versión, para que el verde signifique "así estaba antes".

**Files:**
- Create: `.github/workflows/ci.yml`

**Interfaces:**
- Consumes: nada
- Produces: el workflow `CI` y un número de tests de línea base que las tareas 2-11 usan
  como criterio de no regresión.

- [ ] **Step 1: Registrar la línea base local**

```bash
./gradlew clean lint test assembleDebug 2>&1 | tee /tmp/ambio-baseline.log
```

Anotar de la salida: el número total de tests ejecutados y el número de warnings de lint.
Estos dos números son el contrato de no regresión del resto del plan.

Referencia: hay 77 anotaciones `@Test` en el repo (`TimerRepositoryImplTest` 12,
`TimerStateTest` 12, `HomeViewModelTest` 53). Si el build reporta menos de 77 tests
ejecutados, investigar antes de continuar — significa que algún módulo no está corriendo
sus tests.

- [ ] **Step 2: Crear el workflow**

Crear `.github/workflows/ci.yml`:

```yaml
name: CI

on:
  push:
    branches: [ main, 'chore/**' ]
  pull_request:
    branches: [ main ]
  workflow_dispatch:

concurrency:
  group: ${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: true

jobs:
  build:
    name: Lint, test y build
    runs-on: ubuntu-latest

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Configurar JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'

      - name: Configurar Gradle
        uses: gradle/actions/setup-gradle@v4

      - name: Lint
        run: ./gradlew lint

      - name: Tests unitarios
        run: ./gradlew test

      - name: Build debug
        run: ./gradlew assembleDebug

      - name: Publicar informes
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: informes-build
          path: |
            **/build/reports/lint-results-*.html
            **/build/reports/tests/
          retention-days: 7
```

Las tres tareas de Gradle van en pasos separados a propósito: cuando algo falla en CI, el
nombre del paso rojo ya dice qué falló sin abrir los logs.

`assembleDebug` no necesita secretos — `app/build.gradle.kts` cae a la firma de debug
cuando `keystore.properties` no existe, y ese fichero está en `.gitignore`.

- [ ] **Step 3: Verificar que el workflow es válido y pasa**

```bash
git add .github/workflows/ci.yml
git commit -m "ci: add GitHub Actions workflow for lint, test and build"
git push -u origin chore/phase-0-toolchain-modernization
gh run watch
```

Esperado: los tres pasos de Gradle en verde. Si `gh` no está disponible, comprobar en la
pestaña Actions del repositorio.

Si el lint falla en CI pero pasaba localmente, **parar y reportarlo**. No arreglar el lint
aquí, y sobre todo **no quitar ni debilitar el paso de lint del workflow para forzar un
verde** — eso vaciaría de sentido el CI que esta tarea existe para crear. La decisión sobre
qué hacer con esos warnings no es de esta tarea.

---

### Task 2: build-logic y convention plugin de librería

**Files:**
- Create: `build-logic/settings.gradle.kts`
- Create: `build-logic/convention/build.gradle.kts`
- Create: `build-logic/convention/src/main/kotlin/AndroidLibraryConventionPlugin.kt`
- Modify: `settings.gradle.kts`
- Modify: `gradle/libs.versions.toml`
- Modify: `core/common/build.gradle.kts`, `core/domain/build.gradle.kts`,
  `core/data/build.gradle.kts`, `core/di/build.gradle.kts`,
  `feature/home/build.gradle.kts`, `media/build.gradle.kts`, `ui/build.gradle.kts`

**Interfaces:**
- Consumes: la línea base de la Tarea 1.
- Produces: el plugin con id `ambio.android.library`, que fija `compileSdk = 36`,
  `minSdk = 31`, Java 17 y `jvmTarget = JVM_17`. Las tareas 3 y 4 registran plugins
  adicionales en el mismo `build-logic/convention/build.gradle.kts`.

- [ ] **Step 1: Añadir los plugins de Gradle al catálogo de versiones**

En `gradle/libs.versions.toml`, dentro de `[libraries]`, añadir al final:

```toml
# Gradle plugins como dependencias de build-logic
android-gradlePlugin = { group = "com.android.tools.build", name = "gradle", version.ref = "agp" }
kotlin-gradlePlugin = { group = "org.jetbrains.kotlin", name = "kotlin-gradle-plugin", version.ref = "kotlin" }
ksp-gradlePlugin = { group = "com.google.devtools.ksp", name = "com.google.devtools.ksp.gradle.plugin", version.ref = "ksp" }
hilt-gradlePlugin = { group = "com.google.dagger", name = "hilt-android-gradle-plugin", version.ref = "hilt" }
```

- [ ] **Step 2: Crear el build incluido**

Crear `build-logic/settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"
include(":convention")
```

Crear `build-logic/convention/build.gradle.kts`:

```kotlin
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
```

`compileOnly` es correcto y no un error: los plugins llegan al classpath de ejecución
porque el `build.gradle.kts` raíz ya los declara con `apply false`. **No borrar esas
declaraciones del fichero raíz** en ninguna tarea de este plan.

- [ ] **Step 3: Enganchar build-logic al build principal**

En `settings.gradle.kts` (raíz), añadir `includeBuild("build-logic")` como primera línea
dentro de `pluginManagement`:

```kotlin
pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
```

El resto del fichero (bloque `dependencyResolutionManagement`, `rootProject.name` e
`include`) no se toca.

- [ ] **Step 4: Escribir el convention plugin de librería**

Crear `build-logic/convention/src/main/kotlin/AndroidLibraryConventionPlugin.kt`:

```kotlin
import com.android.build.gradle.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")
        pluginManager.apply("org.jetbrains.kotlin.android")

        extensions.configure<LibraryExtension> {
            compileSdk = 36

            defaultConfig {
                minSdk = 31
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
```

Aquí está el núcleo de la estrategia: se usa `compilerOptions` en vez de `kotlinOptions`.
Kotlin 2.0.21 ya lo soporta, así que esto compila hoy — y es la forma que AGP 9 exige, de
modo que la Tarea 6 no tendrá que tocar nada de esto.

- [ ] **Step 5: Aplicar el plugin en los 7 módulos librería**

En cada uno de los 7 módulos, sustituir `alias(libs.plugins.android.library)` y
`alias(libs.plugins.kotlin.android)` por `id("ambio.android.library")`, y borrar del bloque
`android { }` las líneas `compileSdk`, `defaultConfig { minSdk }`, `compileOptions { }` y
`kotlinOptions { }`.

Ejemplo completo — `ui/build.gradle.kts` queda así:

```kotlin
plugins {
    id("ambio.android.library")
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.jbgsoft.ambio.ui"

    buildFeatures {
        compose = true
    }
}

dependencies {
    // Project modules
    implementation(project(":core:domain"))

    // Compose BOM
    implementation(platform(libs.compose.bom))
    implementation(libs.bundles.compose)
    debugImplementation(libs.compose.ui.tooling)

    // AndroidX Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
}
```

`namespace` se queda en cada módulo porque es específico de cada uno. Los bloques
`buildFeatures { compose = true }` y los plugins de Compose y Hilt se quedan por ahora —
son la Tarea 3.

Módulos a modificar y qué conservan:

| Módulo | Plugins que conserva además de `ambio.android.library` |
|---|---|
| `core:common` | `ksp`, `hilt` |
| `core:domain` | `kotlin-compose` |
| `core:data` | `kotlin-compose`, `ksp`, `hilt` |
| `core:di` | `ksp`, `hilt` |
| `feature:home` | `kotlin-compose`, `ksp`, `hilt` |
| `media` | `ksp`, `hilt` |
| `ui` | `kotlin-compose` |

- [ ] **Step 6: Verificar que el build es equivalente**

```bash
./gradlew clean lint test assembleDebug
```

Esperado: verde, con el mismo número de tests y de warnings de lint que la línea base de la
Tarea 1. Ninguna versión de dependencia ha cambiado en esta tarea, así que cualquier
diferencia es atribuible a la refactorización y hay que investigarla antes de seguir.

- [ ] **Step 7: Commit**

```bash
git add build-logic settings.gradle.kts gradle/libs.versions.toml \
        core/*/build.gradle.kts feature/home/build.gradle.kts \
        media/build.gradle.kts ui/build.gradle.kts
git commit -m "build: extract Android library convention plugin

Removes the 12-line SDK/Java/jvmTarget block duplicated across all seven
library modules. Uses compilerOptions instead of kotlinOptions so the
upcoming AGP 9 migration needs no further change here."
```

---

### Task 3: Convention plugins de Compose y Hilt

**Files:**
- Create: `build-logic/convention/src/main/kotlin/AndroidComposeConventionPlugin.kt`
- Create: `build-logic/convention/src/main/kotlin/AndroidHiltConventionPlugin.kt`
- Modify: `build-logic/convention/build.gradle.kts`
- Modify: `core/common/build.gradle.kts`, `core/domain/build.gradle.kts`,
  `core/data/build.gradle.kts`, `core/di/build.gradle.kts`,
  `feature/home/build.gradle.kts`, `media/build.gradle.kts`, `ui/build.gradle.kts`

**Interfaces:**
- Consumes: `ambio.android.library` de la Tarea 2.
- Produces: los ids `ambio.android.compose` y `ambio.android.hilt`. El de Hilt añade por su
  cuenta `implementation(libs.hilt.android)` y `ksp(libs.hilt.android.compiler)`, así que
  los módulos dejan de declararlos.

- [ ] **Step 1: Escribir el convention plugin de Compose**

Crear `build-logic/convention/src/main/kotlin/AndroidComposeConventionPlugin.kt`:

```kotlin
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
```

Se distingue entre aplicación y librería en vez de usar `CommonExtension` genérico porque
esa clase tiene seis parámetros de tipo y configurarla desde un plugin resulta ilegible.

- [ ] **Step 2: Escribir el convention plugin de Hilt**

Crear `build-logic/convention/src/main/kotlin/AndroidHiltConventionPlugin.kt`:

```kotlin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

class AndroidHiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.google.devtools.ksp")
        pluginManager.apply("com.google.dagger.hilt.android")

        val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

        dependencies {
            add("implementation", libs.findLibrary("hilt-android").get())
            add("ksp", libs.findLibrary("hilt-android-compiler").get())
        }
    }
}
```

- [ ] **Step 3: Registrar los dos plugins**

En `build-logic/convention/build.gradle.kts`, dentro del bloque `gradlePlugin { plugins { } }`,
añadir junto al `androidLibrary` existente:

```kotlin
        register("androidCompose") {
            id = "ambio.android.compose"
            implementationClass = "AndroidComposeConventionPlugin"
        }
        register("androidHilt") {
            id = "ambio.android.hilt"
            implementationClass = "AndroidHiltConventionPlugin"
        }
```

- [ ] **Step 4: Aplicar en los módulos**

En cada módulo: sustituir `alias(libs.plugins.kotlin.compose)` por `id("ambio.android.compose")`
y borrar su bloque `buildFeatures { compose = true }`; sustituir la pareja
`alias(libs.plugins.ksp)` + `alias(libs.plugins.hilt)` por `id("ambio.android.hilt")` y
borrar las líneas `implementation(libs.hilt.android)` y `ksp(libs.hilt.android.compiler)`.

`ui/build.gradle.kts` queda:

```kotlin
plugins {
    id("ambio.android.library")
    id("ambio.android.compose")
}

android {
    namespace = "com.jbgsoft.ambio.ui"
}

dependencies {
    // Project modules
    implementation(project(":core:domain"))

    // Compose BOM
    implementation(platform(libs.compose.bom))
    implementation(libs.bundles.compose)
    debugImplementation(libs.compose.ui.tooling)

    // AndroidX Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
}
```

`media/build.gradle.kts` queda:

```kotlin
plugins {
    id("ambio.android.library")
    id("ambio.android.hilt")
}

android {
    namespace = "com.jbgsoft.ambio.media"
}

dependencies {
    // Coroutines
    implementation(libs.bundles.coroutines)

    // Media3
    implementation(libs.bundles.media3)

    // Lifecycle Service
    implementation(libs.lifecycle.service)

    // AndroidX Core
    implementation(libs.androidx.core.ktx)
}
```

Cuidado en `core:data`, `core:di` y `feature:home`: además de Hilt usan `ksp(libs.room.compiler)`.
Esa línea **se queda**; sólo desaparecen las dos de Hilt. El plugin de KSP ya lo aplica
`ambio.android.hilt`, así que `alias(libs.plugins.ksp)` se puede quitar del bloque `plugins`.

Combinación final por módulo:

| Módulo | Bloque `plugins` resultante |
|---|---|
| `core:common` | `library`, `hilt` |
| `core:domain` | `library`, `compose` |
| `core:data` | `library`, `compose`, `hilt` |
| `core:di` | `library`, `hilt` |
| `feature:home` | `library`, `compose`, `hilt` |
| `media` | `library`, `hilt` |
| `ui` | `library`, `compose` |

- [ ] **Step 5: Verificar**

```bash
./gradlew clean lint test assembleDebug
```

Esperado: verde, mismo número de tests que la línea base. Si Hilt falla a generar código en
algún módulo, revisar que `ambio.android.hilt` se esté aplicando ahí y que no haya quedado
un `alias(libs.plugins.ksp)` duplicado.

- [ ] **Step 6: Commit**

```bash
git add build-logic core/*/build.gradle.kts feature/home/build.gradle.kts \
        media/build.gradle.kts ui/build.gradle.kts
git commit -m "build: extract Compose and Hilt convention plugins"
```

---

### Task 4: Convention plugin de aplicación

**Files:**
- Create: `build-logic/convention/src/main/kotlin/AndroidApplicationConventionPlugin.kt`
- Modify: `build-logic/convention/build.gradle.kts`
- Modify: `app/build.gradle.kts`

**Interfaces:**
- Consumes: los plugins de las tareas 2 y 3.
- Produces: el id `ambio.android.application`. Con esto, ningún módulo del proyecto declara
  ya SDK, Java ni jvmTarget por su cuenta — criterio 5 de terminación del spec.

- [ ] **Step 1: Escribir el plugin**

Crear `build-logic/convention/src/main/kotlin/AndroidApplicationConventionPlugin.kt`:

```kotlin
import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")
        pluginManager.apply("org.jetbrains.kotlin.android")

        extensions.configure<ApplicationExtension> {
            compileSdk = 36

            defaultConfig {
                minSdk = 31
                targetSdk = 36
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
```

- [ ] **Step 2: Registrarlo**

En `build-logic/convention/build.gradle.kts`, dentro de `gradlePlugin { plugins { } }`:

```kotlin
        register("androidApplication") {
            id = "ambio.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
```

- [ ] **Step 3: Aplicarlo en `app`**

`app/build.gradle.kts` conserva lo que es específico de la aplicación —`applicationId`,
versiones, firma y `buildTypes`— y pierde lo genérico:

```kotlin
import java.util.Properties

plugins {
    id("ambio.android.application")
    id("ambio.android.compose")
    id("ambio.android.hilt")
}

// Load keystore properties from local file (not committed to git)
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        load(keystorePropertiesFile.inputStream())
    }
}

android {
    namespace = "com.jbgsoft.ambio"

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
            }
        }
    }

    defaultConfig {
        applicationId = "com.jbgsoft.ambio"
        versionCode = 2
        versionName = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (keystorePropertiesFile.exists()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }
}

dependencies {
    // Project modules
    implementation(project(":core:common"))
    implementation(project(":core:data"))
    implementation(project(":core:domain"))
    implementation(project(":core:di"))
    implementation(project(":feature:home"))
    implementation(project(":media"))
    implementation(project(":ui"))

    // Compose BOM
    implementation(platform(libs.compose.bom))
    implementation(libs.bundles.compose)
    debugImplementation(libs.compose.ui.tooling)

    // AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.bundles.lifecycle)

    // Hilt
    implementation(libs.hilt.navigation.compose)

    // Coroutines
    implementation(libs.bundles.coroutines)

    // DataStore
    implementation(libs.datastore.preferences)

    // Room
    implementation(libs.bundles.room)
    ksp(libs.room.compiler)

    // Media3
    implementation(libs.bundles.media3)
}
```

`applicationId`, `versionCode` y `versionName` siguen en `defaultConfig`; lo que sale de ahí
es `minSdk` y `targetSdk`, que ahora los pone el convention plugin.

- [ ] **Step 4: Verificar y comprobar que no queda configuración duplicada**

```bash
./gradlew clean lint test assembleDebug
grep -rn "compileSdk\|kotlinOptions\|targetCompatibility" --include=build.gradle.kts app core feature media ui
```

Esperado: build en verde con el mismo número de tests, y el `grep` **sin resultados** — toda
esa configuración vive ya sólo en `build-logic/`.

- [ ] **Step 5: Commit**

```bash
git add build-logic app/build.gradle.kts
git commit -m "build: extract Android application convention plugin

No module declares compileSdk, minSdk, compileOptions or jvmTarget in its
own build file anymore."
```

---

### Task 5: Gradle wrapper 9.6.1

**Files:**
- Modify: `gradle/wrapper/gradle-wrapper.properties`

**Interfaces:**
- Consumes: el árbol de convention plugins ya en su sitio.
- Produces: Gradle 9.6.1, requisito previo de AGP 9.3.1 en la Tarea 6.

- [ ] **Step 1: Subir el wrapper**

```bash
./gradlew wrapper --gradle-version 9.6.1 --distribution-type bin
./gradlew wrapper --gradle-version 9.6.1 --distribution-type bin
```

Se ejecuta dos veces a propósito: la primera actualiza los scripts, la segunda los regenera
ya con la versión nueva.

- [ ] **Step 2: Verificar**

```bash
./gradlew --version
./gradlew clean lint test assembleDebug
```

Esperado: `Gradle 9.6.1` y build en verde con el mismo número de tests.

**Si falla por configuration cache:** es el riesgo de probabilidad alta identificado en el
spec. Gradle 9 endurece las restricciones y `org.gradle.configuration-cache=true` lleva
activo desde antes. Confirmarlo con:

```bash
./gradlew clean assembleDebug --no-configuration-cache
```

Si con esa bandera pasa, la causa está confirmada. Desactivar la propiedad en
`gradle.properties` con un comentario que diga que se reactiva al cerrar la fase, seguir con
el plan, y reactivarla en la Tarea 11.

- [ ] **Step 3: Commit**

```bash
git add gradle/wrapper/gradle-wrapper.properties gradlew gradlew.bat
git commit -m "build: upgrade Gradle wrapper to 9.6.1"
```

---

### Task 6: AGP 9.3.1, Kotlin 2.3.21, KSP 2.3.10, Hilt 2.60.1

El cambio incompatible de esta tarea —la desaparición de `kotlinOptions`— ya está resuelto
por adelantado gracias a la Tarea 2. Aquí se comprueba que esa apuesta era correcta.

**Corrección del 2026-08-02, tras un primer intento fallido.** Esta tarea apuntaba
originalmente a Kotlin 2.4.10 y dejaba Hilt para la Tarea 11. Ambas cosas eran errores:

- **KSP no tiene release para Kotlin 2.4.x**; la última es 2.3.10, de la línea 2.3. El plan
  afirmaba que KSP 2.3.10 acompañaba a Kotlin 2.4.10 y era falso.
- **Hilt 2.54 no lee metadatos de Kotlin moderno.** El build falló con
  `[Hilt] Provided Metadata instance has version 2.4.0, while maximum supported version is
  2.2.0`. Hilt está acoplado a Kotlin y su subida no puede esperar a la Tarea 11.
- **Hilt 2.60.1 empaqueta `kotlin-metadata-jvm` 2.3.21**, que lee hasta metadatos 2.3 — lo
  que confirma que la línea 2.3 de Kotlin es el objetivo coherente.

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `gradle.properties`

**Interfaces:**
- Consumes: Gradle 9.6.1 de la Tarea 5.
- Produces: el toolchain sobre el que se suben las dependencias en las tareas 7-11.

- [ ] **Step 1: Subir las versiones**

En `gradle/libs.versions.toml`, bloque `[versions]`:

```toml
agp = "9.3.1"
kotlin = "2.3.21"
ksp = "2.3.10"
hilt = "2.60.1"
```

`hilt` sube aquí y no en la Tarea 11: Hilt 2.54 no puede procesar los metadatos que emite
Kotlin 2.3, así que las dos versiones tienen que moverse en el mismo commit.

- [ ] **Step 2: Eliminar la supresión que ya no hace falta**

Borrar de `gradle.properties` estas dos líneas:

```
# Suppress unsupported compile SDK warning
android.suppressUnsupportedCompileSdk=36
```

AGP 9 soporta SDK 36 nativamente.

- [ ] **Step 3: Verificar**

```bash
./gradlew clean lint test assembleDebug
```

Esperado: verde, mismo número de tests.

Notas para el diagnóstico si falla:

- El fallo esperado si algo se pasó por alto es `Unresolved reference: kotlinOptions`. Con la
  Tarea 2 hecha no debería aparecer; si aparece, hay un módulo que se saltó la migración —
  localizarlo con el `grep` del Step 4 de la Tarea 4.
- Si KSP falla al generar código, comprobar que la línea menor de `ksp` coincide con la de
  `kotlin`: KSP 2.3.x acompaña a Kotlin 2.3.x. No existe KSP para Kotlin 2.4.x.
- Si Hilt falla con `Provided Metadata instance has version X, while maximum supported
  version is Y`, la versión de Hilt es demasiado antigua para el Kotlin elegido. Hilt 2.60.1
  empaqueta `kotlin-metadata-jvm` 2.3.21 y por tanto lee metadatos hasta 2.3.
- Kotlin 2.3 puede introducir warnings nuevos de deprecación en código de producción. Anotarlos
  pero **no** arreglarlos en esta tarea: la restricción global prohíbe cambios funcionales.
  Si algún warning es error, parar y reportar.

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml gradle.properties
git commit -m "build: upgrade to AGP 9.3.1, Kotlin 2.3.21, KSP 2.3.10 and Hilt 2.60.1"
```

---

### Task 7: Librerías de test

Primer grupo de dependencias, elegido por ser el más barato: si rompe, el fallo está
contenido en el código de test y no afecta a la app.

**Files:**
- Modify: `gradle/libs.versions.toml`

**Interfaces:**
- Consumes: el toolchain de la Tarea 6.
- Produces: nada que consuman otras tareas.

- [ ] **Step 1: Subir versiones**

En `[versions]`:

```toml
mockk = "1.14.11"
robolectric = "4.16.1"
```

- [ ] **Step 2: Verificar**

```bash
./gradlew clean test
```

Esperado: verde, mismo número de tests que la línea base. Robolectric puede necesitar
descargar artefactos nuevos en la primera ejecución; si tarda, es normal.

- [ ] **Step 3: Commit**

```bash
git add gradle/libs.versions.toml
git commit -m "build: upgrade MockK to 1.14.11 and Robolectric to 4.16.1"
```

---

### Task 8: Room 2.8.4 con exportación de esquemas

Además de subir la versión, esta tarea cierra un hueco real: hoy `AmbioDatabase` declara
`exportSchema = false`, lo que significa que el esquema de la base de datos no se versiona y
una migración futura no sería testeable.

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `core/data/src/main/java/com/jbgsoft/ambio/core/data/local/db/AmbioDatabase.kt:11`
- Modify: `core/data/build.gradle.kts`
- Create: `core/data/schemas/` (generado por el build)

**Interfaces:**
- Consumes: el toolchain de la Tarea 6.
- Produces: `core/data/schemas/com.jbgsoft.ambio.core.data.local.db.AmbioDatabase/1.json`,
  versionado en git. Es la referencia contra la que se validarán futuras migraciones.

- [ ] **Step 1: Subir Room y añadir su plugin de Gradle al catálogo**

En `[versions]`:

```toml
room = "2.8.4"
```

En `[plugins]`, añadir al final:

```toml
room = { id = "androidx.room", version.ref = "room" }
```

- [ ] **Step 2: Aplicar el plugin de Room en `core:data`**

En `core/data/build.gradle.kts`, añadir al bloque `plugins`:

```kotlin
    alias(libs.plugins.room)
```

Y añadir tras el bloque `android { }`:

```kotlin
room {
    schemaDirectory("$projectDir/schemas")
}
```

- [ ] **Step 3: Activar la exportación de esquemas**

En `core/data/src/main/java/com/jbgsoft/ambio/core/data/local/db/AmbioDatabase.kt`, cambiar
la línea 11:

```kotlin
@Database(
    entities = [SessionEntity::class],
    version = 1,
    exportSchema = true
)
```

- [ ] **Step 4: Verificar que el esquema se genera**

```bash
./gradlew clean :core:data:assembleDebug
ls core/data/schemas/com.jbgsoft.ambio.core.data.local.db.AmbioDatabase/
```

Esperado: existe `1.json`. Si el directorio está vacío, el plugin de Room no se está
aplicando o el bloque `room { }` está mal colocado.

- [ ] **Step 5: Verificar el build completo**

```bash
./gradlew clean lint test assembleDebug
```

Esperado: verde, mismo número de tests.

- [ ] **Step 6: Commit**

```bash
git add gradle/libs.versions.toml core/data/build.gradle.kts \
        core/data/src/main/java/com/jbgsoft/ambio/core/data/local/db/AmbioDatabase.kt \
        core/data/schemas
git commit -m "build: upgrade Room to 2.8.4 and export database schemas

Schemas were not being exported, which made future database migrations
untestable. The generated schema is now versioned."
```

---

### Task 9: DataStore, core-ktx, activity-compose y coroutines

**Files:**
- Modify: `gradle/libs.versions.toml`

**Interfaces:**
- Consumes: el toolchain de la Tarea 6.
- Produces: nada que consuman otras tareas.

- [ ] **Step 1: Subir versiones**

En `[versions]`:

```toml
datastore = "1.2.1"
core-ktx = "1.19.0"
activity-compose = "1.13.0"
coroutines = "1.11.0"
```

- [ ] **Step 2: Verificar**

```bash
./gradlew clean lint test assembleDebug
```

Esperado: verde, mismo número de tests. `PreferencesDataStore` y los repositorios que usan
Flow son los candidatos a romper; los tests de `TimerRepositoryImplTest` cubren parte de eso.

- [ ] **Step 3: Commit**

```bash
git add gradle/libs.versions.toml
git commit -m "build: upgrade DataStore, core-ktx, activity-compose and coroutines"
```

---

### Task 10: Media3 1.10.1

La subida con más riesgo funcional del plan: cuatro versiones menores sobre `AudioService`
y `AudioServiceConnection`, y **ningún test cubre el `MediaSessionService`**. Por eso lleva
verificación manual en dispositivo.

**Files:**
- Modify: `gradle/libs.versions.toml`

**Interfaces:**
- Consumes: el toolchain de la Tarea 6.
- Produces: nada que consuman otras tareas.

- [ ] **Step 1: Subir la versión**

En `[versions]`:

```toml
media3 = "1.10.1"
```

- [ ] **Step 2: Verificar la compilación**

```bash
./gradlew clean lint test assembleDebug
```

Esperado: verde. Si `media/src/main/java/com/jbgsoft/ambio/media/AudioService.kt` o
`AudioServiceConnection.kt` no compilan, la API de `MediaSession` ha cambiado; consultar las
notas de versión de Media3 y adaptar, tocando lo mínimo para restaurar el comportamiento
existente. No aprovechar para refactorizar ni para cambiar cómo se comporta el servicio.

- [ ] **Step 3: Verificación manual en dispositivo**

```bash
./gradlew installDebug
```

Comprobar en el dispositivo, en este orden:

1. La app arranca y suena un soundscape al pulsar play.
2. Al minimizar la app, el audio **sigue sonando**.
3. La notificación de reproducción aparece y sus controles de play/pausa funcionan.
4. Al pulsar la notificación, la app vuelve al primer plano.
5. Cambiar de sonido desde el selector reproduce el sonido nuevo.
6. Un temporizador de un minuto llega a cero y suena el chime.

Ninguno de estos seis puntos está cubierto por los tests unitarios. Si alguno falla, parar y
reportar antes de continuar.

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml
git commit -m "build: upgrade Media3 to 1.10.1"
```

---

### Task 11: Compose BOM, Lifecycle y Hilt

Último grupo: la superficie más amplia del proyecto.

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `gradle.properties` (sólo si la Tarea 5 desactivó el configuration cache)

**Interfaces:**
- Consumes: todo lo anterior.
- Produces: el estado final de la Fase 0.

- [ ] **Step 1: Subir versiones**

En `[versions]`:

```toml
compose-bom = "2026.06.01"
lifecycle = "2.11.0"
hilt-navigation-compose = "1.4.0"
```

`hilt` ya no se toca aquí: se adelantó a la Tarea 6 porque está acoplado a la versión de
Kotlin. Sólo sube `hilt-navigation-compose`, que es un artefacto de AndroidX independiente.

- [ ] **Step 2: Verificar**

```bash
./gradlew clean lint test assembleDebug
```

Esperado: verde, mismo número de tests.

Nota sobre `material-icons-extended`: el BOM 2026.06.01 la incluye pinneada en 1.7.8 mientras
el resto de Compose va por 1.10.x, porque la librería de iconos dejó de recibir
actualizaciones. Resuelve sin problema y **no bloquea esta tarea**. El proyecto usa 14 iconos
de ese artefacto; sustituirlos es trabajo de una fase posterior, no de aquí.

- [ ] **Step 3: Reactivar el configuration cache si se desactivó**

Sólo si la Tarea 5 lo desactivó. Restaurar en `gradle.properties`:

```
org.gradle.configuration-cache=true
```

Y verificar:

```bash
./gradlew clean assembleDebug
./gradlew assembleDebug
```

La segunda ejecución debe reportar que reutiliza la configuration cache. Si vuelve a fallar,
dejarlo desactivado con un comentario explicando la causa concreta, y abrir un issue. No
bloquear la fase por esto.

- [ ] **Step 4: Verificación final contra el criterio de terminación del spec**

```bash
./gradlew clean lint test assembleDebug
grep -rn "compileSdk\|kotlinOptions\|targetCompatibility" --include=build.gradle.kts app core feature media ui
./gradlew installDebug
```

Comprobar los cinco criterios del spec:

1. CI en verde (se confirma tras el push del Step 5).
2. Build local en verde sin warnings nuevos respecto a la línea base de la Tarea 1.
3. Número de tests igual o mayor que la línea base.
4. Audio en segundo plano con controles de notificación funcionando en dispositivo.
5. El `grep` no devuelve resultados.

- [ ] **Step 5: Commit y push**

```bash
git add gradle/libs.versions.toml gradle.properties
git commit -m "build: upgrade Compose BOM, Lifecycle and Hilt"
git push
gh run watch
```

Esperado: CI en verde sobre el estado final de la fase.

---

## Resultado esperado de la fase

| | Antes | Después |
|---|---|---|
| Gradle | 8.11.1 | 9.6.1 |
| AGP | 8.8.0 | 9.3.1 |
| Kotlin | 2.0.21 | 2.3.21 |
| Compose BOM | 2025.02.00 | 2026.06.01 |
| Media3 | 1.6.0 | 1.10.1 |
| Room | 2.7.1 | 2.8.4 (con esquemas exportados) |
| Hilt | 2.54 | 2.60.1 |
| Config duplicada | 12 líneas × 7 módulos | 0 |
| CI | ninguna | lint + test + build en cada PR |

Al terminar, la Fase 1 (infraestructura open source) puede montarse sobre un CI que ya
existe, y la Fase 3 puede añadir módulos nuevos con una línea de `plugins` en vez de copiar
el bloque de configuración.
