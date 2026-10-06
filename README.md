# Kamal File Manager

Android file manager with full /sdcard/Android/data/ access via Dhizuku Device Owner.

## Build

This project uses GitHub Actions to build automatically.
Push to main branch → APK appears in Actions → Artifacts.

## Setup (One Time)

After installing the APK, run via Termux on phone (see below):

```
adb shell dpm set-device-owner com.kovak.kamal/.DhizukuAdmin
```

## No ADB on PC? Use Termux on your Phone

See TERMUX_SETUP.md
