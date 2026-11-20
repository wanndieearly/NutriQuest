package com.nutriquest.app

// ============================================================================
//  F7 — Meal and daily scores (Joe)
//
//  Plain Kotlin with no Android code, so it can be unit-tested on the Mac
//  (see app/src/test/.../DailyScoreTest.kt). Implements the proposal's formula:
//
//      Daily Score = 0.45·S_cal + 0.35·S_macro + 0.20·S̄_meal
//
//  S(x, t) — how close a recorded value x is to its target t:
//      d = |x − t| / t
//      d ≤ 10%        → 100
//      10% < d < 50%  → 250 × (0.50 − d)   (falls from 100 to 0)
//      d ≥ 50%        → 0
//  Symmetric: eating far too little scores as badly as eating far too much.
//
//  S_cal   = S(day's kcal, kcal target)
//  S_macro = average of S(protein), S(carbs), S(fat) against their targets
//  S̄_meal  = average Meal Fit Score of the day's meals
//
//  Meal Fit Score (0–100) — does this meal's mix of protein / carbs / fat
//  match the mix in the user's own targets? It looks at the share of energy
//  from each macro, so a small snack and a big dinner are judged fairly.
//      share gap = half the sum of |meal share − target share| (0 = same mix)
//      fit = 100 × (1 − gap / 0.6), then −20 if the meal has a sweet snack,
//      kept between 0 and 100.
//
//  Every number comes from this formula, not from the AI, so a ranking is
//  reproducible and explainable.
// ============================================================================

import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

/** Version tag stored with each meal score, so old scores can be recognised if the rules change. */
internal const val SCORE_RULES_VERSION = "nq-score-v1"

/** The four daily targets the score compares against (from the user's profile). */
internal data class ScoreTargets(
    val energyKcal: Int,
    val proteinGrams: Int,
    val carbohydrateGrams: Int,
    val fatGrams: Int
)

/** Builds score targets from the profile's saved daily targets. */
internal fun UserProfile.scoreTargets() = ScoreTargets(energyKcal, proteinGrams, carbohydrateGrams, fatGrams)

internal data class DailyScoreResult(
    val date: LocalDate,
    /** The headline score, 0–100 */
    val score: Int,
    val calorieScore: Int,
    val macroScore: Int,
    val proteinScore: Int,
    val carbohydrateScore: Int,
    val fatScore: Int,
    val mealFitAverage: Int,
    val mealCount: Int,
    val nutrition: Nutrition,
    /** True once the day is over; until then the score changes as meals are added */
    val isFinal: Boolean,
    /** One short line explaining the biggest thing pulling the score down */
    val explanation: String
)

// ---------------------------------------------------------------------------
// Building blocks
// ---------------------------------------------------------------------------

/** S(x, t): closeness of [actual] to [target], 0–100. */
internal fun closenessScore(actual: Double, target: Double): Int {
    if (target <= 0.0) return 0
    val d = abs(actual - target) / target
    return when {
        d <= 0.10 -> 100
        d >= 0.50 -> 0
        else -> (250.0 * (0.50 - d)).roundToInt().coerceIn(0, 100)
    }
}

/** Share of energy from protein, carbs and fat (each 0–1, summing to 1), or null if there is no energy. */
private fun energyShares(proteinGrams: Double, carbohydrateGrams: Double, fatGrams: Double): Triple<Double, Double, Double>? {
    val p = proteinGrams * 4
    val c = carbohydrateGrams * 4
    val f = fatGrams * 9
    val total = p + c + f
    if (total <= 0.0) return null
    return Triple(p / total, c / total, f / total)
}

/** Meal Fit Score (0–100) for one meal against the user's targets. */
internal fun mealFitScore(items: List<MealItem>, targets: ScoreTargets): Int {
    val n = items.nutrition()
    val meal = energyShares(n.proteinGrams.toDouble(), n.carbohydrateGrams.toDouble(), n.fatGrams.toDouble()) ?: return 0
    val goal = energyShares(targets.proteinGrams.toDouble(), targets.carbohydrateGrams.toDouble(), targets.fatGrams.toDouble())
        ?: return 0
    val gap = (abs(meal.first - goal.first) + abs(meal.second - goal.second) + abs(meal.third - goal.third)) / 2
    var fit = 100.0 * (1.0 - gap / 0.6)
    if (items.any { it.food.sweetSnack && it.servings > 0 }) fit -= 20.0
    return fit.roundToInt().coerceIn(0, 100)
}

/** The MealScore stored on a Meal when it's saved. */
internal fun mealScore(items: List<MealItem>, targets: ScoreTargets) =
    MealScore(points = mealFitScore(items, targets), maxPoints = 100, rulesVersion = SCORE_RULES_VERSION)

// ---------------------------------------------------------------------------
// Daily score
// ---------------------------------------------------------------------------

/**
 * Daily score for [date] from the meals logged that day, or null if there are none.
 * Meal fit is always recalculated against the current [targets] so the whole
 * day is judged on one set of goals.
 */
internal fun dailyScore(
    date: LocalDate,
    allMeals: List<Meal>,
    targets: ScoreTargets,
    today: LocalDate = LocalDate.now()
): DailyScoreResult? {
    val meals = allMeals.filter { it.loggedAt.toLocalDate() == date }
    if (meals.isEmpty()) return null
    val n = meals.flatMap { it.items }.nutrition()

    val cal = closenessScore(n.energyKcal.toDouble(), targets.energyKcal.toDouble())
    val protein = closenessScore(n.proteinGrams.toDouble(), targets.proteinGrams.toDouble())
    val carbs = closenessScore(n.carbohydrateGrams.toDouble(), targets.carbohydrateGrams.toDouble())
    val fat = closenessScore(n.fatGrams.toDouble(), targets.fatGrams.toDouble())
    val macro = (protein + carbs + fat) / 3.0
    val fit = meals.map { mealFitScore(it.items, targets) }.average()

    val score = (0.45 * cal + 0.35 * macro + 0.20 * fit).roundToInt().coerceIn(0, 100)
    return DailyScoreResult(
        date = date,
        score = score,
        calorieScore = cal,
        macroScore = macro.roundToInt(),
        proteinScore = protein,
        carbohydrateScore = carbs,
        fatScore = fat,
        mealFitAverage = fit.roundToInt(),
        mealCount = meals.size,
        nutrition = n,
        isFinal = date.isBefore(today),
        explanation = explain(n, targets, cal, protein, carbs, fat, fit.roundToInt())
    )
}

/** Picks the part with the lowest score and describes it in one line. */
private fun explain(n: Nutrition, t: ScoreTargets, cal: Int, protein: Int, carbs: Int, fat: Int, fit: Int): String {
    fun gap(actual: Int, target: Int, unit: String, label: String): String {
        val pct = if (target > 0) ((actual - target) * 100.0 / target).roundToInt() else 0
        val direction = if (pct >= 0) "over" else "under"
        return "$label: $actual / $target $unit (${abs(pct)}% $direction)"
    }
    val parts = listOf(
        cal to gap(n.energyKcal, t.energyKcal, "kcal", "Calories"),
        protein to gap(n.proteinGrams, t.proteinGrams, "g", "Protein"),
        carbs to gap(n.carbohydrateGrams, t.carbohydrateGrams, "g", "Carbs"),
        fat to gap(n.fatGrams, t.fatGrams, "g", "Fat"),
        fit to "Meal balance: protein / carb / fat mix is $fit/100 against your plan"
    )
    val worst = parts.minBy { it.first }
    return if (worst.first >= 100) "Every target hit. Perfect day!" else "Biggest gap · ${worst.second}"
}
