package com.dogdduddy.earpieceradar.data

import com.dogdduddy.earpieceradar.signal.DetectionMode
import com.dogdduddy.earpieceradar.signal.DetectorConfig
import kotlinx.serialization.Serializable

/**
 * 탐지 튠(임계값 묶음). 프로필마다 하나씩 가진다.
 * 실험으로 확정한 값은 [TuningDefaults] 에 상수로 둔다. 장소별 프로필은 이 값을 출발점으로 조정하고,
 * 어려우면 "기본값으로 초기화" 로 되돌린다.
 */
@Serializable
data class Tuning(
    val deltaThresholdUt: Double = 3.0,
    val zThreshold: Double = 5.0,
    val acThresholdUt: Double = 1.0,
    val debounceSamples: Int = 5,
    val mode: DetectionMode = DetectionMode.VECTOR,
) {
    fun toDetectorConfig() = DetectorConfig(deltaThresholdUt, zThreshold, acThresholdUt, debounceSamples, mode)
}

object TuningDefaults {
    /**
     * 개발자 기본 튠. 자석 실측 후 이 상수만 갱신하면 "기본값으로 초기화" 가 그 값으로 돌아간다.
     * TODO(실측 후): Δ/z/AC 임계값과 기본 mode 확정.
     */
    val DEVELOPER = Tuning(
        deltaThresholdUt = 3.0,
        zThreshold = 5.0,
        acThresholdUt = 1.0,
        debounceSamples = 5,
        mode = DetectionMode.VECTOR,
    )

    /** 선생님용 간단 프리셋. 표준 = DEVELOPER, 민감/보수는 그 배율. */
    enum class Preset(val label: String, val description: String, val factor: Double) {
        SENSITIVE("민감", "작은 변화에도 반응. 오탐 늘어남", 0.5),
        STANDARD("표준", "개발자 기본값", 1.0),
        CONSERVATIVE("보수", "확실한 변화만. 미탐 늘어남", 2.0);

        fun apply(base: Tuning = DEVELOPER): Tuning = base.copy(
            deltaThresholdUt = base.deltaThresholdUt * factor,
            zThreshold = base.zThreshold * factor,
            acThresholdUt = base.acThresholdUt * factor,
        )
    }

    /** 현재 튠이 어느 프리셋과 일치하는지(없으면 null) */
    fun presetOf(t: Tuning): Preset? = Preset.entries.firstOrNull { p ->
        val x = p.apply()
        kotlin.math.abs(x.deltaThresholdUt - t.deltaThresholdUt) < 1e-6 &&
            kotlin.math.abs(x.zThreshold - t.zThreshold) < 1e-6 &&
            kotlin.math.abs(x.acThresholdUt - t.acThresholdUt) < 1e-6
    }
}
