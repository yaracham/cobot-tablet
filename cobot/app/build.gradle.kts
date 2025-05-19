import com.android.build.api.dsl.AaptOptions
import org.apache.tools.ant.util.JavaEnvUtils.VERSION_1_8

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    kotlin("plugin.serialization") version "1.9.0"
}

configurations.all {
    resolutionStrategy.force(
        "org.jetbrains.kotlinx:kotlinx-serialization-core:1.6.3",
        "org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3",
    )
}

//    id("de.undercouch.download") version "5.0.0" }
//repositories {
//    google()
//    mavenCentral()
//}
android {
    namespace = "com.example.cobot"
    compileSdk = 34

    fun AaptOptions.() {
        noCompress += "tflite"
    }
    androidResources

    defaultConfig {
        applicationId = "com.example.cobot"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }
    packaging {
        resources {
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
    }
    buildToolsVersion = "34.0.0"


}

//val assetDir = layout.projectDirectory.dir("src/main/assets/models")
//
//tasks.register<de.undercouch.gradle.tasks.download.Download>("downloadModelFile") {
//    src("https://storage.googleapis.com/mediapipe-models/face_detector/blaze_face_short_range/float16/1/blaze_face_short_range.tflite")
//    dest(assetDir.file("face_detection_short_range.tflite"))
//    overwrite(false)
//}
//tasks.named("preBuild") {
//    dependsOn("downloadModelFile")
//}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.play.services.mlkit.face.detection)
    implementation(libs.firebase.crashlytics.buildtools)
    implementation(libs.androidx.room.ktx)
    implementation(libs.identity.jvm)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    implementation(libs.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    implementation(libs.tasks.vision)
    implementation(libs.tensorflow.lite.v2120)
    implementation(libs.tensorflow.lite.gpu)
    implementation(libs.tensorflow.lite.support)
    implementation(libs.tasks.vision.v01014)

    implementation("com.google.mlkit:object-detection:17.0.2")

    implementation(libs.tasks.core)

    implementation(libs.tasks.vision.v0107)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.serialization.json)


}