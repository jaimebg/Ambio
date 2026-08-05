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

## 2. Los tests instrumentados: escritos, y luego eliminados

Esta sección describía originalmente cuatro tests instrumentados y un job de emulador en CI.
Ambas cosas se retiraron durante la ejecución, en dos decisiones separadas del dueño, y se
deja escrito el recorrido porque el resultado condiciona lo que se entrega.

**Primero cayó el job de CI:** un emulador tarda varios minutos por pull request, más que
lint, tests unitarios, `assembleDebug` y `bundleRelease` juntos.

**Después cayeron los tests.** Se llegaron a escribir los cuatro y funcionaron, pero
acumularon un historial que no compensaba: dos defectos intermitentes distintos —uno con un
60% de fallo—, tres helpers que leían la rejilla sin hacer scroll cuando un `LazyVerticalGrid`
ni siquiera compone lo que no se ve, y una última tanda en la que la suite pasó de 66 segundos
a 16 minutos con dos tests reventando en su preparación por degradación del emulador. Una
suite que hay que interpretar antes de creerla cuesta más de lo que protege.

Lo único que sobrevive de aquel trabajo es lo que se aprendió midiendo, que está en el
apartado de contexto de arriba y es lo que permitió encontrar y arreglar el fallo.

### Lo que esto cuesta, dicho sin adornos

El arreglo del foco sigue entero y cubierto por 372 tests JVM. Lo que desaparece es la
verificación automática de que *suena*: varios criterios de abajo pasan de "verificado por un
test" a "comprobado a mano una vez, sobre API 37, durante esta sesión". Esa comprobación
existió y sus números están en el contexto, pero nada la repetirá sola.

Queda por tanto en pie el riesgo que originó todo este trabajo: **un cambio futuro puede
volver a romper la mezcla sin que nada avise.** Es una decisión consciente sobre coste, no un
descuido, y se anota para que quien lo lea después no crea que hay una red que no hay.

## Criterio de terminación

Reescrito tras eliminar la suite instrumentada. Cada línea dice cómo está verificada de
verdad, no cómo se pensaba verificar.

1. Con los cinco sonidos activos suenan cinco pistas. **Comprobado a mano**, API 37:
   `dumpsys audio` reportaba 1 de 5 antes del arreglo y 5 de 5 después. No hay test.
2. Una llamada entrante pausa las cinco y colgar las reanuda. **Comprobado a mano** con
   `adb emu gsm call` / `cancel`: 5 → 0 → 5. No hay test.
3. Una pausa del usuario **no** se reanuda al recuperar el foco. **Cubierto por test JVM** en
   `MixPlayerTest`, y validado por mutación: borrar `pausedByFocusLoss = false` lo hace fallar.
4. Desenchufar los auriculares pausa la mezcla. **Sin verificar.** El receptor está escrito y
   revisado en `AudioService`, pero las dos mediciones que se intentaron salieron inconclusas.
   Es lo único de esta lista que se entrega sin haber sido observado funcionando ni una vez.
5. La mezcla y sus niveles sobreviven a matar el proceso. **Comprobado a mano** para la mezcla;
   los niveles no se comprobaron en ningún momento.
6. El foco es uno solo para toda la mezcla, no uno por reproductor. **Cubierto por test JVM.**
7. `MixPlayer` sigue testeándose en JVM sin dispositivo: `AudioFocus` no arrastró un `Context`
   dentro. **Cubierto**, y verificado en la revisión final.
8. Lint en 0 errores y los avisos de Kotlin en 2. **Cubierto**, medido con `--rerun-tasks
   --no-build-cache`.
9. Ningún string nuevo hardcodeado. **Cubierto** por el grep de la Fase 2.
