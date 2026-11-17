package com.dogdduddy.earpieceradar.ui.monitor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dogdduddy.earpieceradar.core.SignalHub
import com.dogdduddy.earpieceradar.data.Settings
import com.dogdduddy.earpieceradar.data.SettingsRepository
import com.dogdduddy.earpieceradar.sensor.SensorInfo
import com.dogdduddy.earpieceradar.signal.Baseline
import com.dogdduddy.earpieceradar.signal.MetricSample
import com.dogdduddy.earpieceradar.ui.components.ChartBuffer
import com.dogdduddy.earpieceradar.ui.components.ChartSnapshot
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class MonitorUiState(
    val sensorInfo: SensorInfo? = null,
    val current: MetricSample? = null,
    val baseline: Baseline? = null,
    val calibrationProgress: Float? = null,
    val chart: ChartSnapshot = ChartSnapshot.EMPTY,
    val showX: Boolean = true,
    val showY: Boolean = true,
    val showZ: Boolean = true,
    val showMag: Boolean = true,
    val settings: Settings = Settings(),
    val error: String? = null,
)

class MonitorViewModel(
    private val hub: SignalHub,
    private val settingsRepo: SettingsRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(MonitorUiState(sensorInfo = hub.sensorInfo, settings = settingsRepo.settings.value))
    val ui: StateFlow<MonitorUiState> = _ui.asStateFlow()

    private val chartBuffer = ChartBuffer(settingsRepo.settings.value.chartWindowSeconds * 1_000_000_000L)
    @Volatile private var latest: MetricSample? = null
    private var collectJob: Job? = null

    init {
        viewModelScope.launch { hub.baseline.collect { b -> _ui.update { it.copy(baseline = b) } } }
        viewModelScope.launch { hub.calibrationProgress.collect { p -> _ui.update { it.copy(calibrationProgress = p) } } }
        viewModelScope.launch { hub.error.collect { e -> _ui.update { it.copy(error = e) } } }
        viewModelScope.launch {
            settingsRepo.settings.collect { s ->
                chartBuffer.windowNs = s.chartWindowSeconds * 1_000_000_000L
                _ui.update { it.copy(settings = s) }
            }
        }
    }

    /** 화면이 보일 때만 센서를 구독한다 (Lifecycle 에 맞춰 호출). */
    fun start() {
        if (collectJob?.isActive == true) return
        collectJob = viewModelScope.launch {
            launch { hub.metrics.collect { m -> latest = m; chartBuffer.add(m) } }
            // UI 갱신은 샘플 레이트(수백 Hz)와 분리해서 ~20fps 로 제한
            while (isActive) {
                val m = latest
                if (m != null) _ui.update { it.copy(current = m, chart = chartBuffer.snapshot()) }
                delay(50)
            }
        }
    }

    fun stop() { collectJob?.cancel(); collectJob = null }

    fun calibrate() {
        viewModelScope.launch {
            runCatching { hub.calibrate() }
        }
    }

    fun clearBaseline() = hub.clearBaseline()

    fun toggle(axis: Char) = _ui.update {
        when (axis) {
            'x' -> it.copy(showX = !it.showX)
            'y' -> it.copy(showY = !it.showY)
            'z' -> it.copy(showZ = !it.showZ)
            else -> it.copy(showMag = !it.showMag)
        }
    }
}
