package io.github.leychan.tempo.domain

import org.junit.Assert.*
import org.junit.Test

class TimerEngineTest {
    @Test fun `reference plan lasts exactly five minutes three seconds`() {
        val plan = TrainingPlan()
        assertEquals(303L, plan.totalSeconds)
        val phases = plan.phases()
        assertEquals(303, phases.sumOf { it.seconds })
        assertEquals(8, phases.count { it.stage == Stage.WORK })
        assertEquals(6, phases.count { it.stage == Stage.REST })
        assertEquals(1, phases.count { it.stage == Stage.ROUND_REST })
        assertEquals(Stage.WORK, phases.last().stage)
    }

    @Test fun `phase boundaries skip final ordinary rest in both rounds`() {
        var now = 0L
        val timer = TimerEngine(TrainingPlan()) { now }
        timer.start()
        now = 3000
        assertEquals(Stage.WORK, timer.snapshot().phase!!.stage)
        now = 93000
        assertEquals(Stage.ROUND_REST, timer.snapshot().phase!!.stage)
        assertEquals(120L, timer.snapshot().phaseRemainingSeconds)
        now = 213000
        val secondRound = timer.snapshot()
        assertEquals(Stage.WORK, secondRound.phase!!.stage)
        assertEquals(2, secondRound.phase.round)
        assertEquals(1, secondRound.phase.cycle)
        now = 303000
        assertEquals(TimerStatus.COMPLETED, timer.snapshot().status)
        assertEquals(0L, timer.snapshot().totalRemainingMillis)
    }

    @Test fun `pause and resume retain fractional seconds without drift`() {
        var now = 1000L
        val timer = TimerEngine(TrainingPlan()) { now }
        timer.start()
        now += 350
        timer.pause()
        assertEquals(2650L, timer.snapshot().phaseRemainingMillis)
        now += 50000
        assertEquals(2650L, timer.snapshot().phaseRemainingMillis)
        timer.start()
        now += 2650
        assertEquals(Stage.WORK, timer.snapshot().phase!!.stage)
        assertEquals(15000L, timer.snapshot().phaseRemainingMillis)
    }

    @Test fun `zero optional durations and a single cycle add no rests`() {
        val plan = TrainingPlan(prepareSeconds = 0, workSeconds = 1, restSeconds = 0,
            cycles = 1, rounds = 2, roundRestSeconds = 0)
        assertEquals(2L, plan.totalSeconds)
        assertEquals(listOf(Stage.WORK, Stage.WORK), plan.phases().map { it.stage })
    }

    @Test fun `delayed tick catches up across multiple phases`() {
        var now = 0L
        val engine = TimerEngine(TrainingPlan()) { now }
        engine.start()
        now = 44000
        assertEquals(Stage.REST, engine.snapshot().phase!!.stage)
        assertEquals(2, engine.snapshot().phase!!.cycle)
        assertEquals(9L, engine.snapshot().phaseRemainingSeconds)
        now = 999999
        assertEquals(TimerStatus.COMPLETED, engine.snapshot().status)
        assertEquals(303000L, engine.snapshot().elapsedMillis)
    }

    @Test fun `process restoration pauses active sessions and clamps invalid elapsed time`() {
        val timer = TimerEngine(TrainingPlan()) { 0L }
        timer.restore(4300, TimerStatus.RUNNING)
        assertEquals(TimerStatus.PAUSED, timer.snapshot().status)
        assertEquals(13700L, timer.snapshot().phaseRemainingMillis)
        timer.restore(-100, TimerStatus.PAUSED)
        assertEquals(0L, timer.snapshot().elapsedMillis)
        timer.restore(Long.MAX_VALUE, TimerStatus.RUNNING)
        assertEquals(TimerStatus.COMPLETED, timer.snapshot().status)
    }

    @Test fun `reset returns to the initial phase and allows restart`() {
        var now = 0L
        val timer = TimerEngine(TrainingPlan()) { now }
        timer.start()
        now = 10000
        timer.reset()
        assertEquals(TimerStatus.IDLE, timer.snapshot().status)
        assertEquals(303L, timer.snapshot().totalRemainingSeconds)
        assertEquals(3L, timer.snapshot().phaseRemainingSeconds)
        timer.start()
        now += 1000
        assertEquals(2L, timer.snapshot().phaseRemainingSeconds)
    }

    @Test fun `invalid inputs cannot produce a runnable plan`() {
        listOf(TrainingPlan(name = ""), TrainingPlan(workSeconds = 0), TrainingPlan(cycles = 0),
            TrainingPlan(rounds = 100), TrainingPlan(restSeconds = -1),
            TrainingPlan(workSeconds = 3600, cycles = 99, rounds = 99)).forEach {
            assertNotNull(it.validationError())
            assertThrows(IllegalArgumentException::class.java) { it.phases() }
        }
    }

    @Test fun `one round ignores the configured inter-round rest`() {
        val plan = TrainingPlan(prepareSeconds = 0, workSeconds = 20, restSeconds = 10,
            cycles = 8, rounds = 1, roundRestSeconds = 120)
        assertEquals(230L, plan.totalSeconds)
        assertFalse(plan.phases().any { it.stage == Stage.ROUND_REST })
    }

    @Test fun `format durations including hour-long workouts`() {
        assertEquals("05:03", formatSeconds(303))
        assertEquals("00:00", formatSeconds(-1))
        assertEquals("01:00:01", formatSeconds(3601))
    }
}
