package io.github.leychan.tempo

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.leychan.tempo.data.PlanStore
import io.github.leychan.tempo.data.StoredState
import io.github.leychan.tempo.domain.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class TimerUiState(
    val plans: List<TrainingPlan>,
    val selectedPlan: TrainingPlan,
    val timer: TimerSnapshot,
    val soundEnabled: Boolean,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val backgroundPaused: Boolean = false,
) {
    val hasSession get() = timer.status == TimerStatus.RUNNING || timer.status == TimerStatus.PAUSED
}

enum class Cue { COUNTDOWN, TRANSITION, FINISH }

class TimerViewModel(application: Application) : AndroidViewModel(application) {
    private val store = PlanStore(application)
    private val stored = store.load()
    private var engine = TimerEngine(stored.plans.first { it.id == stored.selectedId }, SystemClock::elapsedRealtime)
        .apply { restore(stored.elapsedMillis, stored.timerStatus) }
    private val mutableState = MutableStateFlow(TimerUiState(
        plans = stored.plans, selectedPlan = engine.plan, timer = engine.snapshot(), soundEnabled = stored.soundEnabled,
        themeMode = stored.themeMode,
        backgroundPaused = stored.timerStatus == TimerStatus.RUNNING || stored.timerStatus == TimerStatus.PAUSED,
    ))
    val state = mutableState.asStateFlow()
    private val cueChannel = Channel<Cue>(Channel.BUFFERED)
    val cues = cueChannel.receiveAsFlow()
    private var lastPersistedSecond = -1L

    init {
        viewModelScope.launch {
            while (isActive) {
                delay(50)
                if (mutableState.value.timer.status == TimerStatus.RUNNING) {
                    val previous = mutableState.value.timer
                    val next = engine.snapshot()
                    mutableState.value = mutableState.value.copy(timer = next)
                    when {
                        next.status == TimerStatus.COMPLETED -> cue(Cue.FINISH)
                        next.phaseIndex != previous.phaseIndex -> cue(Cue.TRANSITION)
                        next.phaseRemainingSeconds in 1..3 &&
                            next.phaseRemainingSeconds != previous.phaseRemainingSeconds -> cue(Cue.COUNTDOWN)
                    }
                    val second = next.elapsedMillis / 1000
                    if (second != lastPersistedSecond) {
                        persist()
                        lastPersistedSecond = second
                    }
                }
            }
        }
    }

    fun toggleTimer() {
        var freshStart = false
        when (state.value.timer.status) {
            TimerStatus.RUNNING -> engine.pause()
            TimerStatus.COMPLETED -> { engine.reset(); engine.start(); freshStart = true }
            TimerStatus.IDLE -> { engine.start(); freshStart = true }
            TimerStatus.PAUSED -> engine.start()
        }
        publish()
        mutableState.value = state.value.copy(backgroundPaused = false)
        if (freshStart) cue(Cue.COUNTDOWN)
        persist()
    }

    fun pauseForBackground() {
        if (state.value.timer.status == TimerStatus.RUNNING) {
            engine.pause()
            publish()
            mutableState.value = state.value.copy(backgroundPaused = state.value.timer.status == TimerStatus.PAUSED)
        }
        persist()
    }

    fun reset() {
        engine.reset()
        publish()
        mutableState.value = state.value.copy(backgroundPaused = false)
        lastPersistedSecond = -1
        persist()
    }

    fun selectPlan(id: String) {
        val plan = state.value.plans.firstOrNull { it.id == id } ?: return
        if (id == state.value.selectedPlan.id) return
        engine = TimerEngine(plan, SystemClock::elapsedRealtime)
        mutableState.value = state.value.copy(selectedPlan = plan, timer = engine.snapshot(), backgroundPaused = false)
        persist()
    }

    fun savePlan(plan: TrainingPlan): Boolean {
        if (plan.validationError() != null) return false
        if (plan.id == state.value.selectedPlan.id && state.value.hasSession) return false
        val exists = state.value.plans.any { it.id == plan.id }
        val plans = if (exists) state.value.plans.map { if (it.id == plan.id) plan else it }
            else state.value.plans + plan
        mutableState.value = state.value.copy(plans = plans)
        if (plan.id == state.value.selectedPlan.id || !state.value.hasSession) {
            engine = TimerEngine(plan, SystemClock::elapsedRealtime)
            mutableState.value = state.value.copy(selectedPlan = plan, timer = engine.snapshot(), backgroundPaused = false)
        }
        persist()
        return true
    }

    fun deletePlan(id: String): Boolean {
        if (state.value.plans.size == 1) return false
        if (id == state.value.selectedPlan.id && state.value.hasSession) return false
        val plans = state.value.plans.filterNot { it.id == id }
        mutableState.value = state.value.copy(plans = plans)
        if (id == state.value.selectedPlan.id) selectPlan(plans.first().id)
        persist()
        return true
    }

    fun toggleSound() {
        mutableState.value = state.value.copy(soundEnabled = !state.value.soundEnabled)
        persist()
    }

    fun setThemeMode(mode: ThemeMode) {
        mutableState.value = state.value.copy(themeMode = mode)
        persist()
    }

    private fun cue(cue: Cue) {
        if (state.value.soundEnabled) cueChannel.trySend(cue)
    }

    private fun publish() { mutableState.value = state.value.copy(timer = engine.snapshot()) }

    private fun persist() {
        val current = state.value
        store.save(StoredState(current.plans, current.selectedPlan.id, current.soundEnabled,
            current.timer.elapsedMillis, current.timer.status, current.themeMode))
    }
}
