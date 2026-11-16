# hardware

The sensor sleeve: one compression sleeve on the shooting arm with three sensor points (upper arm, forearm, back of the hand). Each point has an IMU (ST LSM6DSV16X) and a coin vibration motor. One board on the sleeve (Seeed XIAO ESP32S3 and a battery) reads the three IMUs through thin cables.

Status: no parts bought yet. No circuit drawn yet. KiCad files come later.

## Parts and cost

The full list is in [bom.csv](bom.csv). Prices are from the suppliers' websites in November 2026, without shipping. Lines marked "estimate" are estimates.

| Part | What | Cost |
|---|---|---|
| Sleeve board | Seeed XIAO ESP32S3 | about $8 |
| Sensors | 3x SparkFun LSM6DSV16X board | about $25 each |
| Vibration | 3x coin vibration motor | about $3 each |
| Battery, sleeve, cables | small lithium battery, compression arm sleeve, thin cables | about $30, estimate |
| Tripod | phone tripod | about $15, estimate |
| Own board | KiCad design, online manufacturer | about $20, estimate |

The total is about $157, without shipping. Part of it is estimates.

Not in the list yet: a small transistor and a diode for each motor, and connectors for the cables. Add them when the circuit is drawn.

## Stages

- **Stage 1.** One SparkFun LSM6DSV16X board on the hand, wired to the XIAO ESP32S3. No motors.
- **Stage 2.** The full sleeve with three sensor points and three motors. Our own board is designed in KiCad in parallel. The development board is the backup.

## Points to check

- **Three IMUs on one board.** The LSM6DSV16X has only two I2C addresses. Use two I2C buses or SPI. See [firmware/README.md](../firmware/README.md).
- **Motor current.** A coin motor needs more current than an ESP32 pin can give. Drive each one with a transistor, and put a diode across the motor.
- **Pins.** The XIAO ESP32S3 has a small number of pins. Count the pins for three sensors and three motors before choosing I2C or SPI.
- **Cables.** The cables must survive many shots. U4 tests this.

## Folder plan

```
hardware/
  bom.csv        parts and prices
  kicad/         our own board (stage 2)
  sleeve/        photos and notes on how the sleeve is sewn and where the cables run
```

## Good first tasks

- Find the supplier link for each part and add a `link` column to `bom.csv`.
- Draw the wiring of one LSM6DSV16X board to the XIAO ESP32S3 for stage 1 (a photo of a hand drawing is fine) and add it to this folder.
- Find the current of the coin motor in its datasheet and choose the transistor and the diode. Add them to `bom.csv`.
