package io.github.leychan.tempo

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.content.res.Configuration
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.viewModels
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.leychan.tempo.domain.TimerStatus
import io.github.leychan.tempo.domain.ThemeMode
import io.github.leychan.tempo.data.PlanStore
import io.github.leychan.tempo.ui.TempoApp
import io.github.leychan.tempo.ui.TempoTheme
import io.github.leychan.tempo.ui.LocalTempoColors

class MainActivity : ComponentActivity() {
    private val timerViewModel: TimerViewModel by viewModels()
    private var tone: ToneGenerator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        val mode = PlanStore(this).load().themeMode
        val systemDark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        setTheme(if (mode == ThemeMode.DARK || mode == ThemeMode.SYSTEM && systemDark)
            R.style.Theme_Tempo_Dark else R.style.Theme_Tempo_Light)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        tone = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 70) }.getOrNull()
        setContent {
            val state by timerViewModel.state.collectAsStateWithLifecycle()
            LaunchedEffect(timerViewModel) {
                timerViewModel.cues.collect { cue ->
                    when (cue) {
                        Cue.COUNTDOWN -> tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 100)
                        Cue.TRANSITION -> tone?.startTone(ToneGenerator.TONE_PROP_ACK, 180)
                        Cue.FINISH -> tone?.startTone(ToneGenerator.TONE_PROP_BEEP2, 500)
                    }
                }
            }
            DisposableEffect(state.timer.status) {
                if (state.timer.status == TimerStatus.RUNNING) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            }
            TempoTheme(state.themeMode) {
                val dark = LocalTempoColors.current.isDark
                LaunchedEffect(dark) {
                    val transparent = android.graphics.Color.TRANSPARENT
                    enableEdgeToEdge(
                        statusBarStyle = if (dark) SystemBarStyle.dark(transparent)
                            else SystemBarStyle.light(transparent, transparent),
                        navigationBarStyle = if (dark) SystemBarStyle.dark(transparent)
                            else SystemBarStyle.light(transparent, android.graphics.Color.rgb(27, 43, 54)),
                    )
                }
                TempoApp(state, timerViewModel)
            }
        }
    }

    override fun onStop() {
        if (!isChangingConfigurations) timerViewModel.pauseForBackground()
        super.onStop()
    }

    override fun onDestroy() {
        tone?.release()
        tone = null
        super.onDestroy()
    }
}
