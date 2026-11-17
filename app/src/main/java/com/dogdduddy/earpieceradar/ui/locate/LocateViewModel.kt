package com.dogdduddy.earpieceradar.ui.locate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dogdduddy.earpieceradar.core.SignalHub
import com.dogdduddy.earpieceradar.data.SettingsRepository
import com.dogdduddy.earpieceradar.signal.Baseline
import com.dogdduddy.earpieceradar.signal.MetricSample
import com.dogdduddy.earpieceradar.signal.Vec3
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class LocateUiState(
    val baseline: Baseline? = null,
    val calibrationProgress: Float? = null,
    val current: MetricSample? = null,
    val maxDelta: Double = 0.0,
    /** 최대 Δ 가 나왔을 때의 축별 변화량(어느 축이 지배적인지) */
    val maxDeltaVec: Vec3 = Vec3.ZERO,
    val maxAtMs: Long = 0L,
    val recentPeak: Double = 0.0,  // 최근 2초 최대(움직이면서 피드백용)
)

class LocateViewModel(private val hub: SignalHub, private val settingsRepo: SettingsRepository) : ViewModel() {
    private val _ui = MutableStateFlow(LocateUiState(baseline = hub.baseline.value))
    val ui: StateFlow<LocateUiState> = _ui.asStateFlow()
    private var job: Job? = null
    @Volatile private var latest: MetricSample? = null
    private var maxDelta = 0.0
    private var maxVec = Vec3.ZERO
    private var maxAt = 0L
    private val recent = ArrayDeque<Pair<Long, Double>>()

    init {
        viewModelScope.launch { hub.baseline.collect { b -> _ui.update { it.copy(baseline = b) } } }
        viewModelScope.launch { hub.calibrationProgress.collect { p -> _ui.update { it.copy(calibrationProgress = p) } } }
    }

    fun start() {
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            launch {
                hub.metrics.collect { m ->
                    latest = m
                    val b = hub.baseline.value ?: return@collect
                    if (m.delta > maxDelta) {
                        maxDelta = m.delta
                        maxVec = Vec3.of(m.sample) - b.b0
                        maxAt = System.currentTimeMillis()
                    }
                    synchronized(recent) {
                        recent.addLast(m.sample.timestampNs to m.delta)
                        val cutoff = m.sample.timestampNs - 2_000_000_000L
                        while (recent.isNotEmpty() && recent.first().first < cutoff) recent.removeFirst()
                    }
                }
            }
            while (isActive) {
                val peak = synchronized(recent) { recent.maxOfOrNull { it.second } ?: 0.0 }
                _ui.update { it.copy(current = latest, maxDelta = maxDelta, maxDeltaVec = maxVec, maxAtMs = maxAt, recentPeak = peak) }
                delay(50)
            }
        }
    }

    fun stop() { job?.cancel(); job = null }

    fun resetMax() { maxDelta = 0.0; maxVec = Vec3.ZERO; maxAt = 0L; _ui.update { it.copy(maxDelta = 0.0, maxDeltaVec = Vec3.ZERO) } }

    fun calibrate() = viewModelScope.launch { runCatching { hub.calibrate() }; resetMax() }
}
