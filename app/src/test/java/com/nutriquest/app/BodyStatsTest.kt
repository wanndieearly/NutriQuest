package com.nutriquest.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** F1 / F2: body stats, Mifflin-St Jeor and goal presets. */
class BodyStatsTest {
    private val joe = BodyStats(age = 22, sex = Sex.MALE, heightCm = 175, weightKg = 70, activity = ActivityLevel.MODERATE)

    @Test fun bmrFollowsMifflinStJeor() {
        // 10×70 + 6.25×175 − 5×22 + 5 = 1688.75
        assertEquals(1688.75, joe.bmr(), 0.001)
        assertEquals(1688.75 - 166, joe.copy(sex = Sex.FEMALE).bmr(), 0.001)
        assertEquals(1688.75 - 83, joe.copy(sex = Sex.UNSPECIFIED).bmr(), 0.001)
    }

    @Test fun tdeeMultipliesByActivity() {
        assertEquals(1688.75 * 1.55, joe.tdee(), 0.001)
        assertEquals(1688.75 * 1.2, joe.copy(activity = ActivityLevel.SEDENTARY).tdee(), 0.001)
    }

    @Test fun balancedGoalKeepsMaintenanceEnergy() {
        val t = suggestTargets(joe, DietGoal.BALANCE)
        assertEquals(2620, t.energyKcal)               // 2617.6 rounded to 10
        assertEquals(84, t.proteinGrams)               // 1.2 g × 70 kg
        assertEquals(87, t.fatGrams)                   // 30% of 2620 ÷ 9
        assertEquals((2620 - 84 * 4 - 87 * 9 + 2) / 4, t.carbohydrateGrams)
        assertTrue(t.explanation.startsWith("BMR 1,689 × 1.55 (Moderate) = 2,618 kcal maintenance"))
    }

    @Test fun muscleGoalAddsEnergyAndProtein() {
        val t = suggestTargets(joe, DietGoal.MUSCLE)
        assertEquals(2880, t.energyKcal)               // 2617.6 × 1.10
        assertEquals(126, t.proteinGrams)              // 1.8 g × 70 kg
        assertEquals(80, t.fatGrams)                   // 25%
    }

    @Test fun weightManagementIsAModerateDeficit() {
        val t = suggestTargets(joe, DietGoal.WEIGHT_MANAGEMENT)
        assertEquals(2220, t.energyKcal)               // 2617.6 × 0.85
        assertEquals(112, t.proteinGrams)              // 1.6 g × 70 kg
    }

    @Test fun weightManagementNeverGoesBelowBmrOr1200() {
        val small = BodyStats(age = 60, sex = Sex.FEMALE, heightCm = 150, weightKg = 45, activity = ActivityLevel.SEDENTARY)
        val t = suggestTargets(small, DietGoal.WEIGHT_MANAGEMENT)
        assertEquals(LOWEST_SUGGESTED_KCAL, t.energyKcal)   // 0.85 × TDEE would be ≈ 1060
        assertTrue(t.explanation.contains("safe minimum"))
    }

    @Test fun macrosAddUpToTheEnergyTarget() {
        DietGoal.entries.forEach { goal ->
            val t = suggestTargets(joe, goal)
            val kcal = t.proteinGrams * 4 + t.carbohydrateGrams * 4 + t.fatGrams * 9
            assertEquals("$goal", t.energyKcal.toDouble(), kcal.toDouble(), 12.0)
        }
    }

    @Test fun draftValidatesRealisticRanges() {
        assertEquals(joe, BodyStatsDraft("22", Sex.MALE, "175", "70", ActivityLevel.MODERATE).validated())
        assertNull(BodyStatsDraft("", Sex.MALE, "175", "70").validated())
        assertNull(BodyStatsDraft("22", Sex.MALE, "17", "70").validated())     // height 17 cm
        assertNull(BodyStatsDraft("22", Sex.MALE, "175", "700").validated())   // weight 700 kg
        assertEquals("Height should be 100–250 cm.", BodyStatsDraft("22", Sex.MALE, "17", "70").problem)
        assertNull(BodyStatsDraft(joe).problem)
    }

    @Test fun setupResultFillsTheProfile() {
        val targets = suggestTargets(joe, DietGoal.MUSCLE)
        val profile = SetupResult("  Joe ", joe, DietGoal.MUSCLE, targets, RoastTone.SPICY, RoastRole.GYM_BUDDY)
            .applyTo(UserProfile(language = RoastLanguage.ENGLISH))
        assertEquals("Joe", profile.name)
        assertEquals(DietGoal.MUSCLE, profile.goal)
        assertEquals(targets.energyKcal, profile.energyKcal)
        assertEquals(targets.proteinGrams, profile.proteinGrams)
        assertEquals(RoastTone.SPICY, profile.tone)
        assertEquals(RoastRole.GYM_BUDDY, profile.role)
        assertEquals(RoastLanguage.ENGLISH, profile.language)   // untouched settings are kept
        assertNotNull(profile)
    }
}
