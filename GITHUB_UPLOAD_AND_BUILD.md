# ADI_STORE_V1 — Easy GitHub APK Build

## Important: upload the folders too

When GitHub shows **Upload files**, do NOT select only individual files.

1. Extract this ZIP on Windows.
2. Open the extracted project folder in File Explorer.
3. You should see:
   - `.github`
   - `app`
   - `gradle` (if present)
   - `build.gradle.kts`
   - `settings.gradle.kts`
4. In File Explorer, select the **whole project folder** and drag it into GitHub's upload box.
5. Wait until GitHub shows the `app` folder and `.github` folder in the upload list.
6. Click **Commit changes**.

If the browser's file picker does not allow dragging the whole folder, upload the `app` folder and the other project files first, then create the workflow from GitHub's **Actions** page.

## Build

After the project is uploaded:

1. Open **Actions**.
2. Choose **Build Android APK**.
3. Click **Run workflow**.
4. Wait for the green **Success** check.
5. Open the completed run.
6. Under **Artifacts**, download **ADI_STORE_V1-debug-apk**.
7. Extract the artifact and install the `.apk` on your Android phone.

This workflow installs Gradle 8.9 on the GitHub runner, so a local Gradle wrapper is not required for the GitHub build.

## Security

Use only the Supabase publishable key in the Android app. Never add a Supabase service-role/secret key.
