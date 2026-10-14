package app.tachymeter.tests

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import app.tachymeter.MainActivity
import java.io.File

/** Test-only APK; synthetic measurements never enter the shipping app by an intent or debug mode. */
class SmokeTests : Instrumentation() {
    private var assertions = 0
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        val report = Bundle()
        try {
            val activity = startActivitySync(Intent(Intent.ACTION_MAIN).setClassName("app.tachymeter", "app.tachymeter.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            waitForIdleSync()
            // Isolate injected samples from the emulator's periodic GNSS feed.
            onUi { activity.getSystemService(LocationManager::class.java).removeUpdates(activity) }
            fun texts(): List<TextView> {
                fun collect(view: View): List<TextView> = when (view) {
                    is ViewGroup -> (0 until view.childCount).flatMap { collect(view.getChildAt(it)) }
                    is TextView -> listOf(view)
                    else -> emptyList()
                }
                return collect(activity.window.decorView)
            }
            fun verify(value: Boolean, label: String) { assertions++; check(value) { label } }
            fun has(value: String) = texts().any { it.text.toString() == value }
            fun sample(speed: Float?, accuracy: Float?, ageMillis: Long = 0, mock: Boolean = false) = Location(LocationManager.GPS_PROVIDER).apply {
                latitude = 48.8566; longitude = 2.3522
                time = System.currentTimeMillis()
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos() - ageMillis * 1_000_000L
                setAccuracy(4f)
                if (speed != null) setSpeed(speed)
                if (accuracy != null) speedAccuracyMetersPerSecond = accuracy
                isMock = mock
            }
            onUi {
                activity.onLocationChanged(sample(20f, .2f))
                verify(has("72.0"), "Native speed displayed in km/h")
                verify(has("●  Live GNSS"), "Precise measurement is live")
                verify(has("± 0.7 km/h estimated accuracy"), "Uncertainty conversion")
                texts().filterIsInstance<Button>().first { it.text == "Units: km/h" }.performClick()
                verify(has("44.7"), "Unit button converts speed")
                texts().filterIsInstance<Button>().first { it.text == "Units: mph" }.performClick()
                activity.onLocationChanged(sample(0f, .2f))
                verify(has("0.0"), "Actual zero speed displayed")
                activity.onLocationChanged(sample(null, .2f))
                verify(has("—") && has("●  Waiting for speed"), "Missing speed is not zero")
                activity.onLocationChanged(sample(20f, null))
                verify(has("●  Accuracy unavailable"), "Unavailable accuracy is explicit")
                activity.onLocationChanged(sample(20f, 2f))
                verify(has("●  Low speed confidence"), "Weak measurement flagged")
                activity.onLocationChanged(sample(20f, .2f, mock = true))
                verify(has("●  Simulated location"), "Mock measurement is labelled")
                activity.onLocationChanged(sample(99f, .2f, ageMillis = 5000))
                verify(has("72.0"), "Old measurement cannot replace fresh speed")
                activity.onLocationChanged(sample(Float.NaN, .2f))
                verify(has("●  Waiting for speed"), "NaN is not displayed")
                activity.onLocationChanged(sample(20f, .2f, mock = true))
            }
            waitForIdleSync()
            Thread.sleep(300)
            val output = File(targetContext.getExternalFilesDir(null), "speed-test.png")
            uiAutomation.takeScreenshot().useBitmap { bitmap -> output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
            onUi {
                val icon = targetContext.packageManager.getApplicationIcon("app.tachymeter")
                Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888).useBitmap { bitmap ->
                    icon.setBounds(0, 0, 512, 512)
                    icon.draw(android.graphics.Canvas(bitmap))
                    File(targetContext.getExternalFilesDir(null), "icon.png").outputStream().use {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                }
            }
            Thread.sleep(3300)
            onUi {
                verify(has("●  Signal lost"), "Stale reading becomes signal lost")
                verify(texts().count { it.text.toString() == "72.0" } == 1, "Stale current speed is hidden while average remains")
                activity.onLocationChanged(sample(10f, .2f))
                activity.onProviderDisabled(LocationManager.GPS_PROVIDER)
                verify(!has("36.0"), "Provider loss clears speed immediately")
            }
            report.putString("stream", "PASS: $assertions Android integration assertions\nScreenshot: ${output.absolutePath}\n")
            finish(Activity.RESULT_OK, report)
        } catch (t: Throwable) {
            report.putString("stream", "FAIL after $assertions assertions: ${t.stackTraceToString()}")
            finish(Activity.RESULT_CANCELED, report)
        }
    }
    private fun onUi(block: () -> Unit) {
        var failure: Throwable? = null
        runOnMainSync { try { block() } catch (t: Throwable) { failure = t } }
        failure?.let { throw it }
    }
    private inline fun Bitmap.useBitmap(block: (Bitmap) -> Unit) { try { block(this) } finally { recycle() } }
}
