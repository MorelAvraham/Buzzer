package il.ac.huji.buzzer.ble

/**
 * One sensor data packet from the sleeve, as described in firmware/README.md (draft, version 1).
 *
 * All times are on the board clock. The phone clock conversion happens later.
 */
data class SensorPacket(
    val version: Int,
    val point: SensorPoint,
    /** Packet counter, 0 to 65535. */
    val seq: Int,
    /** Board time of the first sample in microseconds, 0 to 2^32 - 1. */
    val t0Us: Long,
    /** Time between samples in microseconds. */
    val dtUs: Int,
    val accelRange: AccelRange,
    val gyroRange: GyroRange,
    val samples: List<RawSample>,
) {
    /** Board time of sample [i] in microseconds. Can go past 2^32 when the board clock wraps. */
    fun sampleTimeUs(i: Int): Long = t0Us + i.toLong() * dtUs

    /** Sample rate given by the board, in samples per second. */
    val nominalRateHz: Double get() = 1_000_000.0 / dtUs

    /** Sample [i] in physical units: m/s² and degrees per second. */
    fun scaled(i: Int): ScaledSample {
        val s = samples[i]
        val a = accelRange.metresPerSecondSquaredPerLsb
        val g = gyroRange.degreesPerSecondPerLsb
        return ScaledSample(s.ax * a, s.ay * a, s.az * a, s.gx * g, s.gy * g, s.gz * g)
    }
}

/** Raw sensor values, as sent by the board. */
data class RawSample(
    val ax: Short,
    val ay: Short,
    val az: Short,
    val gx: Short,
    val gy: Short,
    val gz: Short,
)

/** Acceleration in m/s², turning speed in degrees per second. */
data class ScaledSample(
    val ax: Double,
    val ay: Double,
    val az: Double,
    val gx: Double,
    val gy: Double,
    val gz: Double,
)

enum class SensorPoint(val code: Int) {
    HAND(0),
    FOREARM(1),
    UPPER_ARM(2);

    companion object {
        fun fromCode(code: Int): SensorPoint? = entries.firstOrNull { it.code == code }
    }
}

private const val STANDARD_GRAVITY = 9.80665

/**
 * Accelerometer full scale, bits 0 to 1 of the flags byte.
 * Sensitivity in mg per LSB from the LSM6DSV16X datasheet. Check against the datasheet
 * before trusting absolute values.
 */
enum class AccelRange(val code: Int, val fullScaleG: Int, mgPerLsb: Double) {
    G2(0, 2, 0.061),
    G4(1, 4, 0.122),
    G8(2, 8, 0.244),
    G16(3, 16, 0.488);

    val metresPerSecondSquaredPerLsb: Double = mgPerLsb / 1000.0 * STANDARD_GRAVITY

    companion object {
        fun fromCode(code: Int): AccelRange? = entries.firstOrNull { it.code == code }
    }
}

/**
 * Gyroscope full scale, bits 2 to 4 of the flags byte.
 * Sensitivity in mdps per LSB from the LSM6DSV16X datasheet. Check against the datasheet
 * before trusting absolute values.
 */
enum class GyroRange(val code: Int, val fullScaleDps: Int, mdpsPerLsb: Double) {
    DPS125(0, 125, 4.375),
    DPS250(1, 250, 8.75),
    DPS500(2, 500, 17.5),
    DPS1000(3, 1000, 35.0),
    DPS2000(4, 2000, 70.0),
    DPS4000(5, 4000, 140.0);

    val degreesPerSecondPerLsb: Double = mdpsPerLsb / 1000.0

    companion object {
        fun fromCode(code: Int): GyroRange? = entries.firstOrNull { it.code == code }
    }
}
