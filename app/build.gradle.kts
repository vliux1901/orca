import com.android.build.api.dsl.ApplicationExtension
import java.util.Properties

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

fun firstConfiguredValue(vararg values: String?): String =
    values.firstOrNull { !it.isNullOrBlank() } ?: ""

fun ApplicationExtension.stringBuildConfigField(name: String, value: String) {
    defaultConfig {
        buildConfigField("String", name, "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
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
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
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
