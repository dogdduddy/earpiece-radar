package com.dogdduddy.earpieceradar.signal

import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/** 합성 데이터 생성기 */
object Synth {
    const val NS = 1_000_000_000L

    fun constant(
        n: Int, hz: Double, x: Double, y: Double, z: Double,
        noiseSigma: Double = 0.0, seed: Int = 42, startNs: Long = 0L,
    ): List<MagSample> {
        val rnd = Random(seed)
        val dt = (NS / hz).toLong()
        return List(n) { i ->
            fun g() = if (noiseSigma == 0.0) 0.0 else gaussian(rnd) * noiseSigma
            MagSample(startNs + i * dt, (x + g()).toFloat(), (y + g()).toFloat(), (z + g()).toFloat(), 3)
        }
    }

    /** 처음 nBefore 샘플은 base, 이후 base+step */
    fun step(nBefore: Int, nAfter: Int, hz: Double, base: Vec3, step: Vec3, noiseSigma: Double = 0.0): List<MagSample> {
        val a = constant(nBefore, hz, base.x, base.y, base.z, noiseSigma, seed = 1)
        val dt = (NS / hz).toLong()
        val b = constant(nAfter, hz, base.x + step.x, base.y + step.y, base.z + step.z, noiseSigma, seed = 2, startNs = nBefore * dt)
        return a + b
    }

    /** x축에 amplitude 사인파(freqHz)를 얹은 신호 */
    fun sine(n: Int, hz: Double, base: Vec3, amplitude: Double, freqHz: Double): List<MagSample> {
        val dt = (NS / hz).toLong()
        return List(n) { i ->
            val t = i * dt / NS.toDouble()
            MagSample(i * dt, (base.x + amplitude * sin(2 * PI * freqHz * t)).toFloat(), base.y.toFloat(), base.z.toFloat(), 3)
        }
    }

    private fun gaussian(r: Random): Double {
        // Box–Muller
        var u1: Double
        do { u1 = r.nextDouble() } while (u1 <= 1e-12)
        val u2 = r.nextDouble()
        return kotlin.math.sqrt(-2.0 * kotlin.math.ln(u1)) * kotlin.math.cos(2 * PI * u2)
    }
}
