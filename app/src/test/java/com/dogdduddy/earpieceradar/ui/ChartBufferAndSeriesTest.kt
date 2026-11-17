package com.dogdduddy.earpieceradar.ui

import com.dogdduddy.earpieceradar.data.BaselineDto
import com.dogdduddy.earpieceradar.data.MagnetType
import com.dogdduddy.earpieceradar.data.StatsDto
import com.dogdduddy.earpieceradar.data.Trial
import com.dogdduddy.earpieceradar.data.TrialConfig
import com.dogdduddy.earpieceradar.signal.DetectionLevel
import com.dogdduddy.earpieceradar.signal.DetectionMode
import com.dogdduddy.earpieceradar.signal.MagSample
import com.dogdduddy.earpieceradar.signal.MetricSample
import com.dogdduddy.earpieceradar.ui.components.ChartBuffer
import com.dogdduddy.earpieceradar.ui.results.ResultsViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartBufferAndSeriesTest {
    private fun ms(tNs: Long, x: Float) = MetricSample(MagSample(tNs, x, 0f, 0f, 3), x.toDouble(), 0.0, 0.0, 0.0, 100.0, DetectionLevel.NONE, DetectionMode.VECTOR)

    @Test
    fun `chart buffer evicts old samples and downsamples`() {
        val buf = ChartBuffer(windowNs = 1_000_000_000L)
        for (i in 0 until 3000) buf.add(ms(i * 1_000_000L, i.toFloat()))  // 1kHz, 3s
        val snap = buf.snapshot(maxPoints = 100)
        assertTrue("size=${snap.size}", snap.size in 90..102)
        assertEquals(0f, snap.times.last(), 0.01f)
        assertTrue(snap.times.first() >= -1.01f)
        assertEquals(2999f, snap.x.last(), 0.01f)
    }

    @Test
    fun `results series groups by magnet and averages repeated distances`() {
        fun t(magnet: MagnetType, d: Double, mean: Double) = Trial(
            id = "id${(Math.random() * 1e6).toInt()}", createdAtEpochMs = 0L, config = TrialConfig(magnet = magnet, distanceCm = d), sensorType = "x",
            baseline = BaselineDto(0.0, 0.0, 0.0, 0.1, 1), stats = StatsDto(1, 0.0, 0.0, mean, mean, 0.0, 0.0, 0.0, 0.0), rawFile = "f",
        )
        val series = ResultsViewModel.buildSeries(listOf(
            t(MagnetType.D3X1, 3.0, 4.0), t(MagnetType.D3X1, 3.0, 6.0), t(MagnetType.D3X1, 1.0, 40.0),
            t(MagnetType.D4X2, 5.0, 2.0),
        ))
        assertEquals(2, series.size)
        val d3 = series.first { it.magnetLabel == MagnetType.D3X1.label }
        assertEquals(listOf(1.0, 3.0), d3.points.map { it.distanceCm })
        assertEquals(5.0, d3.points[1].meanDelta, 1e-9)
        assertEquals(2, d3.points[1].n)
        assertEquals(4.0, d3.points[1].minDelta, 1e-9)
    }
}
