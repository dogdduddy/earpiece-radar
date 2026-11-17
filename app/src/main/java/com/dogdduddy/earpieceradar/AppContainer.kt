package com.dogdduddy.earpieceradar

import android.app.Application
import android.content.Context
import com.dogdduddy.earpieceradar.core.SignalHub
import com.dogdduddy.earpieceradar.data.CsvExporter
import com.dogdduddy.earpieceradar.data.SettingsRepository
import com.dogdduddy.earpieceradar.data.TrialRepository
import com.dogdduddy.earpieceradar.sensor.AndroidMagneticFieldSource
import com.dogdduddy.earpieceradar.sensor.MagneticFieldSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** 수동 DI 컨테이너. Application 수명 동안 단일 인스턴스. */
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settingsRepository = SettingsRepository(context)
    val sensorSource: MagneticFieldSource = AndroidMagneticFieldSource(context)
    val signalHub = SignalHub(sensorSource, settingsRepository, appScope)
    val trialRepository = TrialRepository(context, appScope)
    val csvExporter = CsvExporter(context)
}

class RadarApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as RadarApp).container
