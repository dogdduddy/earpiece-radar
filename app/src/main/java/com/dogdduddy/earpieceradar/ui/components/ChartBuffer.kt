package com.dogdduddy.earpieceradar.ui.components

import com.dogdduddy.earpieceradar.signal.MetricSample

/** 그래프용 스냅샷. times 는 현재 시각 기준 초 단위(음수 → 0). */
data class ChartSnapshot(
    val times: FloatArray,
    val x: FloatArray,
    val y: FloatArray,
    val z: FloatArray,
    val mag: FloatArray,
    val delta: FloatArray,
) {
    val size: Int get() = times.size
    companion object {
        val EMPTY = ChartSnapshot(FloatArray(0), FloatArray(0), FloatArray(0), FloatArray(0), FloatArray(0), FloatArray(0))
    }
}

/** 최근 windowNs 동안의 지표 샘플을 보관하는 링 버퍼. 순수 Kotlin. */
class ChartBuffer(var windowNs: Long) {
    private val buf = ArrayDeque<MetricSample>()

    @Synchronized
    fun add(m: MetricSample) {
        buf.addLast(m)
        val cutoff = m.sample.timestampNs - windowNs
        while (buf.isNotEmpty() && buf.first().sample.timestampNs < cutoff) buf.removeFirst()
    }

    @Synchronized
    fun clear() = buf.clear()

    /** 최대 maxPoints 개로 다운샘플링한 스냅샷 */
    @Synchronized
    fun snapshot(maxPoints: Int = 600): ChartSnapshot {
        if (buf.isEmpty()) return ChartSnapshot.EMPTY
        val nowNs = buf.last().sample.timestampNs
        val stride = ((buf.size + maxPoints - 1) / maxPoints).coerceAtLeast(1)
        val n = (buf.size + stride - 1) / stride
        val t = FloatArray(n); val x = FloatArray(n); val y = FloatArray(n)
        val z = FloatArray(n); val mag = FloatArray(n); val d = FloatArray(n)
        var j = 0
        var i = 0
        for (m in buf) {
            if (i % stride == 0 && j < n) {
                t[j] = ((m.sample.timestampNs - nowNs) / 1e9).toFloat()
                x[j] = m.sample.x; y[j] = m.sample.y; z[j] = m.sample.z
                mag[j] = m.sample.magnitude; d[j] = m.delta.toFloat()
                j++
            }
            i++
        }
        return ChartSnapshot(t.copyOf(j), x.copyOf(j), y.copyOf(j), z.copyOf(j), mag.copyOf(j), d.copyOf(j))
    }
}
