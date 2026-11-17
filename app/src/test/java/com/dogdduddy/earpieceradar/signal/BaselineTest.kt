package com.dogdduddy.earpieceradar.signal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class BaselineTest {
    @Test
    fun `constant plus noise gives mean near constant and sigma near sqrt3 times noise`() {
        val noise = 0.2
        val samples = Synth.constant(n = 3000, hz = 200.0, x = 20.0, y = -30.0, z = 40.0, noiseSigma = noise)
        val cal = BaselineCalibrator(durationNs = 3 * Synth.NS)
        samples.forEach { cal.add(it) }
        assertTrue(cal.isComplete())
        val b = cal.result(0L)
        assertEquals(20.0, b.b0.x, 0.03)
        assertEquals(-30.0, b.b0.y, 0.03)
        assertEquals(40.0, b.b0.z, 0.03)
        // 벡터 편차 RMS = sqrt(3) * noise
        assertEquals(sqrt(3.0) * noise, b.sigma, 0.03)
        assertEquals(noise, b.sigmaAxis.x, 0.02)
        assertEquals(3000, b.sampleCount)
    }

    @Test
    fun `progress and completion follow duration`() {
        val cal = BaselineCalibrator(durationNs = 3 * Synth.NS)
        val samples = Synth.constant(n = 300, hz = 100.0, x = 1.0, y = 1.0, z = 1.0)  // 2.99s span
        samples.forEach { cal.add(it) }
        assertFalse(cal.isComplete())
        assertEquals(0.9967, cal.progress(), 0.001)
        cal.add(MagSample(3 * Synth.NS, 1f, 1f, 1f, 3))
        assertTrue(cal.isComplete())
        assertEquals(1.0, cal.progress(), 1e-9)
    }

    @Test
    fun `zero noise gives zero sigma`() {
        val cal = BaselineCalibrator(1)
        Synth.constant(10, 100.0, 5.0, 5.0, 5.0).forEach { cal.add(it) }
        assertEquals(0.0, cal.result(0L).sigma, 1e-9)
    }
}
