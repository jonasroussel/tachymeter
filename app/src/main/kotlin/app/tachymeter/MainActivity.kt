package app.tachymeter

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.LocationRequest
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : Activity(), LocationListener {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var manager: LocationManager
    private lateinit var prefs: SharedPreferences
    private var reading: SpeedReading? = null
    private val average = SpeedAverage()
    private var fix: Location? = null
    private var active = false
    private var subscribed = false
    private var gnssSubscribed = false
    private var mph = false
    private var keepAwake = true
    private var used = 0
    private var visible = 0
    private var ink = 0
    private var muted = 0
    private var background = 0
    private var surface = 0
    private var accent = 0
    private var pale = 0
    private var warning = 0
    private lateinit var speed: TextView
    private lateinit var unit: TextView
    private lateinit var status: TextView
    private lateinit var uncertainty: TextView
    private lateinit var hint: TextView
    private lateinit var satelliteValue: TextView
    private lateinit var positionValue: TextView
    private lateinit var ageValue: TextView
    private lateinit var averageValue: TextView
    private lateinit var action: Button
    private lateinit var unitsButton: Button
    private lateinit var awakeButton: Button
    private lateinit var signalDetails: LinearLayout

    private val ticker = object : Runnable {
        override fun run() {
            if (!active) return
            render()
            handler.postDelayed(this, 250)
        }
    }
    private val gnss = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            visible = status.satelliteCount
            used = (0 until visible).count(status::usedInFix)
            render()
        }
        override fun onStopped() { used = 0; visible = 0; render() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        manager = getSystemService(LocationManager::class.java)
        prefs = getSharedPreferences("settings", MODE_PRIVATE)
        mph = prefs.getBoolean("mph", false)
        keepAwake = prefs.getBoolean("awake", true)
        buildUi()
        applyAwake()
    }

    override fun onResume() {
        super.onResume()
        active = true
        startGps()
        handler.post(ticker)
    }

    override fun onPause() {
        active = false
        handler.removeCallbacks(ticker)
        stopGps()
        reading = null
        fix = null
        used = 0
        visible = 0
        super.onPause()
    }

    private fun precisePermission() = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    private fun gpsEnabled() = manager.isProviderEnabled(LocationManager.GPS_PROVIDER)

    private fun startGps() {
        if (!active || subscribed || !precisePermission()) return
        if (LocationManager.GPS_PROVIDER !in manager.allProviders) return
        try {
            // Platform GNSS only. No Google Play services or network location provider.
            if (Build.VERSION.SDK_INT >= 31) {
                val request = LocationRequest.Builder(500)
                    .setMinUpdateIntervalMillis(200)
                    .setMinUpdateDistanceMeters(0f)
                    .setQuality(LocationRequest.QUALITY_HIGH_ACCURACY)
                    .setMaxUpdateDelayMillis(0).build()
                manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, request, mainExecutor, this)
            } else {
                manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500, 0f, this, Looper.getMainLooper())
            }
            subscribed = true
            gnssSubscribed = manager.registerGnssStatusCallback(gnss, handler)
        } catch (_: SecurityException) {
            stopGps()
        } catch (_: IllegalArgumentException) {
            stopGps()
        }
    }

    private fun stopGps() {
        if (subscribed) manager.removeUpdates(this)
        if (gnssSubscribed) manager.unregisterGnssStatusCallback(gnss)
        subscribed = false
        gnssSubscribed = false
    }

    override fun onLocationChanged(location: Location) {
        if (!active || location.provider != LocationManager.GPS_PROVIDER) return
        val timestamp = location.elapsedRealtimeNanos
        val now = SystemClock.elapsedRealtimeNanos()
        if (timestamp <= 0 || timestamp > now || now - timestamp >= SpeedReading.STALE_NANOS) return
        if (timestamp <= (reading?.timestampNanos ?: 0)) return
        fix = Location(location)
        reading = SpeedReading(
            metersPerSecond = if (location.hasSpeed()) location.speed else Float.NaN,
            uncertainty = if (location.hasSpeedAccuracy()) location.speedAccuracyMetersPerSecond else Float.NaN,
            timestampNanos = timestamp,
            mock = if (Build.VERSION.SDK_INT >= 31) location.isMock else location.isFromMockProvider,
        )
        average.add(reading!!)
        render()
    }

    override fun onProviderDisabled(provider: String) {
        reading = null; fix = null; used = 0; visible = 0; render()
    }
    override fun onProviderEnabled(provider: String) = render()
    @Deprecated("Required for older Android versions")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        startGps()
        render()
    }

    private fun locationAction() {
        when {
            !precisePermission() -> {
                if (prefs.getBoolean("asked", false) && !shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                } else {
                    prefs.edit().putBoolean("asked", true).apply()
                    requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 1)
                }
            }
            !gpsEnabled() -> startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            else -> { stopGps(); startGps(); render() }
        }
    }

    private fun render() {
        if (!::speed.isInitialized) return
        val now = SystemClock.elapsedRealtimeNanos()
        val permission = precisePermission()
        val enabled = gpsEnabled()
        val current = reading
        val fresh = current?.isFresh(now) == true
        val usable = permission && enabled && current?.isUsable(now) == true
        var color = muted
        var actionLabel: String? = null
        val (label, message) = when {
            LocationManager.GPS_PROVIDER !in manager.allProviders ->
                "GNSS unavailable" to "This device has no satellite location provider."
            !permission -> {
                actionLabel = if (prefs.getBoolean("asked", false)) "Enable precise location" else "Allow location"
                "Location access needed" to "Allow precise location while using the app to measure your speed."
            }
            !enabled -> {
                actionLabel = "Open location settings"
                "Location is off" to "Turn on device location to connect to satellites."
            }
            !subscribed -> {
                actionLabel = "Retry"
                "Unable to start GNSS" to "Check location access and try again."
            }
            !fresh -> if (current == null)
                "Finding satellites" to "Move outdoors with a clear view of the sky."
                else "Signal lost" to "Waiting for a fresh measurement. Try a clearer view of the sky."
            !usable -> "Waiting for speed" to "Location received. The receiver has not reported speed yet."
            current!!.mock -> {
                color = warning
                "Simulated location" to "Test data from Android’s mock location provider."
            }
            current.isWeak -> {
                color = warning
                if (current.hasUncertainty) "Low speed confidence" to "The receiver reports increased uncertainty."
                else "Accuracy unavailable" to "Your receiver provides speed without an uncertainty estimate."
            }
            else -> {
                color = accent
                "Live GNSS" to "Direct satellite speed · no smoothing"
            }
        }
        action.visibility = if (actionLabel == null) View.GONE else View.VISIBLE
        actionLabel?.let { action.display(it) }
        signalDetails.visibility = if (actionLabel != null && resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
            View.GONE else View.VISIBLE
        status.display("●  $label")
        status.setTextColor(color)
        speed.display(if (usable) format(current!!.speed(mph)) else "—")
        speed.setTextColor(if (usable) ink else muted)
        val unitLabel = if (mph) "mph" else "km/h"
        unit.display(unitLabel)
        speed.contentDescription = if (usable) "${speed.text} ${if (mph) "miles" else "kilometres"} per hour" else "Speed unavailable"
        uncertainty.display(if (usable && current!!.hasUncertainty)
            "± ${format(current.accuracy(mph))} $unitLabel estimated accuracy" else "Speed accuracy —")
        hint.display(message)
        satelliteValue.display(if (permission && enabled && visible > 0) "$used / $visible" else "—")
        val position = fix
        positionValue.display(if (permission && enabled && fresh && position != null && position.hasAccuracy() && position.accuracy.isFinite())
            String.format(Locale.getDefault(), "± %.0f m", position.accuracy) else "—")
        ageValue.display(if (permission && enabled && current != null) {
            val age = (now - current.timestampNanos).coerceAtLeast(0) / 1e9
            if (age > 60) "> 60 s" else "${format(age)} s"
        } else "—")
        averageValue.display(if (average.hasValue) format(average.speed(mph)) else "—")
        averageValue.setTextColor(if (average.hasValue) ink else muted)
        unitsButton.display("Units: $unitLabel")
        awakeButton.display("Keep awake: ${if (keepAwake) "on" else "off"}")
    }

    private fun applyAwake() {
        if (keepAwake) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun buildUi() {
        val dark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        fun theme(light: String, night: String) = Color.parseColor(if (dark) night else light)
        background = theme("#F5F6F1", "#101B1A")
        surface = theme("#FFFFFF", "#1C2A28")
        ink = theme("#162F2B", "#F2F6F1")
        muted = theme("#62746E", "#ABBDB7")
        accent = theme("#087F73", "#75D9C4")
        pale = theme("#E5F0E9", "#233D37")
        warning = theme("#986000", "#F3C278")
        window.statusBarColor = background
        window.navigationBarColor = background
        window.decorView.systemUiVisibility = if (dark) 0 else View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val compact = resources.configuration.screenHeightDp < 720
        val gap = if (compact) 8 else 16
        val container = column().apply { setBackgroundColor(this@MainActivity.background) }
        val root = column().apply { setPadding(dp(20), dp(8), dp(20), dp(16)) }
        container.addView(root, LinearLayout.LayoutParams(-1, -1))
        if (Build.VERSION.SDK_INT >= 35) {
            container.setOnApplyWindowInsetsListener { _, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                container.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                insets
            }
        }
        setContentView(container)
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val title = text("Tachymeter", 25, ink).apply {
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(title, LinearLayout.LayoutParams(0, dp(48), 1f))
        header.addView(button("⋯") { showMenu(it) }.apply { textSize = 20f; contentDescription = "Menu" }, LinearLayout.LayoutParams(dp(56), dp(48)))
        root.addView(header)
        if (!compact) root.addView(text("Just you. Your speed. The open sky.", 14, muted))
        root.space(gap)
        val body = if (landscape) LinearLayout(this).also {
            root.addView(it, LinearLayout.LayoutParams(-1, 0, 1f))
        } else root
        val support = column()

        val main = column().apply {
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(16), dp(12), dp(16))
            background = shape(surface, 28)
        }
        status = text("", 14, accent).apply { gravity = Gravity.CENTER }
        main.addView(status)
        main.space(8)
        val speedRow = LinearLayout(this).apply { gravity = Gravity.CENTER }
        val currentColumn = column().apply { gravity = Gravity.CENTER }
        speed = text("—", 96, ink).apply {
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            fontFeatureSettings = "tnum"
            maxLines = 1
            setAutoSizeTextTypeUniformWithConfiguration(32, 96, 1, TypedValue.COMPLEX_UNIT_SP)
        }
        unit = text("km/h", 20, muted).apply { gravity = Gravity.CENTER }
        currentColumn.addView(speed, LinearLayout.LayoutParams(-1, 0, 1f))
        currentColumn.addView(unit)
        val divider = View(this).apply { setBackgroundColor(pale) }
        val averageColumn = column().apply { gravity = Gravity.CENTER }
        averageValue = text("—", 36, muted).apply {
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            fontFeatureSettings = "tnum"
            maxLines = 1
            setAutoSizeTextTypeUniformWithConfiguration(20, 36, 1, TypedValue.COMPLEX_UNIT_SP)
        }
        val averageLabel = text("avg", 16, muted).apply { gravity = Gravity.CENTER }
        averageColumn.addView(averageValue, LinearLayout.LayoutParams(-1, -2))
        averageColumn.addView(averageLabel)
        speedRow.addView(currentColumn, LinearLayout.LayoutParams(0, -1, 1.8f))
        speedRow.addView(divider, LinearLayout.LayoutParams(dp(1), dp(72)).apply { marginStart = dp(8); marginEnd = dp(8) })
        speedRow.addView(averageColumn, LinearLayout.LayoutParams(0, -1, 1f))
        main.addView(speedRow, LinearLayout.LayoutParams(-1, 0, 1f))
        main.space(12)
        uncertainty = text("", 14, muted).apply { gravity = Gravity.CENTER }
        main.addView(uncertainty)
        if (landscape) {
            body.addView(main, LinearLayout.LayoutParams(0, -1, 1f).apply { marginEnd = dp(16) })
            body.addView(support, LinearLayout.LayoutParams(0, -1, 1f))
        } else {
            body.addView(main, LinearLayout.LayoutParams(-1, 0, 1f))
            body.addView(support, LinearLayout.LayoutParams(-1, -2))
            support.space(gap)
        }
        hint = text("", 14, muted).apply { gravity = Gravity.CENTER; minHeight = dp(40) }
        support.addView(hint)
        action = button("Allow location", primary = true) { locationAction() }
        support.addView(action, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(8) })
        support.space(gap)
        val details = column().apply {
            setPadding(dp(20), dp(8), dp(20), dp(8))
            background = shape(pale, 20)
        }
        signalDetails = details
        satelliteValue = detail(details, "Satellites used / visible")
        positionValue = detail(details, "Position accuracy")
        ageValue = detail(details, "Last measurement")
        support.addView(details)
        support.space(gap)
        val controls = LinearLayout(this)
        unitsButton = button("") {
            mph = !mph
            prefs.edit().putBoolean("mph", mph).apply()
            render()
        }
        awakeButton = button("") {
            keepAwake = !keepAwake
            prefs.edit().putBoolean("awake", keepAwake).apply()
            applyAwake()
            render()
        }
        controls.addView(unitsButton, LinearLayout.LayoutParams(0, dp(56), 1f).apply { marginEnd = dp(8) })
        controls.addView(awakeButton, LinearLayout.LayoutParams(0, dp(56), 1f))
        if (landscape) support.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))
        support.addView(controls)
        render()
    }

    private fun detail(parent: LinearLayout, label: String): TextView {
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; minimumHeight = dp(if (resources.configuration.screenHeightDp < 720) 32 else 40) }
        row.addView(text(label, 14, muted), LinearLayout.LayoutParams(0, -2, 1f))
        val value = text("—", 14, ink).apply { setTypeface(Typeface.DEFAULT, Typeface.BOLD) }
        row.addView(value)
        parent.addView(row)
        return value
    }
    private fun text(value: String, size: Int, color: Int) = TextView(this).apply {
        text = value; textSize = size.toFloat(); setTextColor(color)
    }
    private fun button(value: String, primary: Boolean = false, onClick: (View) -> Unit) = Button(this).apply {
        text = value; isAllCaps = false; textSize = 14f
        stateListAnimator = null
        setTextColor(if (primary) this@MainActivity.background else accent)
        setPadding(dp(8), 0, dp(8), 0); minWidth = 0; minimumWidth = 0
        background = RippleDrawable(ColorStateList.valueOf(0x22087F73), shape(if (primary) accent else pale, 16), null)
        setOnClickListener(onClick)
    }
    private fun shape(color: Int, radius: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun LinearLayout.space(size: Int) = addView(View(this@MainActivity), LinearLayout.LayoutParams(1, dp(size)))
    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
    private fun format(value: Double) = String.format(Locale.getDefault(), "%.1f", value)
    private fun TextView.display(value: String) { if (text.toString() != value) text = value }

    private fun showMenu(anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add("Reset average").setOnMenuItemClickListener { average.reset(); render(); true }
            menu.add("About").setOnMenuItemClickListener { showAbout(); true }
        }.show()
    }

    private fun showAbout() {
        AlertDialog.Builder(this).setTitle("Your speed, simply")
            .setMessage("Tachymeter uses Android’s satellite receiver directly. No ads, accounts, internet permission, analytics, or saved routes. Location stops when you leave the app.\n\nSpeed is the receiver’s native measurement, without smoothing or coordinate-based estimates. The ± value is Android’s estimated speed uncertainty at 68% confidence, not a guaranteed error bound. Decimal places do not imply precision.\n\nMeasurements older than 3 seconds are hidden. Amber means uncertainty exceeds 1 m/s (3.6 km/h), is unavailable, or the location is simulated.\n\nFor best results, use outdoors with a clear sky view. Accuracy and update rate depend on your phone and conditions.\n\nVersion 1.3 · Kotlin · Native Android")
            .setPositiveButton("Got it", null).show()
    }
}
