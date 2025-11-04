# Technology Stack

## Build System
- **Gradle** with Kotlin DSL support
- **Android Gradle Plugin**: 8.13.0
- **Java Version**: 11 (source and target compatibility)

## Core Technologies
- **Android SDK**: Compile SDK 36, Min SDK 24, Target SDK 36
- **Programming Language**: Java
- **Package Name**: `com.example.iattendance`

## Key Dependencies
- **AndroidX Libraries**: AppCompat, Material Design, ConstraintLayout, Activity
- **Firebase**: Authentication, Realtime Database, Analytics (BOM 32.7.0)
- **Email**: JavaMail API (android-mail 1.6.7, android-activation 1.6.7)
- **Security**: BCrypt password hashing (at.favre.lib:bcrypt 0.10.2)
- **Testing**: JUnit, Espresso, AndroidX Test

## Common Commands
```bash
# Build the project
./gradlew build

# Install debug APK
./gradlew installDebug

# Run unit tests
./gradlew test

# Run instrumented tests
./gradlew connectedAndroidTest

# Clean build
./gradlew clean

# Generate signed APK
./gradlew assembleRelease
```

## Configuration Files
- **Version Catalog**: `gradle/libs.versions.toml` for dependency management
- **Firebase Config**: `app/google-services.json` (required for Firebase services)
- **ProGuard**: `app/proguard-rules.pro` for code obfuscation in release builds