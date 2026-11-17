package com.dogdduddy.earpieceradar.signal

import org.junit.Assert.assertEquals
import org.junit.Test

class DetectorTest {
    private val cfg = DetectorConfig(deltaThresholdUt = 3.0, zThreshold = 5.0, acThresholdUt = 1.0, debounceSamples = 3)

    @Test
    fun `requires N consecutive samples before switching`() {
        val d = Detector(cfg)
        assertEquals(DetectionLevel.NONE, d.update(10.0, 50.0, 0.0))
        assertEquals(DetectionLevel.NONE, d.update(10.0, 50.0, 0.0))
        assertEquals(DetectionLevel.DETECTED, d.update(10.0, 50.0, 0.0))
        // 한 샘플 튐은 무시
        assertEquals(DetectionLevel.DETECTED, d.update(0.0, 0.0, 0.0))
        assertEquals(DetectionLevel.DETECTED, d.update(10.0, 50.0, 0.0))
        // 내려갈 때도 디바운스
        assertEquals(DetectionLevel.DETECTED, d.update(0.0, 0.0, 0.0))
        assertEquals(DetectionLevel.DETECTED, d.update(0.0, 0.0, 0.0))
        assertEquals(DetectionLevel.NONE, d.update(0.0, 0.0, 0.0))
    }

    @Test
    fun `suspect when only one of delta or z exceeds`() {
        val d = Detector(cfg)
        assertEquals(DetectionLevel.SUSPECT, d.candidateFor(4.0, 2.0, 0.0))
        assertEquals(DetectionLevel.SUSPECT, d.candidateFor(1.0, 6.0, 0.0))
        assertEquals(DetectionLevel.DETECTED, d.candidateFor(4.0, 6.0, 0.0))
        assertEquals(DetectionLevel.NONE, d.candidateFor(1.0, 1.0, 0.0))
    }

    @Test
    fun `ac alone can trigger`() {
        val d = Detector(cfg)
        assertEquals(DetectionLevel.DETECTED, d.candidateFor(0.0, 0.0, 1.5))
        assertEquals(DetectionLevel.SUSPECT, d.candidateFor(0.0, 0.0, 0.6))
        assertEquals(DetectionLevel.NONE, d.candidateFor(0.0, 0.0, 0.4))
    }
}
