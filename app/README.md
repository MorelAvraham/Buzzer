# app

The Android phone app, in Kotlin. It connects to the sleeve over BLE, records the video and the sleeve data on one clock, shows a list of the shots with their measures, plays the video of any shot, and marks the measures that were different from the player's own made shots.

Status: not started. There is no Android project in this folder yet. This README is the plan.

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

## Setup

1. Install Android Studio, the latest stable version, from https://developer.android.com/studio. It comes with a JDK.
2. In Android Studio open SDK Manager and install the latest Android SDK Platform and Android SDK Platform-Tools.
3. On the phone, turn on Developer options and USB debugging.
4. Check that the phone is connected:

```bash
adb devices
```

Once the project exists, build and install the debug app from this folder:

```bash
./gradlew assembleDebug
```

```bash
./gradlew installDebug
```

Decide together and write here: the package name, the minimum Android version, and the phones we test on.

## Code from the earlier project

The earlier project, CoachPocket, had an Android app with a BLE client, a recorder and a clock sync class. It was built for a different sensor pod and has been removed from this folder. It is still in the Git history at commit `3f8c6a3`, for example:

```bash
git show 3f8c6a3:app/core/src/main/kotlin/com/coachpocket/core/sync/ClockSync.kt
```

Other files there: `app/mobile/src/main/java/com/coachpocket/app/ble/PodBleClient.kt` and `app/mobile/src/main/java/com/coachpocket/app/record/SessionRecorder.kt`. They may help as a reference. They have not been checked for Buzzer.

## Good first tasks

- Create the empty Android project in this folder (Kotlin, Jetpack Compose), check that the debug build installs on your phone, and commit it.
- For each phone in the team, find the value of `SENSOR_INFO_TIMESTAMP_SOURCE` and the highest frame rate of the back camera. Write them in an issue.
- Write a first screen that scans for BLE devices and shows their names and signal strength.
