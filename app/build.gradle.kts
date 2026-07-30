import com.android.build.api.dsl.ApplicationExtension
import java.util.Properties
import org.gradle.api.GradleException
import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use(::load)
    }
}

fun localProperty(name: String): String? = localProperties.getProperty(name)?.takeIf { it.isNotBlank() }

fun firstConfiguredOptionalValue(vararg values: String?): String? =
    values.firstOrNull { !it.isNullOrBlank() }

fun firstConfiguredValue(vararg values: String?): String =
    values.firstOrNull { !it.isNullOrBlank() } ?: ""

val releaseStoreFilePath = firstConfiguredOptionalValue(
    providers.gradleProperty("orcaReleaseStoreFile").orNull,
    localProperty("orca.release.storeFile"),
    System.getenv("ORCA_RELEASE_STORE_FILE"),
)
val releaseStorePassword = firstConfiguredOptionalValue(
    providers.gradleProperty("orcaReleaseStorePassword").orNull,
    localProperty("orca.release.storePassword"),
    System.getenv("ORCA_RELEASE_STORE_PASSWORD"),
)
val releaseKeyAlias = firstConfiguredOptionalValue(
    providers.gradleProperty("orcaReleaseKeyAlias").orNull,
    localProperty("orca.release.keyAlias"),
    System.getenv("ORCA_RELEASE_KEY_ALIAS"),
)
val releaseKeyPassword = firstConfiguredOptionalValue(
    providers.gradleProperty("orcaReleaseKeyPassword").orNull,
    localProperty("orca.release.keyPassword"),
    System.getenv("ORCA_RELEASE_KEY_PASSWORD"),
)
val hasReleaseUploadSigningConfig = listOf(
    releaseStoreFilePath,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { !it.isNullOrBlank() }

fun ApplicationExtension.stringBuildConfigField(name: String, value: String) {
    defaultConfig {
        buildConfigField("String", name, "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
    }
}

abstract class VerifyReleaseUploadSigningTask : DefaultTask() {
    @get:Input
    abstract val hasConfig: Property<Boolean>

    @get:Input
    abstract val helpMessage: Property<String>

    @TaskAction
    fun verify() {
        if (!hasConfig.get()) {
            throw GradleException(helpMessage.get())
        }
    }
}

android {
    namespace = "org.vliux.orca"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "org.vliux.orca"
        minSdk = 24
        targetSdk = 36
        versionCode = 2
        versionName = "1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            if (hasReleaseUploadSigningConfig) {
                storeFile = file(requireNotNull(releaseStoreFilePath))
                storePassword = requireNotNull(releaseStorePassword)
                keyAlias = requireNotNull(releaseKeyAlias)
                keyPassword = requireNotNull(releaseKeyPassword)
            }
        }
    }

    buildTypes {
        debug {
            versionNameSuffix = "-debug"
        }
        release {
            if (hasReleaseUploadSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

android.stringBuildConfigField(
    name = "CLOUD_EXTRACTION_PROVIDER_NAME",
    value = "openai",
)

val releaseUploadSigningHelpMessage = """
    Missing release upload signing configuration for Play Store bundle generation.

    Configure one of these sources with all four values:
    - Gradle properties: orcaReleaseStoreFile, orcaReleaseStorePassword, orcaReleaseKeyAlias, orcaReleaseKeyPassword
    - local.properties: orca.release.storeFile, orca.release.storePassword, orca.release.keyAlias, orca.release.keyPassword
    - environment: ORCA_RELEASE_STORE_FILE, ORCA_RELEASE_STORE_PASSWORD, ORCA_RELEASE_KEY_ALIAS, ORCA_RELEASE_KEY_PASSWORD
""".trimIndent()

val verifyReleaseUploadSigning by tasks.registering(VerifyReleaseUploadSigningTask::class) {
    hasConfig.set(hasReleaseUploadSigningConfig)
    helpMessage.set(releaseUploadSigningHelpMessage)
}

tasks.matching { it.name == "bundleRelease" }.configureEach {
    dependsOn(verifyReleaseUploadSigning)
}

android.stringBuildConfigField(
    name = "OPENAI_RESPONSES_URL",
    value = firstConfiguredValue(
        providers.gradleProperty("orcaOpenAiResponsesUrl").orNull,
        localProperty("orca.openAiResponsesUrl"),
        System.getenv("ORCA_OPENAI_RESPONSES_URL"),
        "https://api.openai.com/v1/responses",
    ),
)
android.stringBuildConfigField(
    name = "CLOUD_EXTRACTION_MODEL",
    value = firstConfiguredValue(
        providers.gradleProperty("orcaCloudExtractionModel").orNull,
        localProperty("orca.cloudExtractionModel"),
        System.getenv("ORCA_CLOUD_EXTRACTION_MODEL"),
        "gpt-5.4",
    ),
)
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.hilt.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.play.services.mlkit.document.scanner)
    ksp(libs.hilt.compiler)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.guava)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
