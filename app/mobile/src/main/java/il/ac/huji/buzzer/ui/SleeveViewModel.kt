package il.ac.huji.buzzer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import il.ac.huji.buzzer.ble.SleeveConnection

/** Keeps one BLE link for the whole app, so it survives screen changes and rotation. */
class SleeveViewModel(app: Application) : AndroidViewModel(app) {
    val connection = SleeveConnection(app)

    override fun onCleared() {
        connection.disconnect()
    }
}
