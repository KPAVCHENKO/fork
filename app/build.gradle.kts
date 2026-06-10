import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) {
        f.inputStream().use { load(it) }
    }
}

fun prop(name: String, default: String): String = localProps.getProperty(name)?.trim() ?: default

android {
    namespace = "app.fork.messenger"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.fork.messenger"
        minSdk = 26
        targetSdk = 36
        versionCode = 6
        versionName = "0.6.0"

        // Только ABI, для которых собрана TDLib (см. tdlib/src/main/jniLibs)
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }

        // Секреты берутся из local.properties и НЕ попадают в git
        buildConfigField("int", "TG_API_ID", prop("tg.apiId", "0"))
        buildConfigField("String", "TG_API_HASH", "\"${prop("tg.apiHash", "")}\"")
        buildConfigField("String", "PROXY_HOST", "\"${prop("proxy.host", "")}\"")
        buildConfigField("int", "PROXY_PORT", prop("proxy.port", "0"))
        buildConfigField("String", "PROXY_SECRET", "\"${prop("proxy.secret", "")}\"")
        buildConfigField("String", "UPDATE_REPO", "\"${prop("update.repo", "")}\"")
    }

    signingConfigs {
        // Единый release-ключ для ВСЕХ релизов: если подписать другим ключом,
        // обновление поверх установленного приложения не встанет.
        val ksFile = rootProject.file(prop("keystore.file", "keystore/fork-release.jks"))
        if (ksFile.exists()) {
            create("release") {
                storeFile = ksFile
                storePassword = prop("keystore.password", "")
                keyAlias = prop("keystore.alias", "fork")
                keyPassword = prop("keystore.keyPassword", "")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":tdlib"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.coil.compose)
    implementation(libs.coil.gif)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.lottie.compose)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
}
