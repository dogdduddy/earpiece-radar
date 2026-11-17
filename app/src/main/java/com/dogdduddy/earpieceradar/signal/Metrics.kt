package com.dogdduddy.earpieceradar.signal

import kotlin.math.sqrt

object Metrics {
    /** Δ = |B − B0| : 벡터 차의 크기(µT). 크기끼리 빼지 않으므로 방향 변화도 잡힌다. */
    fun delta(sample: MagSample, baseline: Baseline): Double = (Vec3.of(sample) - baseline.b0).norm

    /**
     * Δ|B| = | |B − bias| − mag0 | : 편향 제거 크기의 변화(µT). 폰 회전에 둔감해 스캔(움직이며 측정)용.
     * 자석이 지구 자기장과 수직 방향으로 더하면 크기 변화가 작게 나오는 한계가 있다.
     */
    fun deltaMagnitude(sample: MagSample, baseline: Baseline): Double =
        kotlin.math.abs(sample.calMagnitude.toDouble() - baseline.mag0)

    /** z = Δ / σ. σ가 0이면 Δ>0일 때 무한대 대신 큰 값으로 제한. */
    fun zScore(delta: Double, sigma: Double): Double =
        if (sigma > 1e-9) delta / sigma else if (delta > 0) 1e6 else 0.0
}

/**
 * 롤링 윈도우 high-pass RMS.
 * 윈도우 내 각 축 평균을 빼고(= 이동평균 제거) 벡터 편차의 RMS 를 계산한다.
 * 결과 = sqrt(varX + varY + varZ) (모집단 분산). 단일 축 진폭 A 사인파 → A/√2.
 */
class AcRmsEstimator(val windowNs: Long) {
    private val ts = ArrayDeque<Long>()
    private val xs = ArrayDeque<Double>()
    private val ys = ArrayDeque<Double>()
    private val zs = ArrayDeque<Double>()
    private var sx = 0.0; private var sy = 0.0; private var sz = 0.0
    private var sxx = 0.0; private var syy = 0.0; private var szz = 0.0

    val size: Int get() = ts.size

    /** 윈도우에 쌓인 시간 폭(ns). */
    val spanNs: Long get() = if (ts.size < 2) 0L else ts.last() - ts.first()

    /** 윈도우가 80% 이상 채워졌는지 */
    val isWarm: Boolean get() = spanNs >= windowNs * 8 / 10

    fun add(s: MagSample): Double {
        val x = s.x.toDouble(); val y = s.y.toDouble(); val z = s.z.toDouble()
        ts.addLast(s.timestampNs); xs.addLast(x); ys.addLast(y); zs.addLast(z)
        sx += x; sy += y; sz += z
        sxx += x * x; syy += y * y; szz += z * z
        val cutoff = s.timestampNs - windowNs
        while (ts.size > 1 && ts.first() < cutoff) {
            ts.removeFirst()
            val ox = xs.removeFirst(); val oy = ys.removeFirst(); val oz = zs.removeFirst()
            sx -= ox; sy -= oy; sz -= oz
            sxx -= ox * ox; syy -= oy * oy; szz -= oz * oz
        }
        return current()
    }

    fun current(): Double {
        val n = ts.size
        if (n < 2) return 0.0
        // 수치 오차로 음수가 될 수 있어 0으로 클램프. 누적 합 방식은 µT 스케일(~100)에서 충분히 정확.
        val vx = (sxx / n - (sx / n) * (sx / n)).coerceAtLeast(0.0)
        val vy = (syy / n - (sy / n) * (sy / n)).coerceAtLeast(0.0)
        val vz = (szz / n - (sz / n) * (sz / n)).coerceAtLeast(0.0)
        return sqrt(vx + vy + vz)
    }

    fun reset() {
        ts.clear(); xs.clear(); ys.clear(); zs.clear()
        sx = 0.0; sy = 0.0; sz = 0.0; sxx = 0.0; syy = 0.0; szz = 0.0
    }
}

/** event.timestamp 기준 실측 샘플링 레이트. 최근 windowNs 안의 타임스탬프로 (n−1)/span 계산. */
class SampleRateEstimator(private val windowNs: Long = 1_000_000_000L) {
    private val ts = ArrayDeque<Long>()

    fun add(timestampNs: Long): Double {
        ts.addLast(timestampNs)
        val cutoff = timestampNs - windowNs
        while (ts.size > 2 && ts.first() < cutoff) ts.removeFirst()
        return hz()
    }

    fun hz(): Double {
        if (ts.size < 2) return 0.0
        val span = ts.last() - ts.first()
        if (span <= 0) return 0.0
        return (ts.size - 1) * 1e9 / span
    }

    fun reset() = ts.clear()
}
