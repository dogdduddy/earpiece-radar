package com.dogdduddy.earpieceradar.core

import com.dogdduddy.earpieceradar.data.ProfileRepository
import com.dogdduddy.earpieceradar.data.SettingsRepository
import com.dogdduddy.earpieceradar.data.Tuning
import com.dogdduddy.earpieceradar.sensor.MagneticFieldSource
import com.dogdduddy.earpieceradar.sensor.SensorInfo
import com.dogdduddy.earpieceradar.signal.Baseline
import com.dogdduddy.earpieceradar.signal.BaselineCalibrator
import com.dogdduddy.earpieceradar.signal.MagSample
import com.dogdduddy.earpieceradar.signal.MetricSample
import com.dogdduddy.earpieceradar.signal.MetricsEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.takeWhile

/**
 * 앱 전역 신호 허브. 센서 flow 를 공유하고(구독자가 있을 때만 리스너 등록),
 * 모든 샘플에 MetricsEngine 을 적용해 지표 스트림을 만든다.
 * 기준값(B0, σ)은 여기 한 곳에 보관되고, 임계값/모드는 활성 프로필에서 읽는다.
 */
class SignalHub(
    private val source: MagneticFieldSource,
    private val settingsRepo: SettingsRepository,
    private val profileRepo: ProfileRepository,
    scope: CoroutineScope,
) {
    val sensorInfo: SensorInfo? get() = source.info

    private val _baseline = MutableStateFlow<Baseline?>(null)
    val baseline: StateFlow<Baseline?> = _baseline.asStateFlow()

    private val _calibrationProgress = MutableStateFlow<Float?>(null)
    /** null = 캘리브레이션 중 아님, 0..1 진행률 */
    val calibrationProgress: StateFlow<Float?> = _calibrationProgress.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private data class EngineKey(val acWindowNs: Long, val tuning: Tuning)

    private fun currentKey() = EngineKey(settingsRepo.settings.value.acWindowNs, profileRepo.active.value.tuning)
    private var engineKey = currentKey()
    private var engine = MetricsEngine(engineKey.acWindowNs, engineKey.tuning.toDetectorConfig())

    val samples: SharedFlow<MagSample> = source.samples()
        .catch { e -> _error.value = e.message ?: "센서 오류" }
        .shareIn(scope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 1500), replay = 0)

    val metrics: SharedFlow<MetricSample> = samples
        .map { s ->
            val key = currentKey()
            if (key != engineKey) {
                // 설정/프로필이 바뀌면 엔진 재구성 (AC 윈도우 / 임계값 / 디바운스 / 모드)
                engine = MetricsEngine(key.acWindowNs, key.tuning.toDetectorConfig())
                engineKey = key
            }
            engine.baseline = _baseline.value
            engine.process(s)
        }
        .shareIn(scope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 1500), replay = 0)

    /**
     * durationSeconds 동안 샘플을 모아 B0·σ 를 갱신한다. 완료되면 Baseline 반환.
     * 호출자가 취소하면 진행률만 지우고 기존 기준값은 유지한다.
     */
    suspend fun calibrate(durationSeconds: Int = settingsRepo.settings.value.calibrationSeconds): Baseline {
        val cal = BaselineCalibrator(durationSeconds * 1_000_000_000L)
        _calibrationProgress.value = 0f
        try {
            samples.takeWhile { s ->
                cal.add(s)
                _calibrationProgress.value = cal.progress().toFloat()
                !cal.isComplete()
            }.collect { }
            val b = cal.result(System.currentTimeMillis())
            _baseline.value = b
            engine.detector.reset()
            return b
        } finally {
            _calibrationProgress.value = null
        }
    }

    /** 프로필에 저장된 영점 등을 외부에서 적용 */
    fun setBaseline(b: Baseline?) { _baseline.value = b; engine.detector.reset() }

    fun clearBaseline() = setBaseline(null)

    /** 다음 샘플을 하나 기다린다(센서가 살아있는지 확인용). */
    suspend fun awaitFirstSample(): MagSample = samples.first()
}
