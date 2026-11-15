package com.nutriquest.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.ZonedDateTime

@RunWith(AndroidJUnit4::class)
class MealRecordTest {
    @Test
    fun exportsWeightedPizzaAndItsReferenceSeparatelyFromManualEntries() {
        val pizza = requireNotNull(catalogFood("pizza"))
        val meal = Meal(name = "Pizza lunch", items = listOf(MealItem(pizza, 2, 150)))
        val record = meal.toRecordJson()
        val item = record.getJSONArray("items").getJSONObject(0)
        assertEquals(798, record.getJSONObject("nutrition").getInt("energyKcal"))
        assertEquals(399, item.getJSONObject("nutritionPerServing").getInt("energyKcal"))
        assertEquals(150, item.getInt("gramsPerServing"))
        assertEquals(100, item.getInt("referenceGrams"))
        assertEquals("usda_sr27_21299", item.getString("nutritionSource"))
        assertTrue(item.getString("sourceUrl").startsWith("https://www.ars.usda.gov/"))
    }

    @Test
    fun exportsStableIdentityLocalDayAndNullableScore() {
        val meal = Meal(
            loggedAt = ZonedDateTime.parse("2026-11-11T00:15:00+08:00[Asia/Hong_Kong]"),
            name = "Campus lunch",
            items = listOf(MealItem(foodList.first(), 2)),
            photo = File("private-photo.jpg"),
            source = MealSource.CAMERA
        )
        val record = meal.toRecordJson()
        assertEquals(1, record.getInt("schemaVersion"))
        assertEquals(meal.id.toString(), record.getString("mealId"))
        assertEquals("2026-11-10T16:15:00Z", record.getString("loggedAt"))
        assertEquals("2026-11-11", record.getString("loggedOn"))
        assertEquals("Asia/Hong_Kong", record.getString("timeZone"))
        assertEquals("camera", record.getString("source"))
        assertEquals(520, record.getJSONObject("nutrition").getInt("energyKcal"))
        assertEquals("steamed_rice", record.getJSONArray("items").getJSONObject(0).getString("foodId"))
        assertTrue(record.has("score"))
        assertTrue(record.isNull("score"))
        assertFalse(record.toString().contains("private-photo.jpg"))

        val scored = meal.copy(score = MealScore(72, 100, "demo-v1")).toRecordJson()
        assertEquals(record.getString("mealId"), scored.getString("mealId"))
        assertEquals(72, scored.getJSONObject("score").getInt("points"))
        assertEquals(100, scored.getJSONObject("score").getInt("maxPoints"))
        assertEquals("demo-v1", scored.getJSONObject("score").getString("rulesVersion"))
    }
}
