package com.dogdduddy.earpieceradar.signal

/** 한 샘플에 대해 계산된 지표 묶음 */
data class MetricSample(
    val sample: MagSample,
    /** 벡터 Δ = |B − B0| */
    val delta: Double,
    /** 크기 Δ = ||B−bias| − mag0| */
    val deltaMag: Double,
    /** 판정에 쓰인 Δ 의 z (mode 에 따라 delta 또는 deltaMag 기준) */
    val z: Double,
    val acRms: Double,
    val hz: Double,
    val level: DetectionLevel,
    val mode: DetectionMode,
) {
    /** 판정에 쓰인 Δ */
    val deltaUsed: Double get() = if (mode == DetectionMode.MAGNITUDE) deltaMag else delta
}

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
        val mode = detector.config.mode
        val delta = if (b != null) Metrics.delta(s, b) else 0.0
        val deltaMag = if (b != null) Metrics.deltaMagnitude(s, b) else 0.0
        val z = if (b != null) {
            if (mode == DetectionMode.MAGNITUDE) Metrics.zScore(deltaMag, b.sigmaMag) else Metrics.zScore(delta, b.sigma)
        } else 0.0
        val acRms = ac.add(s)
        val hz = rate.add(s.timestampNs)
        val used = if (mode == DetectionMode.MAGNITUDE) deltaMag else delta
        val level = detector.update(if (b != null) used else 0.0, z, acRms)
        return MetricSample(s, delta, deltaMag, z, acRms, hz, level, mode)
    }

    fun reset() { ac.reset(); rate.reset(); detector.reset() }
}
