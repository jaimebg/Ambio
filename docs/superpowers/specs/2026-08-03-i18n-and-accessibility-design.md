# Fase 2 — Internacionalización y accesibilidad

**Fecha:** 2026-08-03
**Estado:** Pendiente de aprobación

## Contexto

La Fase 0 modernizó el toolchain; la Fase 1 hizo el proyecto encontrable e instalable. Esta
fase ataca quién puede *usar* la app.

Estado al empezar, medido sobre el código:

| | |
|---|---|
| `stringResource` en todo el proyecto | **0 usos** |
| Strings de UI hardcodeados | ~33 |
| Locales de la ficha de Play | 48 |
| Idiomas de la app | 1 (inglés) |
| `app/src/main/res/values/strings.xml` | 25 strings, **24 de ellos código muerto** |
| `ui/.../theme/Color.kt` | paleta entera, **código muerto**: nadie la referencia |
| Temas que cumplen WCAG AA | **0 de 5** |

Dos hallazgos que definen el trabajo:

**El `strings.xml` existente nunca se usó.** Vive en el módulo `app`, pero toda la UI está
en `feature:home` y `ui`. Como `app` depende de `feature:home` y no al revés, y el proyecto
tiene `android.nonTransitiveRClass=true`, esos módulos no pueden acceder a esos recursos ni
queriendo. `R.string` no aparece en ningún fichero Kotlin. Sólo `app_name` se usa, desde el
manifest.

**Hay strings de presentación en las capas de dominio y datos.** Los nombres y descripciones
de los sonidos están en `SoundRepositoryImpl` (`core:data`) y los `displayName` de
`TimerPreset` en `core:domain`. Es el mismo tipo de fuga que el `ImageVector` de `Sound.kt`
que la Fase 0 dejó fuera de alcance.

## Objetivos

1. Que ningún string visible por el usuario esté hardcodeado en Kotlin.
2. Que traducir la app a un idioma nuevo sea añadir un fichero, sin tocar código.
3. Que las capas de dominio y datos dejen de contener texto en inglés.
4. Que la app sea usable con TalkBack.
5. Que los cinco temas cumplan WCAG AA.

## No objetivos

- **Traducir la app.** Decisión del dueño: esta fase extrae a recursos y deja las
  traducciones como contribuciones externas. "Traduce Ambio al alemán" es una *good first
  issue* ideal —autocontenida, sin necesidad de entender el código, con impacto visible— y
  encaja con el trabajo de comunidad que sigue pendiente de la Fase 1.
- Rediseñar la paleta con generadores de Material 3. Se hace ajuste mínimo dirigido.
- Modo claro. La app es sólo oscura (`darkColorScheme` incondicional) y sigue siéndolo.
- Corregir el `ImageVector` de `Sound.kt`, que sigue haciendo que `core:domain` dependa de
  Compose. Es refactor de producto y no bloquea nada de esta fase.

## Diseño

### 1. Extracción de strings, cada uno en su módulo

Con `nonTransitiveRClass=true` cada módulo necesita sus propios recursos:

| Fichero | Contenido |
|---|---|
| `feature/home/src/main/res/values/strings.xml` *(nuevo)* | ~20 strings de UI |
| `core/data/src/main/res/values/strings.xml` *(nuevo)* | 10 de sonidos |
| `app/src/main/res/values/strings.xml` | se queda **sólo** con `app_name` |

Los sonidos van en `core:data` porque es donde ya viven sus otros recursos: `res/raw/` con
los audios y `res/drawable/` con las ilustraciones. El texto acompaña al resto del sonido.

Los 24 strings muertos de `app` se borran. Llevan desde el primer commit sin que nadie los
lea, y mantenerlos invita a que alguien los "traduzca" sin efecto.

### 2. Sacar la presentación de dominio y datos

`Sound.name` y `Sound.description` pasan de `String` a `@StringRes nameRes: Int` y
`@StringRes descriptionRes: Int`. **No es un patrón nuevo:** ese mismo modelo ya declara
`@RawRes audioRes` y `@DrawableRes illustrationRes`. La UI los resuelve con
`stringResource()`, y `core:data` deja de contener inglés.

`TimerPreset.displayName` pasa a `@StringRes displayNameRes: Int`.

Dos strings necesitan formato, no sustitución directa:

- **`"25 min"` / `"50 min"`** llevan la unidad incrustada. En otros idiomas la unidad cambia
  y el orden de las palabras también. Pasan a un recurso `plurals` con el número como
  argumento.
- **`"$value $suffix"`** (`TimerPresetSelector.kt:210`) es una concatenación en código, que
  es el mismo problema una capa más abajo. Pasa igualmente a `plurals`.

### 3. Accesibilidad: semántica

- **Elementos decorativos con `contentDescription = null`**, para que TalkBack no los
  anuncie. Hoy varios llevan descripción y generan ruido.
- **`"Decrease"` y `"Increase"` pasan a decir qué ajustan.** Hoy un usuario ciego oye
  "disminuir" sin saber si es la duración del foco, la del descanso o el volumen: el mismo
  par de botones aparece en tres contextos.
- **El estado del temporizador se anuncia al cambiar**, con `liveRegion`. Pasar de Focus a
  Break, o llegar a Completed, es información que hoy sólo existe visualmente — y es
  justamente el evento central de la app.
- **Áreas táctiles a 48dp mínimo**, el umbral de Material.
- **Recorrido completo con TalkBack** en el emulador API 37, validando orden de lectura.

### 4. Contraste

**Los colores viven en `core/domain/.../model/SoundTheme.kt`, no en `ui/.../theme/Color.kt`.**
`Theme.kt` lee `soundTheme.primary`, `soundTheme.background`, etc. del enum de dominio.
`Color.kt` es una segunda copia de la paleta que **nadie referencia**: es código muerto, como
el `strings.xml` de `app`, y con valores que además ya divergen — su bloque `Wind*` define
Cave en gris `7B8794` mientras el enum real la define en marrón `6B5B4F`. Se borra.

Ratios actuales, medidos sobre los valores reales de `SoundTheme.kt`. Umbrales WCAG AA: 3.0
para componentes de UI, 4.5 para texto normal.

| Tema | primary/fondo | primary/surface | onPrimary/primary |
|---|---|---|---|
| Rain | 4.16 | 3.56 | **3.87** ✗ |
| Fireplace | 4.80 | 4.17 | **3.50** ✗ |
| Forest | **2.26** ✗ | **1.87** ✗ | 6.39 |
| Ocean | 3.64 | **2.80** ✗ | 4.87 |
| Cave | **2.71** ✗ | **2.36** ✗ | 6.49 |

**Fallan los cinco temas**, no cuatro.

**La corrección principal no está en los primarios: está en `onPrimary`.** Forzar blanco
como color del texto sobre el botón es lo que crea el conflicto — el primario tendría que ser
a la vez claro para destacar sobre el fondo y oscuro para que el blanco encima se lea. Con
tono y saturación fijos, Forest y Cave **no tienen solución** bajo esa restricción.

Material 3 no exige blanco: sobre un primario claro, lo correcto es texto oscuro. Usando el
**propio color de fondo de cada tema** como `onPrimary` —un color que ya existe en la
paleta, no uno nuevo— el conflicto desaparece:

| Tema | primary | onPrimary | p/fondo | p/surface | on/p | on/sec | ΔL |
|---|---|---|---|---|---|---|---|
| Rain | `5C7AEA` → `6481EB` | `1A1F3C` (fondo) | 4.51 | 3.86 | 4.51 | 5.91 | +0.018 |
| Fireplace | **sin cambio** | `2D1810` (fondo) | 4.80 | 4.17 | 4.80 | 8.25 | 0 |
| Forest | `2D6A4F` → `44A178` | `1B2E1F` (fondo) | 4.54 | 3.77 | 4.54 | 5.82 | +0.153 |
| Ocean | `0077B6` → `0087CE` | `0A1929` (fondo) | 4.52 | 3.48 | 4.52 | 9.16 | +0.047 |
| Cave | `6B5B4F` → `927D6C` | `1C1816` (fondo) | 4.51 | 3.92 | 4.51 | 5.32 | +0.134 |

La columna `on/sec` importa porque `Theme.kt` mapea también `onSecondary` a `onPrimary`: los
cinco pasan con holgura.

Fireplace no cambia ningún color, y Rain y Ocean cambian menos de 0.05 de luminosidad.
**Forest y Cave sí cambian de forma visible, y no es evitable:** con 1.87 y 2.36 de contraste
contra las tarjetas, sus botones de play son hoy casi invisibles. Forest pasa de verde oscuro
a verde medio; Cave, de marrón oscuro a marrón medio.

Nota: `Color.kt` se borra entero en lugar de corregirlo. Mantener dos paletas de las que sólo
una se usa es cómo se llegó a que Cave tuviera dos colores distintos según el fichero.

#### El cambio de `onPrimary` obliga a separar roles en `Theme.kt`

`Theme.kt:55-72` reutiliza `animatedOnPrimary` en **cuatro** roles del `ColorScheme`:

```kotlin
onPrimary = animatedOnPrimary,             // sobre primary   → color claro
onSecondary = animatedOnPrimary,           // sobre secondary → color claro
onPrimaryContainer = animatedOnPrimary,    // sobre surfaceVariant → OSCURO
onSecondaryContainer = animatedOnPrimary,  // sobre surfaceVariant → OSCURO
```

Los dos primeros van sobre colores claros y el texto oscuro es correcto. **Los dos últimos
van sobre `surfaceVariant`, que es oscuro**: poner ahí el fondo del tema daría contraste de
**1.35 a 1.58**, ilegible. Cambiar `onPrimary` sin tocar el mapeo introduciría una regresión
peor que el problema que arregla.

La corrección es separarlos:

| Rol | Valor | Sobre | Ratio |
|---|---|---|---|
| `onPrimary`, `onSecondary` | fondo del tema | primary / secondary (claros) | 4.50–4.80 / 5.82–9.16 |
| `onPrimaryContainer`, `onSecondaryContainer` | `Color.White` | `surfaceVariant` (oscuro) | 9.80–12.17 |

Los pares de texto que ya funcionaban se dejan intactos, verificados: blanco sobre `surface`
da 11.97–14.60, y `onSurfaceVariant` (blanco al 70%) sobre `surfaceVariant` da 5.78–6.83.
Ninguno de los dos se toca.

## Criterio de terminación

1. `git grep -nE 'text = "|contentDescription = "' -- '*.kt'` sobre código de producción no
   devuelve ningún string visible por el usuario.
2. `core/data/.../SoundRepositoryImpl.kt` y `core/domain/.../TimerPreset.kt` no contienen
   texto en inglés.
3. `app/src/main/res/values/strings.xml` contiene únicamente `app_name`.
4. Un script de contraste confirma que los cinco temas pasan los umbrales, **incluidos los
   roles de contenedor** — `onPrimaryContainer` sobre `surfaceVariant` no puede quedar en
   oscuro-sobre-oscuro.
5. Los 154 tests siguen pasando y el lint sigue en 0 errores.
6. Recorrido con TalkBack en emulador API 37: cada control anuncia qué hace, los decorativos
   no se anuncian, y el cambio de estado del temporizador se locuta.
7. La app arranca y reproduce audio tras los cambios, verificado en dispositivo.
