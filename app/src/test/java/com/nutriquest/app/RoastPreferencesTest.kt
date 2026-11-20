package com.nutriquest.app

import org.junit.Assert.*
import org.junit.Test

class RoastPreferencesTest {
    @Test
    fun englishPromptUsesSavedGoalsRoleAndTone() {
        val profile = UserProfile(
            goal = DietGoal.ROUTINE,
            goalNotes = "Cook lunch at home",
            role = RoastRole.CAPTAIN,
            tone = RoastTone.SPICY,
            language = RoastLanguage.ENGLISH
        )
        val instructions = roastInstructions(
            RoastScope.DAY,
            listOf(Meal(name = "Lunch", items = listOf(MealItem(foodList.first(), 2)))),
            profile
        )
        assertTrue(instructions.system.contains(profile.role.prompt))
        assertTrue(instructions.system.contains("sharp, exaggerated punchline"))
        assertTrue(instructions.request.contains("Cook lunch at home"))
        assertTrue(instructions.request.contains("Steamed rice × 2"))
        assertTrue(instructions.request.contains("today's diary (1 logged meal)"))
        assertTrue(acceptableLanguage("Keep your plan going.", RoastLanguage.ENGLISH))
        assertFalse(acceptableLanguage("呢餐好有趣，chef's kiss!", RoastLanguage.ENGLISH))
        assertFalse(acceptableLanguage("呢餐好有趣，chef's kiss!", RoastLanguage.CANTONESE))
    }

    @Test
    fun cockyToneUsesAccountabilityInstructionsInEveryLanguage() {
        val meal = Meal(name = "Lunch", items = listOf(MealItem(foodList.first(), 1)))
        for (language in RoastLanguage.entries) {
            for (role in RoastRole.entries) {
                val profile = UserProfile(tone = RoastTone.COCKY, language = language, role = role)
                val instructions = roastInstructions(RoastScope.MEAL, listOf(meal), profile)
                val gentle = roastInstructions(RoastScope.MEAL, listOf(meal), profile.copy(tone = RoastTone.GENTLE))
                if (language == RoastLanguage.ENGLISH) {
                    assertTrue(instructions.system.contains("smug rival"))
                    assertTrue(instructions.system.contains("accountability"))
                } else {
                    assertTrue(instructions.system.contains("飲食目標"))
                    assertTrue(instructions.system.contains("激將法"))
                }
                assertTrue(instructions.request.contains(if (language == RoastLanguage.ENGLISH) "Steamed rice" else "白飯"))
                assertNotEquals(gentle.system, instructions.system)
            }
        }
        assertEquals(" That was your big play?", mixedRoastSignOff(RoastTone.COCKY, RoastScope.DAY))
        assertNotEquals(
            mixedRoastSignOff(RoastTone.GENTLE, RoastScope.MEAL),
            mixedRoastSignOff(RoastTone.COCKY, RoastScope.MEAL)
        )
    }

    @Test
    fun changedPreferencesMakePreviousCommentsStale() {
        val meal = Meal(name = "Lunch", items = listOf(MealItem(foodList.first(), 1)))
        val old = RoastKey(RoastScope.MEAL, listOf(meal.id), UserProfile())
        val changed = old.copy(profile = old.profile.copy(language = RoastLanguage.ENGLISH))
        assertNotEquals(old, changed)
        assertNull(ProfileDraft(UserProfile(), energy = "0").validated())
        assertNull(ProfileDraft(UserProfile(), protein = "invalid").validated())
        assertEquals(1800, ProfileDraft(UserProfile(), energy = "1800").validated()?.energyKcal)
        assertTrue(mentionsUnloggedFood("Rice and oatmeal.", listOf(meal)))
        assertFalse(mentionsUnloggedFood("Rice is the star.", listOf(meal)))
        assertTrue(mentionsLoggedFood("你碗白飯連劇本都懶得改。", listOf(meal)))
        assertFalse(mentionsLoggedFood("Your confidence is doing all the cooking.", listOf(meal)))
        assertFalse(mentionsLoggedFood("Your priceless choices.", listOf(meal)))
        assertTrue(containsRoastDirections("白飯配烤雞？（用語氣評論）"))
        assertFalse(containsRoastDirections("你碗白飯都冇你咁自信。"))
    }
}
