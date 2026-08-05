# El tile en frío: que suene algo

**Fecha:** 2026-08-05
**Estado:** Pendiente de aprobación

## Contexto

La rama `feat/widget-and-tile` añadió un widget y un tile de Ajustes rápidos. El widget se
eliminó por decisión del dueño —no aportaba nada que la notificación multimedia no hiciera
mejor—. Queda el tile, y la revisión final lo probó en dispositivo y encontró que **no
funciona en el caso que justifica su existencia**.

### Lo que se midió

Sobre `emulator-5554`, disparando el tile de verdad con
`cmd statusbar expand-settings` seguido de `cmd statusbar click-tile`:

| | Resultado |
|---|---|
| El intent llega a `AudioService` | sí — `Background started FGS: Allowed` en el log |
| Suena algo | **no** |
| Se pide el foco de audio | **sí**, silenciando lo que sonara antes |
| La app sobrevive | **no** — `ForegroundServiceDidNotStartInTimeException` |

### La causa

**Nada le dice a `MixPlayer` qué reproducir.** El único emisor de `SET_MIX` es
`AudioServiceConnection`, y sólo se alcanza desde `HomeViewModel`. Con la app cerrada, el
servicio arranca con `entries` vacío.

De ahí sale todo lo demás en cadena: sin entradas no hay timeline, sin timeline Media3 decide
que no hay notificación que mostrar, sin notificación nunca se llama a `startForeground()`, y
el sistema mata el proceso por no cumplir el plazo.

El crash es la consecuencia, no la causa.

**Y un segundo fallo independiente:** `MixPlayer.handleSetPlayWhenReady` pide el foco de audio
antes de comprobar que hay algo que reproducir. Por eso un toque en frío no sólo no suena —
además calla lo que el usuario estuviera escuchando en otra app.

### De quién es el error

Del spec anterior, y es mío. Escribí que el arranque en frío salía gratis porque
`MediaButtonReceiver` levanta el servicio. Levantarlo no basta: sube vacío. Lo di por bueno
razonando sobre el enrutado del intent sin preguntarme qué encontraría el servicio al llegar.

## Objetivos

1. Que un toque en el tile con la app cerrada reproduzca la mezcla guardada.
2. Que no se pida el foco de audio si no hay nada que reproducir.
3. Que el servicio no muera esperando un `startForeground` que nunca va a llegar.

## No objetivos

- **Devolver el widget.** Se eliminó por decisión del dueño y no vuelve.
- **Cambiar la mezcla desde el tile.** Un tile es un interruptor.

---

## 1. Inversión de dependencia, el patrón que este módulo ya usa dos veces

El arreglo natural sería que `AudioService` leyera la mezcla guardada. Choca con una
restricción que este proyecto lleva cuatro ramas sosteniendo y que sus revisores comprueban
cada vez: **`media` no declara ninguna dependencia de proyecto**, y la mezcla vive en DataStore
detrás de `SoundRepository`, en `core:domain`.

`media` no necesita ver el dominio. Necesita que alguien le dé una lista de `MixEntry`, un tipo
que ya es suyo:

```kotlin
// en el módulo media
interface MixSource {
    suspend fun currentMix(): List<MixEntry>
}
```

`AudioService` ya es `@AndroidEntryPoint`, así que la inyecta con Hilt. La implementación vive
donde `SoundRepository` sí es visible, y traduce `ActiveSound` a `MixEntry`.

Es literalmente lo que ya se hizo dos veces en este módulo: `SoundTrack` para no meter
`ExoPlayer` dentro de `MixPlayer`, y `AudioFocus` para no meterle un `Context`. No se inventa
un patrón, se repite el que hay.

**Quién la consulta, y no es el servicio.** La orden de reproducir llega a `MixPlayer` a través
de la sesión; `AudioService` no la ve pasar. Así que el `MixSource` entra por el constructor de
`MixPlayer`, junto al `createTrack` y el `audioFocus` que ya recibe por la misma razón.
`handleSetPlayWhenReady` devuelve un `ListenableFuture`, que es precisamente el mecanismo con
el que `SimpleBasePlayer` admite un handler asíncrono — cargar la mezcla no obliga a torcer
nada.

**El orden exacto, porque el spec sin él admite dos implementaciones distintas:**

```
handleSetPlayWhenReady(true):
  1. si entries está vacío → pedir la mezcla al MixSource y cargarla
  2. si entries SIGUE vacío → no pedir foco, no reproducir, y avisar al servicio para que pare
  3. pedir foco; si lo deniegan → no reproducir
  4. reproducir
```

Los pasos 1 y 3 van en ese orden y no al revés: pedir el foco antes de saber si hay algo que
reproducir es el segundo fallo de este spec, y hacerlo antes de cargar lo reintroduciría.

En caliente `entries` no está vacío, el paso 1 no hace nada y el camino normal no cambia.

## 2. No pedir foco sin nada que reproducir

En `MixPlayer.handleSetPlayWhenReady`, la petición de foco pasa a ir **después** de comprobar
que hay entradas. Sin ellas no se pide foco y no se reproduce.

Son dos fallos que hoy comparten una línea de tiempo pero no tienen relación: uno es "no sé qué
reproducir" y el otro es "pido permiso para algo que no voy a hacer". Se arreglan por separado
porque el segundo seguiría siendo un error aunque el primero no existiera — cualquier otra ruta
que pida reproducir con la mezcla vacía silenciaría otra app igual.

## 3. El cinturón

Si tras consultar el `MixSource` sigue sin haber nada que reproducir —el paso 2 de arriba— el
servicio se para solo en lugar de esperar un `startForeground` que no va a llegar.

`MixPlayer` no puede pararlo: no tiene `Context` y no va a tenerlo. Avisa, y `AudioService`
—que sí lo tiene— llama a `stopSelf()`. El aviso puede ser un `Player.Listener` sobre el estado
o un callback en el constructor; lo que no puede es meter un `Context` dentro de `MixPlayer`,
que es la propiedad que lo mantiene testeable en JVM y la única cobertura que esa clase tiene.

No debería ocurrir: `getActiveMix()` nunca emite vacío. Pero el fallo que este spec arregla
consistía precisamente en un servicio esperando algo que nunca llegaba, y la diferencia entre
un bug y un crash del sistema es exactamente esta salida.

## 4. Lo testeable sin dispositivo

Este proyecto no tiene tests instrumentados y no los va a tener. `MixSource` es una interfaz de
un método, así que la decisión del servicio —"si está vacío, pide y carga"— se prueba en JVM
con un doble, igual que `SoundTrack` y `AudioFocus`. La guarda del foco también.

Importa más de lo normal aquí: la rama tal como está **aporta cero tests netos**, porque los
ocho que añadió murieron con el widget. Esto los devuelve, y sobre la lógica que de verdad
puede fallar.

## 5. La verificación en dispositivo, que ahora sí se puede hacer

El intento anterior se quedó sin comprobar porque `adb` no puede construir el `KeyEvent`
parcelable que lleva el intent, y Media3 descarta el intent sin él.

La revisión final encontró la forma: **`cmd statusbar expand-settings`** para que el tile esté
escuchando, y luego **`cmd statusbar click-tile <componente>`**. Así el toque lo origina el
propio tile, que sí construye el `KeyEvent`. Queda escrito aquí porque es la receta que
convierte el criterio 1 en algo comprobable en vez de en una casilla.

---

## Criterio de terminación

1. Con la app forzada a parar, pulsar el tile reproduce la mezcla guardada. **Verificado en
   emulador** con la receta de §5, no razonado.
2. Con la mezcla vacía, no se pide el foco de audio. Cubierto por un test JVM.
3. Si no hay nada que reproducir, el servicio se para y no aparece
   `ForegroundServiceDidNotStartInTimeException`. Verificado en emulador.
4. La ruta caliente no cambia: con la app abierta y sonando, el tile sigue pausando y
   reanudando.
5. `media` sigue sin declarar ninguna dependencia de proyecto.
6. La lógica nueva del servicio y la guarda del foco están cubiertas por tests JVM.
7. El lint sigue en 0 errores y los avisos del compilador de Kotlin no suben de 2.
