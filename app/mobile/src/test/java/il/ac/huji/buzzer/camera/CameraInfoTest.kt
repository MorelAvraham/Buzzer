package il.ac.huji.buzzer.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.util.Range
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowCameraCharacteristics

@RunWith(RobolectricTestRunner::class)
class CameraInfoTest {

    private fun characteristics(
        facing: Int,
        source: Int,
        fps: Array<Range<Int>>,
    ): CameraCharacteristics {
        val c = ShadowCameraCharacteristics.newCameraCharacteristics()
        val shadow = shadowOf(c)
        shadow.set(CameraCharacteristics.LENS_FACING, facing)
        shadow.set(CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE, source)
        shadow.set(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES, fps)
        shadow.set(
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL,
            CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_FULL,
        )
        return c
    }

    @Test
    fun readsTimestampSourceAndMaxFps() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = context.getSystemService(CameraManager::class.java)
        shadowOf(manager).addCamera(
            "0",
            characteristics(
                CameraMetadata.LENS_FACING_BACK,
                CameraMetadata.SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME,
                arrayOf(Range(15, 30), Range(30, 60)),
            ),
        )
        shadowOf(manager).addCamera(
            "1",
            characteristics(
                CameraMetadata.LENS_FACING_FRONT,
                CameraMetadata.SENSOR_INFO_TIMESTAMP_SOURCE_UNKNOWN,
                arrayOf(Range(15, 30)),
            ),
        )

        val report = CameraInfoReader.read(context)
        assertEquals(2, report.cameras.size)
        val back = report.backCameras.single()
        assertEquals("0", back.id)
        assertEquals("REALTIME", back.timestampSource)
        assertEquals(60, back.maxFps)
        assertNull(back.maxHighSpeedFps)
        assertEquals("FULL", back.hardwareLevel)
        assertEquals("UNKNOWN", report.cameras.first { it.id == "1" }.timestampSource)

        val table = report.toMarkdown().trim().lines()
        assertEquals(3, table.size) // header, separator, one back camera
        assertTrue(table[2].contains("| REALTIME | 60 | none | FULL |"))
    }
}
