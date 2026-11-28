package il.ac.huji.buzzer.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import il.ac.huji.buzzer.camera.VideoRecorder
import il.ac.huji.buzzer.camera.VideoResult
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Issue #20: record a video and write the phone clock time of every frame. */
@Composable
fun VideoTestScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var granted by remember { mutableStateOf(hasCamera(context)) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    var fps by remember { mutableIntStateOf(30) }
    var recording by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<VideoResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val recorder = remember { VideoRecorder(context) }
    val previewView = remember { PreviewView(context) }

    DisposableEffect(Unit) { onDispose { recorder.stop() } }

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Video test", style = MaterialTheme.typography.headlineSmall)
        if (!granted) {
            Text("The app needs the camera to film the shots.")
            Button(onClick = { ask.launch(Manifest.permission.CAMERA) }) { Text("Allow camera") }
            return@Column
        }

        LaunchedEffect(fps) {
            error = null
            recorder.bind(owner, previewView, fps) { error = it }
        }
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f))

        Text("Frame rate:")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (f in listOf(30, 60)) {
                FilterChip(
                    selected = f == fps,
                    onClick = { if (!recording) fps = f },
                    label = { Text("$f fps") },
                )
            }
        }

        if (recording) {
            Button(onClick = { recorder.stop() }) { Text("Stop") }
        } else {
            Button(onClick = {
                val dir = newTestFolder(context)
                recording = true
                result = null
                recorder.start(File(dir, "video.mp4"), File(dir, "frametimes.csv")) {
                    recording = false
                    result = it
                }
            }) { Text("Record") }
        }

        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        result?.let { r ->
            Text(
                listOf(
                    "Video frames: ${r.videoFrames}",
                    "Matched to a camera report: ${r.matchedFrames}",
                    "Camera reports: ${r.reportedFrames}",
                    "Timestamp source: ${r.timestampSource}",
                    "Folder: ${r.video.parentFile?.absolutePath}",
                ).joinToString("\n"),
                fontFamily = FontFamily.Monospace,
            )
            r.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

private fun hasCamera(context: Context) =
    context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

/** A new folder for a test recording, inside the app's own storage. */
private fun newTestFolder(context: Context): File {
    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(Date())
    val base = context.getExternalFilesDir("sessions") ?: context.filesDir
    return File(base, "videotest_$stamp").also { it.mkdirs() }
}
