# orca

Build prerequisites:
- Install a JDK compatible with the Android Gradle Plugin and make it available through `JAVA_HOME` or `PATH`.
- Install the Android SDK and let Android Studio or `local.properties` provide `sdk.dir`.

The Gradle wrapper defaults `GRADLE_USER_HOME` and `ANDROID_USER_HOME` to project-local folders so builds do not depend on machine-global cache directories.

## Current Status

The app is currently at the Phase 0 foundation stage:
- reusable Room/data/importing/extraction foundations are in app-owned packages
- prototype-only UI and debug entry points have been removed
- the launcher screen is a neutral status shell until later phases wire product flows

Local provider config:
- Add these entries to `local.properties` for Android Studio-friendly local testing:
  - `orca.cloudExtractionApiKey=YOUR_OPENAI_API_KEY`
  - `orca.cloudExtractionModel=gpt-5.4`
  - optional: `orca.openAiResponsesUrl=https://api.openai.com/v1/responses`
- Gradle properties and env vars still work, but `local.properties` is the most reliable path for IDE builds.

Example:

```properties
sdk.dir=/home/your-user/Android/Sdk
orca.cloudExtractionApiKey=sk-...
orca.cloudExtractionModel=gpt-5.4
```

Build and install:

```bash
JAVA_HOME=/path/to/jdk ./gradlew :app:assembleDebug
```

## App Use

1. Launch the app.
2. Review the foundation status screen.
3. Use build/test flows to verify backend progress until later phases add import and review screens.

## Debugging

Runtime logs use the logcat tag:

```text
OrcaApp
```

Useful command:

```bash
adb logcat -s OrcaApp
```

The logs intentionally avoid raw document text and API keys.
