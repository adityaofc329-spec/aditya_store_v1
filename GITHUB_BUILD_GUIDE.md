# ADI_STORE_V1 — GitHub APK Build

## Build APK without Android Studio

1. Create a GitHub repository.
2. Upload the **contents of this project** (not the ZIP file itself) to the repository.
3. Open **Actions**.
4. Select **Build Android APK**.
5. Click **Run workflow**.
6. Wait for the workflow to finish.
7. Open the completed workflow run.
8. Under **Artifacts**, download `ADI_STORE_V1-debug-apk`.
9. Extract the downloaded artifact and install the `.apk` on your Android phone.

## Important

- This workflow builds a **debug APK**.
- A debug APK is suitable for testing, not final Play Store release.
- Do not put Supabase service-role/secret keys in the Android project.
- Use only the Supabase publishable key on the client.
- Before production release, configure a proper signing key and a release build.
