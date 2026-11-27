package il.ac.huji.buzzer.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class FoundDevice(val address: String, val name: String?, val rssi: Int)

enum class Link { IDLE, SCANNING, CONNECTING, CONNECTED, DISCONNECTED }

data class SleeveState(
    val link: Link = Link.IDLE,
    val devices: List<FoundDevice> = emptyList(),
    val address: String? = null,
    val mtu: Int? = null,
    val streaming: Boolean = false,
    val received: Long = 0,
    val lost: Long = 0,
    val outOfOrder: Long = 0,
    val badPackets: Long = 0,
    val nominalRateHz: Double? = null,
    val measuredRateHz: Double = 0.0,
    val lastRoundTripMs: Double? = null,
    val message: String? = null,
)

/** Gets every decoded packet and every ping, for example to record them. */
interface SleeveListener {
    fun onPacket(packet: SensorPacket, phoneMs: Double)
    fun onPing(phoneSendMs: Double, phoneReceiveMs: Double, boardUsRaw: Long)
}

/** Phone clock in milliseconds. The same clock is used for the video frames. */
fun phoneNowMs(): Double = SystemClock.elapsedRealtimeNanos() / 1_000_000.0

/**
 * The BLE link to the sleeve: scan, connect, turn on notifications, read packets, send commands
 * and time pings. Every call checks the Bluetooth permissions first, so the MissingPermission
 * lint warning is turned off for this class.
 */
@SuppressLint("MissingPermission")
class SleeveConnection(context: Context) {
    private val app = context.applicationContext
    private val adapter = app.getSystemService(BluetoothManager::class.java)?.adapter
    private val main = Handler(Looper.getMainLooper())
    private val lock = Any()
    private val queue = GattOpQueue()
    private val stats = PacketStats()
    private val rate = RateMeter()
    private val pendingPings = HashMap<Long, Double>()
    private var badPackets = 0L
    private var gatt: BluetoothGatt? = null

    private val _state = MutableStateFlow(SleeveState())
    val state: StateFlow<SleeveState> = _state.asStateFlow()

    @Volatile
    var listener: SleeveListener? = null

    val bluetoothOn: Boolean get() = adapter?.isEnabled == true

    // ---- Scan ----

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val found = FoundDevice(result.device.address, result.scanRecord?.deviceName, result.rssi)
            _state.update { s ->
                s.copy(devices = (s.devices.filter { it.address != found.address } + found).sortedByDescending { it.rssi })
            }
        }

        override fun onScanFailed(errorCode: Int) {
            _state.update { it.copy(link = Link.IDLE, message = "Scan failed, error $errorCode") }
        }
    }

    fun startScan(durationMs: Long = 10_000) {
        if (!ready()) return
        val scanner = adapter?.bluetoothLeScanner ?: return
        _state.update { it.copy(link = Link.SCANNING, devices = emptyList(), message = null) }
        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(BuzzerGatt.SERVICE)).build()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        scanner.startScan(listOf(filter), settings, scanCallback)
        main.postDelayed({ stopScan() }, SCAN_TOKEN, durationMs)
    }

    fun stopScan() {
        main.removeCallbacksAndMessages(SCAN_TOKEN)
        if (hasPermissions(app)) adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        _state.update { if (it.link == Link.SCANNING) it.copy(link = Link.IDLE) else it }
    }

    // ---- Connect ----

    fun connect(address: String) {
        if (!ready()) return
        stopScan()
        disconnect()
        val device: BluetoothDevice = adapter?.getRemoteDevice(address) ?: return
        synchronized(lock) {
            stats.reset()
            rate.reset()
            pendingPings.clear()
            badPackets = 0
        }
        _state.update {
            it.copy(link = Link.CONNECTING, address = address, mtu = null, streaming = false, message = null)
        }
        gatt = device.connectGatt(app, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    fun disconnect() {
        main.removeCallbacks(pingTask)
        synchronized(lock) { queue.clear() }
        gatt?.let {
            if (hasPermissions(app)) {
                it.disconnect()
                it.close()
            }
        }
        gatt = null
        _state.update { if (it.link == Link.IDLE) it else it.copy(link = Link.DISCONNECTED, streaming = false) }
    }

    // ---- Commands ----

    fun startStreaming(rateHz: Int) {
        write("start streaming", BuzzerGatt.startStreaming(rateHz))
        _state.update { it.copy(streaming = true) }
    }

    fun stopStreaming() {
        write("stop streaming", BuzzerGatt.stopStreaming())
        _state.update { it.copy(streaming = false) }
    }

    fun vibrate(point: SensorPoint, durationMs: Int, strength: Int) =
        write("vibrate", BuzzerGatt.vibrate(point, durationMs, strength))

    private fun sendPing() {
        enqueue("time ping") {
            writeNow(TimePing.encodeRequest(rememberPing(phoneNowMs())))
        }
    }

    /** Stores the send time of a ping and returns the phone time to put in the request. */
    internal fun rememberPing(now: Double): Long {
        val ms = now.toLong()
        synchronized(lock) {
            // Forget pings that got no reply, so the map does not grow if the board never answers.
            pendingPings.values.removeAll { it < now - 10_000 }
            pendingPings[ms and 0xFFFF_FFFFL] = now
        }
        return ms
    }

    private val pingTask = object : Runnable {
        override fun run() {
            if (_state.value.link != Link.CONNECTED) return
            sendPing()
            main.postDelayed(this, PING_EVERY_MS)
        }
    }

    private fun write(name: String, bytes: ByteArray) = enqueue(name) { writeNow(bytes) }

    private fun writeNow(bytes: ByteArray): Boolean {
        val g = gatt ?: return false
        val c = g.getService(BuzzerGatt.SERVICE)?.getCharacteristic(BuzzerGatt.CONTROL) ?: return false
        val type = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeCharacteristic(c, bytes, type) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            c.value = bytes
            c.writeType = type
            @Suppress("DEPRECATION")
            g.writeCharacteristic(c)
        }
    }

    private fun enableNotifications(uuid: java.util.UUID): Boolean {
        val g = gatt ?: return false
        val c = g.getService(BuzzerGatt.SERVICE)?.getCharacteristic(uuid) ?: return false
        if (!g.setCharacteristicNotification(c, true)) return false
        val d = c.getDescriptor(BuzzerGatt.CLIENT_CONFIG) ?: return false
        val on = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeDescriptor(d, on) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            d.value = on
            @Suppress("DEPRECATION")
            g.writeDescriptor(d)
        }
    }

    private fun enqueue(name: String, start: () -> Boolean) {
        if (!hasPermissions(app)) return
        synchronized(lock) { queue.enqueue(name, start) }
    }

    private fun opDone() = synchronized(lock) { queue.done() }

    // ---- GATT callbacks, on a Binder thread ----

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                enqueue("request MTU") { g.requestMtu(BuzzerGatt.MTU) }
                enqueue("discover services") { g.discoverServices() }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                main.removeCallbacks(pingTask)
                synchronized(lock) { queue.clear() }
                g.close()
                if (gatt == g) gatt = null
                _state.update {
                    it.copy(link = Link.DISCONNECTED, streaming = false, message = "Disconnected (status $status)")
                }
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            _state.update { it.copy(mtu = mtu) }
            opDone()
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val service = g.getService(BuzzerGatt.SERVICE)
            if (status != BluetoothGatt.GATT_SUCCESS || service == null) {
                _state.update { it.copy(message = "Buzzer service not found on this device") }
                opDone()
                return
            }
            enqueue("notify sensor data") { enableNotifications(BuzzerGatt.SENSOR_DATA) }
            enqueue("notify status") { enableNotifications(BuzzerGatt.STATUS) }
            enqueue("connection priority") {
                g.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                // No callback for this one, so it ends here.
                main.post { opDone() }
                true
            }
            enqueue("connected") {
                _state.update { it.copy(link = Link.CONNECTED) }
                main.post(pingTask)
                main.post { opDone() }
                true
            }
            opDone()
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) = opDone()

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) = opDone()

        // Android 13 and newer.
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) =
            handleNotification(c.uuid, value)

        // Android 12 and older.
        @Deprecated("Replaced in Android 13 by the version with the value")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) handleNotification(c.uuid, c.value ?: return)
        }
    }

    private fun handleNotification(uuid: java.util.UUID, value: ByteArray) =
        onNotification(uuid, value, phoneNowMs())

    /** Handles one notification that arrived at phone time [now]. Internal so tests can call it. */
    internal fun onNotification(uuid: java.util.UUID, value: ByteArray, now: Double) {
        when (uuid) {
            BuzzerGatt.SENSOR_DATA -> handlePacket(value, now)
            BuzzerGatt.STATUS -> handleStatus(value, now)
        }
    }

    private fun handlePacket(value: ByteArray, now: Double) {
        val packet = try {
            SensorPacketDecoder.decode(value)
        } catch (e: PacketFormatException) {
            synchronized(lock) { badPackets++ }
            _state.update { it.copy(badPackets = it.badPackets + 1, message = "Bad packet: ${e.message}") }
            return
        }
        listener?.onPacket(packet, now)
        val snapshot = synchronized(lock) {
            stats.add(packet)
            rate.add(now, packet.samples.size)
            arrayOf(stats.received, stats.lost, stats.outOfOrder)
        }
        _state.update {
            it.copy(
                received = snapshot[0],
                lost = snapshot[1],
                outOfOrder = snapshot[2],
                nominalRateHz = packet.nominalRateHz,
                measuredRateHz = synchronized(lock) { rate.rateHz(now) },
            )
        }
    }

    private fun handleStatus(value: ByteArray, now: Double) {
        val reply = TimePing.decodeReply(value) ?: return
        val sent = synchronized(lock) { pendingPings.remove(reply.phoneMsEcho) } ?: return
        listener?.onPing(sent, now, reply.boardUs)
        _state.update { it.copy(lastRoundTripMs = now - sent) }
    }

    private fun ready(): Boolean {
        if (!hasPermissions(app)) {
            _state.update { it.copy(message = "Bluetooth permission is missing") }
            return false
        }
        if (!bluetoothOn) {
            _state.update { it.copy(message = "Bluetooth is off") }
            return false
        }
        return true
    }

    companion object {
        const val PING_EVERY_MS = 1_000L
        private val SCAN_TOKEN = Any()

        /** Runtime permissions the app needs for BLE on this Android version. */
        fun requiredPermissions(): Array<String> =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
            }

        fun hasPermissions(context: Context): Boolean = requiredPermissions().all {
            context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
        }
    }
}
