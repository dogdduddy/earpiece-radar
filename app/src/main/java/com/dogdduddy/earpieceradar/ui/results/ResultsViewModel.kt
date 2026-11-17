package com.dogdduddy.earpieceradar.ui.results

import androidx.lifecycle.ViewModel
import com.dogdduddy.earpieceradar.data.CsvExporter
import com.dogdduddy.earpieceradar.data.TrialRepository

class ResultsViewModel(private val repo: TrialRepository, private val exporter: CsvExporter) : ViewModel()
