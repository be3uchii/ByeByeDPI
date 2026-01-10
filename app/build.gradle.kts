import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val abis = setOf("armeabi-v7a", "arm64-v8a")

android {
    namespace = "io.github.dovecoteescapee.byedpi"
    compileSdk = 34

    defaultConfig {
        applicationId = "io.github.romanvht.byedpi"
        minSdk = 21
        targetSdk = 33
        versionCode = 1690
        versionName = "1.6.9"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters.addAll(abis)
        }
        
        // Оставляем только нужные языки для экономии места
        resourceConfigurations.addAll(listOf("en", "ru"))
    }

    signingConfigs {
        create("release") {
            storeFile = file("release.keystore")
            storePassword = "android_secure"
            keyAlias = "release_key"
            keyPassword = "android_secure"
            enableV1Signing = true
            enableV2Signing = true
        }
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_1_8)
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    splits {
        abi {
            isEnable = true
            reset()
            include(*abis.toTypedArray())
            isUniversalApk = false // ОТКЛЮЧИЛИ УНИВЕРСАЛЬНЫЙ
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-service:2.6.2")
}

tasks.register<Exec>("runNdkBuild") {
    group = "build"
    val ndkDir = android.ndkDirectory
    executable = if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) "$ndkDir\\ndk-build.cmd" else "$ndkDir/ndk-build"
    setArgs(listOf(
        "NDK_PROJECT_PATH=build/intermediates/ndkBuild",
        "NDK_LIBS_OUT=src/main/jniLibs",
        "APP_BUILD_SCRIPT=src/main/jni/Android.mk",
        "NDK_APPLICATION_MK=src/main/jni/Application.mk"
    ))
}

tasks.preBuild {
    dependsOn("runNdkBuild")
}
