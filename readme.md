Technical Details

Minimum Android Version: 6.0 (API 23)
Target Android Version: 14 (API 34)
Bluetooth Type: Classic Bluetooth (SPP)
UUID: 00001101-0000-1000-8000-00805F9B34FB
Programming Language: Java
UI Framework: Android XML layouts with Material Design

---

## CI Build (GitHub Actions) ✅

This repository contains a GitHub Actions workflow that builds a debug APK and uploads it as a downloadable artifact on every push to `main` or `master`, on pull requests, or when triggered manually.

How to use:
1. Push your repository to GitHub (create a new repo and push all files).
2. If you prefer reproducible builds, generate and commit the Gradle wrapper locally by running `gradle wrapper` on a machine with Gradle installed (this will add `gradlew`, `gradlew.bat` and `gradle/wrapper/gradle-wrapper.jar`).
3. On GitHub, go to Actions → select **Android Build** workflow and run it (or wait for `push`).
4. When the workflow completes, open the workflow run and download the artifact named `app-debug-apk` (contains `app-debug.apk`).

Notes:
- The workflow uses `gradle/gradle-build-action` to install Gradle on the runner, so the wrapper is not strictly required for the workflow to run. However, committing the Gradle wrapper is recommended for reproducible builds.
- The APK produced is the Debug build (signed with the debug key) and is suitable for installing on a device for testing.
