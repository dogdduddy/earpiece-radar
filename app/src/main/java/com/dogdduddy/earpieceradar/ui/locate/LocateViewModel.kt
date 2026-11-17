package com.dogdduddy.earpieceradar.ui.locate

import androidx.lifecycle.ViewModel
import com.dogdduddy.earpieceradar.core.SignalHub
import com.dogdduddy.earpieceradar.data.SettingsRepository

class LocateViewModel(private val hub: SignalHub, private val settingsRepo: SettingsRepository) : ViewModel()
