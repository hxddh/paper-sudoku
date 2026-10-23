plugins {
    alias(libs.plugins.android.test)
}

val benchmarkRules = providers.gradleProperty("paperSudoku.benchmarkRules").orNull ?: "Macrobenchmark"

android {
    namespace = "com.titpea.papersudoku.benchmark"
    compileSdk {
        version = release(37) {
            minorApiLevel = 0
        }
    }
    buildToolsVersion = "37.0.0"

    defaultConfig {
        minSdk = 28
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // CI measures on an emulator; numbers there are for trend, not for the 400 ms budget.
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR,LOW-BATTERY"
        // Macrobenchmarks (and the plain SmokeTest) by default; BaselineProfileGenerator runs only
        // with -PpaperSudoku.benchmarkRules=BaselineProfile (see that class).
        testInstrumentationRunnerArguments["androidx.benchmark.enabledRules"] = benchmarkRules
    }

    buildTypes {
        create("benchmark") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}

androidComponents {
    beforeVariants(selector().all()) { it.enable = it.buildType == "benchmark" }
}
