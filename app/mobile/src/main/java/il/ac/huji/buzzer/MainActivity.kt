package il.ac.huji.buzzer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import il.ac.huji.buzzer.ui.CameraInfoScreen

/** The screens of the app. Kept simple on purpose: one enum, no navigation library. */
enum class Screen(val title: String) {
    HOME("Home"),
    CAMERA_INFO("Camera info"),
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                BuzzerApp()
            }
        }
    }
}

@Composable
fun BuzzerApp() {
    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    BackHandler(enabled = screen != Screen.HOME) { screen = Screen.HOME }
    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        val inner = Modifier.padding(padding)
        when (screen) {
            Screen.HOME -> HomeScreen(onOpen = { screen = it }, modifier = inner)
            Screen.CAMERA_INFO -> CameraInfoScreen(modifier = inner)
        }
    }
}

@Composable
fun HomeScreen(onOpen: (Screen) -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
            )
            for (s in Screen.entries.filter { it != Screen.HOME }) {
                Button(onClick = { onOpen(s) }, modifier = Modifier.fillMaxWidth()) {
                    Text(s.title)
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    MaterialTheme {
        HomeScreen(onOpen = {})
    }
}
