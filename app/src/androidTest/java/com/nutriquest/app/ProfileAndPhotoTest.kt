package com.nutriquest.app

import android.graphics.Bitmap
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.net.Uri
import androidx.core.graphics.createBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ProfileAndPhotoTest {
    @Test
    fun profileSurvivesReopeningStoreAndToleratesUnknownSettings() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val preferenceName = "profile_test_${UUID.randomUUID()}"
        val context = object : ContextWrapper(target) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                target.getSharedPreferences(preferenceName, mode)
        }
        val profile = UserProfile(name = "Test Explorer", goal = DietGoal.FEWER_SWEETS, energyKcal = 1800, tone = RoastTone.COCKY, language = RoastLanguage.ENGLISH)
        try {
            ProfileStore(context).save(profile)
            assertEquals(profile, ProfileStore(context).load())
        } finally {
            target.getSharedPreferences(preferenceName, 0).edit().clear().commit()
        }
        val unknown = JSONObject().put("tone", "future_tone").put("energyKcal", 0)
        assertEquals(UserProfile(), UserProfile.fromJson(unknown))
        for (tone in RoastTone.entries) {
            assertEquals(tone, UserProfile.fromJson(JSONObject().put("tone", tone.name)).tone)
        }
        for (goal in DietGoal.entries) {
            assertEquals(goal, UserProfile.fromJson(JSONObject().put("goal", goal.name)).goal)
        }
    }

    @Test
    fun albumImportCreatesIndependentNormalizedPhoto(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = File.createTempFile("album-fixture-", ".png", context.cacheDir)
        val image = createBitmap(320, 240)
        val imported: File
        try {
            source.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
            imported = MealPhotoImporter(context).copy(Uri.fromFile(source))
        } finally {
            image.recycle()
            source.delete()
        }
        try {
            assertTrue(imported.length() > 0)
            val decoded = decodePhoto(imported, 640)
            assertNotNull(decoded)
            assertEquals(320, decoded?.width)
            decoded?.recycle()
        } finally {
            imported.delete()
        }
    }
}
