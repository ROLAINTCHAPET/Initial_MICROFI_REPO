plugins {
    id("com.android.application")
    // The Flutter Gradle Plugin must be applied after the Android and Kotlin Gradle plugins.
    id("dev.flutter.flutter-gradle-plugin")
}

android {
    namespace = "com.microfi.microfi_mobile"
    // permission_handler_android requires its dependents to compile against API 37 or later, so
    // Flutter's default (36) is not enough here. But API 37 ships only in minor-versioned form --
    // android-37.0/37.1/37.2, the same way android-36.1 does -- and Google publishes no plain
    // "android-37". A bare `compileSdk = 37`, which is how this was previously written (assigned
    // twice, silently overriding flutter.compileSdkVersion on the line above), therefore fails
    // with "Failed to find target with hash string 'android-37'" even once Platform 37.0 is
    // installed. AGP 9 expresses a minor-versioned platform as the two properties below.
    compileSdk = 37
    compileSdkMinor = 0
    ndkVersion = flutter.ndkVersion

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        // TODO: Specify your own unique Application ID (https://developer.android.com/studio/build/application-id.html).
        applicationId = "com.microfi.microfi_mobile"
        // You can update the following values to match your application needs.
        // For more information, see: https://flutter.dev/to/review-gradle-config.
        minSdk = flutter.minSdkVersion
        targetSdk = flutter.targetSdkVersion
        versionCode = flutter.versionCode
        versionName = flutter.versionName
    }

    buildTypes {
        release {
            // TODO: Add your own signing config for the release build.
            // Signing with the debug keys for now, so `flutter run --release` works.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

flutter {
    source = "../.."
}
