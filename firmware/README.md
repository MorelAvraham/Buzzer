# firmware

Firmware for the sleeve board: Seeed XIAO ESP32S3, programmed in C with ESP-IDF. The board reads the IMUs (ST LSM6DSV16X), sends the data to the phone over BLE and drives the vibration motors.

Status: not started. There is no ESP-IDF project in this folder yet. This README is the plan.

Stage 1 needs one IMU only, on the hand, and no motors.

## Planned modules

| Module | What it does | Stage |
|---|---|---|
| `imu` | Set up the LSM6DSV16X, read accelerometer and gyroscope samples from its FIFO | 1 (one IMU), 2 (three IMUs) |
| `timestamp` | Give every sample a time on the board clock in microseconds | 1 |
| `ble_stream` | BLE service: send sensor packets, receive start, stop and vibration commands | 1 |
| `vibration` | Drive the three coin motors, one per sensor point | 2 |

## Points to check before the sleeve is built

- **I2C addresses.** The LSM6DSV16X has only two I2C addresses (0x6A and 0x6B). Three sensors cannot share one I2C bus. Options: the ESP32-S3 has two I2C buses, or use SPI with one chip select line per sensor. Decide this with the hardware design.
- **Cable length.** I2C over a cable along the arm may lose data at high speed. U4 tests this.
- **Motors.** A coin motor draws more current than an ESP32 pin can give. Each motor needs a small transistor and a diode. See [hardware/README.md](../hardware/README.md).
- **Sample rate.** Not decided. The LSM6DSV16X can run at 120, 240, 480 or 960 samples per second, among others. A faster rate gives a more exact release time but more data over BLE.

## BLE packet format

**DRAFT. Not built and not agreed. The app and the firmware must agree on it before either side builds it.**

All numbers are little-endian.

### GATT

| | UUID | Properties |
|---|---|---|
| Buzzer service | `a0e80001-f9e8-48f1-abb4-2eccab5b865a` | advertised |
| Sensor data | `a0e80002-f9e8-48f1-abb4-2eccab5b865a` | notify |
| Control | `a0e80003-f9e8-48f1-abb4-2eccab5b865a` | write |
| Status | `a0e80004-f9e8-48f1-abb4-2eccab5b865a` | read, notify |

### Sensor data packet

One notification holds several samples from one sensor point.

| Offset | Type | Field |
|---|---|---|
| 0 | u8 | `version`, 1 for this draft |
| 1 | u8 | `point`: 0 hand, 1 forearm, 2 upper arm |
| 2 | u16 | `seq`: packet counter, +1 per packet, wraps at 65536. A gap means lost packets |
| 4 | u32 | `t0_us`: board time of the first sample in microseconds. Wraps after about 71 minutes |
| 8 | u16 | `dt_us`: time between samples in microseconds |
| 10 | u8 | `n`: number of samples in this packet |
| 11 | u8 | `flags`: accelerometer range in bits 0 to 1, gyroscope range in bits 2 to 4 |
| 12 | 12 bytes per sample | `ax, ay, az, gx, gy, gz` as i16 raw values |

With an MTU of 247 bytes, a packet can hold up to 19 samples (12 + 19 x 12 = 240 bytes).

Range codes in `flags`:

| Code | Accelerometer (bits 0 to 1) | Gyroscope (bits 2 to 4) |
|---|---|---|
| 0 | ±2 g | ±125 dps |
| 1 | ±4 g | ±250 dps |
| 2 | ±8 g | ±500 dps |
| 3 | ±16 g | ±1000 dps |
| 4 | | ±2000 dps |
| 5 | | ±4000 dps |

The app decoder for this draft is `app/mobile/src/main/java/il/ac/huji/buzzer/ble/SensorPacketDecoder.kt`, with tests that show example packets byte by byte.

### Control commands

| First byte | Command | Then |
|---|---|---|
| 0x01 | Start streaming | u16 sample rate in Hz |
| 0x02 | Stop streaming | nothing |
| 0x10 | Vibrate | u8 point, u16 duration in ms, u8 strength 0 to 255 |
| 0x20 | Time ping | u32 phone time in ms. The board answers on Status with the phone time and its own `t_us` |

The reply to a time ping, on Status, is 9 bytes: `0x20`, u32 the phone time from the request, u32 the board time in microseconds when the board read the request.

The time ping lets the app map board time to phone time. The app sends a ping about once a second during a recording. At the end it keeps the ping with the shortest round trip in each 5 s window and fits a straight line through them, which also corrects a small difference in clock speed. This assumes the delay is about the same in both directions. Code: `app/mobile/src/main/java/il/ac/huji/buzzer/sync/ClockSync.kt`.

## Setup

We plan to use ESP-IDF v5.5. When you install it, write the exact version (for example v5.5.1) here, so all three of us use the same one.

Install ESP-IDF on Linux or macOS:

```bash
mkdir -p ~/esp
```

```bash
cd ~/esp && git clone -b v5.5 --recursive https://github.com/espressif/esp-idf.git
```

```bash
cd ~/esp/esp-idf && ./install.sh esp32s3
```

Then, in every new terminal:

```bash
. ~/esp/esp-idf/export.sh
```

On Windows, use the ESP-IDF installer from https://docs.espressif.com/projects/esp-idf/en/stable/esp32s3/get-started/ instead.

Create the project the first time (one member does this and commits it):

```bash
cd firmware && idf.py create-project sleeve
```

Build and flash. The XIAO ESP32S3 shows up as `/dev/ttyACM0` on Linux. Check the port name on your machine.

```bash
cd firmware/sleeve && idf.py set-target esp32s3
```

```bash
idf.py build
```

```bash
idf.py -p /dev/ttyACM0 flash monitor
```

## Code from the earlier project

The earlier project, CoachPocket, had a draft BLE protocol for a wrist pod. It is still in the Git history at commit `3f8c6a3`:

```bash
git show 3f8c6a3:docs/pod-ble-protocol.md
```

It may help as a reference. The format above is new and does not depend on it.

## Good first tasks

- Install ESP-IDF, create the `sleeve` project, flash the hello world example to the XIAO ESP32S3 and commit the project with the exact ESP-IDF version in this README.
- Connect one LSM6DSV16X and print its `WHO_AM_I` register over the serial monitor.
- Read the FIFO and timestamp part of the LSM6DSV16X datasheet and write in an issue how the board can give each sample an exact time.
