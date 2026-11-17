package com.dogdduddy.earpieceradar.data

import android.content.Context
import com.dogdduddy.earpieceradar.signal.Baseline
import com.dogdduddy.earpieceradar.signal.DetectionMode
import com.dogdduddy.earpieceradar.signal.MagSample
import com.dogdduddy.earpieceradar.signal.Metrics
import com.dogdduddy.earpieceradar.signal.TrialStats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Locale
import java.util.UUID

/**
 * 내부 파일 저장소.
 *  - files/trials/trials.json : 트라이얼 요약 목록(JSON 배열)
 *  - files/trials/raw/<id>.csv : 트라이얼별 raw 샘플
 * Room 보다 단순하고, 내보내기가 파일 이어붙이기만으로 끝난다.
 */
class TrialRepository(context: Context, private val scope: CoroutineScope) {
    private val dir = File(context.filesDir, "trials").apply { mkdirs() }
    private val rawDir = File(dir, "raw").apply { mkdirs() }
    private val indexFile = File(dir, "trials.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    private val mutex = Mutex()

    private val _trials = MutableStateFlow<List<Trial>>(emptyList())
    val trials: StateFlow<List<Trial>> = _trials.asStateFlow()

    init {
        scope.launch { _trials.value = loadIndex() }
    }

    private fun loadIndex(): List<Trial> = try {
        if (indexFile.exists()) json.decodeFromString<List<Trial>>(indexFile.readText()) else emptyList()
    } catch (e: Exception) { emptyList() }

    private fun saveIndex(list: List<Trial>) {
        val tmp = File(dir, "trials.json.tmp")
        tmp.writeText(json.encodeToString(list))
        tmp.renameTo(indexFile)
    }

    /** 녹화 결과 저장. raw CSV 와 요약을 함께 기록하고 Trial 을 돌려준다. */
    suspend fun save(
        config: TrialConfig,
        sensorType: String,
        profileName: String,
        mode: DetectionMode,
        baseline: Baseline,
        samples: List<MagSample>,
        stats: TrialStats,
    ): Trial = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString().take(8)
        val rawName = "$id.csv"
        writeRawCsv(File(rawDir, rawName), id, baseline, samples)
        val trial = Trial(
            id = id,
            createdAtEpochMs = System.currentTimeMillis(),
            config = config,
            sensorType = sensorType,
            profileName = profileName,
            mode = mode,
            baseline = BaselineDto.from(baseline),
            stats = StatsDto.from(stats),
            rawFile = rawName,
        )
        mutex.withLock {
            val next = _trials.value + trial
            saveIndex(next)
            _trials.value = next
        }
        trial
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val t = _trials.value.find { it.id == id } ?: return@withLock
            File(rawDir, t.rawFile).delete()
            val next = _trials.value - t
            saveIndex(next)
            _trials.value = next
        }
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        mutex.withLock {
            rawDir.listFiles()?.forEach { it.delete() }
            saveIndex(emptyList())
            _trials.value = emptyList()
        }
    }

    fun rawFileOf(trial: Trial): File = File(rawDir, trial.rawFile)

    private fun writeRawCsv(file: File, trialId: String, baseline: Baseline, samples: List<MagSample>) {
        file.bufferedWriter().use { w ->
            w.write(RAW_HEADER); w.newLine()
            val t0 = samples.firstOrNull()?.timestampNs ?: 0L
            samples.forEachIndexed { i, s ->
                val d = Metrics.delta(s, baseline)
                val z = Metrics.zScore(d, baseline.sigma)
                w.write(rawLine(trialId, i, s, t0, d, z, Metrics.deltaMagnitude(s, baseline))); w.newLine()
            }
        }
    }

    companion object {
        const val RAW_HEADER =
            "trial_id,idx,timestamp_ns,t_rel_ms,x_ut,y_ut,z_ut,mag_ut,accuracy,delta_ut,z_score," +
                "bias_x,bias_y,bias_z,cal_mag_ut,delta_mag_ut"

        fun rawLine(trialId: String, i: Int, s: MagSample, t0: Long, delta: Double, z: Double, deltaMag: Double = 0.0): String =
            String.format(
                Locale.US, "%s,%d,%d,%.3f,%.4f,%.4f,%.4f,%.4f,%d,%.4f,%.3f,%.3f,%.3f,%.3f,%.4f,%.4f",
                trialId, i, s.timestampNs, (s.timestampNs - t0) / 1e6,
                s.x, s.y, s.z, s.magnitude, s.accuracy, delta, z,
                s.bx, s.by, s.bz, s.calMagnitude, deltaMag,
            )
    }
}
