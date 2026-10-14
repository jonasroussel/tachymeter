package app.tachymeter

import kotlin.math.abs

fun main() {
    var assertions = 0
    fun verify(condition: Boolean, message: String) {
        assertions++
        check(condition) { message }
    }
    val t = 10_000_000_000L
    val reading = SpeedReading(10f, .2f, t)
    verify(reading.isUsable(t), "Fresh speed is usable")
    verify(abs(reading.speed(false) - 36) < .00001, "m/s to km/h")
    verify(abs(reading.speed(true) - 22.369362920544) < .00001, "m/s to mph")
    verify(abs(reading.accuracy(false) - .72) < .00001, "Accuracy uses same units")
    verify(!reading.isWeak, "Precise speed")
    verify(reading.isFresh(t + 2_999_999_999L), "Just before stale threshold")
    verify(!reading.isUsable(t + 3_000_000_000L), "Hide exactly at stale threshold")
    verify(!reading.isFresh(t - 1), "Reject future timestamps")
    verify(!SpeedReading(1f, 1f, 0).isFresh(1), "Reject absent timestamp")
    for (invalid in floatArrayOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -1f)) {
        verify(!SpeedReading(invalid, 0f, t).isUsable(t), "Reject invalid speed")
        verify(!SpeedReading(1f, invalid, t).hasUncertainty, "Reject invalid uncertainty")
    }
    verify(SpeedReading(0f, .1f, t).isUsable(t), "Measured zero remains valid")
    verify(SpeedReading(1f, Float.NaN, t).isWeak, "Unknown accuracy flagged")
    verify(SpeedReading(1f, 1.1f, t).isWeak, "Poor accuracy flagged")
    verify(!SpeedReading(1f, 1f, t).isWeak, "Threshold boundary")
    verify(SpeedReading(1f, 0f, t, mock = true).mock, "Preserve mock status")

    val average = SpeedAverage()
    verify(!average.hasValue, "Empty average has no value")
    average.add(SpeedReading(10f, 0f, t))
    verify(!average.hasValue, "Single reading has no duration")
    average.add(SpeedReading(20f, 0f, t + 1_000_000_000L))
    verify(abs(average.speed(false) - 15 * 3.6) < .00001, "Trapezoid mean over one second")
    average.add(SpeedReading(100f, 0f, t + 5_000_000_000L))
    verify(abs(average.speed(false) - 15 * 3.6) < .00001, "Stale gap skipped")
    average.add(SpeedReading(100f, 0f, t + 6_000_000_000L))
    verify(abs(average.speed(false) - 57.5 * 3.6) < .00001, "Resumes after gap: (15·1 + 100·1) / 2")
    average.add(SpeedReading(Float.NaN, 0f, t + 6_500_000_000L))
    average.add(SpeedReading(10f, 0f, t + 7_000_000_000L))
    verify(abs(average.speed(false) - 57.5 * 3.6) < .00001, "Invalid reading breaks the chain")
    average.add(SpeedReading(1.38f, 0f, t + 7_500_000_000L)) // 4.968 km/h < 5 km/h
    average.add(SpeedReading(20f, 0f, t + 8_000_000_000L))
    verify(abs(average.speed(false) - 57.5 * 3.6) < .00001, "Speed below 5 km/h is ignored and breaks the chain")
    average.add(SpeedReading(1.4f, 0f, t + 8_500_000_000L)) // 5.04 km/h >= 5 km/h
    verify(abs(average.speed(false) - (57.5 * 3.6 * 2 + (20 * 3.6 + 1.4 * 3.6) / 2 * 0.5) / 2.5) < .001, "Speed above 5 km/h is included")
    average.reset()
    verify(!average.hasValue, "Reset clears value")
    println("PASS: $assertions measurement assertions")
}
