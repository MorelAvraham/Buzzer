package il.ac.huji.buzzer.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import il.ac.huji.buzzer.camera.CameraInfoReader

/** Issue #17: shows what we need to know about the cameras of this phone. */
@Composable
fun CameraInfoScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val report = remember { runCatching { CameraInfoReader.read(context) } }
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Camera info", style = MaterialTheme.typography.headlineSmall)
        report.onFailure { Text("Could not read the cameras: ${it.message}") }
        report.onSuccess { r ->
            Text("${r.manufacturer} ${r.model}, Android ${r.androidVersion} (API ${r.sdk})")
            for (c in r.cameras) {
                Text(
                    "Camera ${c.id} (${c.facing})\n" +
                        "Timestamp source: ${c.timestampSource}\n" +
                        "Max fps: ${c.maxFps ?: "?"}\n" +
                        "Max slow-motion fps: ${c.maxHighSpeedFps ?: "none"}\n" +
                        "Hardware level: ${c.hardwareLevel}",
                    fontFamily = FontFamily.Monospace,
                )
            }
            Button(onClick = { copy(context, r.toMarkdown()) }) {
                Text("Copy as a table for issue #17")
            }
        }
    }
}

private fun copy(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(ClipData.newPlainText("Camera info", text))
}
