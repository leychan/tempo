package io.github.leychan.tempo.data

import android.content.Context
import io.github.leychan.tempo.domain.TimerStatus
import io.github.leychan.tempo.domain.ThemeMode
import io.github.leychan.tempo.domain.TrainingPlan
import org.json.JSONArray
import org.json.JSONObject

data class StoredState(
    val plans: List<TrainingPlan>,
    val selectedId: String,
    val soundEnabled: Boolean = true,
    val elapsedMillis: Long = 0,
    val timerStatus: TimerStatus = TimerStatus.IDLE,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
)

class PlanStore(context: Context) {
    private val preferences = context.getSharedPreferences("tempo", Context.MODE_PRIVATE)

    fun load(): StoredState {
        val fallback = defaults()
        return runCatching {
            val raw = preferences.getString("state", null) ?: return fallback
            val root = JSONObject(raw)
            val array = root.getJSONArray("plans")
            val plans = (0 until array.length()).mapNotNull { index ->
                runCatching { array.getJSONObject(index).toPlan() }
                    .getOrNull()?.takeIf { it.validationError() == null }
            }.distinctBy { it.id }.ifEmpty { fallback.plans }
            val storedId = root.optString("selectedId")
            val selectedId = plans.firstOrNull { it.id == storedId }?.id ?: plans.first().id
            val session = root.optJSONObject("session")
            val matchesPlan = session?.optString("planId") == selectedId
            StoredState(
                plans, selectedId, root.optBoolean("soundEnabled", true),
                if (matchesPlan) session!!.optLong("elapsedMillis", 0) else 0,
                if (matchesPlan) runCatching {
                    TimerStatus.valueOf(session!!.optString("status", "IDLE"))
                }.getOrDefault(TimerStatus.IDLE) else TimerStatus.IDLE,
                runCatching { ThemeMode.valueOf(root.optString("themeMode", "SYSTEM")) }
                    .getOrDefault(ThemeMode.SYSTEM),
            )
        }.getOrDefault(fallback)
    }

    fun save(state: StoredState) {
        val root = JSONObject().apply {
            put("version", 2)
            put("plans", JSONArray().apply { state.plans.forEach { put(it.toJson()) } })
            put("selectedId", state.selectedId)
            put("soundEnabled", state.soundEnabled)
            put("themeMode", state.themeMode.name)
            put("session", JSONObject().apply {
                put("planId", state.selectedId)
                put("elapsedMillis", state.elapsedMillis)
                put("status", state.timerStatus.name)
            })
        }
        preferences.edit().putString("state", root.toString()).apply()
    }

    private fun defaults(): StoredState {
        val plans = listOf(
            TrainingPlan(id = "starter", name = "轻量间歇"),
            TrainingPlan(id = "tabata", name = "经典 Tabata", prepareSeconds = 10,
                workSeconds = 20, restSeconds = 10, cycles = 8, rounds = 1,
                roundRestSeconds = 0),
        )
        return StoredState(plans, plans.first().id)
    }

    private fun TrainingPlan.toJson() = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("prepare", prepareSeconds)
        put("work", workSeconds)
        put("rest", restSeconds)
        put("cycles", cycles)
        put("rounds", rounds)
        put("roundRest", roundRestSeconds)
    }

    private fun JSONObject.toPlan() = TrainingPlan(
        id = getString("id"), name = getString("name"), prepareSeconds = getInt("prepare"),
        workSeconds = getInt("work"), restSeconds = getInt("rest"), cycles = getInt("cycles"),
        rounds = getInt("rounds"), roundRestSeconds = getInt("roundRest"),
    )
}
