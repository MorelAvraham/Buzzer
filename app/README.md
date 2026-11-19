# app

The Android phone app, in Kotlin. It connects to the sleeve over BLE, records the video and the sleeve data on one clock, shows a list of the shots with their measures, plays the video of any shot, and marks the measures that were different from the player's own made shots.

Status: project skeleton only (#16). The app opens one screen with the name Buzzer. None of the modules below is built yet.

## Stage 1 needs only three things

1. **Connect** to the sleeve over BLE (one sensor on the hand).
2. **Record** the video and the sensor data on one clock.
3. **Export** the files for the laptop analysis, named as in [data/README.md](../data/README.md).

The shot list, the replay and the analysis on the phone come in stage 2.

## Planned modules

| Module | What it does | Stage |
|---|---|---|
| `ble` | Scan, connect, read the sensor packets, send the vibration command | 1 (read), 2 (vibration) |
| `record` | Start and stop a session, write the sensor CSV and the frame time stamps, export the files | 1 |
| `camera` | Video recording with CameraX or Camera2, a time stamp for every frame | 1 |
| `shots` | The list of shots with their measures, marks for measures that differ from the made shots | 2 |
| `replay` | Play the video of one shot | 2 |
| `analysis` | The camera and sleeve analysis from `vision/` and `analysis/`, run on the phone with LiteRT | 2 |

## One clock

Everything is written on the phone clock, `SystemClock.elapsedRealtimeNanos()`, in milliseconds in the CSV files.

- **Video frames.** The camera gives a time stamp for every frame. Check `CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE` on each phone. If it is `REALTIME`, the frame time stamps are already on the phone clock. If it is `UNKNOWN`, they need a conversion. Write the frame number and time of every frame to a CSV next to the video.
- **Sensor samples.** The sleeve sends its own board time with each packet. The app turns board time into phone time. How to do this is part of the BLE draft in [firmware/README.md](../firmware/README.md).
- **Check.** Tap the hand sensor against the ball in front of the camera at the start of each recording. The tap is visible in the video and in the sensor data, so it shows if the two clocks match.

## Project settings

Proposed in #16. Change them in `mobile/build.gradle.kts` and here if the team decides otherwise.

| Setting | Value | Why |
|---|---|---|
| Package name | `il.ac.huji.buzzer` | University domain, reversed |
| Minimum Android version | Android 10 (API 29) | To be checked against the three team phones in #17 |
| Compile and target SDK | API 37 | Latest stable SDK in November 2026 |
| Gradle module | `:mobile` | One module for now. The planned modules are packages inside it |

Tool versions: Gradle 9.9.1 (wrapper), Android Gradle Plugin 9.5.1, Kotlin 2.4.21, Compose BOM 2026.09.00, JDK 17 or newer. All library versions are in `gradle/libs.versions.toml`.

## Files

```
app/
  settings.gradle.kts         project name and modules
  build.gradle.kts            plugin versions for all modules
  gradle/libs.versions.toml   all library and plugin versions
  gradlew, gradlew.bat        Gradle wrapper, use these instead of a local Gradle
  mobile/
    build.gradle.kts          Android settings for the app
    src/main/                 app code, manifest and resources
    src/test/                 tests that run on the laptop with Robolectric
```

## Setup

1. Install Android Studio, the latest stable version, from https://developer.android.com/studio. It comes with a JDK.
2. Open the `app/` folder (not the repository root) in Android Studio. It downloads the SDK and Gradle the first time.
3. On the phone, turn on Developer options and USB debugging.
4. Check that the phone is connected:

```bash
adb devices
```

Build, test and install from the `app/` folder:

```bash
./gradlew assembleDebug
```

```bash
./gradlew testDebugUnitTest
```

```bash
./gradlew lintDebug
```

```bash
./gradlew installDebug
```

On Windows, use `gradlew.bat` instead of `./gradlew`.

## What was checked

On a Linux machine without a phone or an emulator, with JDK 21 and Android SDK platform 37:

- `./gradlew clean testDebugUnitTest lintDebug assembleDebug` passed.
- `testDebugUnitTest`: 1 test, 0 failures. It starts `MainActivity` with Robolectric and checks that the screen shows "Buzzer".
- `lintDebug`: no issues.
- The debug APK has package `il.ac.huji.buzzer`, minimum SDK 29, target SDK 37 and the launcher activity `MainActivity`.

Not checked yet: installing and opening the app on a real phone. This is the last step of #16.

## Code from the earlier project

The earlier project, CoachPocket, had an Android app with a BLE client, a recorder and a clock sync class. It was built for a different sensor pod and has been removed from this folder. It is still in the Git history at commit `3f8c6a3`, for example:

```bash
git show 3f8c6a3:app/core/src/main/kotlin/com/coachpocket/core/sync/ClockSync.kt
```

Other files there: `app/mobile/src/main/java/com/coachpocket/app/ble/PodBleClient.kt` and `app/mobile/src/main/java/com/coachpocket/app/record/SessionRecorder.kt`. They may help as a reference. They have not been checked for Buzzer.

## Good first tasks

- Install the debug app on your own phone, open it, and write the phone model and Android version in #16.
- Add a GitHub Actions workflow that runs `./gradlew testDebugUnitTest lintDebug assembleDebug` in `app/` on every pull request that changes `app/`.
- Replace the placeholder launcher icon in `mobile/src/main/res/drawable/ic_launcher.xml` with the Buzzer logo from `docs/assets/`.
