# Bravia launcher

Native Kotlin + Compose for TV home launcher for a Sony Bravia (Android TV 10) at 192.168.0.160:5555. Build and install with `./deploy.sh --open`.

## Rules

- When the user says they're done with a change, commit it and push to `main` on GitHub (github.com/srujnil/bravia-launcher, public). Don't push mid-iteration; check `git status` first so no build output goes up.
- Explain each adb command before running anything that changes the TV's settings or packages.
- The stock launcher `com.google.android.tvlauncher` is disabled. Revert: `adb shell pm enable com.google.android.tvlauncher`.
