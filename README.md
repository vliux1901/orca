# orca

Build prerequisites:
- Install a JDK compatible with the Android Gradle Plugin and make it available through `JAVA_HOME` or `PATH`.
- Install the Android SDK and let Android Studio or `local.properties` provide `sdk.dir`.

The Gradle wrapper defaults `GRADLE_USER_HOME` and `ANDROID_USER_HOME` to project-local folders so builds do not depend on machine-global cache directories.
