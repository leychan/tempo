package io.github.leychan.tempo.domain

import java.util.UUID

data class TrainingPlan(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "间歇训练",
    val prepareSeconds: Int = 3,
    val workSeconds: Int = 15,
    val restSeconds: Int = 10,
    val cycles: Int = 4,
    val rounds: Int = 2,
    val roundRestSeconds: Int = 120,
) {
    val totalSeconds: Long
        get() = prepareSeconds.toLong() + rounds.toLong() *
            (cycles.toLong() * workSeconds + (cycles - 1L) * restSeconds) +
            (rounds - 1L) * roundRestSeconds

    fun validationError(): String? = when {
        name.isBlank() -> "请输入计划名称"
        name.length > 30 -> "计划名称最多 30 个字"
        prepareSeconds !in 0..3600 -> "准备时间须在 0～3600 秒之间"
        workSeconds !in 1..3600 -> "锻炼时间须在 1～3600 秒之间"
        restSeconds !in 0..3600 -> "休息时间须在 0～3600 秒之间"
        cycles !in 1..99 -> "周期数须在 1～99 之间"
        rounds !in 1..99 -> "循环数须在 1～99 之间"
        roundRestSeconds !in 0..3600 -> "循环间休息须在 0～3600 秒之间"
        totalSeconds > 86400 -> "单次训练总时长不能超过 24 小时"
        else -> null
    }

    fun phases(): List<Phase> {
        require(validationError() == null) { validationError().orEmpty() }
        return buildList {
            if (prepareSeconds > 0) add(Phase(Stage.PREPARE, prepareSeconds, 1, 1))
            for (round in 1..rounds) {
                for (cycle in 1..cycles) {
                    add(Phase(Stage.WORK, workSeconds, cycle, round))
                    if (cycle < cycles && restSeconds > 0) {
                        add(Phase(Stage.REST, restSeconds, cycle, round))
                    }
                }
                if (round < rounds && roundRestSeconds > 0) {
                    add(Phase(Stage.ROUND_REST, roundRestSeconds, cycles, round))
                }
            }
        }
    }
}

enum class Stage(val title: String, val instruction: String) {
    PREPARE("准备", "调整呼吸，准备开始"),
    WORK("锻炼", "保持节奏，专注当下"),
    REST("休息", "放松一下，恢复呼吸"),
    ROUND_REST("循环间休息", "补充水分，为下一组蓄力"),
}

data class Phase(val stage: Stage, val seconds: Int, val cycle: Int, val round: Int)

fun formatSeconds(seconds: Long): String {
    val value = seconds.coerceAtLeast(0)
    val minutes = (value / 60) % 60
    return if (value >= 3600) {
        "%02d:%02d:%02d".format(java.util.Locale.ROOT, value / 3600, minutes, value % 60)
    } else {
        "%02d:%02d".format(java.util.Locale.ROOT, value / 60, value % 60)
    }
}
