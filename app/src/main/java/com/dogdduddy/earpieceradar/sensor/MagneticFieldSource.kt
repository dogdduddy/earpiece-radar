package com.dogdduddy.earpieceradar.sensor

import com.dogdduddy.earpieceradar.signal.MagSample
import kotlinx.coroutines.flow.Flow

/** 사용 중인 센서 정보(화면 표시용) */
data class SensorInfo(
    val name: String,
    val vendor: String,
    val typeLabel: String,     // "MAGNETIC_FIELD_UNCALIBRATED" / "MAGNETIC_FIELD"
    val isUncalibrated: Boolean,
    val minDelayUs: Int,       // 센서가 보고하는 최소 샘플 간격 → 최대 Hz
    val resolutionUt: Float,
    val maxRangeUt: Float,
) {
    val maxHz: Double get() = if (minDelayUs > 0) 1_000_000.0 / minDelayUs else 0.0
}

/**
 * 자기장 샘플 소스 추상화.
 * 2단계에서 BLE 스캔 등 다른 소스를 붙일 수 있게 인터페이스로 분리해 둔다.
 */
interface MagneticFieldSource {
    val info: SensorInfo?
    val isAvailable: Boolean get() = info != null

    /** 구독하는 동안만 센서 리스너를 등록하는 cold flow */
    fun samples(): Flow<MagSample>
}
