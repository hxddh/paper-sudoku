plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.titpea.papersudoku"
    compileSdk {
        version = release(37) {
            minorApiLevel = 0
        }
    }
    buildToolsVersion = "37.0.0"

    defaultConfig {
        applicationId = "com.titpea.papersudoku"
        minSdk = 26
        targetSdk = 36
        // versionCode = major × 10000 + minor × 100 + patch of versionName (checked by
        // tools/ci/release-preflight.sh), so every release has a larger one.
        versionCode = 110000
        versionName = "11.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    val sideload = providers.gradleProperty("paperSudoku.sideload").orNull == "true"
    val releaseKeystore = providers.environmentVariable("PAPER_SUDOKU_KEYSTORE").orNull
    val releaseStorePassword = providers.environmentVariable("PAPER_SUDOKU_STORE_PASSWORD").orNull
    val releaseKeyAlias = providers.environmentVariable("PAPER_SUDOKU_KEY_ALIAS").orNull
    val releaseKeyPassword = providers.environmentVariable("PAPER_SUDOKU_KEY_PASSWORD").orNull
    // The fixed key for the GitHub "-sideload" APKs (not the Play upload key), so one sideload
    // build updates the next. Without it, sideload builds fall back to the debug key.
    val sideloadKeystore = providers.environmentVariable("PAPER_SUDOKU_SIDELOAD_KEYSTORE").orNull
    val sideloadStorePassword = providers.environmentVariable("PAPER_SUDOKU_SIDELOAD_STORE_PASSWORD").orNull
    val sideloadKeyAlias = providers.environmentVariable("PAPER_SUDOKU_SIDELOAD_KEY_ALIAS").orNull
    val sideloadKeyPassword = providers.environmentVariable("PAPER_SUDOKU_SIDELOAD_KEY_PASSWORD").orNull

    signingConfigs {
        if (sideloadKeystore != null && sideloadStorePassword != null &&
            sideloadKeyAlias != null && sideloadKeyPassword != null
        ) {
            create("sideload") {
                storeFile = file(sideloadKeystore)
                storePassword = sideloadStorePassword
                keyAlias = sideloadKeyAlias
                keyPassword = sideloadKeyPassword
            }
        }
        if (releaseKeystore != null && releaseStorePassword != null &&
            releaseKeyAlias != null && releaseKeyPassword != null
        ) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // -PpaperSudoku.sideload=true signs for the GitHub "-sideload" releases: with the
            // sideload key above when its four PAPER_SUDOKU_SIDELOAD_* variables are set, else
            // with the build machine's debug key. Play builds use the private upload key above.
            signingConfig = if (sideload) {
                signingConfigs.findByName("sideload") ?: signingConfigs.getByName("debug")
            } else {
                signingConfigs.findByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        // Release code, debug-signed and profileable, for the :benchmark Macrobenchmarks.
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            isDebuggable = false
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

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    // Unit tests run against android.jar's stubs; trace sections (android.os.Trace) are no-ops there.
    testOptions.unitTests.isReturnDefaultValues = true

    lint {
        abortOnError = true
        // Warnings don't fail the build. Lint always writes its HTML, XML, SARIF and text reports
        // to build/reports; android.yml prints the findings (tools/ci/lint-summary.py) and
        // uploads the reports.
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":core:game"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.compose.material3.adaptive.layout)
    implementation(libs.androidx.compose.material3.adaptive.navigation)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.datastore.preferences)
    ksp(libs.androidx.room.compiler)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4.accessibility)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
