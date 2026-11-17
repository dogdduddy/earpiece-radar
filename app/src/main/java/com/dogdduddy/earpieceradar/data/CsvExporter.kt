package com.dogdduddy.earpieceradar.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 트라이얼 요약 CSV / raw 샘플 CSV 를 cache/exports 에 만들고 공유 시트로 내보낸다. */
class CsvExporter(private val context: Context) {
    private val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
    private val stamp get() = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    suspend fun writeSummaryCsv(trials: List<Trial>): File = withContext(Dispatchers.IO) {
        val f = File(exportDir, "trials_summary_$stamp.csv")
        f.bufferedWriter().use { w ->
            w.write(SUMMARY_HEADER); w.newLine()
            trials.forEach { w.write(summaryLine(it)); w.newLine() }
        }
        f
    }

    suspend fun writeRawCsv(trials: List<Trial>, repo: TrialRepository): File = withContext(Dispatchers.IO) {
        val f = File(exportDir, "raw_samples_$stamp.csv")
        f.bufferedWriter().use { w ->
            w.write(TrialRepository.RAW_HEADER); w.newLine()
            trials.forEach { t ->
                val raw = repo.rawFileOf(t)
                if (raw.exists()) {
                    raw.bufferedReader().useLines { lines ->
                        lines.drop(1).forEach { w.write(it); w.newLine() }
                    }
                }
            }
        }
        f
    }

    fun share(file: File, title: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    companion object {
        const val SUMMARY_HEADER =
            "trial_id,created_at,magnet,distance_cm,orientation,placement,note,sensor_type,fresh_baseline," +
                "b0_x,b0_y,b0_z,sigma_ut,sample_count,duration_ms,sample_rate_hz," +
                "mean_delta_ut,max_delta_ut,std_delta_ut,mean_z,ac_rms_ut,mean_mag_ut"

        fun summaryLine(t: Trial): String {
            val c = t.config; val b = t.baseline; val s = t.stats
            val created = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(t.createdAtEpochMs))
            return listOf(
                t.id, created, csv(c.magnetLabel), fmt(c.distanceCm, 2), csv(c.orientation.label), csv(c.placement.label),
                csv(c.note), t.sensorType, c.freshBaseline.toString(),
                fmt(b.b0x, 4), fmt(b.b0y, 4), fmt(b.b0z, 4), fmt(b.sigma, 4),
                s.sampleCount.toString(), fmt(s.durationMs, 1), fmt(s.sampleRateHz, 1),
                fmt(s.meanDelta, 4), fmt(s.maxDelta, 4), fmt(s.stdDelta, 4), fmt(s.meanZ, 3), fmt(s.acRms, 4), fmt(s.meanMagnitude, 3),
            ).joinToString(",")
        }

        private fun fmt(v: Double, digits: Int) = String.format(Locale.US, "%.${digits}f", v)
        private fun csv(s: String): String =
            if (s.any { it == ',' || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
    }
}
