package com.nutriquest.app

import java.io.File
import java.time.ZonedDateTime
import java.util.UUID
import kotlin.math.roundToInt

internal data class Food(
    val id: String,
    val name: String,
    val emoji: String,
    val calories: Int,
    val protein: Int,
    val carbs: Int,
    val fat: Int,
    val chineseName: String = name,
    val aliases: Set<String> = emptySet(),
    val referenceGrams: Int? = null,
    val defaultGrams: Int? = referenceGrams,
    val nutritionSource: String = "demo_catalog",
    val sourceUrl: String? = null,
    val referenceDescription: String = "Demo serving",
    val sweetSnack: Boolean = false
)

internal data class MealItem(val food: Food, val servings: Int, val gramsPerServing: Int? = food.defaultGrams) {
    val portionLabel: String get() = gramsPerServing?.let { "$it g" } ?: if (food.nutritionSource == "demo_catalog") "demo serving" else "serving"
    val scale: Double get() = food.referenceGrams?.let { (gramsPerServing ?: it).toDouble() / it } ?: 1.0
}

internal data class Nutrition(
    val energyKcal: Int,
    val proteinGrams: Int,
    val carbohydrateGrams: Int,
    val fatGrams: Int
)

internal fun List<MealItem>.nutrition() = Nutrition(
    energyKcal = sumOf { it.food.calories * it.servings * it.scale }.roundToInt(),
    proteinGrams = sumOf { it.food.protein * it.servings * it.scale }.roundToInt(),
    carbohydrateGrams = sumOf { it.food.carbs * it.servings * it.scale }.roundToInt(),
    fatGrams = sumOf { it.food.fat * it.servings * it.scale }.roundToInt()
)

internal data class MealScore(val points: Int, val maxPoints: Int, val rulesVersion: String) {
    init {
        require(maxPoints > 0 && points in 0..maxPoints)
        require(rulesVersion.isNotBlank())
    }
}

internal enum class MealSource(val wireValue: String) {
    MANUAL("manual"),
    CAMERA("camera"),
    ALBUM("album")
}

internal data class Meal(
    val id: UUID = UUID.randomUUID(),
    val loggedAt: ZonedDateTime = ZonedDateTime.now(),
    val name: String,
    val items: List<MealItem>,
    val photo: File? = null,
    val source: MealSource = MealSource.MANUAL,
    val score: MealScore? = null
) {
    val nutrition = items.nutrition()
    val calories get() = nutrition.energyKcal
    val protein get() = nutrition.proteinGrams
    val carbs get() = nutrition.carbohydrateGrams
    val fat get() = nutrition.fatGrams
}
