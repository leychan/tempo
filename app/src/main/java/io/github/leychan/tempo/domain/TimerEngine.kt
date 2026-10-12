package io.github.leychan.tempo.domain

enum class TimerStatus { IDLE, RUNNING, PAUSED, COMPLETED }

data class TimerSnapshot(
    val status: TimerStatus,
    val phaseIndex: Int,
    val phase: Phase?,
    val nextPhase: Phase?,
    val phaseRemainingMillis: Long,
    val totalRemainingMillis: Long,
    val elapsedMillis: Long,
    val phaseProgress: Float,
    val totalProgress: Float,
) {
    val phaseRemainingSeconds get() = (phaseRemainingMillis + 999) / 1000
    val totalRemainingSeconds get() = (totalRemainingMillis + 999) / 1000
}

/** A monotonic clock avoids drift, wall-clock changes and accumulated tick error. */
class TimerEngine(val plan: TrainingPlan, private val clock: () -> Long) {
    private val phases = plan.phases()
    private val totalMillis = plan.totalSeconds * 1000
    private var committedElapsed = 0L
    private var startedAt = 0L
    private var status = TimerStatus.IDLE

    fun start() {
        if (status == TimerStatus.RUNNING || status == TimerStatus.COMPLETED) return
        startedAt = clock()
        status = TimerStatus.RUNNING
    }

    fun pause() {
        if (status != TimerStatus.RUNNING) return
        committedElapsed = elapsed()
        status = if (committedElapsed >= totalMillis) TimerStatus.COMPLETED else TimerStatus.PAUSED
    }

    fun reset() {
        status = TimerStatus.IDLE
        committedElapsed = 0
    }

    /** A restored active session is always paused, so relaunch cannot start silently. */
    fun restore(elapsedMillis: Long, savedStatus: TimerStatus) {
        committedElapsed = elapsedMillis.coerceIn(0, totalMillis)
        status = when {
            committedElapsed >= totalMillis -> TimerStatus.COMPLETED
            savedStatus == TimerStatus.IDLE -> TimerStatus.IDLE
            else -> TimerStatus.PAUSED
        }
        if (status == TimerStatus.IDLE) committedElapsed = 0
    }

    private fun elapsed(): Long = (committedElapsed +
        if (status == TimerStatus.RUNNING) (clock() - startedAt).coerceAtLeast(0) else 0)
        .coerceIn(0, totalMillis)

    fun snapshot(): TimerSnapshot {
        val elapsed = elapsed()
        if (elapsed >= totalMillis) {
            status = TimerStatus.COMPLETED
            committedElapsed = totalMillis
            return TimerSnapshot(status, phases.size, null, null, 0, 0, elapsed, 1f, 1f)
        }
        var phaseStart = 0L
        phases.forEachIndexed { index, phase ->
            val duration = phase.seconds * 1000L
            if (elapsed < phaseStart + duration) {
                val within = elapsed - phaseStart
                return TimerSnapshot(
                    status, index, phase, phases.getOrNull(index + 1), duration - within,
                    totalMillis - elapsed, elapsed, within.toFloat() / duration,
                    elapsed.toFloat() / totalMillis,
                )
            }
            phaseStart += duration
        }
        error("Validated plan must contain a work phase")
    }
}
