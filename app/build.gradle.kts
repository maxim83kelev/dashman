import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

// ─── Автоинкремент билда ──────────────────────────────────────────────────────

val versionPropsFile = file("version.properties")
val versionProps = Properties().apply {
    if (versionPropsFile.exists()) load(versionPropsFile.inputStream())
}
val buildNumber = (versionProps.getProperty("BUILD_NUMBER") ?: "1").toInt()

// Инкрементируем и сохраняем при каждой сборке
versionProps["BUILD_NUMBER"] = (buildNumber + 1).toString()
versionPropsFile.writer().use { versionProps.store(it, null) }

// ─────────────────────────────────────────────────────────────────────────────

val localProps = Properties().apply {
        val f = rootProject.file("local.properties")
        if (f.exists()) load(f.inputStream())
    }

android {
    namespace = "cz.kelev.dashman"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "cz.kelev.dashman"
        minSdk = 26
        targetSdk = 36
        versionCode = buildNumber
        versionName = "1.$buildNumber"

        buildConfigField("int", "BUILD_NUMBER", "$buildNumber")
        buildConfigField("String", "BASE_URL", "\"${localProps["BASE_URL"]}\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    androidResources {
        noCompress += listOf("png")
    }

    signingConfigs {
        create("release") {
            storeFile = file("shihta.keystore")
            storePassword = localProps["KEYSTORE_PASSWORD"] as String
            keyAlias = localProps["KEY_ALIAS"] as String
            keyPassword = localProps["KEY_PASSWORD"] as String
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material)
    implementation("com.google.android.material:material:1.12.0")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation("ai.picovoice:porcupine-android:4.0.0")
    ksp(libs.androidx.room.compiler)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}