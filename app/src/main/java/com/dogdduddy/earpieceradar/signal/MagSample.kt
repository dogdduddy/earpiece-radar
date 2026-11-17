package com.dogdduddy.earpieceradar.signal

import kotlin.math.sqrt

/** 지자기 센서 원시 샘플. 단위 µT, timestamp는 SensorEvent.timestamp(ns, 단조 증가). */
data class MagSample(
    val timestampNs: Long,
    val x: Float,
    val y: Float,
    val z: Float,
    val accuracy: Int,
) {
    val magnitude: Float get() = sqrt(x * x + y * y + z * z)
}

/** 3차원 벡터(double). B0 등 내부 계산용. */
data class Vec3(val x: Double, val y: Double, val z: Double) {
    val norm: Double get() = sqrt(x * x + y * y + z * z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    companion object {
        val ZERO = Vec3(0.0, 0.0, 0.0)
        fun of(s: MagSample) = Vec3(s.x.toDouble(), s.y.toDouble(), s.z.toDouble())
    }
}
