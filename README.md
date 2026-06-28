# orca

Build prerequisites:
- Install a JDK compatible with the Android Gradle Plugin and make it available through `JAVA_HOME` or `PATH`.
- Install the Android SDK and let Android Studio or `local.properties` provide `sdk.dir`.

The Gradle wrapper defaults `GRADLE_USER_HOME` and `ANDROID_USER_HOME` to project-local folders so builds do not depend on machine-global cache directories.

## Prototype Setup

The current prototype is a cloud-first document extraction flow:
- import one or more images or PDFs
- copy them to app-private storage
- enqueue a `WorkManager` job
- send the original source files to OpenAI Responses API
- parse strict JSON
- normalize and persist extracted facts and maintenance events locally

Local OpenAI prototype config:
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

## Prototype Use

1. Launch the app.
2. Confirm the header says `OpenAI extraction is configured.`
3. Import one or more images or PDFs.
4. Wait for the document status to move through `QUEUED` -> `RUNNING` -> `SUCCEEDED` or `FAILED`.
5. Open the document card to inspect extracted facts, evidence snippets, and service items.

## Debugging

Prototype runtime logs use the logcat tag:

```text
OrcaPrototype
```

Useful command:

```bash
adb logcat -s OrcaPrototype
```

The logs intentionally avoid raw document text, raw prompts, raw model responses, and API keys.
