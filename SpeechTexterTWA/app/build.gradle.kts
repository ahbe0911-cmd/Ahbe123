plugins {
    id("com.android.application")
}

android {
    namespace = "ir.ahmad.speechtexter.twa"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "ir.ahmad.speechtexter.twa"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "1.0.1"

    }

    androidResources {
        localeFilters += listOf("fa", "en")
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
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

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        warningsAsErrors = true
        checkDependencies = true
        // AGP 8.13 officially supports up to API 36. GitHub's runner also
        // installs the Android 17/API 37 preview, which makes lint report the
        // supported target as "old" even though compiling against 37 would be
        // outside this stable AGP's compatibility range.
        disable += "OldTargetApi"
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE.md",
                "META-INF/LICENSE-notice.md"
            )
        }
    }
}

dependencies {
    implementation("com.google.androidbrowserhelper:androidbrowserhelper:2.7.3")
    // Force the current stable AndroidX Browser instead of ABH's older transitive version.
    implementation("androidx.browser:browser:1.10.0")
}

val verifyTwaConfig by tasks.registering {
    group = "verification"
    description = "Checks the security-critical TWA configuration."

    val manifestFile = layout.projectDirectory.file("src/main/AndroidManifest.xml")
    val stringsFile = layout.projectDirectory.file("src/main/res/values/strings.xml")
    val launcherFile = layout.projectDirectory.file(
        "src/main/java/ir/ahmad/speechtexter/twa/SafeLauncherActivity.java"
    )

    inputs.files(manifestFile, stringsFile, launcherFile)

    doLast {
        val manifest = manifestFile.asFile.readText()
        val strings = stringsFile.asFile.readText()
        val launcher = launcherFile.asFile.readText()

        require("https://www.speechtexter.com/" in strings) {
            "SpeechTexter HTTPS launch URL is missing"
        }
        require("android.support.customtabs.trusted.DEFAULT_URL" in manifest) {
            "TWA default URL metadata is missing"
        }
        require("android:usesCleartextTraffic=\"false\"" in manifest) {
            "Cleartext traffic must stay disabled"
        }
        require("android.permission.RECORD_AUDIO" !in manifest) {
            "The TWA host must not request microphone access; Chrome owns that permission"
        }
        require("android.permission.WRITE_EXTERNAL_STORAGE" !in manifest) {
            "Legacy broad storage permission must not be present"
        }
        require(".SafeLauncherActivity" in manifest) {
            "Crash-safe launcher must remain the entry activity"
        }
        require("android:launchMode=\"singleTask\"" !in manifest) {
            "LauncherActivity must not use the incompatible singleTask launch mode"
        }
        require("getFallbackStrategy" in launcher && "catch (RuntimeException" in launcher) {
            "Browser fallback crash guard is missing"
        }
    }
}

tasks.named("check") {
    dependsOn(verifyTwaConfig)
}
