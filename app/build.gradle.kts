plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Numéro de version fourni par la compilation GitHub (numéro de l'exécution), 1 en local.
val buildNumber = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 1

// La clé de signature n'est jamais dans le dépôt : elle arrive par des secrets GitHub.
val releaseKeystorePath: String? = System.getenv("ASSISTANT_KEYSTORE_PATH")

android {
    namespace = "fr.bastien.assistant"
    compileSdk = 35

    defaultConfig {
        applicationId = "fr.bastien.assistant"
        minSdk = 26
        targetSdk = 35
        versionCode = buildNumber
        versionName = "0.2.$buildNumber"

        // Retour de la connexion Microsoft : fr.bastien.assistant://auth (voir TodoConfig.REDIRECT_URI).
        manifestPlaceholders["appAuthRedirectScheme"] = "fr.bastien.assistant"
    }

    signingConfigs {
        create("release") {
            if (releaseKeystorePath != null) {
                // Un seul mot de passe pour le fichier et la clé, alias fixe : deux secrets suffisent.
                val password = System.getenv("ASSISTANT_KEYSTORE_PASSWORD")
                storeFile = file(releaseKeystorePath)
                storePassword = password
                keyAlias = "assistant"
                keyPassword = password
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (releaseKeystorePath != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
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

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

ksp {
    // Schéma de la base exporté à chaque compilation : indispensable pour les migrations sans perte.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.biometric)
    // Version récente imposée : celle tirée par biometric est trop ancienne pour les résultats d'activité.
    implementation(libs.androidx.fragment.ktx)

    implementation(libs.androidx.glance.appwidget)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.appauth)
    // Thème des écrans de connexion d'AppAuth (voir Theme.Assistant.AuthRedirect).
    implementation(libs.androidx.appcompat)
    implementation(libs.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
