package com.dogdduddy.earpieceradar.ui.record

import androidx.lifecycle.ViewModel
import com.dogdduddy.earpieceradar.core.SignalHub
import com.dogdduddy.earpieceradar.data.SettingsRepository
import com.dogdduddy.earpieceradar.data.TrialRepository

class RecordViewModel(
    private val hub: SignalHub,
    private val settingsRepo: SettingsRepository,
    private val trialRepo: TrialRepository,
) : ViewModel()
