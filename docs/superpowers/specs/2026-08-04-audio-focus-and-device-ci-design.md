# Foco de audio centralizado, y el emulador que lo prueba

**Fecha:** 2026-08-04
**Estado:** Pendiente de aprobación

## Contexto

La Fase 3b se fusionó con nueve verificaciones en dispositivo pendientes, anotadas en la
descripción de su PR. Al ejecutar la primera de ellas apareció esto:

**El mezclador no mezcla.** La app crea los cinco `ExoPlayer`, persiste y restaura la mezcla,
y pinta la paleta correcta — y sólo suena un sonido.

### Lo que se midió, no lo que se supone

Sobre el AVD `Ambio_API37`, API 37, con TalkBack desactivado para descartar ruido, contando
las `AudioPlaybackConfiguration` del proceso de la app:

| Estado del código | Pistas en `started` |
|---|---|
| `handleAudioFocus = true` — lo que hay en `main` | **1 de 5** |
| `handleAudioFocus = false` — sonda de diagnóstico | **5 de 5** |
| Sonda + llamada entrante vía `adb emu gsm call` | 5 de 5 — la mezcla **no se pausa** |

La pila de foco del sistema tenía **una sola entrada** para `com.jbgsoft.ambio`, no cinco.

### La causa

Cada `ExoPlayerSoundTrack` se construye con `handleAudioFocus = true`, así que cada uno pide
el foco por su cuenta con atributos idénticos. El sistema mantiene una entrada por cliente y
cada petición nueva desplaza a la anterior: al añadir un sonido, el que ya sonaba recibe
pérdida de foco y se pausa a sí mismo. Cinco reproductores del mismo proceso compitiendo
entre ellos.

El spec de la Fase 3b anticipó el riesgo, pero en el sitio equivocado: esperábamos que el foco
fallara ante una llamada entrante, y falla mucho antes — entre los propios sonidos. Su plan B
escrito era exactamente el arreglo que aquí se adopta.

### Por qué ningún test lo vio

Catorce rondas de revisión, 344 tests y tres revisores que rompieron código a propósito para
validar sus propios tests. Ninguno podía verlo: el foco de audio no existe en la JVM, sólo en
un sistema Android real.

Y hay un agravante que condiciona el diseño de los tests nuevos. Durante todo el fallo,
**`MixPlayer` reportaba `PlaybackState PLAYING(3)` a la sesión de medios** mientras cuatro de
sus cinco pistas estaban en `paused`. La abstracción decía la verdad sobre lo que había
mandado hacer, y mentía sobre lo que estaba pasando.

## Objetivos

1. Que los cinco sonidos suenen a la vez, que es lo que la Fase 3b prometió.
2. Que una llamada entrante pause la mezcla entera y colgar la reanude — el criterio 10, que
   sigue sin cumplirse.
3. Que exista una prueba automática de las dos cosas, para que no vuelvan a pasar.

## No objetivos

- **El Baseline Profile**, aunque el emulador lo desbloquee. Es otro proyecto, y añadirlo
  infla una PR que ya trae un arreglo crítico y la primera infraestructura instrumentada del
  repositorio.
- **Las nueve verificaciones de la PR #5 al completo.** Cuatro entran aquí; las otras cinco
  —mezcla audible bien equilibrada, tacto del deslizador— dependen de juicio humano o de
  hardware real, y un emulador no las contesta honestamente.
- **F-Droid, widget y tile de Ajustes rápidos.** Los otros tres proyectos de la "Fase 4".

---

## 1. Foco centralizado

`ExoPlayerSoundTrack` pasa a `handleAudioFocus = false`, y `MixPlayer` pide el foco una sola
vez para toda la mezcla.

### La interfaz, y por qué no un `Context`

`MixPlayer(looper, createTrack)` no recibe `Context` hoy, y eso no es accidental: es lo que
permite testearlo en JVM con un doble, una propiedad que costó una ronda de correcciones
conseguir y que es la única cobertura que este código puede tener.

El foco entra por la misma puerta que las pistas:

```kotlin
enum class FocusChange { LOST, LOST_TRANSIENT, LOST_TRANSIENT_DUCK, GAINED }

interface AudioFocus {
    fun request(): Boolean
    fun abandon()
    fun onChange(listener: (FocusChange) -> Unit)
}
```

`AndroidAudioFocus(context)` es la implementación real, sobre `AudioManager` y
`AudioFocusRequest`. `AudioService` la construye igual que ya construye las pistas:

```kotlin
player = MixPlayer(mainLooper, { ExoPlayerSoundTrack(this) }, AndroidAudioFocus(this))
```

Es el patrón que `SoundTrack` ya estableció en este módulo. No se inventa uno nuevo.

### El comportamiento

| Evento del sistema | Qué hace la mezcla |
|---|---|
| Pérdida permanente | pausa entera, abandona el foco |
| Pérdida transitoria (una llamada) | pausa entera, **conserva** el foco |
| Pérdida transitoria con atenuación | multiplica el maestro por **0.2**, sigue sonando |
| Recuperación | reanuda, o restaura el maestro si estaba atenuada |

**Cuándo se pide y se suelta, exactamente:** se pide en `handleSetPlayWhenReady(true)`, antes
de reanudar ninguna pista. Se abandona en `handleStop`, en `handleRelease` y al recibir una
pérdida permanente. Una pausa del usuario —`handleSetPlayWhenReady(false)`— **no** lo suelta,
porque soltarlo y volver a pedirlo en cada pausa haría que otra app se colara en medio.

Una petición denegada deja la mezcla en pausa: no se reproduce sin foco.

El **0.2** es una decisión nuestra, no un valor heredado: es la convención de Android para
atenuar, y no pude confirmar en el artefacto de Media3 1.10.1 qué multiplicador usa su propio
gestor de foco. Queda fijado aquí para que dos implementaciones no elijan números distintos.

**Distinción que importa:** una pausa por pérdida de foco no es lo mismo que una pausa del
usuario. Sólo la primera se reanuda sola al recuperar el foco. `MixPlayer` tiene que
distinguirlas, o colgar una llamada reanudaría audio que el usuario había pausado a mano.

### `setHandleAudioBecomingNoisy`

También está hoy por reproductor, así que desenchufar los auriculares dispara cinco pausas
independientes. Se centraliza igual, por coherencia y por la misma razón: lo que decide sobre
la mezcla debe ser una sola cosa.

## 2. El emulador en CI

Un job nuevo en `.github/workflows/ci.yml`, con `reactivecircus/android-emulator-runner`.

**El riesgo real, y no se cierra leyendo documentación.** El AVD local es API 37 y ahí está
todo verificado, pero las imágenes disponibles para el runner van por detrás. Si 37 no está,
hay que bajar a la más alta que sí esté y **comprobarlo ejecutando el workflow**. La Fase 2
metió tres errores en un plan escrito sólo con lecturas y greps; éste no se escribe así.

El job corre en cada PR. La alternativa —sólo en `main`— dejaría entrar exactamente el tipo de
fallo que este proyecto acaba de sufrir, y lo detectaría después de fusionarlo.

## 3. Cuatro tests, y el primero es el que faltaba

| Test | Qué falla si se rompe |
|---|---|
| **Los cinco sonidos están sonando** | el fallo de hoy, exactamente |
| La app arranca sin estrellarse | la clase de fallo que se coló dos tareas enteras en la Fase 2 con CI en verde |
| Una llamada entrante pausa la mezcla, colgar la reanuda | el criterio 10 |
| La mezcla y sus niveles sobreviven a matar el proceso | la persistencia, hoy cubierta sólo en JVM |

El primero no estaba en el alcance acordado y se añade con motivo: sin él, el arreglo llega
sin la red que impide que vuelva.

### Sobre qué se asierta

**Los tests no le preguntan a la app por su estado.** Es el punto central de este diseño y
sale directamente de lo que pasó: `MixPlayer` reportaba `PLAYING` con cuatro pistas pausadas,
así que un test que asertara sobre `MixPlayer`, sobre el `MediaController` o sobre el
`PlaybackState` de la sesión **habría pasado en verde durante todo el fallo**.

La aserción va contra lo que reporta el sistema: `dumpsys audio`, leído desde el test con
`UiAutomation.executeShellCommand`, contando las `AudioPlaybackConfiguration` del proceso de
la app que están en `state:started`. Es la fuente que no está contaminada por el bug que se
busca.

**Cómo aísla el test su propio proceso.** `dumpsys audio` lista las configuraciones de todo el
sistema, así que hay que filtrar. Cada línea trae `u/pid:<uid>/<pid>`, y el test obtiene el
suyo por el mismo canal de shell: `pidof <packageName>`. Filtrar por nombre de paquete no
sirve — `dumpsys audio` no lo incluye en esas líneas. Sin este filtro el test contaría el audio
de otras apps del emulador y pasaría por motivos ajenos, que es la forma más silenciosa de
tener un test inútil.

**Y hay que esperar, no medir de inmediato.** La app arranca las pistas con un fundido de
entrada de 8 segundos y el estado del sistema tarda en reflejarse; una medición tomada justo
después de pulsar reproducir da un recuento a medias. El test sondea hasta que el recuento se
estabiliza o se agota un plazo, en vez de dormir una cantidad fija elegida a ojo.

Para la llamada entrante, `adb emu gsm call <número>` y `adb emu gsm cancel <número>`
—verificados funcionando sobre el AVD local antes de escribir esto—. El test los invoca por
el mismo camino de shell.

### Lo que esto le cuesta al repositorio

`:app` no tiene source set `androidTest`, y no hay un solo test instrumentado en todo el
proyecto: es el seguimiento más antiguo, abierto desde la Fase 0. Esta parte lo cierra, y con
él llega su coste — un runner de Hilt para tests, las reglas de Compose, y un job de CI que
tarda minutos en vez de segundos.

---

## Criterio de terminación

1. Con los cinco sonidos activos, `dumpsys audio` reporta **cinco** configuraciones en
   `state:started` para el proceso de la app. Verificado por un test, no a mano.
2. Una llamada entrante simulada pausa las cinco, y colgar las reanuda las cinco.
3. Una pausa hecha por el usuario **no** se reanuda al recuperar el foco.
4. La app arranca sin excepción fatal, verificado por un test.
5. La mezcla y sus niveles sobreviven a matar el proceso, verificado por un test.
6. Los cuatro tests corren en CI sobre un emulador, en cada pull request.
7. Los tests unitarios de `MixPlayer` siguen ejecutándose en JVM sin dispositivo: la interfaz
   `AudioFocus` no puede haber arrastrado un `Context` dentro de `MixPlayer`.
8. El lint sigue en 0 errores y los avisos del compilador de Kotlin no suben de 2.
9. Ningún string nuevo queda hardcodeado, comprobado con
   `Text\("|text = "|contentDescription = "`.
