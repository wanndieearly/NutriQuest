package com.nutriquest.app

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** F9: reminder text, timing, notification and WorkManager scheduling. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReminderTest {
    private lateinit var context: Application
    private val today = LocalDate.of(2026, 11, 19)
    private val rice = catalogFood("rice")!!
    private val chicken = catalogFood("chicken")!!

    private fun meal(date: LocalDate = today) = Meal(
        loggedAt = ZonedDateTime.of(date.atTime(13, 0), ZoneId.of("Asia/Hong_Kong")),
        name = "Lunch",
        items = listOf(MealItem(rice, 1), MealItem(chicken, 1))
    )

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
    }

    // ----- message -----

    @Test fun emptyDayAsksForTheNextMeal() {
        val english = reminderMessage(today, emptyList(), UserProfile(language = RoastLanguage.ENGLISH))
        assertEquals("No meals logged yet today 🍽️", english.title)
        val cantonese = reminderMessage(today, emptyList(), UserProfile(language = RoastLanguage.CANTONESE))
        assertEquals("今日仲未記低任何一餐 🍽️", cantonese.title)
    }

    @Test fun mealsFromOtherDaysDoNotCount() {
        val message = reminderMessage(today, listOf(meal(today.minusDays(1))), UserProfile(language = RoastLanguage.ENGLISH))
        assertEquals("No meals logged yet today 🍽️", message.title)
    }

    @Test fun loggedDayShowsScoreAndGoalReminder() {
        val profile = UserProfile(language = RoastLanguage.ENGLISH)
        val meals = listOf(meal())
        val message = reminderMessage(today, meals, profile)
        val score = dailyScore(today, meals, profile.scoreTargets(), today = today)!!.score
        assertTrue(message.title, message.title.startsWith("Today so far: $score/100"))
        assertEquals(goalFeedback(meals, meals, profile).reminder, message.body)
    }

    // ----- timing -----

    @Test fun delayIsUntilLaterTodayOrTomorrow() {
        val eight = LocalTime.of(20, 0)
        assertEquals(Duration.ofHours(2), delayUntil(LocalDateTime.of(2026, 11, 19, 18, 0), eight))
        assertEquals(Duration.ofHours(23), delayUntil(LocalDateTime.of(2026, 11, 19, 21, 0), eight))
        assertEquals(Duration.ofDays(1), delayUntil(LocalDateTime.of(2026, 11, 19, 20, 0), eight))
    }

    @Test fun settingsAreSaved() {
        val store = ReminderStore(context)
        store.save(ReminderSettings(enabled = true, time = LocalTime.of(21, 30)))
        assertEquals(ReminderSettings(true, LocalTime.of(21, 30)), store.load())
    }

    // ----- notification -----

    @Test fun noNotificationWithoutPermission() {
        shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertFalse(ReminderNotifier.show(context, ReminderMessage("t", "b")))
    }

    @Test fun notificationIsPostedWithTitleAndText() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertTrue(ReminderNotifier.show(context, ReminderMessage("Today so far: 72/100 💪", "Keep going.")))
        val posted = shadowOf(context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).allNotifications.single()
        assertEquals("Today so far: 72/100 💪", shadowOf(posted).contentTitle)
        assertEquals("Keep going.", shadowOf(posted).contentText)
        assertNotNull(posted.contentIntent)   // tapping opens the app
    }

    @Test fun workerReadsSavedMealsAndNotifies() = runBlocking {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val result = TestListenableWorkerBuilder<DailyReminderWorker>(context).build().doWork()
        assertEquals(ListenableWorker.Result.success(), result)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        assertEquals(1, shadowOf(manager).allNotifications.size)
    }

    // ----- scheduling -----

    @Test fun scheduleAndCancelTheDailyWork() {
        val settings = ReminderSettings(enabled = true, time = LocalTime.of(20, 0))
        DailyReminder.schedule(context, settings)
        val work = WorkManager.getInstance(context).getWorkInfosForUniqueWork("nutriquest-daily-reminder").get().single()
        assertEquals(WorkInfo.State.ENQUEUED, work.state)
        DailyReminder.cancel(context)
        val cancelled = WorkManager.getInstance(context).getWorkInfosForUniqueWork("nutriquest-daily-reminder").get().single()
        assertEquals(WorkInfo.State.CANCELLED, cancelled.state)
    }
}
