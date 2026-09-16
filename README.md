# Nutri

Kotlin Multiplatform + Compose Multiplatform starter for Android and iOS.

The Android UI currently includes the Feast Fit Today dashboard and Log & Scan
flow with local sample data and working hydration, portion, tab, and log actions.

## Linux setup

```sh
direnv allow
gradle :composeApp:assembleDebug
```

Set `ANDROID_HOME` to an installed Android SDK if it is not at `~/.android/sdk`.

## iOS

Open the iOS host in Xcode on macOS. iOS compilation and signing require macOS/Xcode.
