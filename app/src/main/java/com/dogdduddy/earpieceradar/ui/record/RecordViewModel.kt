package com.dogdduddy.earpieceradar.ui.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dogdduddy.earpieceradar.core.SignalHub
import com.dogdduddy.earpieceradar.data.Settings
import com.dogdduddy.earpieceradar.data.ProfileRepository
import com.dogdduddy.earpieceradar.data.SettingsRepository
import com.dogdduddy.earpieceradar.data.Trial
import com.dogdduddy.earpieceradar.data.TrialConfig
import com.dogdduddy.earpieceradar.data.TrialRepository
import com.dogdduddy.earpieceradar.signal.Baseline
import com.dogdduddy.earpieceradar.signal.MagSample
import com.dogdduddy.earpieceradar.signal.MetricSample
import com.dogdduddy.earpieceradar.signal.TrialStats
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

sealed interface RecordPhase {
    data object Idle : RecordPhase
    data class Calibrating(val progress: Float) : RecordPhase
    data class Countdown(val secondsLeft: Int) : RecordPhase
    data class Recording(val progress: Float, val sampleCount: Int, val live: MetricSample?) : RecordPhase
    data object Saving : RecordPhase
    data class Done(val trial: Trial, val stats: TrialStats) : RecordPhase
    data class Error(val message: String) : RecordPhase
}

data class RecordUiState(
    val config: TrialConfig = TrialConfig(),
    val phase: RecordPhase = RecordPhase.Idle,
    val baseline: Baseline? = null,
    val settings: Settings = Settings(),
    val sameConfigCount: Int = 0,
    val totalTrials: Int = 0,
    val sensorAvailable: Boolean = true,
)

class RecordViewModel(
    private val hub: SignalHub,
    private val settingsRepo: SettingsRepository,
    private val trialRepo: TrialRepository,
    private val profileRepo: ProfileRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(RecordUiState(settings = settingsRepo.settings.value, sensorAvailable = hub.sensorInfo != null))
    val ui: StateFlow<RecordUiState> = _ui.asStateFlow()
    private var runJob: Job? = null

    init {
        viewModelScope.launch { hub.baseline.collect { b -> _ui.update { it.copy(baseline = b) } } }
        viewModelScope.launch { settingsRepo.settings.collect { s -> _ui.update { it.copy(settings = s) } } }
        viewModelScope.launch {
            trialRepo.trials.collect { list ->
                _ui.update { it.copy(totalTrials = list.size, sameConfigCount = countSame(list, it.config)) }
            }
        }
    }

    private fun countSame(list: List<Trial>, c: TrialConfig) = list.count {
        it.config.magnet == c.magnet && it.config.distanceCm == c.distanceCm &&
            it.config.orientation == c.orientation && it.config.placement == c.placement
    }

    fun updateConfig(transform: (TrialConfig) -> TrialConfig) = _ui.update {
        val c = transform(it.config)
        it.copy(config = c, sameConfigCount = countSame(trialRepo.trials.value, c))
    }

    fun backToIdle() { runJob?.cancel(); _ui.update { it.copy(phase = RecordPhase.Idle) } }

    fun cancel() {
        runJob?.cancel(); runJob = null
        _ui.update { it.copy(phase = RecordPhase.Idle) }
    }

    /** 현재 config 로 트라이얼 1회 실행: [영점] → 카운트다운 → 녹화 → 저장 */
    fun start() {
        if (runJob?.isActive == true) return
        val cfg = _ui.value.config
        val st = settingsRepo.settings.value
        runJob = viewModelScope.launch {
            try {
                // 1) 기준값
                var baseline = hub.baseline.value
                if (cfg.freshBaseline || baseline == null) {
                    _ui.update { it.copy(phase = RecordPhase.Calibrating(0f)) }
                    val progressJob = launch {
                        hub.calibrationProgress.collect { p -> if (p != null) _ui.update { it.copy(phase = RecordPhase.Calibrating(p)) } }
                    }
                    baseline = hub.calibrate(st.calibrationSeconds)
                    progressJob.cancel()
                }
                // 2) 카운트다운 (자석을 위치에 놓는 시간)
                for (s in st.countdownSeconds downTo 1) {
                    _ui.update { it.copy(phase = RecordPhase.Countdown(s)) }
                    delay(1000)
                }
                // 3) 녹화: 샘플 타임스탬프 기준 recordSeconds. 센서가 멈춰도 빠져나오도록 wall-clock 안전장치.
                val recordNs = st.recordSeconds * 1_000_000_000L
                val samples = ArrayList<MagSample>(st.recordSeconds * 500)
                _ui.update { it.copy(phase = RecordPhase.Recording(0f, 0, null)) }
                var firstTs = -1L
                var counter = 0
                withTimeoutOrNull(st.recordSeconds * 1000L + 3000L) {
                    hub.metrics.takeWhile { m ->
                        if (firstTs < 0) firstTs = m.sample.timestampNs
                        val elapsed = m.sample.timestampNs - firstTs
                        if (elapsed <= recordNs) samples.add(m.sample)
                        if (++counter % 10 == 0) {
                            val p = (elapsed.toDouble() / recordNs).toFloat().coerceIn(0f, 1f)
                            _ui.update { it.copy(phase = RecordPhase.Recording(p, samples.size, m)) }
                        }
                        elapsed < recordNs
                    }.collect { }
                }
                if (samples.size < 2) throw IllegalStateException("샘플이 거의 수집되지 않았습니다 (${samples.size}개). 센서 상태를 확인하세요.")
                // 4) 통계 + 저장
                _ui.update { it.copy(phase = RecordPhase.Saving) }
                val stats = TrialStats.compute(samples, baseline, st.acWindowNs)
                val trial = trialRepo.save(
                    config = cfg,
                    sensorType = hub.sensorInfo?.typeLabel ?: "unknown",
                    profileName = profileRepo.active.value.name,
                    mode = profileRepo.active.value.tuning.mode,
                    baseline = baseline,
                    samples = samples,
                    stats = stats,
                )
                _ui.update { it.copy(phase = RecordPhase.Done(trial, stats)) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update { it.copy(phase = RecordPhase.Error(e.message ?: e.toString())) }
            }
        }
    }

    /** "같은 설정으로 다시": config 유지한 채 즉시 재실행 */
    fun repeat() { _ui.update { it.copy(phase = RecordPhase.Idle) }; start() }
}
