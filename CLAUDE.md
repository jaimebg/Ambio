## Build & Run

Build debug APK:
```bash
./gradlew assembleDebug
```

Clean and build:
```bash
./gradlew clean assembleDebug
```

## Validation

- Build: `./gradlew assembleDebug`
- Lint: `./gradlew lint`
- Tests: `./gradlew test`

## Operational Notes

- Java 17 or newer for everyday builds (installed via sdkman or homebrew)
- **Release APKs must be built on JDK 21**, from a clone checked out at the tag.
  The build succeeds on 17, but F-Droid ships this app as a reproducible build
  and a JDK 17 APK does not match what F-Droid's Debian builders produce. The
  full release procedure and the traps are in `fdroid/README.md` — read it before
  cutting a release.
- Project uses Gradle wrapper (no global Gradle needed)
- CompileSDK 36 requires suppression flag (already in gradle.properties)

### Codebase Patterns

- MVVM + Clean Architecture with multi-module structure
- Hilt for dependency injection
- Compose for UI with Material Design 3
- Media3 MediaSessionService for background audio
- DataStore for preferences, Room for session history
