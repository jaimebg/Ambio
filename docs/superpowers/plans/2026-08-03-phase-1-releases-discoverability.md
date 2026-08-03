# Fase 1 — Releases y descubribilidad: Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Que Ambio tenga historial de versiones navegable, releases que se publican solas desde el changelog que ya alimenta la ficha de Play, y un README que diga en qué versión va y cómo instalarla — sin secretos y sin pasos manuales recurrentes.

**Architecture:** Todo vive en `.github/workflows/ci.yml` y `README.md`. El job de release se añade al workflow existente con `needs: build`, de modo que ninguna Release pueda publicarse sin haber pasado antes lint, tests, `assembleDebug` y `bundleRelease`. Las notas salen de `fastlane/metadata/android/en-US/changelogs/`, la misma fuente que usa Fastlane para Play.

**Tech Stack:** GitHub Actions, `gh` CLI, shields.io, Fastlane metadata.

**Spec:** `docs/superpowers/specs/2026-08-03-releases-and-discoverability-design.md`

## Global Constraints

- **Ningún secreto nuevo en GitHub Actions.** El proyecto usa Play App Signing y el dueño no
  quiere claves de firma en CI. Ninguna tarea puede requerir un secret.
- **No se adjuntan APKs ni AABs** a las releases, por la restricción de firma anterior.
- **No se toca código de la app.** Esta fase sólo modifica `.github/workflows/ci.yml`,
  `README.md`, y crea un tag. Ningún fichero bajo `app/`, `core/`, `feature/`, `media/`,
  `ui/` o `build-logic/`.
- **No se corta una release nueva.** El proyecto se queda en `versionCode 2` /
  `versionName 1.1.0`. Publicar la Fase 0 es una decisión de producto aparte.
- El job `build` conserva `permissions: contents: read`; sólo el job `release` obtiene
  `contents: write`, a nivel de job.
- Rama de trabajo: `chore/phase-1-releases-discoverability` (ya creada, contiene el spec).

## File Structure

| Fichero | Responsabilidad | Tarea |
|---|---|---|
| `.github/workflows/ci.yml` | Disparadores y coste del CI | 1 |
| `.github/workflows/ci.yml` | Job `release`, gatillado por tags | 2 |
| tag `v1.1.0` + su Release | Historial retroactivo | 3 |
| `README.md` | Badges, enlace de instalación, datos desactualizados | 4 |

---

### Task 1: Disparadores del CI y coste

Dos problemas medidos en la Fase 0: cada push a una rama con PR abierta dispara el workflow
**dos veces** sobre el mismo commit (23 min de runner para 11 de información), y
`bundleRelease` —el paso más caro, 4m 38s, más que el lint— se ejecuta en cada PR aunque
sólo importe antes de publicar.

**Files:**
- Modify: `.github/workflows/ci.yml:3-8` (bloque `on:`)
- Modify: `.github/workflows/ci.yml:44-45` (paso `Build release bundle`)

**Interfaces:**
- Consumes: nada.
- Produces: el disparador `tags: [ 'v*' ]`, del que depende el job `release` de la Tarea 2.

- [ ] **Step 1: Sustituir el bloque de disparadores**

En `.github/workflows/ci.yml`, reemplazar las líneas 3-8 por:

```yaml
on:
  push:
    branches: [ main ]
    tags: [ 'v*' ]
  pull_request:
    branches: [ main ]
  workflow_dispatch:
```

Sale `chore/**` del `push`: las ramas de trabajo reciben CI a través de su PR, no por
partida doble. Entra `tags: [ 'v*' ]`, que es lo que la Tarea 2 necesita.

- [ ] **Step 2: Condicionar `bundleRelease`**

Reemplazar el paso `Build release bundle` por:

```yaml
      - name: Build release bundle
        if: github.ref == 'refs/heads/main' || startsWith(github.ref, 'refs/tags/v')
        run: ./gradlew bundleRelease
```

**No borres ningún paso.** `assembleDebug` se queda: cuesta 2m 15s y no es redundante con
`bundleRelease` — cubre las dependencias `debugImplementation` (el `compose.ui.tooling` de
tres módulos), el merge de manifiestos y recursos de debug y el empaquetado, y es el comando
que ejecuta cualquier contribuidor y que documenta `CLAUDE.md`.

- [ ] **Step 3: Validar la sintaxis del YAML**

```bash
python3 -c "import yaml,sys; yaml.safe_load(open('.github/workflows/ci.yml')); print('YAML valido')"
```

Esperado: `YAML valido`.

- [ ] **Step 4: Commit y push**

```bash
git add .github/workflows/ci.yml
git commit -m "ci: stop duplicate runs and gate the release bundle

Pushes to a branch with an open PR triggered the workflow twice over the
same commit. Branch work now gets CI through its PR. bundleRelease is the
most expensive step at 4m38s and only matters before publishing, so it now
runs on main and tags only."
git push
```

- [ ] **Step 5: Verificar el comportamiento real de los disparadores**

Este paso es el que da valor a la tarea: hay que **observar** que el doble run desapareció.

```bash
gh pr create --fill --base main 2>/dev/null || echo "(PR ya existe)"
gh run list --branch chore/phase-1-releases-discoverability --limit 5 \
  --json event,headSha,status --jq '.[] | "\(.event) \(.headSha[0:7]) \(.status)"'
```

Esperado: para el commit recién empujado aparece **un solo run**, con `event: pull_request`.
Si aparecen dos (uno `push` y otro `pull_request`), el cambio de disparadores no surtió
efecto — investigar antes de continuar.

Esperar a que termine y comprobar que `bundleRelease` fue omitido:

```bash
gh run watch $(gh run list --branch chore/phase-1-releases-discoverability --limit 1 --json databaseId --jq '.[0].databaseId')
gh api "repos/jaimebg/Ambio/actions/runs/$(gh run list --branch chore/phase-1-releases-discoverability --limit 1 --json databaseId --jq '.[0].databaseId')/jobs" \
  --jq '.jobs[0].steps[] | "\(.name): \(.conclusion)"'
```

Esperado: el paso `Build release bundle` aparece con conclusión `skipped`, y `Lint`,
`Tests unitarios` y `Build debug` con `success`.

---

### Task 2: Job de release

**Files:**
- Modify: `.github/workflows/ci.yml` (añadir un segundo job tras el job `build`)

**Interfaces:**
- Consumes: el disparador `tags: [ 'v*' ]` de la Tarea 1.
- Produces: la automatización que la Tarea 3 deja documentada pero no usa (el tag
  retroactivo apunta a un commit sin workflow).

- [ ] **Step 1: Añadir el job `release`**

Al final de `.github/workflows/ci.yml`, después del paso `Publicar informes` del job
`build`, añadir un segundo job al mismo nivel de indentación que `build:`:

```yaml

  release:
    name: Publicar release
    needs: build
    if: startsWith(github.ref, 'refs/tags/v')
    runs-on: ubuntu-latest
    permissions:
      contents: write

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Preparar notas de la release
        run: |
          VERSION_CODE=$(grep -oE 'versionCode = [0-9]+' app/build.gradle.kts | grep -oE '[0-9]+')
          CHANGELOG_DIR=fastlane/metadata/android/en-US/changelogs
          if [ -f "$CHANGELOG_DIR/$VERSION_CODE.txt" ]; then
            NOTES_FILE="$CHANGELOG_DIR/$VERSION_CODE.txt"
          else
            NOTES_FILE="$CHANGELOG_DIR/default.txt"
          fi
          echo "versionCode $VERSION_CODE, notas desde $NOTES_FILE"
          cp "$NOTES_FILE" release-notes.md

      - name: Crear la release
        env:
          GH_TOKEN: ${{ github.token }}
        run: |
          gh release create "$GITHUB_REF_NAME" \
            --title "$GITHUB_REF_NAME" \
            --notes-file release-notes.md \
            --generate-notes
```

Tres cosas deliberadas:

- **`needs: build`** es lo que garantiza que ninguna Release exista sin haber pasado lint,
  tests, `assembleDebug` y `bundleRelease`. En un workflow separado, ambos se dispararían en
  paralelo con el mismo tag y la Release podría publicarse mientras el build falla.
- **Los permisos van en el job**, no en el workflow. El bloque `permissions: contents: read`
  de nivel workflow sigue aplicando a `build`; sólo `release` obtiene escritura.
- **`--generate-notes` junto a `--notes-file`** hace que GitHub añada la lista de cambios
  autogenerada debajo del texto del changelog. Si al verificar resultara que `gh` ignora uno
  de los dos, construir el cuerpo a mano concatenando ambos y decirlo en el informe.

- [ ] **Step 2: Validar la sintaxis y la estructura de jobs**

```bash
python3 -c "
import yaml
w = yaml.safe_load(open('.github/workflows/ci.yml'))
jobs = w['jobs']
assert set(jobs) == {'build', 'release'}, jobs
assert jobs['release']['needs'] == 'build'
assert jobs['release']['permissions'] == {'contents': 'write'}
assert w['permissions'] == {'contents': 'read'}
print('estructura correcta')
"
```

Esperado: `estructura correcta`.

- [ ] **Step 3: Comprobar que el extractor de versionCode funciona**

El job depende de sacar el `versionCode` con `grep`. Verificarlo contra el fichero real
antes de confiar en él:

```bash
grep -oE 'versionCode = [0-9]+' app/build.gradle.kts | grep -oE '[0-9]+'
```

Esperado: `2`, y **una sola línea**. Si salen varias, el `grep` es ambiguo y hay que
afinarlo antes de seguir.

Y que el fichero de notas existe:

```bash
ls fastlane/metadata/android/en-US/changelogs/
cat fastlane/metadata/android/en-US/changelogs/default.txt
```

Esperado: existe `default.txt` con las cuatro líneas del changelog de 1.1.0.

- [ ] **Step 4: Commit y push**

```bash
git add .github/workflows/ci.yml
git commit -m "ci: publish a GitHub Release when a version tag is pushed

Release notes come from the Fastlane changelog, the same source that feeds
the Play Store listing, so the two cannot drift. needs: build means no
release can exist without lint, tests and both builds passing first."
git push
```

- [ ] **Step 5: Verificar que el job no se activa fuera de tags**

```bash
gh run watch $(gh run list --branch chore/phase-1-releases-discoverability --limit 1 --json databaseId --jq '.[0].databaseId')
gh api "repos/jaimebg/Ambio/actions/runs/$(gh run list --branch chore/phase-1-releases-discoverability --limit 1 --json databaseId --jq '.[0].databaseId')/jobs" \
  --jq '.jobs[] | "\(.name): \(.conclusion)"'
```

Esperado: aparece únicamente el job `Lint, test y build`. El job `Publicar release` **no
debe aparecer**, porque este push no es un tag. Si aparece, la condición `if` está mal.

- [ ] **Step 6: Probar la automatización de verdad, con un tag desechable**

Sin este paso, la automatización no se ejercita hasta la próxima release real — y si algo
está mal, se descubriría en el peor momento. Se prueba con un tag temporal que luego se
borra.

**Aviso:** esto crea brevemente una Release pública en el repositorio. Se borra en el mismo
paso. Si prefieres no hacerlo sobre el repo público, dilo y lo verificamos de otra forma.

```bash
git tag -a v0.0.1-ci-test -m "Prueba de la automatización de releases"
git push origin v0.0.1-ci-test
```

Esperar a que termine el workflow del tag:

```bash
sleep 30
RUN=$(gh run list --limit 10 --json databaseId,headBranch --jq '[.[] | select(.headBranch=="v0.0.1-ci-test")][0].databaseId')
gh run watch "$RUN"
gh api "repos/jaimebg/Ambio/actions/runs/$RUN/jobs" --jq '.jobs[] | "\(.name): \(.conclusion)"'
```

Esperado: **dos** jobs, `Lint, test y build` y `Publicar release`, ambos `success`. Y dentro
del primero, el paso `Build release bundle` debe haberse **ejecutado** (no `skipped`), porque
el ref es un tag.

Comprobar el contenido de la Release generada:

```bash
gh release view v0.0.1-ci-test --json body --jq .body
```

Esperado: el cuerpo contiene las cuatro líneas del changelog de `default.txt` **y** una
sección de cambios autogenerada por GitHub. Si sólo aparece una de las dos, `gh` no está
combinando `--notes-file` con `--generate-notes`: construir el cuerpo a mano concatenando
ambos, y dejarlo dicho en el informe.

- [ ] **Step 7: Limpiar el tag de prueba**

```bash
gh release delete v0.0.1-ci-test --yes
git push --delete origin v0.0.1-ci-test
git tag -d v0.0.1-ci-test
```

Verificar que no queda rastro:

```bash
gh release list
git tag
git ls-remote --tags origin
```

Esperado: no aparece `v0.0.1-ci-test` en ninguno de los tres. En este punto de la fase, `git
tag` puede estar vacío si la Tarea 3 aún no se ha hecho, o mostrar sólo `v1.1.0` si ya se
hizo — ambas cosas son correctas.

---

### Task 3: Tag y Release retroactivos de v1.1.0

**Files:**
- Ninguno. Esta tarea crea un tag de git y una Release en GitHub.

**Interfaces:**
- Consumes: nada del repositorio de trabajo.
- Produces: el tag `v1.1.0`, que hace que el badge de última release de la Tarea 4 tenga
  algo que mostrar, y que las comparaciones de GitHub tengan un punto de partida.

- [ ] **Step 1: Confirmar el commit objetivo**

```bash
git show --stat 4bc32a2 | head -8
git show 4bc32a2:app/build.gradle.kts | grep -E "versionCode|versionName"
```

Esperado: el commit es `chore: release 1.1.0` del 2026-01-27, con `versionCode = 2` y
`versionName = "1.1.0"`. Si no coincide, parar y reportar.

- [ ] **Step 2: Crear el tag anotado y empujarlo**

```bash
git tag -a v1.1.0 4bc32a2 -m "Ambio 1.1.0"
git push origin v1.1.0
```

**No crear `v1.0.0`.** Esa versión existió en el código pero nunca se publicó en Play;
tagearla inventaría una release que no ocurrió.

- [ ] **Step 3: Comprobar que el tag NO dispara el workflow**

```bash
sleep 20
gh run list --limit 5 --json event,headBranch,status --jq '.[] | "\(.event) \(.headBranch) \(.status)"'
```

Esperado: **no aparece ningún run nuevo para `v1.1.0`**. Es lo correcto y esperado: GitHub
ejecuta los workflows desde el commit tageado, y en `4bc32a2` todavía no existe
`.github/workflows/ci.yml` — se creó en la Fase 0. Por eso esta Release se crea a mano.

Si SÍ apareciera un run, algo no cuadra con esa premisa: parar y reportarlo.

- [ ] **Step 4: Crear la Release a mano**

```bash
gh release create v1.1.0 \
  --title "v1.1.0" \
  --notes-file fastlane/metadata/android/en-US/changelogs/default.txt
```

`default.txt` es exactamente el changelog de la 1.1.0, así que es el texto correcto para
esta Release. No se usa `--generate-notes` aquí: generaría la lista de todos los commits
desde el inicio del proyecto.

- [ ] **Step 5: Verificar**

```bash
gh release view v1.1.0 --json tagName,name,body,isDraft,isPrerelease \
  --jq '"tag: \(.tagName)\ntitulo: \(.name)\nborrador: \(.isDraft), prerelease: \(.isPrerelease)\n---\n\(.body)"'
git tag
```

Esperado: la Release existe, no es borrador ni prerelease, su cuerpo son las cuatro líneas
del changelog, y `git tag` lista **únicamente** `v1.1.0`.

---

### Task 4: README — badges, instalación y datos desactualizados

El README tiene cinco badges tecnológicos y ninguno que lleve a instalar la app ni que
indique en qué versión va. Además, la Fase 0 invalidó dos afirmaciones que siguen ahí.

**Files:**
- Modify: `README.md:11-17` (bloque de badges)
- Modify: `README.md` (sección `## Requirements`)
- Modify: `README.md` (bloque `## Build Commands`)

**Interfaces:**
- Consumes: el tag `v1.1.0` de la Tarea 3, sin el cual el badge de release no muestra nada.
- Produces: nada que consuman otras tareas.

- [ ] **Step 1: Reemplazar el bloque de badges**

Sustituir el bloque completo que hoy va de `<p align="center">` con los cinco badges
`img.shields.io` (líneas 11-17) por:

```html
<p align="center">
  <a href="https://play.google.com/store/apps/details?id=com.jbgsoft.ambio">
    <img src="https://img.shields.io/badge/Google%20Play-Download-414141?logo=googleplay&logoColor=white" alt="Get it on Google Play">
  </a>
  <a href="https://github.com/jaimebg/Ambio/releases/latest">
    <img src="https://img.shields.io/github/v/release/jaimebg/Ambio?label=release&color=3DDC84" alt="Latest release">
  </a>
  <a href="https://github.com/jaimebg/Ambio/actions/workflows/ci.yml">
    <img src="https://github.com/jaimebg/Ambio/actions/workflows/ci.yml/badge.svg" alt="CI">
  </a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Android-12%2B-3DDC84?logo=android&logoColor=white" alt="Android 12+">
  <img src="https://img.shields.io/badge/Kotlin-2.3-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin 2.3">
  <img src="https://img.shields.io/badge/Jetpack%20Compose-UI-4285F4?logo=jetpackcompose&logoColor=white" alt="Jetpack Compose">
  <img src="https://img.shields.io/badge/Material%20Design%203-darkblue" alt="Material Design 3">
  <img src="https://img.shields.io/badge/License-MIT-green" alt="MIT License">
</p>
```

Dos filas a propósito: **arriba lo accionable** —instalar, ver la última versión, ver si el
build está verde— y debajo lo informativo. El badge de Kotlin pasa de `2.0` a `2.3`, que es
la versión real tras la Fase 0.

- [ ] **Step 2: Corregir los datos que la Fase 0 invalidó**

En la sección `## Requirements`, sustituir la línea de Android Studio:

```
- Android Studio Ladybug or newer
```

por:

```
- Android Studio Otter or newer (AGP 9 requires it)
```

En el bloque `## Build Commands`, la línea de tests dice `(75 tests)`. El número real tras
la Fase 0 es 154 — son 77 métodos `@Test` ejecutados contra las variantes debug y release.
Sustituirla por:

```
./gradlew test            # Run unit tests (154: 77 tests × debug and release variants)
```

- [ ] **Step 3: Verificar que no quedan datos obsoletos conocidos**

```bash
grep -nE "Kotlin-2\.0|75 tests|Ladybug" README.md && echo "QUEDAN OBSOLETOS" || echo "limpio"
grep -nE "play.google.com|shields.io/github/v/release|actions/workflows/ci.yml/badge" README.md
```

Esperado: el primer comando imprime `limpio`. El segundo muestra las tres líneas de los
badges nuevos.

- [ ] **Step 4: Comprobar que los badges resuelven de verdad**

Un badge roto es peor que ningún badge. Comprobar que las tres URLs devuelven contenido:

```bash
curl -sf -o /dev/null -w "play: %{http_code}\n" "https://img.shields.io/badge/Google%20Play-Download-414141?logo=googleplay&logoColor=white"
curl -sf -o /dev/null -w "release: %{http_code}\n" "https://img.shields.io/github/v/release/jaimebg/Ambio?label=release&color=3DDC84"
curl -sf -o /dev/null -w "ci: %{http_code}\n" "https://github.com/jaimebg/Ambio/actions/workflows/ci.yml/badge.svg"
curl -sf -o /dev/null -w "play store: %{http_code}\n" "https://play.google.com/store/apps/details?id=com.jbgsoft.ambio"
```

Esperado: `200` en los cuatro. El badge de release sólo muestra `v1.1.0` si la Tarea 3 se
completó; si devuelve 200 pero el SVG dice "no releases", revisar la Tarea 3.

Si la URL de Play Store no devuelve 200, la app no está publicada bajo ese `applicationId`:
parar y reportarlo en vez de dejar un enlace roto en la portada del proyecto.

- [ ] **Step 5: Commit y push**

```bash
git add README.md
git commit -m "docs: add install and release badges, refresh stale facts

The README had five technology badges and nothing that let a visitor
install the app or see which version the project is on. Also corrects two
claims Phase 0 invalidated: the Kotlin badge and the unit test count."
git push
```

---

## Resultado esperado de la fase

| | Antes | Después |
|---|---|---|
| Tags | ninguno | `v1.1.0` |
| Releases | ninguna | 1, y las futuras se publican solas |
| Fuente de las notas | — | el changelog que ya alimenta Play |
| Runs por push con PR abierta | 2 | 1 |
| Feedback en PR | ~11m 30s | ~7m |
| `bundleRelease` | en cada PR | sólo en `main` y tags |
| Badges de instalación y versión | ninguno | Play Store, última release, CI |

Al terminar, la otra mitad de la fase original —`CONTRIBUTING.md`, plantillas de issue y PR,
`CODE_OF_CONDUCT.md`— se puede montar sobre un proyecto que ya se puede encontrar, seguir e
instalar.
