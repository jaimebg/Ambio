# Fase 3a — Navegación, ajustes y estadísticas

**Fecha:** 2026-08-04
**Estado:** Pendiente de aprobación

## Contexto

Las tres fases anteriores dejaron el toolchain moderno, el proyecto encontrable y la app
traducible y accesible. Ésta es la primera de producto.

El roadmap original planteaba una única "Fase 3" con navegación, ajustes, estadísticas y
mezclador multi-sonido. Al sondear el código resultó que son **dos proyectos**, no uno:

- Las tres primeras son "añadir pantallas": comparten el trabajo de navegación y no tocan la
  capa de audio.
- El mezclador es una reescritura de `AudioService` —hoy un solo `ExoPlayer` con
  `repeatMode = REPEAT_MODE_ONE` envuelto por el `MediaSession`— más una decisión de producto
  sin resolver: si suenan lluvia y hoguera a la vez, de qué color es la app.

El mezclador queda como **Fase 3b**, con su propio spec.

### Lo que ya existe y no se usa

`SessionRepository` expone cuatro operaciones y sólo se llama a una:

| Operación | ¿Se usa hoy? |
|---|---|
| `saveSession(session)` | Sí — `HomeViewModel.kt:337` |
| `getAllSessions()` | **No** |
| `getTotalFocusMinutes()` | **No** |
| `getCompletedSessionCount()` | **No** |
| `deleteSession(id)` | **No** |

`GetSessionStatsUseCase` está completo y devuelve
`SessionStats(totalFocusMinutes, completedSessionCount)`. Nadie lo invoca. La base de datos
lleva acumulando sesiones desde el primer commit sin que ningún usuario las haya visto.

**La pantalla de estadísticas es, por tanto, casi sólo UI.** No hay que tocar la capa de
datos.

## Objetivos

1. Que las sesiones que la app lleva guardando sean visibles para el usuario.
2. Que los tres comportamientos hoy siempre activos —hápticos, chime y efectos— se puedan
   apagar.
3. Que la app tenga navegación, de modo que la Fase 3b y las siguientes puedan añadir
   pantallas sin rehacer nada.

## No objetivos

- **El mezclador multi-sonido.** Es la Fase 3b.
- **Rachas y gráficos semanales.** Se valoraron y se descartaron: exigen calcular series
  temporales desde `completedAt`, lo que añade lógica de dominio y sus tests a una fase que
  no la necesita.
- **Mantener la pantalla encendida durante una sesión.** Era el único ajuste candidato que
  añadía comportamiento nuevo en vez de exponer uno existente; el dueño lo descartó.
- **Separar `UserPreferences` en estado y preferencias.** Ver la nota en §3.

## Diseño

### 1. Módulos y navegación

Dos módulos nuevos, hermanos de `feature:home`:

| Módulo | Contenido |
|---|---|
| `feature:settings` | Pantalla de ajustes y su ViewModel |
| `feature:stats` | Pantalla de estadísticas y su ViewModel |

Cada uno arranca con tres líneas en su bloque `plugins` gracias a los convention plugins de
la Fase 0 — que era el objetivo declarado de aquella fase, y ésta es la primera vez que se
comprueba.

El grafo de navegación vive en `app`, el único módulo que conoce a los tres.

**Sobre `navigation-compose`: no está ausente, está sin declarar.** Verificado sobre
`:app:debugRuntimeClasspath`, la versión **2.9.0** ya llega al classpath de forma transitiva,
arrastrada por `hilt-navigation-compose:1.4.0`. Es decir, el `NavHost` compilaría hoy sin
tocar el catálogo — y eso es precisamente lo que hay que evitar: usar directamente la API de
una librería que no declaramos significa que su versión la decide otra dependencia, y que el
día que `hilt-navigation-compose` deje de arrastrarla el build se rompe por un motivo que no
apunta a la causa.

Se declara explícitamente en el catálogo, en la versión actual **2.9.8**, lo que de paso la
sube desde la 2.9.0 transitiva. Es el mismo tipo de desajuste entre classpath de compilación
y de ejecución que provocó el crash de arranque de la Fase 2, sólo que cogido antes.

**`MainActivity` pasa de llamar a `HomeScreen()` a montar el `NavHost`, y el `AmbioTheme`
sube con él.** Hoy lo aplica `HomeScreen` internamente (`HomeScreen.kt:61`); si se queda
ahí, Ajustes y Estadísticas no heredan el tema del sonido seleccionado y aparecerían con la
paleta por defecto de Material. El tema tiene que envolver el `NavHost`.

### 2. Entrada a las pantallas

**Dos `IconButton` discretos arriba a la derecha, sin barra superior.**

La pantalla principal es deliberadamente inmersiva: ocupa todo el alto, con los efectos
ambientales de fondo y sin cromo. Un `TopAppBar` añadiría una barra permanente sobre esos
efectos y comería altura vertical, que en pantallas por debajo de 600dp ya va justa —
`HomeScreen.kt:84-101` tiene toda una escala de tamaños responsive para exprimirla. Una
navegación inferior de tres pestañas cambiaría el carácter de la app: dejaría de ser una
pantalla inmersiva con extras para ser una app de tres secciones.

Coste asumido: dos elementos más compitiendo por la atención en la zona superior, junto al
selector de modo que ya está ahí.

### 3. Ajustes

`UserPreferences` gana tres booleanos, los tres `true` por defecto:

```kotlin
val hapticsEnabled: Boolean = true
val chimeEnabled: Boolean = true
val effectsEnabled: Boolean = true
```

y `PreferencesDataStore` sus tres `booleanPreferencesKey`. Los consumidores ya existen:

| Ajuste | Dónde se aplica |
|---|---|
| Hápticos | `HapticManager`, 17 puntos de llamada en `feature:home` |
| Chime | `chimePlayer.playChime(...)` en `HomeViewModel.kt:317` |
| Efectos | `AmbientEffectsOverlay` en `HomeScreen.kt:70` |

Cada ajuste es una condición sobre código que ya está escrito. Ninguno añade comportamiento.

**Nota sobre `UserPreferences`.** Con estos tres campos la clase pasa a mezclar dos cosas de
naturaleza distinta: **estado de sesión** —`lastSoundId`, `volume`, `lastMode`, que describen
dónde se quedó el usuario— y **preferencias** —los tres nuevos, que describen cómo quiere que
se comporte la app. Tienen ciclos de vida distintos: un "restablecer ajustes" debería borrar
las segundas y no las primeras. No se separan en esta fase porque sería refactor sin
beneficio inmediato, pero queda anotado: la clase está creciendo en dos direcciones y la
próxima que añada campos debería plantearse el corte.

### 4. Estadísticas

La pantalla muestra los dos totales y la lista de sesiones, cada una con su sonido, duración
y fecha. Borrar una entrada usa el `deleteSession` que ya existe.

Se añade `GetSessionHistoryUseCase`, un envoltorio fino sobre
`sessionRepository.getAllSessions()`. **Fino a propósito:** el proyecto usa casos de uso en
todos sus ViewModels, y uno que llamara al repositorio directamente rompería el patrón que un
contribuidor espera encontrar. La consistencia vale más aquí que evitar una capa.

**El punto que hay que resolver con cuidado:** `Session` guarda `soundId: String`, y desde la
Fase 2 los nombres de sonido son `@StringRes`. La pantalla resuelve
`soundId → SoundRepository.getSoundById(id) → nameRes`. **Ese `getSoundById` devuelve
nullable** (`SoundRepository.kt`), y una sesión guardada con un sonido que ya no exista en la
lista —exactamente lo que pasó cuando "Wind" se renombró a "Cave"— daría `null`. La pantalla
necesita un texto de reserva, no un crash. Ese renombrado ya ocurrió una vez en la historia
de este proyecto, así que no es un caso hipotético.

## Criterio de terminación

1. La app navega entre las tres pantallas, y las tres heredan el tema del sonido
   seleccionado.
2. Apagar cada uno de los tres ajustes suprime su comportamiento, y el ajuste sobrevive a
   cerrar y reabrir la app.
3. La pantalla de estadísticas muestra los totales y el historial de sesiones reales, y
   borrar una entrada la elimina de la base de datos.
4. Una sesión cuyo `soundId` no corresponda a ningún sonido actual se muestra con un texto de
   reserva, sin crash. Verificado con un test.
5. Los 164 tests siguen pasando y el lint sigue en 0 errores.
6. Ningún string nuevo queda hardcodeado: los de los dos módulos nuevos van a sus propios
   `strings.xml`, siguiendo lo que estableció la Fase 2.
7. Verificado en dispositivo: la app arranca, navega, reproduce audio y conserva los ajustes.
