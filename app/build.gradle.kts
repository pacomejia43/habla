plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// En GitHub Actions, el número de ejecución se usa como versión (1, 2, 3...).
val code = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 1
// En GitHub Actions este valor es "usuario/repositorio" y la app sabe dónde buscar actualizaciones.
val repo = System.getenv("GITHUB_REPOSITORY") ?: ""

android {
    namespace = "mx.habla"
    compileSdk = 34

    defaultConfig {
        applicationId = "mx.habla"
        minSdk = 26
        targetSdk = 34
        versionCode = code
        versionName = "1.$code"
        buildConfigField("String", "GITHUB_REPO", "\"$repo\"")
    }

    signingConfigs {
        // Llave fija incluida en el repo: así cada versión se firma igual y Android permite actualizar.
        create("release") {
            storeFile = file("habla.keystore")
            storePassword = "habla123"
            keyAlias = "habla"
            keyPassword = "habla123"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core:1.13.1") // FileProvider para instalar la actualización
}
