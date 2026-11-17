package com.dogdduddy.earpieceradar.signal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class TrialStatsTest {
    @Test
    fun `stats of constant offset recording`() {
        val base = Vec3(20.0, 10.0, -40.0)
        val calSamples = Synth.constant(600, 200.0, base.x, base.y, base.z, noiseSigma = 0.1, seed = 7)
        val cal = BaselineCalibrator(1); calSamples.forEach { cal.add(it) }
        val baseline = cal.result(0L)

        val rec = Synth.constant(1000, 200.0, base.x + 3.0, base.y, base.z + 4.0, noiseSigma = 0.1, seed = 8)
        val st = TrialStats.compute(rec, baseline, acWindowNs = 500_000_000L)
        assertEquals(1000, st.sampleCount)
        assertEquals(200.0, st.sampleRateHz, 0.5)
        assertEquals(5.0, st.meanDelta, 0.05)
        assertTrue(st.maxDelta >= st.meanDelta)
        assertTrue(st.stdDelta < 0.3)
        assertEquals(5.0 / baseline.sigma, st.meanZ, 1.0)
        // 노이즈만 있으므로 AC RMS ≈ sqrt(3)*0.1
        assertEquals(sqrt(3.0) * 0.1, st.acRms, 0.03)
    }

    @Test
    fun `empty list gives EMPTY`() {
        val b = Baseline(Vec3.ZERO, 1.0, Vec3.ZERO, 0, 0, 0)
        assertEquals(TrialStats.EMPTY, TrialStats.compute(emptyList(), b, 1))
    }

    @Test
    fun `engine pipeline produces consistent metrics`() {
        val engine = MetricsEngine(acWindowNs = 500_000_000L, detectorConfig = DetectorConfig(debounceSamples = 2))
        val base = Vec3(0.0, 0.0, 50.0)
        val calSamples = Synth.constant(400, 200.0, base.x, base.y, base.z, noiseSigma = 0.05)
        val cal = BaselineCalibrator(1); calSamples.forEach { cal.add(it) }
        engine.baseline = cal.result(0L)
        var last: MetricSample? = null
        Synth.constant(400, 200.0, 5.0, 0.0, 50.0, noiseSigma = 0.05, seed = 9).forEach { last = engine.process(it) }
        assertEquals(5.0, last!!.delta, 0.2)
        assertEquals(200.0, last!!.hz, 1.0)
        assertEquals(DetectionLevel.DETECTED, last!!.level)
    }
}
