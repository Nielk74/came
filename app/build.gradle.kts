import java.io.FileOutputStream
import java.util.Base64

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

fun buildParam(name: String): String? =
    (System.getenv(name) ?: (project.findProperty(name) as String?))?.takeIf { it.isNotBlank() }

fun resolveKeystoreFile(): File? {
    buildParam("KEYSTORE_FILE")?.let { path ->
        File(path).takeIf(File::exists)?.let { return it }
    }
    buildParam("KEYSTORE_B64")?.let { encoded ->
        val output = layout.buildDirectory.file("release-keystore.jks").get().asFile
        output.parentFile?.mkdirs()
        FileOutputStream(output).use { it.write(Base64.getDecoder().decode(encoded.trim())) }
        return output
    }
    return null
}

android {
    namespace = "com.nielk74.came"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.nielk74.came"
        minSdk = 26
        targetSdk = 34
        versionCode = buildParam("VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = buildParam("VERSION_NAME") ?: "0.1.0"
        buildConfigField(
            "String",
            "GITHUB_REPO",
            "\"${buildParam("GITHUB_REPO") ?: "Nielk74/came"}\""
        )
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    val keystore = resolveKeystoreFile()
    val storePasswordValue = buildParam("STORE_PASSWORD")
    val keyAliasValue = buildParam("KEY_ALIAS")
    val keyPasswordValue = buildParam("KEY_PASSWORD")
    val canSignRelease = keystore != null && storePasswordValue != null &&
        keyAliasValue != null && keyPasswordValue != null

    signingConfigs {
        if (canSignRelease) {
            create("release") {
                storeFile = keystore
                storePassword = storePasswordValue
                keyAlias = keyAliasValue
                keyPassword = keyPasswordValue
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (canSignRelease) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging.resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.exifinterface)

    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}

