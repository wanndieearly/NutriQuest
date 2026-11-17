package com.nutriquest.app

import kotlin.math.roundToInt

internal data class FoodNutritionDraft(
    val food: Food, val name: String, val energy: String, val protein: String,
    val carbs: String, val fat: String, val grams: String, val sweetSnack: Boolean = food.sweetSnack
) {
    fun validated(): Food? {
        if (name.isBlank() || name.length > 60) return null
        val kcal = energy.toDoubleOrNull()?.takeIf { it.isFinite() && it in 0.0..10000.0 }?.roundToInt() ?: return null
        val macros = listOf(protein, carbs, fat).map {
            it.toDoubleOrNull()?.takeIf { value -> value.isFinite() && value in 0.0..1000.0 }?.roundToInt() ?: return null
        }
        val weight = if (grams.isBlank()) null else grams.toIntOrNull()?.takeIf { it in 1..2000 } ?: return null
        return food.copy(
            name = name.trim(), chineseName = if (name.trim() == food.name) food.chineseName else name.trim(),
            calories = kcal, protein = macros[0], carbs = macros[1], fat = macros[2],
            referenceGrams = weight, defaultGrams = weight, nutritionSource = "user_entered",
            sourceUrl = null, referenceDescription = "Your nutrition label / entry", sweetSnack = sweetSnack
        )
    }

    companion object {
        fun from(item: MealItem): FoodNutritionDraft {
            val nutrition = listOf(item.copy(servings = 1)).nutrition()
            val custom = item.food.name.isBlank() || item.food.referenceDescription == "Needs nutrition"
            return FoodNutritionDraft(item.food, item.food.name,
                if (custom) "" else nutrition.energyKcal.toString(),
                if (custom) "" else nutrition.proteinGrams.toString(),
                if (custom) "" else nutrition.carbohydrateGrams.toString(),
                if (custom) "" else nutrition.fatGrams.toString(), item.gramsPerServing?.toString().orEmpty())
        }
    }
}
