# Project Structure

## Root Level
```
iAttendance/
├── app/                    # Main application module
├── gradle/                 # Gradle wrapper and version catalog
├── build.gradle           # Root build configuration
├── settings.gradle        # Project settings
└── gradle.properties      # Global Gradle properties
```

## App Module Structure
```
app/
├── src/
│   ├── main/
│   │   ├── java/com/example/iattendance/    # Java source code
│   │   ├── res/                             # Android resources
│   │   └── AndroidManifest.xml              # App manifest
│   ├── test/                                # Unit tests
│   └── androidTest/                         # Instrumented tests
├── build.gradle                             # App-level build config
├── google-services.json                     # Firebase configuration
└── proguard-rules.pro                       # ProGuard rules
```

## Source Code Organization
All Java classes are located in `app/src/main/java/com/example/iattendance/`:

- **MainActivity.java** - Main entry point and login screen
- **LoadingScreenActivity.java** - App launch screen
- **EmailVerificationActivity.java** - Email verification flow
- **StudentDashboardActivity.java** - Student interface
- **TeacherDashboardActivity.java** - Teacher interface  
- **AdminDashboardActivity.java** - Administrator interface
- **FirebaseAuthHelper.java** - Firebase authentication utilities
- **SessionManager.java** - User session management
- **EmailService.java** - Email sending functionality

## Resource Structure
- **layouts/**: XML layout files for activities and fragments
- **values/**: Strings, colors, dimensions, styles
- **drawable/**: Vector drawables and images
- **mipmap-*/**: App launcher icons for different densities
- **anim/**: Animation resources
- **menu/**: Menu definitions

## Naming Conventions
- **Activities**: `[Purpose]Activity.java` (e.g., `StudentDashboardActivity.java`)
- **Layouts**: `activity_[name].xml` for activities
- **Resources**: Use snake_case for resource names
- **Package**: Single package structure under `com.example.iattendance`