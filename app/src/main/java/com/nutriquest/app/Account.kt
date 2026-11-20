package com.nutriquest.app

// ============================================================================
//  F1 — Local account, first-launch setup and reminder settings (Joe)
//
//  The app runs locally, so "signing in" means creating an account on this
//  phone: a random user ID (also used by Groups) plus the name, body stats,
//  goal and chef chosen in the setup screens. No password or internet needed.
// ============================================================================

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import java.io.IOException
import java.util.UUID

/** This install's account ID, shared by the account card and Groups (F8). */
internal object LocalAccount {
    // Same preferences file and key Groups used before, so existing group memberships carry over
    private const val PREFS = "nutriquest_groups"
    private const val KEY_USER_ID = "userId"

    fun userId(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString(KEY_USER_ID, null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString(KEY_USER_ID, it).apply()
        }
    }
}

/** What the setup screens collect before saving. */
internal data class SetupResult(
    val name: String,
    val body: BodyStats,
    val goal: DietGoal,
    val targets: SuggestedTargets,
    val tone: RoastTone,
    val role: RoastRole
) {
    fun applyTo(profile: UserProfile) = profile.copy(
        name = name.trim().take(30),
        goal = goal,
        energyKcal = targets.energyKcal,
        proteinGrams = targets.proteinGrams,
        carbohydrateGrams = targets.carbohydrateGrams,
        fatGrams = targets.fatGrams,
        tone = tone,
        role = role
    )
}

internal class AccountViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("nutriquest_account", Context.MODE_PRIVATE)
    private val bodyStore = BodyStatsStore(application)
    private val reminderStore = ReminderStore(application)

    val userId: String = LocalAccount.userId(application)

    /** False until the first-launch setup has been finished or skipped. */
    var setupDone by mutableStateOf(prefs.getBoolean(KEY_SETUP_DONE, false))
        private set

    var body by mutableStateOf(bodyStore.load())
        private set
    var bodyDraft by mutableStateOf(body?.let(::BodyStatsDraft) ?: BodyStatsDraft())
        private set
    var bodyMessage by mutableStateOf<String?>(null)
        private set
    var bodyError by mutableStateOf<String?>(null)
        private set

    var reminders by mutableStateOf(reminderStore.load())
        private set

    // ------------------------------------------------------------- setup

    fun finishSetup(result: SetupResult) {
        saveBody(result.body)
        markSetupDone()
    }

    fun skipSetup() = markSetupDone()

    /** Shows the setup screens again (meals and groups are kept). */
    fun restartSetup() {
        prefs.edit().putBoolean(KEY_SETUP_DONE, false).apply()
        setupDone = false
    }

    private fun markSetupDone() {
        prefs.edit().putBoolean(KEY_SETUP_DONE, true).apply()
        setupDone = true
    }

    // -------------------------------------------------------- body stats

    fun editBody(draft: BodyStatsDraft) {
        bodyDraft = draft
        bodyMessage = null
        bodyError = null
    }

    /** Validates and saves the draft; returns the saved stats, or null with [bodyError] explaining why. */
    fun saveBodyDraft(): BodyStats? {
        val stats = bodyDraft.validated()
        if (stats == null) {
            bodyError = bodyDraft.problem
            return null
        }
        return if (saveBody(stats)) stats.also { bodyMessage = "Body stats saved." } else null
    }

    private fun saveBody(stats: BodyStats): Boolean = try {
        bodyStore.save(stats)
        body = stats
        bodyDraft = BodyStatsDraft(stats)
        true
    } catch (error: IOException) {
        bodyError = error.message
        false
    }

    // --------------------------------------------------------- reminders

    fun updateReminders(settings: ReminderSettings) {
        reminderStore.save(settings)
        reminders = settings
        if (settings.enabled) DailyReminder.schedule(getApplication(), settings) else DailyReminder.cancel(getApplication())
    }

    fun sendTestReminder() = DailyReminder.runNow(getApplication())

    private companion object {
        const val KEY_SETUP_DONE = "setupDone"
    }
}
