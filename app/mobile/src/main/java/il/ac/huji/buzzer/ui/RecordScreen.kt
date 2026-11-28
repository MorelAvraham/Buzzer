package il.ac.huji.buzzer.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import il.ac.huji.buzzer.ble.Link
import il.ac.huji.buzzer.ble.SleeveConnection
import il.ac.huji.buzzer.camera.VideoRecorder
import il.ac.huji.buzzer.record.SessionController
import il.ac.huji.buzzer.record.SessionForm
import il.ac.huji.buzzer.record.SessionName
import il.ac.huji.buzzer.record.SessionSummary
import java.io.File

/** Issue #22: record one session (video and hand sensor) and export the folder. */
@Composable
fun RecordScreen(sleeve: SleeveConnection, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val sleeveState by sleeve.state.collectAsStateWithLifecycle()
    var cameraGranted by remember { mutableStateOf(hasCameraPermission(context)) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { cameraGranted = it }

    var playerText by rememberSaveable { mutableStateOf("") }
    var shotType by rememberSaveable { mutableStateOf("free throw") }
    var plannedText by rememberSaveable { mutableStateOf("") }
    var side by rememberSaveable { mutableStateOf("right") }
    var distanceText by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var fps by rememberSaveable { mutableIntStateOf(30) }
    var rateHz by rememberSaveable { mutableIntStateOf(240) }

    val video = remember { VideoRecorder(context) }
    val controller = remember { SessionController(context, sleeve, video) }
    val previewView = remember { PreviewView(context) }
    var running by remember { mutableStateOf(false) }
    var summary by remember { mutableStateOf<SessionSummary?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) { onDispose { if (controller.isRunning) controller.stop() } }

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Record a session", style = MaterialTheme.typography.headlineSmall)
        if (!cameraGranted) {
            Text("The app needs the camera to film the shots.")
            Button(onClick = { ask.launch(Manifest.permission.CAMERA) }) { Text("Allow camera") }
            return@Column
        }

        LaunchedEffect(fps) {
            cameraError = null
            video.bind(owner, previewView, fps) { cameraError = it }
        }
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f))
        cameraError?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        val sleeveText = if (sleeveState.link == Link.CONNECTED) {
            "Sleeve: connected (${sleeveState.address})"
        } else {
            "Sleeve: not connected. Connect it on the Sleeve screen, or record video only."
        }
        Text(sleeveText)

        val player = SessionName.normalisePlayer(playerText)
        OutlinedTextField(
            value = playerText,
            onValueChange = { playerText = it },
            label = { Text("Player code, for example P01. Never a name.") },
            isError = playerText.isNotEmpty() && player == null,
            singleLine = true,
            enabled = !running,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = shotType,
            onValueChange = { shotType = it },
            label = { Text("Shot type") },
            singleLine = true,
            enabled = !running,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = plannedText,
            onValueChange = { plannedText = it.filter(Char::isDigit) },
            label = { Text("Planned shots (optional)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            enabled = !running,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = distanceText,
            onValueChange = { distanceText = it },
            label = { Text("Camera distance from the shooter in m (optional)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            enabled = !running,
            modifier = Modifier.fillMaxWidth(),
        )
        Text("Camera on the shooter's:")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (s in listOf("left", "right")) {
                FilterChip(selected = side == s, onClick = { if (!running) side = s }, label = { Text(s) })
            }
        }
        Text("Video frame rate:")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (f in listOf(30, 60)) {
                FilterChip(selected = fps == f, onClick = { if (!running) fps = f }, label = { Text("$f fps") })
            }
        }
        Text("Sensor sample rate (not decided yet):")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (r in SAMPLE_RATES) {
                FilterChip(selected = rateHz == r, onClick = { if (!running) rateHz = r }, label = { Text("$r Hz") })
            }
        }
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Notes (optional)") },
            enabled = !running,
            modifier = Modifier.fillMaxWidth(),
        )
        Text("Before the first shot: tap the hand sensor against the ball in front of the camera (#23).")

        if (running) {
            Button(onClick = { controller.stop() }) { Text("Stop") }
        } else {
            Button(
                enabled = player != null,
                onClick = {
                    val form = SessionForm(
                        player = player!!,
                        shotType = shotType.trim().ifEmpty { "free throw" },
                        plannedShots = plannedText.toIntOrNull(),
                        cameraSide = side,
                        cameraDistanceM = distanceText.replace(',', '.').toDoubleOrNull(),
                        notes = notes.trim(),
                        frameRate = fps,
                        sensorRateHz = rateHz,
                    )
                    summary = null
                    running = true
                    controller.start(form) {
                        running = false
                        summary = it
                    }
                },
            ) { Text("Start") }
        }

        summary?.let { s -> SummaryPanel(context, s) }
    }
}

@Composable
private fun SummaryPanel(context: Context, s: SessionSummary) {
    Text(
        listOf(
            "Folder: ${s.folder.name}",
            "Video frames: ${s.video?.videoFrames ?: 0} (matched ${s.video?.matchedFrames ?: 0})",
            "Sensor samples: ${s.sensorSamples}",
            "Clock pings: ${s.clockPings}",
            "Files:",
        ).joinToString("\n") + s.files.joinToString("") { "\n  ${it.name}" },
        fontFamily = FontFamily.Monospace,
    )
    for (w in s.warnings) Text(w, color = MaterialTheme.colorScheme.error)
    OutlinedButton(onClick = { share(context, s.files) }) { Text("Share the session files") }
}

private fun hasCameraPermission(context: Context) =
    context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

/** Opens the Android share sheet with all files of the session, for example to Google Drive. */
private fun share(context: Context, files: List<File>) {
    val authority = "${context.packageName}.files"
    val uris = ArrayList<Uri>(files.map { FileProvider.getUriForFile(context, authority, it) })
    val intent = Intent(Intent.ACTION_SEND_MULTIPLE)
        .setType("*/*")
        .putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(intent, "Share session"))
}
