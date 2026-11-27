package il.ac.huji.buzzer.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import il.ac.huji.buzzer.ble.Link
import il.ac.huji.buzzer.ble.SleeveConnection
import il.ac.huji.buzzer.ble.SleeveState
import java.util.Locale

/** The sample rate is not decided yet (firmware/README.md). These are the options to try. */
val SAMPLE_RATES = listOf(120, 240, 480)

/** Issue #18 and #19: find the sleeve, connect, and show what arrives. */
@Composable
fun SleeveScreen(connection: SleeveConnection, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val state by connection.state.collectAsStateWithLifecycle()
    var granted by remember { mutableStateOf(SleeveConnection.hasPermissions(context)) }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = SleeveConnection.hasPermissions(context)
    }
    var rateHz by rememberSaveable { mutableIntStateOf(240) }

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Sleeve", style = MaterialTheme.typography.headlineSmall)

        if (!granted) {
            Text("The app needs the Bluetooth permission to find the sleeve.")
            Button(onClick = { askPermission.launch(SleeveConnection.requiredPermissions()) }) {
                Text("Allow Bluetooth")
            }
            return@Column
        }
        if (!connection.bluetoothOn) {
            Text("Bluetooth is off. Turn it on in the phone settings, then come back.")
        }

        Text("Status: ${statusText(state)}")
        state.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        when (state.link) {
            Link.CONNECTED, Link.CONNECTING -> ConnectedPanel(connection, state, rateHz) { rateHz = it }
            else -> ScanPanel(connection, state)
        }
    }
}

@Composable
private fun ScanPanel(connection: SleeveConnection, state: SleeveState) {
    if (state.link == Link.SCANNING) {
        OutlinedButton(onClick = { connection.stopScan() }) { Text("Stop scan") }
    } else {
        Button(onClick = { connection.startScan() }) { Text("Scan for the sleeve") }
    }
    if (state.devices.isEmpty() && state.link == Link.SCANNING) {
        Text("Looking for devices with the Buzzer service. On Android 10 and 11, location must be on.")
    }
    for (d in state.devices) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("${d.name ?: "(no name)"}\n${d.address}  ${d.rssi} dBm")
            Button(onClick = { connection.connect(d.address) }) { Text("Connect") }
        }
    }
}

@Composable
private fun ConnectedPanel(
    connection: SleeveConnection,
    state: SleeveState,
    rateHz: Int,
    onRate: (Int) -> Unit,
) {
    Text(
        listOf(
            "Device: ${state.address}",
            "MTU: ${state.mtu ?: "-"}",
            "Board rate: ${state.nominalRateHz?.let { fmt(it) } ?: "-"} Hz",
            "Arriving: ${fmt(state.measuredRateHz)} samples/s",
            "Packets: ${state.received}",
            "Lost: ${state.lost} (${fmt(lostPercent(state))} %)",
            "Out of order: ${state.outOfOrder}",
            "Bad packets: ${state.badPackets}",
            "Last ping round trip: ${state.lastRoundTripMs?.let { fmt(it) } ?: "-"} ms",
        ).joinToString("\n"),
        fontFamily = FontFamily.Monospace,
    )
    Text("Sample rate (not decided yet):")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (r in SAMPLE_RATES) {
            FilterChip(selected = r == rateHz, onClick = { onRate(r) }, label = { Text("$r Hz") })
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.streaming) {
            Button(onClick = { connection.stopStreaming() }) { Text("Stop streaming") }
        } else {
            Button(onClick = { connection.startStreaming(rateHz) }, enabled = state.link == Link.CONNECTED) {
                Text("Start streaming")
            }
        }
        OutlinedButton(onClick = { connection.disconnect() }) { Text("Disconnect") }
    }
}

fun statusText(s: SleeveState): String = when (s.link) {
    Link.IDLE -> "not connected"
    Link.SCANNING -> "scanning"
    Link.CONNECTING -> "connecting"
    Link.CONNECTED -> if (s.streaming) "connected, streaming" else "connected"
    Link.DISCONNECTED -> "disconnected"
}

fun lostPercent(s: SleeveState): Double =
    if (s.received + s.lost == 0L) 0.0 else 100.0 * s.lost / (s.received + s.lost)

private fun fmt(v: Double) = String.format(Locale.ROOT, "%.1f", v)
