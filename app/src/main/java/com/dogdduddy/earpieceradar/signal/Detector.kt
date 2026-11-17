package com.dogdduddy.earpieceradar.signal

enum class DetectionLevel { NONE, SUSPECT, DETECTED }

/**
 * 판정에 쓰는 Δ 의 종류.
 *  - VECTOR: Δ = |B − B0|. 민감하지만 폰을 회전시키면 지구 자기장 방향 변화만으로 튄다 → 폰 고정 측정용.
 *  - MAGNITUDE: Δ|B| = ||B−bias| − mag0|. 회전에 둔감 → 폰을 움직이며 스캔하는 용도.
 */
enum class DetectionMode { VECTOR, MAGNITUDE }

data class DetectorConfig(
    val deltaThresholdUt: Double = 3.0,
    val zThreshold: Double = 5.0,
    val acThresholdUt: Double = 1.0,
    /** 같은 후보 레벨이 연속 N 샘플 유지될 때만 상태를 바꾼다. */
    val debounceSamples: Int = 5,
    val mode: DetectionMode = DetectionMode.VECTOR,
)

/**
 * 3단계 탐지 상태 기계.
 *  - DETECTED 후보: (Δ ≥ Δthr 그리고 z ≥ zthr) 또는 AC ≥ ACthr
 *  - SUSPECT 후보: Δ ≥ Δthr 또는 z ≥ zthr 또는 AC ≥ ACthr/2
 *  - 그 외 NONE
 * 후보 레벨이 debounceSamples 번 연속되면 현재 상태로 확정(상승/하강 모두 디바운스).
 */
class Detector(var config: DetectorConfig = DetectorConfig()) {
    var level: DetectionLevel = DetectionLevel.NONE
        private set
    private var candidate: DetectionLevel = DetectionLevel.NONE
    private var streak = 0

    fun candidateFor(delta: Double, z: Double, acRms: Double): DetectionLevel {
        val c = config
        val dcHit = delta >= c.deltaThresholdUt && z >= c.zThreshold
        val acHit = acRms >= c.acThresholdUt
        if (dcHit || acHit) return DetectionLevel.DETECTED
        val weak = delta >= c.deltaThresholdUt || z >= c.zThreshold || acRms >= c.acThresholdUt / 2
        return if (weak) DetectionLevel.SUSPECT else DetectionLevel.NONE
    }

    fun update(delta: Double, z: Double, acRms: Double): DetectionLevel {
        val cand = candidateFor(delta, z, acRms)
        if (cand == candidate) streak++ else { candidate = cand; streak = 1 }
        if (cand == level) { streak = 0; candidate = level; return level }
        if (streak >= config.debounceSamples.coerceAtLeast(1)) {
            level = cand
            streak = 0
        }
        return level
    }

    fun reset() {
        level = DetectionLevel.NONE; candidate = DetectionLevel.NONE; streak = 0
    }
}
