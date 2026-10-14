# Tachymeter

A small personal Android speedometer written in **Kotlin**, using Android's native GNSS APIs and platform views. Inspired by CoMaps' quiet green palette and simple controls; no CoMaps assets or code are used.

Version 1.3 centers a smaller “44” readout inside the adaptive gauge icon. Version 1.1 removes the promotional footer, and replaces the scrolling container with a fixed, height-adaptive layout. Landscape uses two columns. When a location action is required in landscape, empty signal details are hidden to keep that action and both controls visible. Install over version 1.0; the signing identity is unchanged.

## Install

The ready-to-install, self-signed APK is **[dist/tachymeter-1.3.apk](dist/tachymeter-1.3.apk)**. It supports Android 8.0 and newer with a GNSS receiver, across processor architectures.

1. Transfer the APK to your phone (USB, Quick Share, or your preferred file transfer).
2. Open it in the phone's file manager. If prompted, allow that file manager to install unknown apps, then tap Install.
3. Open Tachymeter, tap **Allow location**, and choose **Precise** and **While using the app**.
4. Go outdoors with a clear view of the sky. The first satellite fix may take a while.

The only app permissions are coarse and fine location. There is **no internet permission**, advertising SDK, Google Play services dependency, account, analytics, location log, or background service. Only the units, screen-awake preference, and permission-request flag are saved. The screen stays awake by default while this activity is visible; the control can disable that. Location updates stop when the activity is paused, including when another app or the lock screen takes over.

## Measurement behavior

- Requests the platform `LocationManager.GPS_PROVIDER` at a 500 ms interval, with no minimum movement or batching. Actual frequency depends on the receiver and OS.
- Uses `Location.speed` directly, rather than differencing coordinates. Android documents that native speed can be more accurate, for example when GNSS Doppler measurements are used by the receiver.
- Does not smooth speed, snap small values to zero, or fabricate speed when a location contains none.
- Displays `speedAccuracyMetersPerSecond` in the selected units. Android defines this as an estimated 68% confidence uncertainty, not a guaranteed bound. A decimal digit is a display choice, not a precision claim.
- Flags uncertainty over 1 m/s (3.6 km/h), missing uncertainty, and simulated locations. The 1 m/s cutoff is an app display policy, not a hardware guarantee.
- Rejects invalid, future, old, duplicate, and out-of-order measurements. Hides a reading at three seconds of age, evaluated every 250 ms with the monotonic clock.
- Clears readings after leaving the app or losing the provider. Position accuracy is shown separately from speed accuracy.
- Supports km/h and mph, system light/dark mode, a fixed screen with a two-column landscape layout, and Android font scaling.

Real accuracy is limited by your phone's receiver, sky visibility, interference, and the receiver's own processing. Emulator tests validate software behavior; they do not measure physical GNSS accuracy on your phone.

## Build and signing

The included APK was built with Kotlin 2.2.20, Android SDK build tools 36.0.0, and the installed Android 37.0 platform stubs; it targets API 36 with a minimum API of 26. Only Kotlin's standard library is packaged alongside the app.

The fully offline SDK build uses the Android Studio bundled JDK and existing local tools:

```sh
./scripts/setup-kotlin.sh  # only needed if .tools/kotlinc is absent; downloads official compiler
./scripts/test.sh
./scripts/build.sh
```

Override `JAVA_HOME`, `ANDROID_SDK_ROOT`, `ANDROID_JAR`, or `KOTLIN_HOME` for another machine. The scripts default to this Mac's Android Studio and SDK paths. Android Studio Gradle project metadata is also included; it requires the API 36 SDK, Gradle 8.13, JDK 17, and first-time plugin downloads. The delivered APK uses the verified SDK build script.

The signing identity is generated locally in `signing/tachymeter.p12`, with its password in `signing/password.txt`. These files have restricted permissions and are excluded from source control. **Keep a private backup of both to sign future updates with the same identity.** Do not distribute them with the APK. The build reuses this key; the APK is not debuggable. Signature information and the APK checksum are in `dist/`.

## Verification

`scripts/test.sh` runs 22 Kotlin assertions covering conversions, uncertainty, zero/missing/invalid measurements, freshness boundaries, and mock status.

`scripts/test-android.sh` builds a separate test APK, installs both APKs on `ANDROID_SERIAL` (default `emulator-5556`), grants location on that test device, and runs 14 Android integration assertions. They cover the actual activity readout, unit button, uncertainty states, mock labelling, stale-data rejection, signal timeout, and provider loss. The synthetic feed is isolated from the emulator's own GPS. Test code is not included in the delivered APK. Use this script only on a test device.

The APK signature and alignment are verified on every build. Screenshots under `dist/` are emulator captures; the speed preview is explicitly simulated.

## Android references

- [Native speed and its accuracy](https://developer.android.com/reference/android/location/Location#getSpeed())
- [Speed uncertainty definition](https://developer.android.com/reference/android/location/Location#getSpeedAccuracyMetersPerSecond())
- [Platform location updates](https://developer.android.com/reference/android/location/LocationManager)
- [Visual inspiration: CoMaps](https://www.comaps.app/)
