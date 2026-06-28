# orca

Build prerequisites:
- Install a JDK compatible with the Android Gradle Plugin and make it available through `JAVA_HOME` or `PATH`.
- Install the Android SDK and let Android Studio or `local.properties` provide `sdk.dir`.

Local OpenAI prototype config:
- Add these entries to `local.properties` for Android Studio-friendly local testing:
  - `orca.cloudExtractionApiKey=YOUR_OPENAI_API_KEY`
  - `orca.cloudExtractionModel=gpt-5.4`
  - optional: `orca.openAiResponsesUrl=https://api.openai.com/v1/responses`
- Gradle properties and env vars still work, but `local.properties` is the most reliable path for IDE builds.

The Gradle wrapper defaults `GRADLE_USER_HOME` and `ANDROID_USER_HOME` to project-local folders so builds do not depend on machine-global cache directories.
