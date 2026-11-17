package com.dogdduddy.earpieceradar.ui.simple

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dogdduddy.earpieceradar.core.SignalHub
import com.dogdduddy.earpieceradar.data.Profile
import com.dogdduddy.earpieceradar.data.ProfileRepository
import com.dogdduddy.earpieceradar.data.SettingsRepository
import com.dogdduddy.earpieceradar.data.TuningDefaults
import com.dogdduddy.earpieceradar.signal.Baseline
import com.dogdduddy.earpieceradar.signal.DetectionLevel
import com.dogdduddy.earpieceradar.signal.DetectionMode
import com.dogdduddy.earpieceradar.signal.MetricSample
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class SimpleUiState(
    val sensorAvailable: Boolean = true,
    val baseline: Baseline? = null,
    val calibrationProgress: Float? = null,
    val current: MetricSample? = null,
    /** 최근 3초 동안의 최고 레벨/최대 Δ (스캔 중 놓치지 않도록) */
    val recentLevel: DetectionLevel = DetectionLevel.NONE,
    val recentMaxDelta: Double = 0.0,
    val profiles: List<Profile> = emptyList(),
    val active: Profile = Profile.DEFAULT,
    val calibrationSeconds: Int = 3,
)

class SimpleViewModel(
    private val hub: SignalHub,
    private val settingsRepo: SettingsRepository,
    private val profileRepo: ProfileRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(SimpleUiState(sensorAvailable = hub.sensorInfo != null, profiles = profileRepo.profiles.value, active = profileRepo.active.value))
    val ui: StateFlow<SimpleUiState> = _ui.asStateFlow()
    private var job: Job? = null
    @Volatile private var latest: MetricSample? = null
    private val recent = ArrayDeque<MetricSample>()

    init {
        viewModelScope.launch { hub.baseline.collect { b -> _ui.update { it.copy(baseline = b) } } }
        viewModelScope.launch { hub.calibrationProgress.collect { p -> _ui.update { it.copy(calibrationProgress = p) } } }
        viewModelScope.launch { profileRepo.profiles.collect { p -> _ui.update { it.copy(profiles = p) } } }
        viewModelScope.launch { profileRepo.active.collect { p -> _ui.update { it.copy(active = p) } } }
        viewModelScope.launch { settingsRepo.settings.collect { s -> _ui.update { it.copy(calibrationSeconds = s.calibrationSeconds) } } }
        // 시작 시 활성 프로필에 저장된 영점이 있고 현재 영점이 없으면 적용
        if (hub.baseline.value == null) profileRepo.active.value.savedBaseline?.let { hub.setBaseline(it.toBaseline(profileRepo.active.value.savedBaselineAtEpochMs)) }
    }

    fun start() {
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            launch {
                hub.metrics.collect { m ->
                    latest = m
                    synchronized(recent) {
                        recent.addLast(m)
                        val cutoff = m.sample.timestampNs - 3_000_000_000L
                        while (recent.isNotEmpty() && recent.first().sample.timestampNs < cutoff) recent.removeFirst()
                    }
                }
            }
            while (isActive) {
                val (lvl, maxD) = synchronized(recent) {
                    (recent.maxOfOrNull { it.level.ordinal }?.let { DetectionLevel.entries[it] } ?: DetectionLevel.NONE) to
                        (recent.maxOfOrNull { it.deltaUsed } ?: 0.0)
                }
                _ui.update { it.copy(current = latest, recentLevel = lvl, recentMaxDelta = maxD) }
                delay(60)
            }
        }
    }

    fun stop() { job?.cancel(); job = null }

    fun calibrate() = viewModelScope.launch { runCatching { hub.calibrate() } }

    fun selectProfile(id: String) {
        profileRepo.select(id)
        profileRepo.active.value.savedBaseline?.let { hub.setBaseline(it.toBaseline(profileRepo.active.value.savedBaselineAtEpochMs)) }
    }
    fun createProfile(name: String) { profileRepo.create(name) }
    fun renameProfile(name: String) = profileRepo.rename(profileRepo.active.value.id, name)
    fun deleteProfile() = profileRepo.delete(profileRepo.active.value.id)
    fun saveBaselineToProfile() { hub.baseline.value?.let { profileRepo.saveBaseline(profileRepo.active.value.id, it) } }
    fun clearProfileBaseline() = profileRepo.clearBaseline(profileRepo.active.value.id)
    fun applyPreset(p: TuningDefaults.Preset) = profileRepo.updateActive { it.copy(tuning = p.apply().copy(mode = it.tuning.mode, debounceSamples = it.tuning.debounceSamples)) }
    fun setMode(m: DetectionMode) = profileRepo.updateActive { it.copy(tuning = it.tuning.copy(mode = m)) }
    fun resetTuning() = profileRepo.resetTuning()
    fun enableDeveloperMode() = settingsRepo.setDeveloperMode(true)
}
