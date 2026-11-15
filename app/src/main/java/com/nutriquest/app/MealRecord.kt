package com.nutriquest.app

import org.json.JSONArray
import org.json.JSONObject

/**
 * Version 1 of the meal payload for a future authenticated service.
 * The meal ID is stable across exports so a receiver can upsert it.
 * loggedOn is the date in the logging time zone, which lets the service
 * group records by the same day the user sees in the diary.
 */
internal fun Meal.toRecordJson(): JSONObject = JSONObject().apply {
    put("schemaVersion", 1)
    put("mealId", id.toString())
    put("loggedAt", loggedAt.toInstant().toString())
    put("loggedOn", loggedAt.toLocalDate().toString())
    put("timeZone", loggedAt.zone.id)
    put("name", name)
    put("source", source.wireValue)
    put("nutritionSource", items.map { it.food.nutritionSource }.distinct().singleOrNull() ?: "mixed")
    put("nutrition", nutrition.toJson())
    put("items", JSONArray().apply {
        items.forEach { item ->
            put(JSONObject().apply {
                put("foodId", item.food.id)
                put("name", item.food.name)
                put("servings", item.servings)
                put("gramsPerServing", item.gramsPerServing ?: JSONObject.NULL)
                put("nutritionSource", item.food.nutritionSource)
                put("sourceUrl", item.food.sourceUrl ?: JSONObject.NULL)
                put("referenceDescription", item.food.referenceDescription)
                put("referenceGrams", item.food.referenceGrams ?: JSONObject.NULL)
                put("nutritionPerServing", listOf(item.copy(servings = 1)).nutrition().toJson())
            })
        }
    })
    put("score", score?.let {
        JSONObject()
            .put("points", it.points)
            .put("maxPoints", it.maxPoints)
            .put("rulesVersion", it.rulesVersion)
    } ?: JSONObject.NULL)
}

private fun Nutrition.toJson() = JSONObject()
    .put("energyKcal", energyKcal)
    .put("proteinGrams", proteinGrams)
    .put("carbohydrateGrams", carbohydrateGrams)
    .put("fatGrams", fatGrams)
