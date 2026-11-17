package com.dogdduddy.earpieceradar

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.dogdduddy.earpieceradar.ui.RadarApp
import com.dogdduddy.earpieceradar.ui.theme.EarpieceRadarTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 측정 중 화면이 꺼지지 않게 한다 (앱이 포그라운드인 동안 항상).
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // 진동 모터의 자석이 센서에 간섭하므로 햅틱을 끈다. 앱은 Vibrator 를 쓰지 않는다.
        window.decorView.isHapticFeedbackEnabled = false

        setContent {
            EarpieceRadarTheme {
                CompositionLocalProvider(LocalHapticFeedback provides NoHaptics) {
                    RadarApp(appContainer)
                }
            }
        }
    }
}

/** Compose 컴포넌트가 요청하는 햅틱을 모두 무시한다. */
private object NoHaptics : HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) = Unit
}
