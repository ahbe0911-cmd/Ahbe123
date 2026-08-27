import java.util.Base64
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "ir.dasteyar.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "ir.dasteyar.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "1.2.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true; buildConfig = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }

    val generatedOmniRes = layout.buildDirectory.dir("generated/omnibox/res").get().asFile
    sourceSets.getByName("main").res.srcDir(generatedOmniRes)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

val prepareOmniBoxIcon by tasks.registering {
    val source = layout.projectDirectory.file("src/main/omnibox_icon.webp.b64")
    val output = layout.buildDirectory.file("generated/omnibox/res/drawable-nodpi/omnibox_icon.webp")
    inputs.file(source)
    outputs.file(output)
    doLast {
        val target = output.get().asFile
        target.parentFile.mkdirs()
        val encoded = source.asFile.readText().trim()
        target.writeBytes(Base64.getDecoder().decode(encoded))
    }
}

tasks.named("preBuild").configure {
    dependsOn(prepareOmniBoxIcon)
}

dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.2")
    implementation("androidx.compose.ui:ui:1.11.4")
    implementation("androidx.compose.ui:ui-tooling-preview:1.11.4")
    implementation("androidx.compose.ui:ui-text-google-fonts:1.11.4")
    implementation("androidx.compose.foundation:foundation:1.11.4")
    implementation("androidx.compose.animation:animation:1.11.4")
    implementation("androidx.compose.material3:material3:1.3.2")
    implementation("androidx.compose.material:material-icons-extended:1.6.0")
    debugImplementation("androidx.compose.ui:ui-tooling:1.11.4")
}
