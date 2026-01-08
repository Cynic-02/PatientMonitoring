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

---

## Release-signed build (optional)

You can configure a signed Release build in CI by creating a Java keystore and storing it as a secret in the GitHub repository.

1) Generate a keystore locally:

```bash
keytool -genkeypair -v -keystore my-release-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias my_key_alias
```

2) Base64-encode the keystore and add it as a repository secret named `KEYSTORE_BASE64`:

- macOS / Linux: `base64 my-release-key.jks | pbcopy` (or `base64 my-release-key.jks > keystore.b64` then paste)
- Windows (PowerShell): `[Convert]::ToBase64String([IO.File]::ReadAllBytes('my-release-key.jks')) | Set-Clipboard`

3) Add secrets `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD` (same alias password used when generating the keystore).

4) There is a provided GitHub Actions workflow `.github/workflows/android-release.yml` which:
   - Decodes the keystore from `KEYSTORE_BASE64` and saves it as `release-keystore.jks` in the runner
   - Sets `KEYSTORE_PATH` and invokes Gradle `assembleRelease` with signing values read from environment variables
   - Uploads the resulting `app-release.apk` as an artifact named `app-release-apk` (download from Actions UI)

5) Use the workflow dispatch or push a tag like `v1.0.0` to trigger a release-signed build.

If you'd like, I can help you generate the keystore locally or guide you through adding the secrets in the repository settings.
