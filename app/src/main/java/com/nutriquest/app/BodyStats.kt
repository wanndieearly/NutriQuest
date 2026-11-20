package com.nutriquest.app

// ============================================================================
//  F1 / F2 — Body stats and target presets (Joe)
//
//  BodyStats      age, sex, height, weight, activity level (F1)
//  suggestTargets daily kcal / protein / carbs / fat for a DietGoal (F2)
//
//  Energy uses the Mifflin-St Jeor equation:
//      BMR  = 10 × kg + 6.25 × cm − 5 × age + s     (s = +5 male, −161 female, −78 not stated)
//      TDEE = BMR × activity factor
//  Each goal then adjusts calories and sets protein per kg and fat as a share
//  of energy; carbs fill the rest. These are estimates for a student app, not
//  medical advice, so weight-management targets never go below BMR or 1200 kcal.
// ============================================================================

import android.content.Context
import org.json.JSONObject
import kotlin.math.roundToInt

internal enum class Sex(val label: String, val bmrOffset: Int) {
    MALE("Male", 5),
    FEMALE("Female", -161),
    UNSPECIFIED("Prefer not to say", -78)   // midpoint of the two
}

internal enum class ActivityLevel(val label: String, val detail: String, val factor: Double) {
    SEDENTARY("Mostly sitting", "Desk or study most of the day", 1.2),
    LIGHT("Light", "Walking around, exercise 1–3 days a week", 1.375),
    MODERATE("Moderate", "Exercise 3–5 days a week", 1.55),
    ACTIVE("Very active", "Hard exercise 6–7 days a week", 1.725),
    ATHLETE("Athlete", "Training twice a day or a physical job", 1.9)
}

internal data class BodyStats(
    val age: Int,
    val sex: Sex,
    val heightCm: Int,
    val weightKg: Int,
    val activity: ActivityLevel
) {
    /** Basal metabolic rate (kcal/day), Mifflin-St Jeor. */
    fun bmr(): Double = 10.0 * weightKg + 6.25 * heightCm - 5.0 * age + sex.bmrOffset

    /** Total daily energy expenditure (kcal/day): BMR × activity factor. */
    fun tdee(): Double = bmr() * activity.factor

    fun toJson(): JSONObject = JSONObject()
        .put("schemaVersion", 1)
        .put("age", age)
        .put("sex", sex.name)
        .put("heightCm", heightCm)
        .put("weightKg", weightKg)
        .put("activity", activity.name)

    companion object {
        val AGE_RANGE = 13..100
        val HEIGHT_RANGE = 100..250
        val WEIGHT_RANGE = 25..300

        fun fromJson(json: JSONObject): BodyStats? = BodyStatsDraft(
            age = json.optString("age"),
            sex = Sex.entries.find { it.name == json.optString("sex") } ?: Sex.UNSPECIFIED,
            heightCm = json.optString("heightCm"),
            weightKg = json.optString("weightKg"),
            activity = ActivityLevel.entries.find { it.name == json.optString("activity") } ?: ActivityLevel.LIGHT
        ).validated()
    }
}

/** Text-field version of [BodyStats], so half-typed numbers never crash anything. */
internal data class BodyStatsDraft(
    val age: String = "",
    val sex: Sex = Sex.UNSPECIFIED,
    val heightCm: String = "",
    val weightKg: String = "",
    val activity: ActivityLevel = ActivityLevel.LIGHT
) {
    constructor(stats: BodyStats) : this(
        stats.age.toString(), stats.sex, stats.heightCm.toString(), stats.weightKg.toString(), stats.activity
    )

    /** Null if any number is missing or outside a realistic range. */
    fun validated(): BodyStats? {
        fun parse(text: String, range: IntRange) = text.trim().toIntOrNull()?.takeIf { it in range }
        return BodyStats(
            age = parse(age, BodyStats.AGE_RANGE) ?: return null,
            sex = sex,
            heightCm = parse(heightCm, BodyStats.HEIGHT_RANGE) ?: return null,
            weightKg = parse(weightKg, BodyStats.WEIGHT_RANGE) ?: return null,
            activity = activity
        )
    }

    val problem: String?
        get() = when {
            age.trim().toIntOrNull() !in BodyStats.AGE_RANGE -> "Age should be ${BodyStats.AGE_RANGE.first}–${BodyStats.AGE_RANGE.last}."
            heightCm.trim().toIntOrNull() !in BodyStats.HEIGHT_RANGE -> "Height should be ${BodyStats.HEIGHT_RANGE.first}–${BodyStats.HEIGHT_RANGE.last} cm."
            weightKg.trim().toIntOrNull() !in BodyStats.WEIGHT_RANGE -> "Weight should be ${BodyStats.WEIGHT_RANGE.first}–${BodyStats.WEIGHT_RANGE.last} kg."
            else -> null
        }
}

// ---------------------------------------------------------------------------
// F2 presets
// ---------------------------------------------------------------------------

/** How a diet goal turns maintenance energy into targets. */
private data class GoalPlan(val calorieFactor: Double, val proteinPerKg: Double, val fatShare: Double, val note: String)

private fun planFor(goal: DietGoal): GoalPlan = when (goal) {
    DietGoal.WEIGHT_MANAGEMENT -> GoalPlan(0.85, 1.6, 0.30, "15% below maintenance, higher protein to stay full")
    DietGoal.MUSCLE -> GoalPlan(1.10, 1.8, 0.25, "10% above maintenance, 1.8 g protein per kg")
    DietGoal.FEWER_SWEETS -> GoalPlan(1.00, 1.2, 0.30, "maintenance energy; the roasts watch the sweet snacks")
    DietGoal.BALANCE, DietGoal.ROUTINE, DietGoal.CUSTOM -> GoalPlan(1.00, 1.2, 0.30, "maintenance energy, balanced split")
}

internal data class SuggestedTargets(
    val energyKcal: Int,
    val proteinGrams: Int,
    val carbohydrateGrams: Int,
    val fatGrams: Int,
    /** e.g. "BMR 1,689 × 1.55 (Moderate) = 2,618 kcal maintenance · 15% below …" */
    val explanation: String
)

internal const val LOWEST_SUGGESTED_KCAL = 1200

internal fun suggestTargets(stats: BodyStats, goal: DietGoal): SuggestedTargets {
    val plan = planFor(goal)
    val bmr = stats.bmr()
    val maintenance = stats.tdee()
    var kcal = maintenance * plan.calorieFactor
    val floored = plan.calorieFactor < 1.0 && kcal < maxOf(bmr, LOWEST_SUGGESTED_KCAL.toDouble())
    if (floored) kcal = maxOf(bmr, LOWEST_SUGGESTED_KCAL.toDouble())
    val energy = (kcal / 10).roundToInt() * 10

    val protein = (stats.weightKg * plan.proteinPerKg).roundToInt()
    val fat = (energy * plan.fatShare / 9).roundToInt()
    val carbs = ((energy - protein * 4 - fat * 9) / 4.0).roundToInt().coerceAtLeast(50)

    val explanation = buildString {
        append("BMR ${"%,d".format(bmr.roundToInt())} × ${stats.activity.factor} (${stats.activity.label}) ")
        append("= ${"%,d".format(maintenance.roundToInt())} kcal maintenance · ${plan.note}")
        if (floored) append(" · kept at a safe minimum of ${"%,d".format(energy)} kcal")
    }
    return SuggestedTargets(energy, protein, carbs, fat, explanation)
}

// ---------------------------------------------------------------------------
// Storage
// ---------------------------------------------------------------------------

internal class BodyStatsStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("nutriquest_body", Context.MODE_PRIVATE)

    fun load(): BodyStats? = runCatching {
        preferences.getString("stats", null)?.let { BodyStats.fromJson(JSONObject(it)) }
    }.getOrNull()

    fun save(stats: BodyStats) {
        if (!preferences.edit().putString("stats", stats.toJson().toString()).commit()) {
            throw java.io.IOException("Your body stats couldn’t be saved. Try again.")
        }
    }
}
