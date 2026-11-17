package com.dogdduddy.earpieceradar.ui.settings

import androidx.lifecycle.ViewModel
import com.dogdduddy.earpieceradar.data.Settings
import com.dogdduddy.earpieceradar.data.SettingsRepository
import kotlinx.coroutines.flow.StateFlow

class SettingsViewModel(private val repo: SettingsRepository) : ViewModel() {
    val settings: StateFlow<Settings> = repo.settings
    fun update(transform: (Settings) -> Settings) = repo.update(transform)
    fun reset() = repo.resetDefaults()
}
