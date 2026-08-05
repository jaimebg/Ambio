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

- Java 17 required (installed via sdkman or homebrew)
- Project uses Gradle wrapper (no global Gradle needed)
- CompileSDK 36 requires suppression flag (already in gradle.properties)

### Codebase Patterns

- MVVM + Clean Architecture with multi-module structure
- Hilt for dependency injection
- Compose for UI with Material Design 3
- Media3 MediaSessionService for background audio
- DataStore for preferences, Room for session history
