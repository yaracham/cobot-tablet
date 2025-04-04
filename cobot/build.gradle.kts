plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.jetbrains.kotlin.android) apply false
//    id("com.chaquo.python") version "16.0.0" apply false
}

buildscript {
    repositories {
        google()
        mavenCentral()
        maven("https://chaquo.com/maven")
//        maven { "https://jitpack.io" }
    }
    dependencies {
        classpath(libs.gradle)
    }

}

//allprojects {
//    repositories {
//        google()
//        mavenCentral()
//    }
//}
