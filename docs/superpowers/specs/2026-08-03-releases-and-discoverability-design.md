# Fase 1 — Releases y descubribilidad

**Fecha:** 2026-08-03
**Estado:** Pendiente de aprobación

## Contexto

La Fase 0 dejó el toolchain moderno y un CI que valida cada cambio. Esta fase ataca un
problema distinto, que el sondeo del repositorio destapó al empezar a diseñarla.

Estado del proyecto al iniciar:

| | |
|---|---|
| Tags de git | ninguno |
| Releases publicadas | ninguna |
| Issues (abiertas o cerradas) | ninguna |
| Stars / forks | 0 / 0 |
| Ficheros de comunidad | solo `LICENSE` |
| README | 5 badges tecnológicos, ninguno de instalación ni de versión |

**El proyecto no tiene audiencia.** No hay contribuidores cuya barrera bajar. Por eso esta
fase invierte el orden que planteaba el roadmap original: antes que el papeleo de
contribución va lo que hace que el proyecto se pueda encontrar y seguir. El papeleo
(`CONTRIBUTING.md`, plantillas, `CODE_OF_CONDUCT.md`) queda para después.

## Restricciones que definen el diseño

**El proyecto usa Play App Signing** y el dueño no quiere claves de firma en GitHub
Actions. Las dos cosas juntas descartan publicar APKs:

- Sin clave en CI no hay artefacto firmable, y un APK firmado en debug no acredita nada:
  cualquiera puede producir esa firma.
- Con Play App Signing, el APK que reciben los usuarios lo firma Google con una clave que
  el proyecto no posee. Cualquier APK que publicáramos tendría una firma distinta, y dos
  APKs con firmas distintas no pueden actualizarse entre sí: quien instalara desde GitHub
  quedaría en una rama de instalación separada y tendría que desinstalar —perdiendo sus
  datos— para pasarse a la versión de Play.

Se valoró y descartó adjuntar el APK universal firmado por Google que la Play Console
permite descargar del *App Bundle Explorer* —que sí conservaría la compatibilidad de
actualización— porque exige un paso manual en cada release. Esta fase se queda en lo
totalmente automatizable.

## Objetivos

1. Que el repositorio tenga historial de versiones navegable.
2. Que cada versión futura genere su Release sola, sin pasos manuales ni secretos.
3. Que quien llegue al README vea en qué versión va el proyecto y cómo instalarlo.
4. Que el CI deje de hacer trabajo duplicado y dé feedback más rápido en las PRs.

## No objetivos

- `CONTRIBUTING.md`, plantillas de issue/PR, `CODE_OF_CONDUCT.md`, `SECURITY.md`. Es la otra
  mitad de la fase original; se hace cuando el proyecto ya se pueda encontrar.
- Adjuntar APKs o AABs a las releases, por la restricción de firma de arriba.
- F-Droid, que sigue en la Fase 4. Resolvería la instalación sin pasos manuales —compila
  desde el código y firma con su propia clave— pero crea su propia rama de instalación,
  distinta de Play, y es una decisión de distribución con entidad propia.
- **Cortar una release nueva.** La Fase 0 cambió el binario entero (targetSdk 37 y todo el
  toolchain) y el proyecto sigue en `versionCode 2` / `versionName 1.1.0`. Publicar eso es
  una decisión de producto, no infraestructura.

## Diseño

### 1. Tag retroactivo `v1.1.0`

Un único tag anotado sobre `4bc32a2` ("chore: release 1.1.0", 2026-01-27), que es el commit
donde `versionCode` pasó a 2 y `versionName` a "1.1.0".

**No se crea `v1.0.0`.** La versión 1.0.0 existió en el código —desde el commit inicial
hasta `4bc32a2^`— pero nunca llegó a publicarse en Play. Tagearla inventaría una release
que no ocurrió.

**Su Release se crea a mano, y sólo ésta.** GitHub ejecuta los workflows desde el commit
tageado, y en `4bc32a2` todavía no existe `.github/workflows/ci.yml` —se creó en la Fase 0—,
así que empujar el tag no dispararía nada. La Release de `v1.1.0` se crea con
`gh release create` en un paso único, con el texto de `changelogs/default.txt`, que es
precisamente el changelog de esa versión. Todos los tags futuros salen del commit posterior
a esta fase y sí activan la automatización.

### 2. Job de release dentro de `ci.yml`

El workflow existente gana un segundo job, `release`, que sólo se activa en tags:

```yaml
  release:
    needs: build
    if: startsWith(github.ref, 'refs/tags/v')
    permissions:
      contents: write
```

**El `needs: build` es la decisión importante.** Encadenar el release al job de build
garantiza que ninguna Release pueda existir sin que antes hayan pasado lint, tests,
`assembleDebug` y `bundleRelease`. Si se hiciera en un workflow separado, ambos se
dispararían en paralelo con el mismo tag y la Release podría publicarse mientras el build
falla.

Los permisos van a nivel de job, no de workflow: el job `build` conserva
`contents: read` y sólo `release` obtiene `contents: write`.

El job:

1. Lee `versionCode` de `app/build.gradle.kts`.
2. Busca `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`; si no existe, usa
   `changelogs/default.txt`.
3. Crea la Release con ese texto como cuerpo, más la lista de cambios que GitHub genera
   automáticamente desde el tag anterior.

**Las notas salen del mismo fichero que alimenta la ficha de Play.** Una sola fuente de
verdad, sin dos changelogs que se desincronicen. Hoy sólo existe `default.txt`, que Fastlane
usa como comodín para cualquier `versionCode`; el diseño lo respeta como fallback, y a
partir de la próxima versión basta con añadir `3.txt` para que Play y GitHub cuenten lo
mismo. No hace falta renombrar nada ahora.

### 3. Ajuste de disparadores y coste del CI

Medido sobre un run completo de la Fase 0:

| Paso | Tiempo |
|---|---|
| Lint | 3m 51s |
| Tests | 43s |
| Build debug | 2m 15s |
| Build release bundle | 4m 38s |
| **Total** | **~11m 30s** |

Dos problemas:

- **Trabajo duplicado.** Con `push: [main, 'chore/**']` y `pull_request: [main]`, cada push
  a una rama con PR abierta dispara el workflow dos veces sobre el mismo commit: 23 minutos
  de runner para 11 de información. Se introdujo al arreglar el disparador en la Fase 0.
- **`bundleRelease` es el paso más caro** —más que el lint— y sólo importa antes de
  publicar. Correrlo en cada PR de un contribuidor es coste sin retorno.

Disparadores nuevos:

```yaml
on:
  push:
    branches: [ main ]
    tags: [ 'v*' ]
  pull_request:
    branches: [ main ]
  workflow_dispatch:
```

Y `bundleRelease` condicionado:

```yaml
      - name: Build release bundle
        if: github.ref == 'refs/heads/main' || startsWith(github.ref, 'refs/tags/v')
        run: ./gradlew bundleRelease
```

Resultado: las PRs bajan a ~7 minutos, las ramas dejan de duplicar trabajo —reciben CI a
través de su PR—, y nada llega a una release sin pasar por R8, porque las releases salen de
tags y el tag ejecuta el paso.

**No se elimina ningún paso.** `assembleDebug` cuesta 2m 15s y no es redundante con
`bundleRelease`: cubre las dependencias `debugImplementation` (el `compose.ui.tooling` de
tres módulos), el merge de manifiestos y recursos de debug y el empaquetado. Además es el
comando que ejecuta cualquier contribuidor en su máquina y el que documenta `CLAUDE.md`; si
se rompe, se rompe justo para quien intente colaborar.

Se valoró paralelizar en dos jobs —wall-clock del más lento en vez de la suma, y en repo
público los minutos son gratis— pero cada job repaga el setup y pierde el estado incremental
de Gradle. Para este tamaño de proyecto, el `if` da mejor relación coste/complejidad.

### 4. Badges e instalación en el README

El README tiene hoy cinco badges tecnológicos (Android, Kotlin, Compose, Material Design 3,
MIT) y **ninguno que lleve a instalar la app ni que indique en qué versión va**. Se añaden:

- Badge de Google Play enlazando a la ficha de la app.
- Badge de última release, que se actualiza solo contra la API de GitHub.
- Badge de estado del CI.

Y el enlace de instalación sube por encima del pliegue, junto al feature graphic, en vez de
quedar accesible sólo desde la homepage del autor.

## Criterio de terminación

1. Existe el tag `v1.1.0` en `4bc32a2`, con su Release creada a mano, y no existe `v1.0.0`.
2. Empujar un tag `v*` desde un commit posterior a esta fase crea una Release cuyo cuerpo
   contiene el texto del changelog de Fastlane, y sólo la crea si el job de build pasó.
3. Un push a una rama con PR abierta dispara el workflow **una sola vez**.
4. `bundleRelease` no se ejecuta en PRs, y sí en `main` y en tags.
5. El job `build` conserva `permissions: contents: read`.
6. El README muestra los badges de Play Store, última release y CI, y el enlace de
   instalación está por encima del pliegue.
