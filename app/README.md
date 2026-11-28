# app

The Android phone app, in Kotlin. It connects to the sleeve over BLE, records the video and the sleeve data on one clock, shows a list of the shots with their measures, plays the video of any shot, and marks the measures that were different from the player's own made shots.

Status: the stage 1 recorder is written and its logic is unit tested on a laptop. **It has not been run on a phone, with a real sleeve or with a real camera yet.** The screens are below. Issues #17 to #23 say what is left to check on a device.

## Stage 1 needs only three things

1. **Connect** to the sleeve over BLE (one sensor on the hand).
2. **Record** the video and the sensor data on one clock.
3. **Export** the files for the laptop analysis, named as in [data/README.md](../data/README.md).

The shot list, the replay and the analysis on the phone come in stage 2.

## Screens

| Screen | What it does | Issue |
|---|---|---|
| Record a session | Player code and session details, video and hand sensor together, then a summary and a share button | #22 |
| Sleeve | Bluetooth permission, scan, connect, link stats (board rate, samples that arrive, lost packets, ping round trip), start and stop streaming | #18, #19 |
| Video test | Video only, at 30 or 60 fps, and how many frames got a time | #20 |
| Camera info | Timestamp source and frame rates of each camera, copied as a table | #17 |

## Code

| Package | What it does | Stage |
|---|---|---|
| `ble` | Packet decoder, lost packet count, BLE scan and connect, commands, time pings | 1 (read), 2 (vibration) |
| `sync` | Board clock unwrap and the board time to phone time mapping | 1 |
| `camera` | CameraX video with a time for every frame, camera info | 1 |
| `record` | Session names, `hand.csv`, `meta.json`, and the session that ties video and sensor together | 1 |
| `ui` | The screens | 1 |
| `shots`, `replay`, `analysis` | Shot list, replay, and the analysis on the phone with LiteRT | 2, not started |

## One session on the phone

1. Connect the sleeve on the **Sleeve** screen.
2. Open **Record a session**, type the player code (P01, never a name), fill in the details and press **Start**.
3. Tap the hand sensor against the ball in front of the camera (#23), then shoot.
4. Press **Stop**. The app writes one folder, named as in [data/README.md](../data/README.md):

```
20261201_P01_S01/
  20261201_P01_S01_video.mp4
  20261201_P01_S01_frametimes.csv
  20261201_P01_S01_hand.csv
  20261201_P01_S01_meta.json
```

5. Press **Share the session files** to send them, for example to the shared Drive folder. Or copy all sessions with a cable:

```bash
adb pull /sdcard/Android/data/il.ac.huji.buzzer/files/sessions/
```

If the sleeve is not connected, only the video is recorded and the summary says so. If no time ping reply arrived, `hand.csv` is not written, because the sensor times cannot be put on the phone clock.

## One clock

Everything is written on the phone clock, `SystemClock.elapsedRealtimeNanos()`, in milliseconds in the CSV files.

- **Video frames.** A Camera2 capture callback reports the sensor time stamp of every frame. After the recording, the frame times in the MP4 file are matched to those reports, so `frametimes.csv` has one row per video frame even if the encoder drops frames. If the camera timestamp source is `REALTIME`, the time stamps are already on the phone clock. If it is `UNKNOWN`, the offset is estimated and can be late by a few ms. Code: `camera/FrameTimes.kt`.
- **Sensor samples.** The sleeve sends its board time with each packet. The phone sends a time ping every second. At the end of the session the app fits a straight line from board time to phone time through the best ping of each 5 s window. Code: `sync/ClockSync.kt`.
- **Check.** Tap the hand sensor against the ball in front of the camera at the start of each recording. The tap is visible in the video and in the sensor data, so it shows if the two clocks match (#23).

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

- `./gradlew testDebugUnitTest lintDebug assembleDebug` passes: 59 tests, 0 failures, lint with 0 errors.
- The tests cover the packet decoder (with a packet written byte by byte), lost packets, the clock mapping (with a simulated clock that runs 100 ppm fast), the CSV files, the matching of video frames to camera reports (with dropped frames), session names, `meta.json`, and that each screen opens.

Not checked: anything on a real phone, with a real camera or with a real sleeve. BLE and CameraX code only compiles here.

## Code from the earlier project

The earlier project, CoachPocket, had an Android app with a BLE client, a recorder and a clock sync class. It was built for a different sensor pod and has been removed from this folder. It is still in the Git history at commit `3f8c6a3`, for example:

```bash
git show 3f8c6a3:app/core/src/main/kotlin/com/coachpocket/core/sync/ClockSync.kt
```

Other files there: `app/mobile/src/main/java/com/coachpocket/app/ble/PodBleClient.kt` and `app/mobile/src/main/java/com/coachpocket/app/record/SessionRecorder.kt`. They may help as a reference. They have not been checked for Buzzer.

## Good first tasks

- Install the app on your phone, open **Camera info** and paste the table in #17.
- Set up a fake sleeve in the nRF Connect app on a second phone and follow the steps in #18. Write what happened in the issue.
- Record 10 seconds on **Video test** at 30 and at 60 fps and check that `frametimes.csv` has one row per video frame (#20).
- Add a GitHub Actions workflow that runs `./gradlew testDebugUnitTest lintDebug assembleDebug` in `app/` on every pull request that changes `app/`.
