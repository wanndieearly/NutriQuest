package com.nutriquest.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** F7 score engine tests. Run with the green ▶ in Android Studio or `./gradlew test`. */
class DailyScoreTest {
    private val targets = ScoreTargets(energyKcal = 2000, proteinGrams = 100, carbohydrateGrams = 250, fatGrams = 65)
    private val hk = ZoneId.of("Asia/Hong_Kong")
    private val day = LocalDate.of(2026, 11, 10)
    private val today = LocalDate.of(2026, 11, 16)

    /** A food whose nutrition is "per serving" (no gram reference), to make the maths easy. */
    private fun food(kcal: Int, protein: Int, carbs: Int, fat: Int, sweet: Boolean = false, id: String = "f$kcal$protein$carbs$fat") =
        Food(id = id, name = id, emoji = "🍽️", calories = kcal, protein = protein, carbs = carbs, fat = fat, sweetSnack = sweet)

    private fun meal(vararg foods: Food, date: LocalDate = day, hour: Int = 12) = Meal(
        loggedAt = ZonedDateTime.of(date.atTime(hour, 0), hk),
        name = "Test meal",
        items = foods.map { MealItem(it, 1) }
    )

    // ----- S(x, t) -----

    @Test fun closenessFollowsTheProposalCurve() {
        assertEquals(100, closenessScore(100.0, 100.0))   // exact
        assertEquals(100, closenessScore(110.0, 100.0))   // 10% off still full marks
        assertEquals(50, closenessScore(130.0, 100.0))    // 30% off → 250 × 0.20
        assertEquals(0, closenessScore(150.0, 100.0))     // 50% off → 0
        assertEquals(0, closenessScore(400.0, 100.0))
    }

    @Test fun closenessIsSymmetric() {
        assertEquals(closenessScore(70.0, 100.0), closenessScore(130.0, 100.0))
        assertEquals(closenessScore(55.0, 100.0), closenessScore(145.0, 100.0))
    }

    @Test fun zeroTargetScoresZero() = assertEquals(0, closenessScore(50.0, 0.0))

    // ----- Meal fit -----

    @Test fun mealWithTheSameMacroMixAsTheTargetsFitsPerfectly() {
        // Exactly the same protein/carb/fat mix as the daily targets (÷ 2.5)
        assertEquals(100, mealFitScore(listOf(MealItem(food(800, 40, 100, 26), 1)), targets))
    }

    @Test fun sweetSnackCostsTwentyPoints() {
        assertEquals(80, mealFitScore(listOf(MealItem(food(800, 40, 100, 26, sweet = true), 1)), targets))
    }

    @Test fun pureSugarMealFitsBadly() {
        // All energy from carbs: share gap ≈ 0.50 → 100 × (1 − 0.50/0.6) ≈ 17
        assertEquals(17, mealFitScore(listOf(MealItem(food(400, 0, 100, 0), 1)), targets))
    }

    @Test fun mealWithNoEnergyScoresZero() {
        assertEquals(0, mealFitScore(listOf(MealItem(food(0, 0, 0, 0), 1)), targets))
    }

    // ----- Daily score -----

    @Test fun hittingEveryTargetScoresOneHundred() {
        val result = dailyScore(day, listOf(meal(food(2000, 100, 250, 65))), targets, today)!!
        assertEquals(100, result.score)
        assertEquals(100, result.calorieScore)
        assertEquals(100, result.macroScore)
        assertEquals(100, result.mealFitAverage)
        assertEquals("Every target hit. Perfect day!", result.explanation)
    }

    @Test fun noMealsMeansNoScore() = assertNull(dailyScore(day, emptyList(), targets, today))

    @Test fun eatingHalfOrOneAndAHalfTimesTheTargetsScoresTheSame() {
        val half = dailyScore(day, listOf(meal(food(1000, 50, 125, 33))), targets, today)!!
        val oneAndHalf = dailyScore(day, listOf(meal(food(3000, 150, 375, 98))), targets, today)!!
        // Calories and macros are all ≥50% off → 0; the meal mix still matches → 0.20 × 100
        assertEquals(20, half.score)
        assertEquals(half.score, oneAndHalf.score)
    }

    @Test fun usesTheProposalWeights() {
        // kcal 1700 (15% under → 88), protein 70 (30% under → 50), carbs 250 (100), fat 65 (100)
        val result = dailyScore(day, listOf(meal(food(1700, 70, 250, 65))), targets, today)!!
        assertEquals(88, result.calorieScore)
        assertEquals(50, result.proteinScore)
        assertEquals(83, result.macroScore)          // (50 + 100 + 100) / 3
        val expected = Math.round(0.45 * 88 + 0.35 * (250.0 / 3) + 0.20 * result.mealFitAverage).toInt()
        assertEquals(expected, result.score)
        assertTrue(result.explanation.contains("Protein"))
    }

    @Test fun onlyCountsMealsFromThatDay() {
        val meals = listOf(
            meal(food(2000, 100, 250, 65)),
            meal(food(5000, 10, 900, 200), date = day.minusDays(1))
        )
        val result = dailyScore(day, meals, targets, today)!!
        assertEquals(1, result.mealCount)
        assertEquals(100, result.score)
    }

    @Test fun todayIsProvisionalAndPastDaysAreFinal() {
        assertTrue(dailyScore(day, listOf(meal(food(500, 20, 60, 15))), targets, today)!!.isFinal)
        assertFalse(dailyScore(today, listOf(meal(food(500, 20, 60, 15), date = today)), targets, today)!!.isFinal)
    }

    @Test fun mealScoreIsStoredOutOfOneHundredWithTheRulesVersion() {
        val score = mealScore(listOf(MealItem(food(800, 40, 100, 26), 1)), targets)
        assertEquals(100, score.points)
        assertEquals(100, score.maxPoints)
        assertEquals(SCORE_RULES_VERSION, score.rulesVersion)
    }
}
