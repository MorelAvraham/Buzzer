package il.ac.huji.buzzer.record

import il.ac.huji.buzzer.ble.ScaledSample
import il.ac.huji.buzzer.ble.SensorPacket
import il.ac.huji.buzzer.ble.SensorPoint
import il.ac.huji.buzzer.sync.BoardClockUnwrapper
import il.ac.huji.buzzer.sync.ClockMapping
import il.ac.huji.buzzer.sync.ClockSync
import il.ac.huji.buzzer.sync.Ping
import java.io.Writer
import java.util.Locale

/**
 * Collects the samples of one sensor point during a recording, on the board clock.
 *
 * The samples are turned into phone time only at the end, in [writeCsv], with a clock mapping
 * fitted on all the pings of the session. This is more exact than converting each sample as it
 * arrives, and fits the stage 1 plan, where the analysis runs after the session.
 */
class SensorRecording(val point: SensorPoint) {
    private val unwrapper = BoardClockUnwrapper()
    private val times = ArrayList<Long>()
    private val values = ArrayList<ScaledSample>()
    private val pings = ArrayList<Ping>()

    val sampleCount: Int @Synchronized get() = times.size
    val pingCount: Int @Synchronized get() = pings.size

    /** Packets from other sensor points are ignored. Called from the BLE thread. */
    @Synchronized
    fun addPacket(packet: SensorPacket) {
        if (packet.point != point) return
        val t0 = unwrapper.unwrap(packet.t0Us)
        for (i in packet.samples.indices) {
            times.add(t0 + i.toLong() * packet.dtUs)
            values.add(packet.scaled(i))
        }
    }

    /** [boardUsRaw] is the 32-bit board time from the ping reply. */
    @Synchronized
    fun addPing(phoneSendMs: Double, phoneReceiveMs: Double, boardUsRaw: Long) {
        pings.add(Ping(phoneSendMs, phoneReceiveMs, unwrapper.unwrap(boardUsRaw)))
    }

    @Synchronized
    fun clockMapping(): ClockMapping? = ClockSync.fit(pings)

    /**
     * Writes the samples as CSV, columns as in data/README.md, sorted by time.
     * Returns the mapping used, or null and writes nothing if there was no ping.
     */
    @Synchronized
    fun writeCsv(out: Writer): ClockMapping? {
        val mapping = clockMapping() ?: return null
        out.write(HEADER)
        out.write("\n")
        for (i in times.indices.sortedBy { times[it] }) {
            val s = values[i]
            val t = mapping.toPhoneMs(times[i])
            // Locale.ROOT so the decimal point is always "." whatever the phone language is.
            out.write(
                String.format(
                    Locale.ROOT,
                    "%.3f,%.4f,%.4f,%.4f,%.3f,%.3f,%.3f,%d\n",
                    t, s.ax, s.ay, s.az, s.gx, s.gy, s.gz, times[i],
                ),
            )
        }
        out.flush()
        return mapping
    }

    companion object {
        const val HEADER = "t_ms,ax,ay,az,gx,gy,gz,board_us"
    }
}
