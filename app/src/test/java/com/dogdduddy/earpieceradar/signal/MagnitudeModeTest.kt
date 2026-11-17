package com.dogdduddy.earpieceradar.signal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class MagnitudeModeTest {
    /** 지구 자기장 벡터 E 를 폰 좌표에서 angle 만큼 회전시킨 뒤 고정 편향 bias 를 더한 raw 샘플 */
    private fun rotated(tNs: Long, angle: Double, bias: Vec3 = Vec3(120.0, -40.0, 60.0)): MagSample {
        val e = Vec3(0.0, 30.0, -40.0) // |E| = 50
        val ex = e.x * cos(angle) - e.y * sin(angle)
        val ey = e.x * sin(angle) + e.y * cos(angle)
        return MagSample(tNs, (ex + bias.x).toFloat(), (ey + bias.y).toFloat(), (e.z + bias.z).toFloat(), 3,
            bias.x.toFloat(), bias.y.toFloat(), bias.z.toFloat())
    }

    @Test
    fun `calMagnitude removes bias and is rotation invariant while raw magnitude is not`() {
        val a = rotated(0, 0.0); val b = rotated(1, 1.2)
        assertEquals(50.0, a.calMagnitude.toDouble(), 1e-3)
        assertEquals(50.0, b.calMagnitude.toDouble(), 1e-3)
        assertTrue(kotlin.math.abs(a.magnitude - b.magnitude) > 5f)
    }

    @Test
    fun `magnitude mode ignores phone rotation but vector mode fires`() {
        val cal = BaselineCalibrator(1)
        for (i in 0 until 200) cal.add(rotated(i * 5_000_000L, 0.0))
        val base = cal.result(0L)
        val turned = rotated(0, 0.5)
        val dVec = Metrics.delta(turned, base)
        val dMag = Metrics.deltaMagnitude(turned, base)
        assertTrue("vector delta should be large: $dVec", dVec > 10.0)
        assertEquals(0.0, dMag, 1e-3)

        val vec = MetricsEngine(500_000_000L, DetectorConfig(debounceSamples = 1, mode = DetectionMode.VECTOR)).apply { baseline = base }
        val mag = MetricsEngine(500_000_000L, DetectorConfig(debounceSamples = 1, mode = DetectionMode.MAGNITUDE)).apply { baseline = base }
        assertEquals(DetectionLevel.NONE, mag.process(turned).level)
        assertTrue(vec.process(turned).level != DetectionLevel.NONE)
    }

    @Test
    fun `magnet along field direction changes magnitude`() {
        val cal = BaselineCalibrator(1)
        for (i in 0 until 200) cal.add(rotated(i * 5_000_000L, 0.0))
        val base = cal.result(0L)
        // E 방향(0, 0.6, -0.8) 으로 5 µT 추가
        val s0 = rotated(0, 0.0)
        val s = s0.copy(y = s0.y + 3f, z = s0.z - 4f)
        assertEquals(5.0, Metrics.deltaMagnitude(s, base), 1e-3)
        assertEquals(5.0, Metrics.delta(s, base), 1e-3)
        // sigmaMag 는 상수 신호에서 0 → z 는 클램프된 큰 값
        assertEquals(0.0, base.sigmaMag, 1e-9)
        assertEquals(sqrt(0.0), base.sigma, 1e-9)
    }

    @Test
    fun `trial stats include magnitude delta`() {
        val cal = BaselineCalibrator(1)
        for (i in 0 until 100) cal.add(rotated(i * 5_000_000L, 0.0))
        val base = cal.result(0L)
        val rec = List(100) { i -> val s = rotated(i * 5_000_000L, 0.0); s.copy(y = s.y + 3f, z = s.z - 4f) }
        val st = TrialStats.compute(rec, base, 500_000_000L)
        assertEquals(5.0, st.meanDeltaMag, 1e-3)
        assertEquals(5.0, st.maxDeltaMag, 1e-3)
    }
}
