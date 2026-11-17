package com.dogdduddy.earpieceradar.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.dogdduddy.earpieceradar.signal.MagSample
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * TYPE_MAGNETIC_FIELD_UNCALIBRATED 우선, 없으면 TYPE_MAGNETIC_FIELD 폴백.
 * OS 의 hard-iron 자동 보정이 작은 자석의 변화를 지워버릴 수 있어서 보정 전 값을 쓴다.
 * SENSOR_DELAY_FASTEST + HIGH_SAMPLING_RATE_SENSORS 권한으로 최대 레이트 요청.
 */
class AndroidMagneticFieldSource(context: Context) : MagneticFieldSource {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    override val info: SensorInfo? = sensor?.let {
        val uncal = it.type == Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED
        SensorInfo(
            name = it.name,
            vendor = it.vendor,
            typeLabel = if (uncal) "MAGNETIC_FIELD_UNCALIBRATED" else "MAGNETIC_FIELD",
            isUncalibrated = uncal,
            minDelayUs = it.minDelay,
            resolutionUt = it.resolution,
            maxRangeUt = it.maximumRange,
        )
    }

    override fun samples(): Flow<MagSample> = callbackFlow {
        val s = sensor ?: run { close(IllegalStateException("지자기 센서 없음")); return@callbackFlow }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                // UNCALIBRATED: values[0..2] = 보정 전 값, values[3..5] = 추정 iron bias (무시)
                trySend(MagSample(event.timestamp, event.values[0], event.values[1], event.values[2], event.accuracy))
                    .onFailure { /* 소비자가 느리면 드롭 */ }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        val ok = sensorManager.registerListener(listener, s, SensorManager.SENSOR_DELAY_FASTEST)
        if (!ok) close(IllegalStateException("센서 리스너 등록 실패"))
        awaitClose { sensorManager.unregisterListener(listener) }
    }
}
