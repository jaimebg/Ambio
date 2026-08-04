# Fase 3b — Mezclador multi-sonido

**Fecha:** 2026-08-04
**Estado:** Pendiente de aprobación

## Contexto

La Fase 3a separó lo que el roadmap original llamaba "Fase 3" en dos proyectos y dejó éste
fuera con un motivo concreto: el mezclador reescribe la capa de audio y trae una decisión de
producto sin resolver —de qué color es la app cuando suenan lluvia y hoguera a la vez—
mientras que navegación, ajustes y estadísticas sólo añadían pantallas.

Esa decisión ya está resuelta y validada numéricamente (§6). Queda escribirla.

La fase arrastra además un bug que la 3a descubrió y no arregló.

## Objetivos

1. Que suenen varios sonidos a la vez, cada uno con su propio nivel.
2. Que el sonido seleccionado sobreviva a cerrar y reabrir la app.
3. Que la app siga teniendo un color coherente y accesible con cualquier mezcla.

## No objetivos

- **Mezclas guardadas como preajustes.** Se añade cuando alguien las pida.
- **Un límite por debajo de cinco sonidos simultáneos.** El sistema de color asume que las 31
  combinaciones son alcanzables; un tope las volvería inalcanzables sin ganar nada.
- **Volúmenes por sonido en el historial.** Las sesiones guardan qué sonaba, no a qué nivel.

---

## 1. El bug de persistencia va primero, y va solo

`SoundRepositoryImpl` arranca su flujo con un literal:

```kotlin
private val selectedSoundIdFlow = MutableStateFlow("rain")

override fun getSelectedSound(): Flow<Sound> = combine(
    selectedSoundIdFlow,
    preferencesDataStore.preferences
) { currentId, prefs ->
    getSoundById(currentId) ?: getSoundById(prefs.lastSoundId) ?: sounds.first()
}
```

`currentId` siempre resuelve a un sonido válido, así que la segunda rama es código muerto y
`lastSoundId` se escribe sin que nadie lo lea nunca. El arreglo es arrancar el flujo en `null`
y dejar que la reserva funcione.

**Va en su propio commit, antes del mezclador.** Es un bug de usuario que la Fase 3a
amplificó —desde que el tema envuelve el grafo de navegación, las tres pantallas arrancan
azules en vez de sólo la principal— y no tiene por qué esperar a una funcionalidad grande.
Que sea pequeño no lo hace parte del mezclador: si la reescritura de audio se complica, este
arreglo ya está.

El coste de separarlo es explícito y se acepta: `getSelectedSound()` desaparece en §4, así
que estas tres líneas se reescriben dentro de la misma fase. Lo que sobrevive no es el código
sino **el test** —que la selección persiste entre arranques— y ése es el que hay que escribir
aquí, porque hoy no existe ninguno y es lo que dejó pasar el bug.

## 2. Una convención que evita dos migraciones

`Session.soundId` y `UserPreferences.lastSoundId` son ambos `String`. En vez de cambiarles el
tipo, se aprovecha que **una lista de ids separada por comas es un superconjunto válido de un
id suelto**: `"rain"` se lee como lista de uno, `"rain,fireplace"` como lista de dos.

Ni Room ni DataStore necesitan migración, y las sesiones que la base de datos lleva
acumulando desde el primer commit se siguen resolviendo. La pantalla de estadísticas de la
Fase 3a resuelve cada id por separado y reutiliza para cada uno el texto de reserva que ya
construyó para el caso de un sonido renombrado.

Los niveles se codifican con `:` y son opcionales:

| Cadena | Se lee como |
|---|---|
| `"rain"` | lluvia al 100% — el formato antiguo |
| `"rain,fireplace"` | ambos al 100% |
| `"rain:1.0,fireplace:0.6"` | lluvia al 100%, hoguera al 60% |

Un único codificador en `core:domain` sirve a los dos almacenes. DataStore guarda la forma
con niveles; Room guarda la forma sin ellos, porque el historial no los necesita.

**Reglas exactas del formato**, para que el ida y vuelta sea estable:

- Los ids se emiten en el orden de `getAllSounds()`, no en el de activación. Así la misma
  mezcla produce siempre la misma cadena, y dos sesiones con los mismos sonidos son
  comparables.
- Un nivel ausente se lee como `1.0`.
- Al escribir niveles se emiten con dos decimales (`"rain:0.60"`), y se recortan a `[0, 1]`.
- Un segmento cuyo id no exista se descarta al leer; si no queda ninguno, se cae a la mezcla
  por defecto —lluvia al 100%— que es lo que ya hacía `sounds.first()`.

**El nombre del campo cambia, la clave de DataStore no.** `UserPreferences.lastSoundId` pasa
a llamarse `lastMix`, porque ya no guarda un id. La clave subyacente sigue siendo la cadena
`"last_sound_id"`: cambiarla no rompe la compilación y borraría la mezcla de cada usuario en
silencio. Es el mismo tipo de invariante que la Fase 3a dejó anotado para el nombre del
fichero de preferencias.

## 3. La mezcla nunca está vacía

**Siempre hay al menos un sonido activo.** Desactivar el último no hace nada; la tarjeta se
muestra deshabilitada.

No es un detalle de UI: es lo que hace que el espacio de paletas sean exactamente los 31
subconjuntos no vacíos de cinco sonidos. Permitir la mezcla vacía obligaría a inventar un
color para "nada suena", que es un estado que la app no tiene —pausar no cambia el tema.

## 4. Audio: `SimpleBasePlayer`, sin jugador líder

Hoy `AudioService` construye un `ExoPlayer` con `repeatMode = REPEAT_MODE_ONE` y lo envuelve
en un `MediaSession`. Un `MediaSession` envuelve exactamente un `Player`, y ése es el
problema a resolver.

**El diseño:** un `ExoPlayer` por sonido activo, cada uno con su volumen, y una pieza nueva
`MixPlayer : SimpleBasePlayer` que **no delega en ninguno de ellos**. Publica su propio
estado y un único `MediaItem` sintético que describe la mezcla completa, y trata los
`ExoPlayer` como salidas de audio que comanda. El `MediaSession` envuelve el `MixPlayer`.

`SimpleBasePlayer` y `ForwardingSimpleBasePlayer` están ambos en `media3-common` 1.10.1
(verificado sobre el artefacto en la caché de Gradle).

**Por qué no la alternativa barata.** Designar uno de los `ExoPlayer` como líder y envolverlo
con `ForwardingSimpleBasePlayer` es bastante menos código. Falla porque el líder deja de
existir cuando quitas ese sonido de la mezcla, y el delegado no se puede cambiar después de
construirlo: reasignarlo obliga a reiniciar el sonido que pase a ser líder — un corte audible
en un sonido que el usuario no ha tocado, justo al tocar otro.

**Ventaja lateral que pesa en este repositorio:** `MixPlayer` no necesita `Context` ni
dispositivo, así que se testea en JVM. En un proyecto sin un solo test instrumentado, la
diferencia entre una pieza testable y una que no lo es no es teórica.

**Foco de audio — a verificar en dispositivo, no en CI.** Cada `ExoPlayer` conserva su
`handleAudioFocus = true`. Con cinco reproductores del mismo proceso, una llamada entrante
debería pausarlos todos por simetría, y el retorno reanudarlos. Es lo esperado, no lo
comprobado. Si se porta mal, el foco se centraliza en `MixPlayer` y los reproductores pasan a
`handleAudioFocus = false`.

**Notificación.** El `MediaItem` sintético lleva el título de la mezcla. Sigue sin cargar
carátula: las ilustraciones son XML vectorial y `RawResourceDataSource` no las puede abrir.
Es una deuda anterior, anotada desde la Fase 3a, y esta fase no la cierra.

### El canal de control: comandos de sesión personalizados

`AudioServiceConnection` no habla con el servicio por referencia directa, sino a través de un
`MediaController`. La interfaz `Player` que ese controlador expone tiene `play`, `pause`,
`volume` — y ningún sitio por el que quepa "pon el sonido X al 60%".

El canal correcto son los comandos personalizados de Media3, verificados presentes en
`media3-session` 1.10.1:

```kotlin
// en el modulo media, compartidos por servicio y conexion
object MixCommands {
    const val SET_ACTIVE = "com.jbgsoft.ambio.SET_SOUND_ACTIVE"
    const val SET_LEVEL  = "com.jbgsoft.ambio.SET_SOUND_LEVEL"
    const val ARG_SOUND_ID  = "sound_id"
    const val ARG_AUDIO_RES = "audio_res"
    const val ARG_TITLE     = "title"
    const val ARG_ACTIVE    = "active"
    const val ARG_LEVEL     = "level"
}
```

`MediaSession.Callback.onConnect` los declara con `AcceptedResultBuilder`, y
`onCustomCommand` los traduce a llamadas sobre `MixPlayer`.

**Lo que viaja son primitivas, no objetos de dominio.** El módulo `media` no depende de
`core:domain` —comprobado en su `build.gradle.kts`— y no va a empezar a hacerlo: `MixPlayer`
recibe un id opaco, un `@RawRes` y un título, exactamente como el `playSound(audioRes, name,
description, illustrationRes)` que ya existe. El servicio no sabe qué es un `Sound`, y no
tiene por qué.

Es la parte del diseño que más fácil se subestima: sin este apartado, la primera
implementación descubre a mitad de camino que no tiene por dónde mandar un nivel.

### Interfaz que cambia

```kotlin
data class ActiveSound(
    val sound: Sound,
    val level: Float   // 0..1, propio de este sonido; el maestro se aplica aparte
)

interface SoundRepository {
    fun getAllSounds(): List<Sound>
    fun getSoundById(id: String): Sound?
    fun getActiveMix(): Flow<List<ActiveSound>>       // nunca vacío, en orden de getAllSounds()
    suspend fun setSoundActive(soundId: String, active: Boolean)
    suspend fun setSoundLevel(soundId: String, level: Float)
}
```

`getSelectedSound(): Flow<Sound>` y `setSelectedSound(String)` desaparecen. Sus únicos
consumidores son `HomeViewModel` y el ViewModel de tema que introdujo la Fase 3a.

`setSoundActive(id, false)` sobre el último sonido activo es una operación sin efecto, no un
error: la regla de §3 se aplica en el repositorio, no sólo en la UI, para que no dependa de
que una pantalla se acuerde de deshabilitar un botón.

## 5. Volumen

El `VolumeSlider` actual pasa a ser el maestro y conserva los fundidos de entrada y salida
que ya implementa `AudioServiceConnection`. Cada sonido activo añade su propio nivel, y el
volumen efectivo de cada `ExoPlayer` es `maestro × nivel`.

Los niveles se persisten (§2). El maestro ya se persistía.

## 6. Color

### Un sonido: la paleta intacta

Las cinco paletas pintadas a mano no se tocan. La Fase 2 las ajustó para que pasaran WCAG AA
y siguen pasando byte a byte. Ningún usuario actual ve un cambio de color.

### Dos o más: media equitativa, y los colores de texto derivados

Media aritmética por canal de los seis roles, **ignorando los niveles de volumen**. El color
cambia sólo al añadir o quitar un sonido, nunca al mover un deslizador — que es lo que lo
hace predecible, y lo que hace que el espacio de paletas sea finito.

Con dos correcciones sobre los colores de texto:

1. `onPrimary` y `onSecondary` **no se promedian, se derivan**: negro o blanco, el que más
   contraste dé contra el primario y el secundario mezclados. Promediarlos era la causa de
   los fallos — los `onPrimary` de cada tema son fondos tintados, y su media queda demasiado
   clara contra un primario que también se ha ido al tono medio.
2. Si aun así el primario o el secundario no llegan a 4.5, se **aclaran en pasos del 1%**
   hasta llegar.

### El algoritmo, exacto

Los números de la sección siguiente sólo se sostienen sobre esta definición. Una
implementación que redondee distinto produce otros colores.

```
mezclar(sonidos):                          # sonidos.size >= 2
  para cada rol r en {primary, secondary, background, surface, surfaceVariant}:
    para cada canal c en {R, G, B}:
      mezcla[r][c] = Math.round(suma(tema[r][c] de cada sonido) / sonidos.size)

  on = el de {negro, blanco} que maximice
         min(contraste(on, mezcla.primary), contraste(on, mezcla.secondary))

  para cada rol r en {primary, secondary}:                 # como mucho 100 pasos
    mientras contraste(on, mezcla[r]) < 4.5:
      si on es negro:  mezcla[r][c] = Math.round(c + (255 - c) * 0.01)   # aclarar
      si no:           mezcla[r][c] = Math.round(c * 0.99)               # oscurecer

  mezcla.onPrimary = mezcla.onSecondary = on
```

Tres precisiones que no son opcionales:

- **`Math.round` es half-up**, no el redondeo bancario. Con redondeo bancario dos de las 31
  paletas salen desplazadas un punto por canal. Ambos modos pasan WCAG AA, pero sólo uno
  reproduce los valores tabulados abajo.
- **`contraste` es la fórmula de WCAG** ya implementada en `ThemeContrastTest`: linealización
  sRGB, luminancia relativa `0.2126 R + 0.7152 G + 0.0722 B`, y `(L_claro + 0.05) /
  (L_oscuro + 0.05)`.
- La rama de oscurecido **no se ejecuta hoy**: el color derivado es negro en las 31 paletas.
  Se especifica porque el bucle no puede quedar indefinido para un caso que un cambio futuro
  de paleta sí alcanzaría.

### Lo que dieron los números

Recorridas las 31 paletas:

| | Naive (promediar los seis roles) | Diseño final |
|---|---|---|
| Paletas con algún incumplimiento | 26 de 31, **todos** en `onPrimary`/`primary` | **0** |
| Peor caso | 3.61 contra umbral 4.5 | 4.51 contra umbral 4.5 |
| Paletas que necesitan aclarado | — | 1 (`FIREPLACE+OCEAN`, 3 pasos) |

Que los 26 fallos cayeran todos en el mismo par de roles es lo que señaló el arreglo: no
había que abandonar la mezcla equitativa, sólo dejar de promediar dos de los seis colores.

Ejemplos del resultado:

| Mezcla | primary | onPrimary | background |
|---|---|---|---|
| Lluvia | `#6481EB` | `#1A1F3C` | `#1A1F3C` |
| Lluvia + Hoguera | `#A66F78` | `#000000` | `#241C26` |
| Hoguera + Océano | `#77756D` | `#000000` | `#1C191D` |
| Los cinco | `#6D8187` | `#000000` | `#1B1E22` |

**Coste asumido y declarado:** las mezclas de tres o más convergen hacia grises azulados. Los
cinco juntos dan `#6D8187`, que no se parece a ninguno de los cinco temas. Es inherente a
promediar tonos repartidos por la rueda de color. La alternativa —mezclar en HSL por el arco
corto del matiz— produce colores más vivos pero depende del orden de activación, y deja de
ser predecible, que era el requisito.

### La firma del tema cambia

`AmbioTheme(soundTheme: SoundTheme = SoundTheme.RAIN)` anima los seis colores del enum. Con
mezcla, la paleta ya no es un valor del enum, así que el parámetro pasa a ser una paleta:

```kotlin
data class AmbioPalette(
    val primary: Color, val onPrimary: Color, val secondary: Color,
    val background: Color, val surface: Color, val surfaceVariant: Color
)
```

`SoundTheme` sigue siendo la fuente de verdad por sonido y gana un `toPalette()`. La máquina
de animación de `Theme.kt` no cambia: anima seis `Color` venga de donde venga, así que la
transición al añadir o quitar un sonido sale gratis.

### El test deja de muestrear

`ThemeContrastTest` recorre hoy `SoundTheme.entries`. Pasa a recorrer **los 31 subconjuntos
no vacíos**, con el `assertWithMessage` que la Fase 2 añadió nombrando la mezcla que falla.

Es la primera vez en este proyecto que un test de contraste puede ser exhaustivo en lugar de
aproximado, y es consecuencia directa de haber desacoplado el color del volumen: con mezcla
ponderada el espacio sería continuo y sólo cabría muestrear.

## 7. UI

Las tarjetas del `SoundBottomSheet` pasan de selección única a interruptores. Cada sonido
activo despliega su propio deslizador de nivel dentro de la tarjeta.

`CurrentSoundBar` muestra la mezcla. Con uno o dos sonidos, sus nombres; con tres o más, un
recuento, porque cinco nombres no caben en una fila y truncarlos deja una etiqueta ilegible.

Los strings nuevos van al `strings.xml` de `feature:home`, como estableció la Fase 2.

---

## Criterio de terminación

1. El sonido seleccionado sobrevive a cerrar y reabrir la app. Verificado con un test sobre
   `SoundRepositoryImpl` que hoy no existe.
2. Suenan hasta cinco sonidos a la vez, cada uno responde a su propio deslizador, y el
   maestro los afecta a todos.
3. La mezcla y sus niveles sobreviven a cerrar y reabrir la app.
4. Desactivar el último sonido activo no lo desactiva.
5. Una sesión guardada con el formato antiguo (`"rain"`) y una con el nuevo
   (`"rain,fireplace"`) se muestran ambas correctamente en estadísticas, y un id desconocido
   dentro de una lista sigue cayendo al texto de reserva sin arrastrar a los demás.
   Verificado con tests.
6. Las 31 paletas pasan WCAG AA, verificado recorriéndolas todas.
7. `MixPlayer` tiene tests en JVM que cubren añadir, quitar, reproducir, pausar y parar.
8. El lint sigue en 0 errores y los avisos del compilador de Kotlin no suben de 2.
9. Ningún string nuevo queda hardcodeado. Comprobado con
   `Text\("|text = "|contentDescription = "`, el patrón que la Fase 2 tuvo que ensanchar
   después de que uno se le escapara.
10. Verificado en dispositivo: la app arranca, mezcla cinco sonidos, conserva la mezcla al
    reiniciar, y una llamada entrante pausa y reanuda la mezcla completa.
