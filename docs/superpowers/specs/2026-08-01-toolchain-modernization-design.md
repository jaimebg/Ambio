# Fase 0 — Modernización del toolchain + CI

**Fecha:** 2026-08-01
**Estado:** Aprobado, pendiente de plan de implementación

## Contexto

Ambio lleva sin tocarse desde el release 1.1.0. El toolchain se ha quedado atrás y el
proyecto no tiene integración continua, pese a que el README invita a contribuir.

Esta es la primera de cinco fases del plan para retomar el proyecto:

| Fase | Contenido |
|------|-----------|
| **0** | **Modernización del toolchain + CI** ← este documento |
| 1 | Infraestructura open source (CONTRIBUTING, plantillas, releases, tags) |
| 2 | i18n + accesibilidad |
| 3 | Producto (navegación, ajustes, estadísticas, mezclador multi-sonido) |
| 4 | Distribución y pulido (F-Droid, Baseline Profile, screenshot testing, widget) |

La Fase 0 va primero porque la migración a AGP 9 toca los 8 módulos. Hacerla antes de
añadir módulos nuevos (Fase 3) reduce la superficie a migrar, y sin CI no hay forma de
detectar lo que los tests existentes no cubren.

## Estado actual del build

- 8 módulos: `app`, `core:{common,data,domain,di}`, `feature:home`, `media`, `ui`
- Sin convention plugins. Los 7 módulos librería **repiten el mismo bloque de 12 líneas**:
  `compileSdk = 36`, `minSdk = 31`, `compileOptions` con Java 17 y `kotlinOptions { jvmTarget = "17" }`
- `org.gradle.configuration-cache=true` y `org.gradle.caching=true` ya activos
- Sin `.github/` — no hay CI
- Java 17 (Homebrew OpenJDK 17.0.18)

## Objetivos

1. Toolchain y dependencias en versiones estables actuales
2. CI que valide `lint`, `test` y `assembleDebug` en cada push y PR
3. Eliminar la duplicación de configuración entre módulos, de forma que los módulos que
   añada la Fase 3 no la repitan

## No objetivos

Quedan explícitamente fuera de esta fase:

- ~~Subir `compileSdk` / `targetSdk` por encima de 36.~~ **Revocado el 2026-08-03.** Al
  ejecutar el Paso 5 se descubrió que dos dependencias objetivo lo exigen:
  `core-ktx` 1.19.0 y `hilt-navigation-compose` 1.4.0 declaran `minCompileSdk=37`. Con
  `compileSdk 36` los techos reales serían 1.18.0 y 1.3.0 respectivamente. El dueño decidió
  subir **`compileSdk` y `targetSdk` a 37** en vez de topar las versiones. El SDK 37 está
  en el canal estable (`platforms;android-37.1`, `build-tools;37.0.0`).
  Consecuencia asumida: `targetSdk 37` activa los cambios de comportamiento de Android 17.
  Para una app con `MediaSessionService` en segundo plano, las políticas de foreground
  service son el punto de rotura clásico, y **ningún test automático lo cubre** — la
  verificación manual en dispositivo del Paso 5 pasa de recomendable a imprescindible.
- Corregir la violación de arquitectura donde `core/domain/.../Sound.kt` importa
  `androidx.compose.ui.graphics.vector.ImageVector`, lo que obliga a `core:domain` a
  depender de Compose. El arreglo (usar `@DrawableRes Int`, como ya hace `illustrationRes`
  en ese mismo modelo) es refactor de producto, no de toolchain.
- Baseline Profile y screenshot testing → Fase 4.
- Cualquier cambio funcional de la app.

## Orden de ejecución

Cinco pasos, cada uno verificable de forma independiente.

### Paso 1 — CI sobre el estado actual

Crear `.github/workflows/ci.yml` **antes** de tocar versiones, para establecer una línea
base verde y fiable.

- Disparadores: `push` a `main` y `pull_request` contra `main`
- Runner: `ubuntu-latest`, JDK 17 (`actions/setup-java`, distribución `temurin`)
- Caché de Gradle vía `gradle/actions/setup-gradle`
- Tareas: `./gradlew lint test assembleDebug`

No requiere secretos: el service account de Fastlane está en `.gitignore:68` y no
interviene en el build.

**Verificación:** workflow en verde. Registrar el número de tests que reporta la ejecución
como línea base para los pasos siguientes.

### Paso 2 — Extraer convention plugins

Sin cambiar ni una versión de dependencia. Refactorización pura de configuración.

**Razón del orden:** AGP 9 elimina `kotlinOptions`. Si se sube AGP primero, hay que
arreglar `kotlinOptions` en 8 ficheros y luego borrar esos mismos bloques al extraer los
convention plugins — el trabajo se hace dos veces. Extrayendo primero, el cambio
incompatible se toca en un único sitio.

Estructura, siguiendo la convención de Now in Android para que resulte reconocible a
cualquier contribuidor:

```
build-logic/
  settings.gradle.kts
  convention/
    build.gradle.kts
    src/main/kotlin/
      AndroidApplicationConventionPlugin.kt   → ambio.android.application
      AndroidLibraryConventionPlugin.kt       → ambio.android.library
      AndroidComposeConventionPlugin.kt       → ambio.android.compose
      AndroidHiltConventionPlugin.kt          → ambio.android.hilt
```

Aplicación por módulo:

| Módulo | library | compose | hilt |
|--------|:---:|:---:|:---:|
| `core:common` | ✓ | | ✓ |
| `core:domain` | ✓ | ✓ | |
| `core:data` | ✓ | ✓ | ✓ |
| `core:di` | ✓ | | ✓ |
| `feature:home` | ✓ | ✓ | ✓ |
| `media` | ✓ | | ✓ |
| `ui` | ✓ | ✓ | |

`app` usa `ambio.android.application` + `ambio.android.compose` + `ambio.android.hilt`, y
conserva en su propio fichero lo que le es específico: `applicationId`, `versionCode`,
`versionName`, `signingConfigs` y `buildTypes`.

El `libs.versions.toml` necesita entradas nuevas para que `build-logic` pueda declarar los
plugins como dependencias `compileOnly`:

```toml
android-gradlePlugin = { group = "com.android.tools.build", name = "gradle", version.ref = "agp" }
kotlin-gradlePlugin  = { group = "org.jetbrains.kotlin", name = "kotlin-gradle-plugin", version.ref = "kotlin" }
ksp-gradlePlugin     = { group = "com.google.devtools.ksp", name = "com.google.devtools.ksp.gradle.plugin", version.ref = "ksp" }
```

Y el `settings.gradle.kts` raíz necesita `includeBuild("build-logic")` dentro de
`pluginManagement`.

**Verificación:** CI sigue verde, con el mismo número de tests que la línea base del Paso 1.
Ninguna versión de dependencia ha cambiado, así que cualquier fallo aquí es atribuible
únicamente a la refactorización.

### Paso 3 — Gradle wrapper

`8.11.1` → `9.6.1`. Requisito previo de AGP 9. Se hace aislado para poder atribuir fallos.

**Verificación:** `./gradlew clean assembleDebug lint test` en verde.

### Paso 4 — AGP + Kotlin + KSP

| Herramienta | Actual | Objetivo |
|-------------|--------|----------|
| AGP | 8.8.0 | 9.3.1 |
| Kotlin | 2.0.21 | 2.3.21 |
| KSP | 2.0.21-1.0.28 | 2.3.10 |
| Hilt | 2.54 | 2.60.1 |

**Corrección del 2026-08-02.** El objetivo inicial de este documento era Kotlin 2.4.10. Al
ejecutarlo se comprobó que es inalcanzable hoy, por dos motivos independientes:

- **KSP no tiene ninguna release para Kotlin 2.4.x.** La última es 2.3.10, de la línea 2.3.
  El proyecto necesita KSP para Hilt y Room, así que Kotlin 2.4 no es usable.
- **Hilt 2.60.1 empaqueta `kotlin-metadata-jvm` 2.3.21**, que lee metadatos hasta 2.3.
  Kotlin 2.4.10 emite 2.4.0, de modo que subir Hilt tampoco desbloquea Kotlin 2.4.

Que Hilt 2.60.1 traiga precisamente `kotlin-metadata-jvm` 2.3.21 confirma que la línea 2.3
es el objetivo coherente. Además, **la subida de Hilt se adelanta del Paso 5 a este paso**:
Hilt 2.54 no lee los metadatos de ningún Kotlin moderno, así que está acoplada a Kotlin y
no puede ir después.

Cambio incompatible principal: `kotlinOptions { jvmTarget = "17" }` deja de existir. Se
sustituye por la configuración de `compilerOptions` dentro del convention plugin de
librería — **un solo sitio**, gracias al Paso 2.

Además, eliminar `android.suppressUnsupportedCompileSdk=36` de `gradle.properties`: AGP 9
soporta SDK 36 nativamente y la supresión deja de ser necesaria.

**Verificación:** `./gradlew clean assembleDebug lint test` en verde, mismo número de tests.

### Paso 5 — Resto de dependencias

Versiones estables verificadas contra Google Maven y Maven Central el 2026-08-01:

| Dependencia | Actual | Objetivo |
|-------------|--------|----------|
| Compose BOM | 2025.02.00 | 2026.06.01 |
| Media3 | 1.6.0 | 1.10.1 |
| Room | 2.7.1 | 2.8.4 |
| hilt-navigation-compose | 1.2.0 | 1.4.0 |
| Lifecycle | 2.9.0 | 2.11.0 |
| DataStore | 1.1.4 | 1.2.1 |
| core-ktx | 1.16.0 | 1.19.0 |
| activity-compose | 1.10.0 | 1.13.0 |
| Coroutines | 1.10.1 | 1.11.0 |
| MockK | 1.13.13 | 1.14.11 |
| Robolectric | 4.14.1 | 4.16.1 |

Se suben en este orden, verificando entre grupos: primero las de test (fallo aislado y
barato), después Room y DataStore (persistencia), después Media3 (audio), y por último
Compose BOM y Lifecycle (UI, la superficie más amplia).

**Verificación:** `./gradlew clean assembleDebug lint test` en verde tras cada grupo, y
comprobación manual en dispositivo de que el audio sigue reproduciéndose en segundo plano
con los controles de la notificación — Media3 es la subida con más riesgo funcional y los
tests unitarios no cubren el `MediaSessionService`.

## Riesgos identificados

**Configuration cache (probabilidad alta).** Ya está activo en `gradle.properties:29`.
Gradle 9 endurece bastante sus restricciones y es el candidato más probable a romper
durante el Paso 3 o 4. Mitigación: si bloquea el avance, desactivarlo temporalmente y
reactivarlo como paso final propio, con su propia verificación.

**Room 2.7 → 2.8 (probabilidad media).** Hoy no existe directorio `schemas/`, es decir, el
esquema de la base de datos no se exporta. Esto significa que una migración futura de la BD
no es testeable. Aprovechar este paso para configurar la exportación de esquemas y añadir
`schemas/` al control de versiones.

**Media3 1.6 → 1.10 (probabilidad media).** Cuatro versiones menores de salto sobre
`AudioService` y `AudioServiceConnection`, sin cobertura de tests. De ahí la verificación
manual en dispositivo.

**`material-icons-extended` congelada (sin impacto en esta fase).** El BOM 2026.06.01 la
incluye pinneada en 1.7.8 mientras el resto de Compose va por 1.10.x — la librería dejó de
recibir actualizaciones. Resuelve sin problema, así que **no bloquea la migración**. Queda
anotada como deuda: el proyecto usa 14 iconos de ese artefacto y eventualmente habrá que
sustituirlos por drawables locales. Ese cambio encaja de forma natural con la corrección de
`Sound.kt` mencionada en No objetivos, porque ambos apuntan a lo mismo: que el modelo de
dominio deje de depender de Compose.

## Criterio de terminación

La fase está completa cuando:

1. Los cinco pasos están en verde en CI
2. `./gradlew clean assembleDebug lint test` pasa localmente sin warnings nuevos respecto a
   la línea base del Paso 1
3. El número de tests que pasan es igual o mayor que la línea base
4. La app instalada en dispositivo reproduce audio en segundo plano con controles de
   notificación funcionales
5. Ningún módulo declara ya `compileSdk`, `minSdk`, `compileOptions` ni configuración de
   `jvmTarget` en su propio `build.gradle.kts`
