package com.dogdduddy.earpieceradar.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dogdduddy.earpieceradar.core.SignalHub
import com.dogdduddy.earpieceradar.data.Profile
import com.dogdduddy.earpieceradar.data.ProfileRepository
import com.dogdduddy.earpieceradar.data.Settings
import com.dogdduddy.earpieceradar.data.SettingsRepository
import com.dogdduddy.earpieceradar.data.Tuning
import com.dogdduddy.earpieceradar.data.TuningDefaults
import com.dogdduddy.earpieceradar.signal.Baseline
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: Settings = Settings(),
    val profiles: List<Profile> = emptyList(),
    val active: Profile = Profile.DEFAULT,
    val baseline: Baseline? = null,
)

class SettingsViewModel(
    private val settingsRepo: SettingsRepository,
    private val profileRepo: ProfileRepository,
    private val hub: SignalHub,
) : ViewModel() {
    private val _ui = MutableStateFlow(SettingsUiState(settingsRepo.settings.value, profileRepo.profiles.value, profileRepo.active.value))
    val ui: StateFlow<SettingsUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch { settingsRepo.settings.collect { s -> _ui.update { it.copy(settings = s) } } }
        viewModelScope.launch { profileRepo.profiles.collect { p -> _ui.update { it.copy(profiles = p) } } }
        viewModelScope.launch { profileRepo.active.collect { p -> _ui.update { it.copy(active = p) } } }
        viewModelScope.launch { hub.baseline.collect { b -> _ui.update { it.copy(baseline = b) } } }
    }

    fun updateSettings(transform: (Settings) -> Settings) = settingsRepo.update(transform)
    fun setDeveloperMode(on: Boolean) = settingsRepo.setDeveloperMode(on)
    fun resetTimings() = settingsRepo.resetDefaults()

    fun updateTuning(transform: (Tuning) -> Tuning) = profileRepo.updateActive { it.copy(tuning = transform(it.tuning)) }
    fun applyPreset(p: TuningDefaults.Preset) = profileRepo.updateActive { it.copy(tuning = p.apply().copy(mode = it.tuning.mode, debounceSamples = it.tuning.debounceSamples)) }
    fun resetTuning() = profileRepo.resetTuning()

    fun selectProfile(id: String) {
        profileRepo.select(id)
        profileRepo.active.value.savedBaseline?.let { hub.setBaseline(it.toBaseline(profileRepo.active.value.savedBaselineAtEpochMs)) }
    }
    fun createProfile(name: String) { profileRepo.create(name) }
    fun renameProfile(name: String) = profileRepo.rename(profileRepo.active.value.id, name)
    fun deleteProfile() = profileRepo.delete(profileRepo.active.value.id)
    fun saveBaselineToProfile() { hub.baseline.value?.let { profileRepo.saveBaseline(profileRepo.active.value.id, it) } }
    fun clearProfileBaseline() = profileRepo.clearBaseline(profileRepo.active.value.id)
}
