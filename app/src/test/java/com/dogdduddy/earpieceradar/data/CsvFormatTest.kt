package com.dogdduddy.earpieceradar.data

import com.dogdduddy.earpieceradar.signal.MagSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvFormatTest {
    private val trial = Trial(
        id = "abc12345", createdAtEpochMs = 0L,
        config = TrialConfig(magnet = MagnetType.D3X1, distanceCm = 3.0, orientation = Orientation.N_FRONT, placement = Placement.DESK, note = "hello, \"world\""),
        sensorType = "MAGNETIC_FIELD_UNCALIBRATED",
        baseline = BaselineDto(1.0, 2.0, 3.0, 0.1234, 600),
        stats = StatsDto(1000, 4995.0, 200.1, 4.5, 5.1, 0.2, 36.4, 0.17, 50.0),
        rawFile = "abc12345.csv",
    )

    @Test
    fun `summary line has same column count as header and escapes commas`() {
        val header = CsvExporter.SUMMARY_HEADER.split(",")
        val line = CsvExporter.summaryLine(trial)
        // 따옴표 안 콤마는 분리하지 않고 세기
        val cols = splitCsv(line)
        assertEquals(header.size, cols.size)
        assertEquals("abc12345", cols[0])
        assertEquals("D3×1mm", cols[2])
        assertEquals("3.00", cols[3])
        assertEquals("hello, \"world\"", cols[6])
        assertEquals("0.1234", cols[12])
    }

    @Test
    fun `raw line matches header columns`() {
        val header = TrialRepository.RAW_HEADER.split(",")
        val s = MagSample(2_000_000_000L, 1.5f, -2.5f, 3.5f, 3)
        val line = TrialRepository.rawLine("abc", 7, s, 1_000_000_000L, 4.0, 10.0)
        val cols = line.split(",")
        assertEquals(header.size, cols.size)
        assertEquals("1000.000", cols[3])      // t_rel_ms
        assertEquals("1.5000", cols[4])
        assertEquals("4.0000", cols[9])
        assertEquals("10.000", cols[10])
    }

    @Test
    fun `trial config other label falls back`() {
        assertEquals("기타", TrialConfig(magnet = MagnetType.OTHER).magnetLabel)
        assertEquals("ring", TrialConfig(magnet = MagnetType.OTHER, magnetOther = "ring").magnetLabel)
        assertTrue(TrialConfig().magnetLabel.startsWith("D3"))
    }

    private fun splitCsv(line: String): List<String> {
        val out = ArrayList<String>(); val sb = StringBuilder(); var q = false; var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                q && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> { sb.append('"'); i++ }
                c == '"' -> q = !q
                c == ',' && !q -> { out.add(sb.toString()); sb.clear() }
                else -> sb.append(c)
            }
            i++
        }
        out.add(sb.toString()); return out
    }
}
