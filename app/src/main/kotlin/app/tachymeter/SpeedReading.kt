package app.tachymeter

/** Native receiver measurement. Never derive speed from noisy coordinates or invent zero. */
data class SpeedReading(
    val metersPerSecond: Float,
    val uncertainty: Float,
    val timestampNanos: Long,
    val mock: Boolean = false,
) {
    fun isFresh(nowNanos: Long): Boolean =
        timestampNanos > 0 && nowNanos - timestampNanos in 0 until STALE_NANOS

    val hasSpeed: Boolean get() = metersPerSecond.isFinite() && metersPerSecond >= 0
    val hasUncertainty: Boolean get() = uncertainty.isFinite() && uncertainty >= 0
    val isWeak: Boolean get() = !hasUncertainty || uncertainty > 1f
    fun isUsable(nowNanos: Long): Boolean = isFresh(nowNanos) && hasSpeed
    fun speed(mph: Boolean): Double = metersPerSecond * factor(mph)
    fun accuracy(mph: Boolean): Double = uncertainty * factor(mph)

    companion object {
        const val STALE_NANOS = 3_000_000_000L
        const val MIN_AVERAGE_KMH = 5.0
        fun factor(mph: Boolean) = if (mph) 2.2369362920544 else 3.6
    }
}

/** Time-weighted mean of usable readings at or above 5 km/h. Gaps at or beyond the stale threshold are skipped. */
class SpeedAverage {
    private var weightedSum = 0.0
    private var durationNanos = 0L
    private var last: SpeedReading? = null

    val hasValue: Boolean get() = durationNanos > 0

    fun add(reading: SpeedReading) {
        if (!reading.hasSpeed || reading.speed(false) < SpeedReading.MIN_AVERAGE_KMH) { last = null; return }
        val previous = last
        last = reading
        if (previous == null) return
        val gap = reading.timestampNanos - previous.timestampNanos
        if (gap <= 0 || gap >= SpeedReading.STALE_NANOS) return
        weightedSum += (previous.metersPerSecond + reading.metersPerSecond) / 2.0 * gap
        durationNanos += gap
    }

    fun speed(mph: Boolean): Double = weightedSum / durationNanos * SpeedReading.factor(mph)

    fun reset() { weightedSum = 0.0; durationNanos = 0L; last = null }
}
