# Widget de pantalla de inicio y tile de Ajustes rápidos

**Fecha:** 2026-08-05
**Estado:** Pendiente de aprobación

## Contexto

Ambio reproduce mezclas de hasta cinco sonidos ambientales. Hoy la única forma de pausar o
reanudar es abrir la app, o usar la notificación multimedia mientras suena.

Este proyecto añade dos superficies fuera de la app: un widget en la pantalla de inicio y un
tile en el desplegable de Ajustes rápidos. Es uno de los tres proyectos independientes en que
se descompuso la "Fase 4"; los otros dos —F-Droid y Baseline Profile— no dependen de éste ni
él de ellos.

### Lo que hereda del trabajo anterior

- `AudioService` es un `MediaSessionService` exportado, así que su sesión es alcanzable desde
  fuera del proceso de la UI.
- `MixCodec` guarda la mezcla en DataStore bajo la clave `"last_sound_id"`, de modo que la
  mezcla persistida está disponible aunque el servicio no esté corriendo.
- `mixPalettes(themes)` produce una paleta para cualquier combinación de sonidos, verificada
  contra WCAG AA en las 31 combinaciones posibles.

## Objetivos

1. Pausar y reanudar la mezcla sin abrir la app, desde la pantalla de inicio y desde Ajustes
   rápidos.
2. Ver de un vistazo qué está sonando.
3. Que funcione en frío: si la app lleva horas sin abrirse, pulsar play arranca el servicio y
   reproduce la mezcla guardada.

## No objetivos

- **Cambiar la mezcla desde el widget.** Se valoró y se descartó: cada toque necesitaría
  construir un `MediaController` asíncrono desde un proceso efímero, que es exactamente la
  parte frágil de este tipo de trabajo. El widget muestra y controla la reproducción, no la
  composición.
- **Configuración al añadir el widget.** Nada que elegir todavía.
- **Un widget redimensionable con varias disposiciones.** Un solo tamaño, hecho bien:
  **4×1**, el ancho completo de la rejilla y una fila de alto. Cabe el título de la mezcla y
  el botón, que es todo lo que muestra, y es la forma que la gente ya reconoce como "controles
  de reproducción". `minWidth` 250dp y `minHeight` 50dp en el `appwidget-provider`.

---

## 1. El control no construye un `MediaController`

Un widget y un tile viven en procesos efímeros. Construir un `MediaController` es asíncrono
—`buildAsync`— y puede no completarse antes de que el proceso muera. Es donde este tipo de
proyecto se rompe, y de forma intermitente, que es la peor manera.

En vez de eso, ambas superficies mandan un `PendingIntent` con `Intent.ACTION_MEDIA_BUTTON` y
un `KeyEvent` de `KEYCODE_MEDIA_PLAY_PAUSE`, dirigido a
`androidx.media3.session.MediaButtonReceiver` —clase de Media3, verificada presente en el
artefacto 1.10.1— declarado en el manifiesto.

Media3 lo enruta a `AudioService` **y arranca el servicio si no está corriendo**. Eso es lo
que hace que el objetivo 3 salga gratis: no hay que escribir ningún camino especial para el
arranque en frío.

Ninguna de las dos superficies necesita código de conexión propio.

## 2. El estado lo empuja el servicio

El widget muestra dos cosas, y vienen de sitios distintos:

| Qué muestra | De dónde sale | Disponible cuando |
|---|---|---|
| Qué sonidos componen la mezcla | DataStore | siempre |
| Si está sonando | lo empuja `AudioService` | sólo con el servicio vivo |

**Dos disparadores, cada uno cubriendo lo que sólo él sabe:**

| Cambia | Quién actualiza el widget | Por qué ése |
|---|---|---|
| El estado de reproducción | `AudioService` | es el único que lo sabe |
| La mezcla | la app, al observar `getActiveMix()` | ver abajo |

La segunda fila no es arbitraria. `pushMix()` no llega al servicio cuando el controlador es
nulo, así que el servicio **no se entera** de un cambio de mezcla si no está corriendo — y
confiarle la actualización dejaría el widget desfasado justo en ese caso. Pero la mezcla sólo
la escribe la UI de la app, que es la única que puede cambiarla, así que la app siempre está
viva cuando ocurre. Disparar desde ahí no tiene huecos.

**El detalle que se convierte en bug si no se escribe:** al destruirse, el servicio tiene que
empujar explícitamente "no suena". Glance conserva el último estado escrito, así que sin ese
paso el widget se queda mostrando un botón de pausa sobre un servicio que ya no existe, y el
usuario ve una mentira hasta que toque algo.

Igual al arrancar el sistema: sin estado escrito, la lectura por defecto es "no suena".

## 3. Módulo `feature:widget`

Consistente con `feature:home`, `feature:settings` y `feature:stats`, y mantiene `app`
delgado — hoy es sólo `MainActivity` y el grafo de navegación.

**Los módulos `feature` de este proyecto no tienen manifiesto propio**, comprobado: todo se
declara en `app/src/main/AndroidManifest.xml`. Este módulo rompe ese patrón por necesidad —
un widget y un tile son componentes declarados, y dejarlos en `app` separaría la declaración
del código. Lleva su propio `AndroidManifest.xml`, que el merge de AGP integra.

Se declaran ahí tres cosas: el `AppWidgetProvider` de Glance, el `TileService`, y el
`MediaButtonReceiver` de Media3 con su filtro `ACTION_MEDIA_BUTTON`.

**Glance para el widget**, porque el proyecto es Compose de arriba abajo. **Un `TileService`
normal para el tile**, que no necesita Glance.

### Cómo llegan las dependencias a cada uno

No es simétrico, y conviene saberlo antes de empezar:

- `TileService` extiende `Service`, así que admite `@AndroidEntryPoint` y Hilt lo inyecta
  normal.
- `GlanceAppWidget` **no es un componente de Android**, así que Hilt no puede inyectarlo. Sus
  dependencias se obtienen con `EntryPointAccessors` sobre el `Context` que recibe.

## 4. El color

El widget hereda la paleta mezclada, con la misma `mixPalettes` que usa la app. No se
reimplementa ni se aproxima nada: es la función ya verificada contra WCAG AA en las 31
combinaciones, y usarla aquí extiende esa garantía al widget sin trabajo extra.

`minSdk` es 31, así que no hacen falta reservas de compatibilidad para `TileService` (API 24)
ni para Glance.

## 5. Lo testeable, y lo que no

Este proyecto ya no tiene tests instrumentados: se eliminaron por decisión del dueño. **Nada
va a comprobar automáticamente que el widget se pinta.** Eso condiciona el diseño en vez de
ser una nota al pie.

La lógica sale de la parte visual:

```kotlin
data class WidgetDisplay(
    val title: String,          // "Lluvia + Hoguera", o "3 sonidos"
    val palette: AmbioPalette,
    val isPlaying: Boolean
)

fun widgetDisplay(mix: List<ActiveSound>, isPlaying: Boolean): WidgetDisplay
```

**Sin iconos de sonido, y por un motivo concreto.** `Sound.icon` es un `ImageVector` de
Material Icons, y **Glance no puede pintar un `ImageVector`**: su `Image` acepta un
`ImageProvider` construido sobre un recurso drawable o un bitmap, no un vector de Compose.
Mostrar los iconos exigiría añadir un drawable por sonido sólo para el widget. Se descarta: el
nombre de la mezcla ya identifica lo que suena, y el color hace el resto del trabajo de
reconocimiento.

El título usa la misma regla que la barra de la app y la notificación —nombres unidos por
" + " hasta dos sonidos, y un recuento a partir de tres— para que las tres superficies digan
lo mismo.

Una función pura, testeable en JVM, y un composable de Glance que sólo la renderiza. Es el
mismo patrón que hizo testeable a `MixPlayer`: la lógica en algo sin `Context`, y una capa
fina encima.

**Lo que quedará sin cobertura automática**, dicho aquí para que nadie lo dé por cubierto:
que el `PendingIntent` llega al servicio, que Glance pinta lo que debe, y que el tile
responde al toque. Eso hay que verlo a mano en un emulador antes de dar el trabajo por bueno.

---

## Criterio de terminación

1. El widget muestra los sonidos de la mezcla guardada y si está sonando, con la paleta
   mezclada.
2. Pulsar play/pausa en el widget alterna la reproducción, **también con el servicio parado**,
   arrancándolo.
3. El tile de Ajustes rápidos alterna la reproducción al pulsarlo. Su etiqueta es el nombre de
   la app —no el de la mezcla, que no cabe en un tile— y su estado es `STATE_ACTIVE` cuando
   suena y `STATE_INACTIVE` cuando no, que es como el sistema lo pinta encendido o apagado.
4. Al pararse el servicio, el widget deja de mostrar que está sonando.
5. `widgetDisplay(mix, isPlaying)` está cubierta por tests JVM, incluidos los casos de uno,
   dos y tres o más sonidos.
6. El lint sigue en 0 errores y los avisos del compilador de Kotlin no suben de 2.
7. Ningún string nuevo queda hardcodeado, comprobado con
   `Text\("|text = "|contentDescription = "`.
8. **Verificado a mano en un emulador**, porque nada de esto lo cubre un test: añadir el
   widget, pulsar play con la app cerrada, ver que arranca y suena, pausar desde el tile, y
   comprobar que al parar el servicio el widget no se queda mintiendo.
