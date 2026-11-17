package com.dogdduddy.earpieceradar.signal

import kotlin.math.sqrt

/**
 * 영점 기준값.
 * @param b0 캘리브레이션 구간 벡터 평균
 * @param sigma 노이즈 표준편차. 벡터 편차의 RMS = sqrt(σx² + σy² + σz²) 로 정의한다.
 *   이렇게 두면 자석이 없을 때 Δ = |B − B0| 의 대표값이 σ 근처가 되어 z = Δ/σ 가 직관적이다.
 * @param sigmaAxis 축별 표준편차(정보 표시용)
 */
data class Baseline(
    val b0: Vec3,
    val sigma: Double,
    val sigmaAxis: Vec3,
    val sampleCount: Int,
    val durationNs: Long,
    val createdAtEpochMs: Long,
)

/**
 * 일정 시간 동안 샘플을 모아 Baseline 을 만든다. Welford 누적으로 수치 안정성 확보.
 * 순수 Kotlin. 스레드 안전하지 않으므로 한 코루틴에서만 사용.
 */
class BaselineCalibrator(private val durationNs: Long) {
    private var n = 0
    private var firstTs = 0L
    private var lastTs = 0L
    private val mean = DoubleArray(3)
    private val m2 = DoubleArray(3)

    val sampleCount: Int get() = n

    /** 0.0 ~ 1.0 진행률 */
    fun progress(): Double =
        if (n == 0) 0.0 else ((lastTs - firstTs).toDouble() / durationNs).coerceIn(0.0, 1.0)

    fun isComplete(): Boolean = n >= 2 && (lastTs - firstTs) >= durationNs

    fun add(s: MagSample) {
        if (n == 0) firstTs = s.timestampNs
        lastTs = s.timestampNs
        n++
        val v = doubleArrayOf(s.x.toDouble(), s.y.toDouble(), s.z.toDouble())
        for (i in 0..2) {
            val delta = v[i] - mean[i]
            mean[i] += delta / n
            m2[i] += delta * (v[i] - mean[i])
        }
    }

    fun result(nowEpochMs: Long): Baseline {
        require(n >= 2) { "샘플이 2개 이상 필요" }
        val varX = m2[0] / (n - 1)
        val varY = m2[1] / (n - 1)
        val varZ = m2[2] / (n - 1)
        return Baseline(
            b0 = Vec3(mean[0], mean[1], mean[2]),
            sigma = sqrt(varX + varY + varZ),
            sigmaAxis = Vec3(sqrt(varX), sqrt(varY), sqrt(varZ)),
            sampleCount = n,
            durationNs = lastTs - firstTs,
            createdAtEpochMs = nowEpochMs,
        )
    }

    fun reset() {
        n = 0; firstTs = 0; lastTs = 0
        mean.fill(0.0); m2.fill(0.0)
    }
}
