package com.nutriquest.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.ZonedDateTime

/**
 * F6 storage tests. Robolectric runs real Android + Room code on the computer,
 * so no emulator is needed: `./gradlew test` or the green ▶.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MealStorageTest {
    private lateinit var context: Context
    private lateinit var db: NutriQuestDatabase
    private lateinit var photos: File
    private lateinit var repo: MealRepository

    private val rice = Food(
        id = "white_rice", name = "White rice", emoji = "🍚", calories = 130, protein = 3, carbs = 28, fat = 0,
        chineseName = "白飯", aliases = setOf("rice"), referenceGrams = 100, defaultGrams = 200,
        nutritionSource = "test_source", sourceUrl = "https://example.org/rice", referenceDescription = "Per 100 g"
    )
    private val cake = Food(id = "cake", name = "Cake", emoji = "🍰", calories = 371, protein = 5, carbs = 53, fat = 16, sweetSnack = true)

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, NutriQuestDatabase::class.java).allowMainThreadQueries().build()
        photos = File(context.filesDir, "test_photos").apply { deleteRecursively() }
        repo = MealRepository(db.meals(), photos)
    }

    @After fun tearDown() {
        db.close()
        photos.deleteRecursively()
    }

    private fun lunch(photo: File? = null) = Meal(
        loggedAt = ZonedDateTime.parse("2026-11-16T12:30:00+08:00[Asia/Hong_Kong]"),
        name = "Campus lunch",
        items = listOf(MealItem(rice, 2, 250), MealItem(cake, 1, null)),
        photo = photo,
        source = MealSource.CAMERA,
        score = MealScore(72, 100, SCORE_RULES_VERSION)
    )

    @Test fun savedMealReloadsExactly() = runBlocking {
        val meal = lunch()
        repo.save(meal)
        val loaded = repo.loadMeals().single()
        assertEquals(meal.id, loaded.id)
        assertEquals(meal.loggedAt, loaded.loggedAt)
        assertEquals(meal.name, loaded.name)
        assertEquals(meal.items, loaded.items)          // foods, servings, grams, aliases, source, sweet flag
        assertEquals(meal.nutrition, loaded.nutrition)
        assertEquals(MealSource.CAMERA, loaded.source)
        assertEquals(meal.score, loaded.score)
    }

    @Test fun mealsComeBackOldestFirst() = runBlocking {
        val later = lunch().copy(id = java.util.UUID.randomUUID(), loggedAt = ZonedDateTime.parse("2026-11-16T19:00:00+08:00[Asia/Hong_Kong]"), name = "Dinner")
        repo.save(later)
        repo.save(lunch())
        assertEquals(listOf("Campus lunch", "Dinner"), repo.loadMeals().map { it.name })
    }

    @Test fun mealsAreGroupedByTheLocalDay() = runBlocking {
        // 00:15 in Hong Kong is still the previous day in UTC; the diary day must follow Hong Kong
        val earlyMeal = lunch().copy(loggedAt = ZonedDateTime.parse("2026-11-17T00:15:00+08:00[Asia/Hong_Kong]"))
        repo.save(earlyMeal)
        assertEquals(1, db.meals().mealsOn("2026-11-17").size)
        assertEquals(0, db.meals().mealsOn("2026-11-16").size)
    }

    @Test fun savingTheSameMealTwiceUpdatesIt() = runBlocking {
        val meal = lunch()
        repo.save(meal)
        repo.save(meal.copy(name = "Renamed lunch"))
        assertEquals(listOf("Renamed lunch"), repo.loadMeals().map { it.name })
    }

    @Test fun photoIsMovedToThePermanentFolderAndSurvives() = runBlocking {
        val temp = File(context.cacheDir, "nutriquest_meal_test.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val kept = repo.keepPhoto(temp)!!
        assertEquals(photos, kept.parentFile)
        assertFalse(temp.exists())
        repo.save(lunch(kept))
        val loaded = repo.loadMeals().single()
        assertNotNull(loaded.photo)
        assertTrue(loaded.photo!!.readBytes().contentEquals(byteArrayOf(1, 2, 3)))
    }

    @Test fun missingPhotoLoadsAsNoPhoto() = runBlocking {
        repo.save(lunch(File(photos, "deleted.jpg")))
        assertNull(repo.loadMeals().single().photo)
    }

    @Test fun deleteRemovesTheMealAndItsPhoto() = runBlocking {
        val temp = File(context.cacheDir, "nutriquest_meal_del.jpg").apply { writeBytes(byteArrayOf(9)) }
        val meal = lunch(repo.keepPhoto(temp))
        repo.save(meal)
        repo.delete(meal)
        assertTrue(repo.loadMeals().isEmpty())
        assertFalse(meal.photo!!.exists())
    }

    @Test fun mealWithoutScoreStaysWithoutScore() = runBlocking {
        repo.save(lunch().copy(score = null))
        assertNull(repo.loadMeals().single().score)
    }
}
