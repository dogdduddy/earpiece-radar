package com.dogdduddy.earpieceradar.ui.results

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dogdduddy.earpieceradar.data.CsvExporter
import com.dogdduddy.earpieceradar.data.Trial
import com.dogdduddy.earpieceradar.data.TrialRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 자석 종류별 거리 vs 평균Δ 묶음 (같은 거리 반복은 평균) */
data class DistanceSeries(val magnetLabel: String, val points: List<Point>) {
    data class Point(val distanceCm: Double, val meanDelta: Double, val n: Int, val minDelta: Double, val maxDelta: Double)
}

data class ResultsUiState(
    val trials: List<Trial> = emptyList(),
    val series: List<DistanceSeries> = emptyList(),
    val logY: Boolean = true,
    val logX: Boolean = false,
    val showCubicRef: Boolean = true,
    val message: String? = null,
    val busy: Boolean = false,
)

class ResultsViewModel(private val repo: TrialRepository, private val exporter: CsvExporter) : ViewModel() {
    private val _ui = MutableStateFlow(ResultsUiState())
    val ui: StateFlow<ResultsUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            repo.trials.collect { list -> _ui.update { it.copy(trials = list.sortedByDescending { t -> t.createdAtEpochMs }, series = buildSeries(list)) } }
        }
    }

    fun toggleLogY() = _ui.update { it.copy(logY = !it.logY) }
    fun toggleLogX() = _ui.update { it.copy(logX = !it.logX) }
    fun toggleCubic() = _ui.update { it.copy(showCubicRef = !it.showCubicRef) }
    fun clearMessage() = _ui.update { it.copy(message = null) }

    fun delete(id: String) = viewModelScope.launch { repo.delete(id) }
    fun deleteAll() = viewModelScope.launch { repo.deleteAll() }

    fun exportSummary() = viewModelScope.launch {
        runExport("요약 CSV") { exporter.writeSummaryCsv(repo.trials.value) }
    }

    fun exportRaw() = viewModelScope.launch {
        runExport("raw 샘플 CSV") { exporter.writeRawCsv(repo.trials.value, repo) }
    }

    private suspend fun runExport(title: String, make: suspend () -> java.io.File) {
        if (repo.trials.value.isEmpty()) { _ui.update { it.copy(message = "내보낼 트라이얼이 없습니다") }; return }
        _ui.update { it.copy(busy = true) }
        try {
            val f = make()
            exporter.share(f, "Earpiece Radar $title")
        } catch (e: Exception) {
            _ui.update { it.copy(message = "내보내기 실패: ${e.message}") }
        } finally {
            _ui.update { it.copy(busy = false) }
        }
    }

    companion object {
        fun buildSeries(trials: List<Trial>): List<DistanceSeries> =
            trials.groupBy { it.config.magnetLabel }
                .map { (label, ts) ->
                    val pts = ts.groupBy { it.config.distanceCm }
                        .map { (d, group) ->
                            val deltas = group.map { it.stats.meanDelta }
                            DistanceSeries.Point(d, deltas.average(), group.size, deltas.min(), deltas.max())
                        }
                        .sortedBy { it.distanceCm }
                    DistanceSeries(label, pts)
                }
                .sortedBy { it.magnetLabel }
    }
}
