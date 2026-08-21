import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "dev.mewdeko.mobile"
    compileSdk = 36

    defaultConfig {
        /*
         * The Play identity, deliberately distinct from `namespace`: the code
         * package stays `dev.mewdeko.mobile` while the published app matches
         * the mewdeko.tech reverse-domain convention used by the other apps.
         */
        applicationId = "tech.mewdeko.mobile"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    /*
     * Release signing is read from a gitignored `keystore.properties` or, in
     * CI, from the environment. The same key signs Play uploads and GitHub
     * release APKs so a user can move between them without reinstalling.
     */
    val keystoreProperties = Properties().apply {
        val file = rootProject.file("keystore.properties")
        if (file.exists()) file.inputStream().use { load(it) }
    }

    fun secret(key: String, env: String): String? =
        keystoreProperties.getProperty(key) ?: System.getenv(env)

    signingConfigs {
        create("release") {
            val path = secret("storeFile", "MEWDEKO_KEYSTORE")
            if (path != null) {
                storeFile = file(path)
                storePassword = secret("storePassword", "MEWDEKO_KEYSTORE_PASSWORD")
                keyAlias = secret("keyAlias", "MEWDEKO_KEY_ALIAS")
                keyPassword = secret("keyPassword", "MEWDEKO_KEY_PASSWORD")
                /*
                 * v1 is unnecessary above API 24 and this app targets 26+.
                 * v3 is worth enabling explicitly: it is the scheme that
                 * carries a rotation proof, so this key can be rotated later
                 * without stranding installs from GitHub releases.
                 */
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
                .takeIf { it.storeFile != null }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    /*
     * Lint's UAST analysis crashes on this source set with AGP 8.7 and Kotlin
     * 2.1 ("symbol pointer already disposed"), a lint bug rather than a
     * finding. It reproduces on lintDebug too, in a different codepath, and
     * disabling the detector the error message names doesn't help, so lint
     * is not runnable at all right now. Release packaging must not depend
     * on it.
     */
    lint {
        checkReleaseBuilds = false
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.add("-opt-in=kotlin.time.ExperimentalTime")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.window)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.palette)
    implementation(libs.androidx.browser)

    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.ktor.client.websockets)
    implementation(libs.ktor.client.logging)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.coil.compose)
    implementation(libs.vico.compose.m3)
}
