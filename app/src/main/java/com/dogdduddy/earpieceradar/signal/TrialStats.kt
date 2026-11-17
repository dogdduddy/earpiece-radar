package com.dogdduddy.earpieceradar.signal

import kotlin.math.sqrt

/** 트라이얼 요약 통계 */
data class TrialStats(
    val sampleCount: Int,
    val durationNs: Long,
    val sampleRateHz: Double,
    val meanDelta: Double,
    val maxDelta: Double,
    val stdDelta: Double,
    val meanZ: Double,
    val acRms: Double,
    val meanMagnitude: Double,
    val meanDeltaMag: Double = 0.0,
    val maxDeltaMag: Double = 0.0,
) {
    companion object {
        val EMPTY = TrialStats(0, 0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)

        /**
         * raw 샘플과 baseline 으로부터 통계 계산.
         * acRms 는 전 구간에 롤링 high-pass 를 다시 적용하고, 윈도우가 채워진(isWarm) 이후 값들의 RMS.
         */
        fun compute(samples: List<MagSample>, baseline: Baseline, acWindowNs: Long): TrialStats {
            if (samples.isEmpty()) return EMPTY
            var sumD = 0.0; var sumD2 = 0.0; var maxD = 0.0; var sumZ = 0.0; var sumMag = 0.0
            var sumDm = 0.0; var maxDm = 0.0
            val ac = AcRmsEstimator(acWindowNs)
            var acSum2 = 0.0; var acN = 0
            for (s in samples) {
                val d = Metrics.delta(s, baseline)
                sumD += d; sumD2 += d * d
                if (d > maxD) maxD = d
                sumZ += Metrics.zScore(d, baseline.sigma)
                sumMag += s.magnitude
                val dm = Metrics.deltaMagnitude(s, baseline)
                sumDm += dm; if (dm > maxDm) maxDm = dm
                val a = ac.add(s)
                if (ac.isWarm) { acSum2 += a * a; acN++ }
            }
            val n = samples.size
            val mean = sumD / n
            val variance = if (n > 1) ((sumD2 - n * mean * mean) / (n - 1)).coerceAtLeast(0.0) else 0.0
            val dur = samples.last().timestampNs - samples.first().timestampNs
            return TrialStats(
                sampleCount = n,
                durationNs = dur,
                sampleRateHz = if (dur > 0 && n > 1) (n - 1) * 1e9 / dur else 0.0,
                meanDelta = mean,
                maxDelta = maxD,
                stdDelta = sqrt(variance),
                meanZ = sumZ / n,
                acRms = if (acN > 0) sqrt(acSum2 / acN) else ac.current(),
                meanMagnitude = sumMag / n,
                meanDeltaMag = sumDm / n,
                maxDeltaMag = maxDm,
            )
        }
    }
}
