package com.dogdduddy.earpieceradar.signal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class MetricsTest {
    private fun baselineOf(samples: List<MagSample>): Baseline {
        val cal = BaselineCalibrator(1)
        samples.forEach { cal.add(it) }
        return cal.result(0L)
    }

    @Test
    fun `delta is vector difference magnitude not magnitude difference`() {
        val base = Baseline(Vec3(30.0, 0.0, 0.0), 1.0, Vec3(1.0, 1.0, 1.0), 10, 1, 0)
        // 크기는 같지만(30) 방향만 바뀐 샘플: 크기 차는 0, 벡터 차는 30*sqrt(2)
        val rotated = MagSample(0, 0f, 30f, 0f, 3)
        assertEquals(30.0 * sqrt(2.0), Metrics.delta(rotated, base), 1e-6)
        // 같은 방향으로 3µT 증가
        assertEquals(3.0, Metrics.delta(MagSample(0, 33f, 0f, 0f, 3), base), 1e-6)
    }

    @Test
    fun `z score is delta over sigma`() {
        assertEquals(5.0, Metrics.zScore(2.5, 0.5), 1e-9)
        assertEquals(0.0, Metrics.zScore(0.0, 0.0), 1e-9)
        assertTrue(Metrics.zScore(1.0, 0.0) > 1e5)
    }

    @Test
    fun `step change produces delta equal to step size`() {
        val step = Vec3(0.0, 0.0, 4.0)
        val samples = Synth.step(nBefore = 400, nAfter = 400, hz = 200.0, base = Vec3(20.0, 10.0, -40.0), step = step, noiseSigma = 0.1)
        val b = baselineOf(samples.subList(0, 400))
        val after = samples.subList(400, 800).map { Metrics.delta(it, b) }
        val meanAfter = after.average()
        assertEquals(4.0, meanAfter, 0.1)
        val meanBefore = samples.subList(0, 400).map { Metrics.delta(it, b) }.average()
        assertTrue("baseline delta should be ~sigma", meanBefore < 0.5)
        // z 는 Δ/σ, σ ≈ 0.1*sqrt(3) ≈ 0.173 → z ≈ 23
        val z = Metrics.zScore(meanAfter, b.sigma)
        assertTrue("z=$z", z > 15)
    }

    @Test
    fun `ac rms of constant signal is zero and of sine is amplitude over sqrt2`() {
        val ac = AcRmsEstimator(windowNs = 500_000_000L)
        Synth.constant(200, 200.0, 10.0, 20.0, 30.0).forEach { ac.add(it) }
        assertEquals(0.0, ac.current(), 1e-5)

        val ac2 = AcRmsEstimator(windowNs = 500_000_000L)
        val amplitude = 2.0
        // 50Hz 사인파, 0.5s 윈도우 = 25 주기 → 정확히 정수 주기
        val sine = Synth.sine(n = 600, hz = 400.0, base = Vec3(30.0, 0.0, 0.0), amplitude = amplitude, freqHz = 50.0)
        var last = 0.0
        sine.forEach { last = ac2.add(it) }
        assertTrue(ac2.isWarm)
        assertEquals(amplitude / sqrt(2.0), last, 0.02)
    }

    @Test
    fun `ac rms ignores slow dc offset but keeps window bounded`() {
        val ac = AcRmsEstimator(windowNs = 500_000_000L)
        val samples = Synth.step(nBefore = 200, nAfter = 400, hz = 200.0, base = Vec3(0.0, 0.0, 0.0), step = Vec3(10.0, 0.0, 0.0))
        var last = 0.0
        samples.forEach { last = ac.add(it) }
        // 계단 후 0.5s 이상 지나면 윈도우 안은 다시 상수 → AC 0
        assertEquals(0.0, last, 1e-5)
        assertTrue(ac.size <= 102)
    }

    @Test
    fun `sample rate estimator matches synthetic rate`() {
        val est = SampleRateEstimator(windowNs = Synth.NS)
        var hz = 0.0
        Synth.constant(500, 200.0, 1.0, 1.0, 1.0).forEach { hz = est.add(it.timestampNs) }
        assertEquals(200.0, hz, 0.5)

        val est2 = SampleRateEstimator()
        assertEquals(0.0, est2.add(0L), 1e-9)
        assertEquals(100.0, est2.add(10_000_000L), 1e-6)
    }
}
