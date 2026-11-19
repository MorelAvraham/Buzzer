package il.ac.huji.buzzer

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Starts MainActivity with Robolectric on the laptop and checks the home screen.
// This does not replace opening the app on a real phone.
@RunWith(RobolectricTestRunner::class)
class MainActivityTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeScreenShowsAppName() {
        composeRule.onNodeWithText("Buzzer").assertExists()
    }
}
