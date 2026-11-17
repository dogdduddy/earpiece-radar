package com.dogdduddy.earpieceradar.signal

import kotlin.math.sqrt

/**
 * 지자기 센서 원시 샘플. 단위 µT, timestamp는 SensorEvent.timestamp(ns, 단조 증가).
 * bx/by/bz 는 UNCALIBRATED 센서가 함께 주는 hard-iron 편향 추정값(values[3..5]). 보정 센서면 0.
 */
data class MagSample(
    val timestampNs: Long,
    val x: Float,
    val y: Float,
    val z: Float,
    val accuracy: Int,
    val bx: Float = 0f,
    val by: Float = 0f,
    val bz: Float = 0f,
) {
    /** raw 크기 |B| */
    val magnitude: Float get() = sqrt(x * x + y * y + z * z)

    /**
     * 편향을 뺀 크기 |B − bias|. 폰을 회전시켜도(이상적으로는) 변하지 않는 값이라
     * 폰을 움직이며 스캔하는 용도에 쓴다. raw |B| 는 편향이 폰 좌표에 고정되어 회전에 따라 변한다.
     */
    val calMagnitude: Float get() {
        val cx = x - bx; val cy = y - by; val cz = z - bz
        return sqrt(cx * cx + cy * cy + cz * cz)
    }
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
