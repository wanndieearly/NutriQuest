package com.nutriquest.app

import org.junit.Assert.*
import org.junit.Test

class FoodAndGoalTest {
    @Test
    fun everyCocoFoodMapsToNutritionAndUnknownLabelsRemainUnknown() {
        val labels = listOf("banana", "apple", "sandwich", "orange", "broccoli", "carrot", "hot dog", "pizza", "donut", "cake")
        for (label in labels) assertNotNull("Missing $label", catalogFood(label))
        assertEquals("pizza", catalogFood("  cheese_pizza  ")?.id)
        assertEquals("hot_dog", catalogFood("HOT-DOG")?.id)
        assertNull(catalogFood("chair"))
        assertNull(catalogFood("unrecognised pastry"))
    }

    @Test
    fun pizzaUsesEnteredWeightAndMacrosScaleBeforeRounding() {
        val pizza = requireNotNull(catalogFood("pizza"))
        val portion = MealItem(pizza, 2, 150)
        assertEquals(Nutrition(798, 33, 99, 30), listOf(portion).nutrition())
        val macarons = MealItem(requireNotNull(catalogFood("macaron")), 2)
        assertEquals(129, listOf(macarons).nutrition().energyKcal)
        assertEquals("15 g", macarons.portionLabel)
    }

    @Test
    fun manualNutritionNeedsExplicitValuesAndSupportsServingWithoutWeight() {
        val unknown = Food("custom", "Mystery pastry", "", 0, 0, 0, 0, referenceDescription = "Needs nutrition")
        val empty = FoodNutritionDraft.from(MealItem(unknown, 1))
        assertNull(empty.validated())
        val entered = empty.copy(energy = "200", protein = "3", carbs = "25", fat = "9")
        assertEquals("user_entered", entered.validated()?.nutritionSource)
        assertNull(entered.copy(grams = "0").validated())
        assertNull(entered.copy(fat = "-1").validated())
        assertEquals(50, entered.copy(grams = "50").validated()?.referenceGrams)
        assertEquals(3, entered.copy(protein = "2.7").validated()?.protein)
        assertNull(entered.copy(fat = "NaN").validated())
        assertTrue(entered.copy(sweetSnack = true).validated()?.sweetSnack == true)
    }

    @Test
    fun feedbackUsesGoalAndDayContextInsteadOfDeclaringAllDessertsFailures() {
        val snack = Meal(name = "Afternoon tea", items = listOf(MealItem(requireNotNull(catalogFood("macaron")), 3)))
        val fewerSweets = UserProfile(goal = DietGoal.FEWER_SWEETS, tone = RoastTone.COCKY, language = RoastLanguage.ENGLISH)
        assertEquals(GoalSignal.SWEET_SNACK_REMINDER, goalFeedback(listOf(snack), listOf(snack), fewerSweets).signal)
        val weightPlan = fewerSweets.copy(goal = DietGoal.WEIGHT_MANAGEMENT)
        assertEquals(GoalSignal.PLANNED_TREAT, goalFeedback(listOf(snack), listOf(snack), weightPlan).signal)
        val earlier = Meal(name = "Earlier meal", items = listOf(MealItem(foodList.first(), 9)))
        assertEquals(GoalSignal.OVER_ENTERED_TARGET, goalFeedback(listOf(snack), listOf(earlier, snack), weightPlan).signal)
        val instructions = roastInstructions(RoastScope.MEAL, listOf(snack), weightPlan, listOf(earlier, snack))
        assertTrue(instructions.request.contains("over your entered energy target"))
        assertTrue(instructions.system.contains("A treat alone is not failure"))
        val key = RoastKey(RoastScope.MEAL, listOf(snack.id), weightPlan)
        assertNotEquals(key, key.copy(contextMealIds = listOf(earlier.id, snack.id)))
    }

    @Test
    fun proteinProgressAndCustomFoodsRemainSupported() {
        val meal = Meal(name = "Lunch", items = listOf(MealItem(foodList[1], 1)))
        assertEquals(GoalSignal.PROTEIN_PROGRESS, goalFeedback(listOf(meal), listOf(meal), UserProfile(goal = DietGoal.MUSCLE)).signal)
        val custom = Food("custom", "Matcha bun", "", 200, 3, 30, 5)
        val customMeal = Meal(name = "Snack", items = listOf(MealItem(custom, 1)))
        assertTrue(mentionsLoggedFood("Matcha bun 都要照實記份量呀。", listOf(customMeal)))
        assertTrue(acceptableLanguage("Matcha bun 都要照實記份量呀。", RoastLanguage.CANTONESE, listOf(customMeal)))
        assertTrue(mentionsUnloggedFood("Pizza and macarons.", listOf(meal)))
    }
}
