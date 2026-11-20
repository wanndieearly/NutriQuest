package com.nutriquest.app

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** F1: local account ID, body-stats storage and the setup flag. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AccountTest {
    private val context: Application = ApplicationProvider.getApplicationContext()
    private val joe = BodyStats(22, Sex.MALE, 175, 70, ActivityLevel.MODERATE)

    @Test fun accountIdIsStableAndSharedWithGroups() {
        val first = LocalAccount.userId(context)
        assertEquals(first, LocalAccount.userId(context))
        assertEquals(first, GroupsViewModel(context).userId)
        assertEquals(first, AccountViewModel(context).userId)
    }

    @Test fun bodyStatsSurviveARestart() {
        assertNull(BodyStatsStore(context).load())
        BodyStatsStore(context).save(joe)
        assertEquals(joe, BodyStatsStore(context).load())
    }

    @Test fun finishingSetupSavesBodyAndIsRemembered() {
        val account = AccountViewModel(context)
        assertEquals(false, account.setupDone)
        account.finishSetup(SetupResult("Joe", joe, DietGoal.BALANCE, suggestTargets(joe, DietGoal.BALANCE), RoastTone.ADAPTIVE, RoastRole.CHEF))
        val reopened = AccountViewModel(context)
        assertEquals(true, reopened.setupDone)
        assertEquals(joe, reopened.body)
    }

    @Test fun restartSetupShowsTheScreensAgain() {
        val account = AccountViewModel(context)
        account.skipSetup()
        account.restartSetup()
        assertEquals(false, AccountViewModel(context).setupDone)
    }

    @Test fun invalidBodyDraftExplainsTheProblem() {
        val account = AccountViewModel(context)
        account.editBody(BodyStatsDraft("22", Sex.MALE, "175", "9999"))
        assertNull(account.saveBodyDraft())
        assertEquals("Weight should be 25–300 kg.", account.bodyError)
    }
}
