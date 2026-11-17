package com.dogdduddy.earpieceradar.signal

/** 한 샘플에 대해 계산된 지표 묶음 */
data class MetricSample(
    val sample: MagSample,
    val delta: Double,
    val z: Double,
    val acRms: Double,
    val hz: Double,
    val level: DetectionLevel,
)

/**
 * 실시간 지표 파이프라인. baseline 이 없으면 Δ, z 는 0 으로 두고 AC/Hz 만 계산한다.
 * 순수 Kotlin. 한 코루틴에서 순차 호출.
 */
class MetricsEngine(
    acWindowNs: Long,
    detectorConfig: DetectorConfig,
) {
    var baseline: Baseline? = null
    private val ac = AcRmsEstimator(acWindowNs)
    private val rate = SampleRateEstimator()
    val detector = Detector(detectorConfig)

    fun process(s: MagSample): MetricSample {
        val b = baseline
        val delta = if (b != null) Metrics.delta(s, b) else 0.0
        val z = if (b != null) Metrics.zScore(delta, b.sigma) else 0.0
        val acRms = ac.add(s)
        val hz = rate.add(s.timestampNs)
        val level = if (b != null) detector.update(delta, z, acRms) else detector.update(0.0, 0.0, acRms)
        return MetricSample(s, delta, z, acRms, hz, level)
    }

    fun reset() { ac.reset(); rate.reset(); detector.reset() }
}
