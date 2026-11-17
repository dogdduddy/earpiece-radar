package com.dogdduddy.earpieceradar.data

import com.dogdduddy.earpieceradar.signal.Baseline
import com.dogdduddy.earpieceradar.signal.DetectionMode
import com.dogdduddy.earpieceradar.signal.TrialStats
import com.dogdduddy.earpieceradar.signal.Vec3
import kotlinx.serialization.Serializable

enum class MagnetType(val label: String) {
    NONE("없음(대조군)"), D3X1("D3×1mm"), D4X2("D4×2mm"), OTHER("기타");
}

enum class Orientation(val label: String) {
    N_FRONT("N극 정면"), SIDE("측면"), S_FRONT("S극 정면"), OTHER("기타");
}

enum class Placement(val label: String) {
    EAR("귀"), NECK("목"), DESK("책상"), OTHER("기타");
}

@Serializable
data class BaselineDto(
    val b0x: Double, val b0y: Double, val b0z: Double, val sigma: Double, val sampleCount: Int,
    val mag0: Double = 0.0, val sigmaMag: Double = 0.0,
) {
    fun toBaseline(createdAtEpochMs: Long = 0L) = Baseline(
        Vec3(b0x, b0y, b0z), sigma, Vec3.ZERO, sampleCount, 0, createdAtEpochMs,
        mag0 = if (mag0 > 0) mag0 else Vec3(b0x, b0y, b0z).norm,
        sigmaMag = if (sigmaMag > 0) sigmaMag else sigma,
    )
    companion object {
        fun from(b: Baseline) = BaselineDto(b.b0.x, b.b0.y, b.b0.z, b.sigma, b.sampleCount, b.mag0, b.sigmaMag)
    }
}

@Serializable
data class StatsDto(
    val sampleCount: Int, val durationMs: Double, val sampleRateHz: Double,
    val meanDelta: Double, val maxDelta: Double, val stdDelta: Double,
    val meanZ: Double, val acRms: Double, val meanMagnitude: Double,
    val meanDeltaMag: Double = 0.0, val maxDeltaMag: Double = 0.0,
) {
    companion object {
        fun from(s: TrialStats) = StatsDto(
            s.sampleCount, s.durationNs / 1e6, s.sampleRateHz, s.meanDelta, s.maxDelta,
            s.stdDelta, s.meanZ, s.acRms, s.meanMagnitude, s.meanDeltaMag, s.maxDeltaMag,
        )
    }
}

/** 트라이얼 조건(입력 폼) */
@Serializable
data class TrialConfig(
    val magnet: MagnetType = MagnetType.D3X1,
    val magnetOther: String = "",
    val distanceCm: Double = 3.0,
    val orientation: Orientation = Orientation.N_FRONT,
    val placement: Placement = Placement.DESK,
    val note: String = "",
    val freshBaseline: Boolean = true,
) {
    val magnetLabel: String get() = if (magnet == MagnetType.OTHER && magnetOther.isNotBlank()) magnetOther else magnet.label
}

@Serializable
data class Trial(
    val id: String,
    val createdAtEpochMs: Long,
    val config: TrialConfig,
    val sensorType: String,
    val profileName: String = "",
    val mode: DetectionMode = DetectionMode.VECTOR,
    val baseline: BaselineDto,
    val stats: StatsDto,
    /** raw 샘플 CSV 파일 이름(repository 의 raw 디렉터리 기준) */
    val rawFile: String,
)
