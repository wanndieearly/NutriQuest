package com.nutriquest.app

// ============================================================================
//  F8 — Groups state and actions (Joe)
//
//  Holds this phone's random user ID, nickname and server address (saved on
//  the phone), the groups it has joined, and the leaderboard being shown.
//  syncScores() uploads the last 7 days' daily scores whenever meals change.
// ============================================================================

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.time.LocalDate

internal class GroupsViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("nutriquest_groups", Application.MODE_PRIVATE)

    /** This install's account ID (F1), shared with the Profile account card. */
    val userId: String = LocalAccount.userId(application)

    var nickname by mutableStateOf(prefs.getString("nickname", "") ?: "")
        private set
    var serverUrl by mutableStateOf(prefs.getString("serverUrl", DEFAULT_GROUPS_SERVER) ?: DEFAULT_GROUPS_SERVER)
        private set
    var groups by mutableStateOf<List<GroupInfo>>(emptyList())
        private set
    var selectedGroupId by mutableStateOf(prefs.getString("selectedGroupId", null))
        private set
    var period by mutableStateOf(LeaderboardPeriod.DAY)
        private set
    var leaderboard by mutableStateOf<Leaderboard?>(null)
        private set
    /** null = not checked yet, true = last request worked, false = server unreachable */
    var connected by mutableStateOf<Boolean?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    private var registeredAs: Pair<String, String>? = null   // (server, nickname) last registered
    /** Where daily scores come from (set by the first syncScores call), so Refresh can re-upload them. */
    private var scoreSource: ((LocalDate) -> DailyScoreResult?)? = null
    private val syncLock = Mutex()
    private val api get() = GroupsApi(serverUrl)
    val isSetUp get() = nickname.isNotBlank()

    // ---------------------------------------------------------------- settings

    fun saveSettings(newNickname: String, newServerUrl: String) {
        val name = newNickname.trim().take(30)
        val url = newServerUrl.trim().ifEmpty { DEFAULT_GROUPS_SERVER }
        if (name.isEmpty()) {
            error = "Enter a nickname your friends will recognise."
            return
        }
        nickname = name
        serverUrl = url
        prefs.edit().putString("nickname", name).putString("serverUrl", url).apply()
        registeredAs = null
        refresh()
    }

    private suspend fun ensureRegistered() {
        val key = serverUrl to nickname
        if (registeredAs == key) return
        api.register(userId, nickname)
        registeredAs = key
    }

    /** Runs a server action, showing a spinner and turning failures into a readable message. */
    private fun action(success: String? = null, block: suspend () -> Unit) {
        if (busy) return
        busy = true
        error = null
        message = null
        viewModelScope.launch {
            try {
                ensureRegistered()
                block()
                connected = true
                message = success
            } catch (e: CancellationException) {
                throw e
            } catch (e: GroupsServerException) {
                if (e.message?.startsWith("Can’t reach") == true) connected = false
                error = e.message
            } catch (e: IOException) {
                connected = false
                error = e.message ?: "Network error."
            } finally {
                busy = false
            }
        }
    }

    // ------------------------------------------------------------------ groups

    fun refresh() {
        if (!isSetUp) return
        // Re-upload scores too, in case the server was down when meals were logged
        scoreSource?.let { syncScores(it) }
        action { loadGroupsAndBoard() }
    }

    private suspend fun loadGroupsAndBoard() {
        groups = api.myGroups(userId)
        if (groups.none { it.groupId == selectedGroupId }) selectGroupInternal(groups.firstOrNull()?.groupId)
        loadBoard()
    }

    private suspend fun loadBoard() {
        val id = selectedGroupId
        leaderboard = if (id == null) null else api.leaderboard(id, userId, LocalDate.now(), period)
    }

    /** Quiet refresh used by the screen every few seconds: no spinner, errors only change the status dot. */
    fun pollLeaderboard() {
        if (!isSetUp || busy || selectedGroupId == null) return
        viewModelScope.launch {
            try {
                ensureRegistered()
                loadBoard()
                connected = true
            } catch (e: CancellationException) {
                throw e
            } catch (_: IOException) {
                connected = false
            }
        }
    }

    fun createGroup(name: String) {
        if (name.isBlank()) {
            error = "Give the group a name."
            return
        }
        action("Group created. Share the join code with friends!") {
            val group = api.createGroup(userId, name.trim())
            selectGroupInternal(group.groupId)
            loadGroupsAndBoard()
        }
    }

    fun joinGroup(code: String) {
        val clean = code.filter(Char::isLetterOrDigit).uppercase()
        if (clean.length != 6) {
            error = "Join codes have 6 letters or numbers."
            return
        }
        action("You joined the group!") {
            val group = api.joinGroup(userId, clean)
            selectGroupInternal(group.groupId)
            loadGroupsAndBoard()
        }
    }

    fun leaveSelectedGroup() {
        val id = selectedGroupId ?: return
        action("You left the group.") {
            api.leaveGroup(id, userId)
            selectGroupInternal(null)
            loadGroupsAndBoard()
        }
    }

    fun selectGroup(groupId: String) {
        if (groupId == selectedGroupId) return
        selectGroupInternal(groupId)
        leaderboard = null
        action { loadBoard() }
    }

    private fun selectGroupInternal(groupId: String?) {
        selectedGroupId = groupId
        prefs.edit().putString("selectedGroupId", groupId).apply()
    }

    fun changePeriod(newPeriod: LeaderboardPeriod) {
        if (newPeriod == period) return
        period = newPeriod
        action { loadBoard() }
    }

    fun react(toUserId: String, emoji: String) {
        val id = selectedGroupId ?: return
        action {
            api.react(id, userId, toUserId, LocalDate.now(), emoji)
            loadBoard()
        }
    }

    // ------------------------------------------------------------------ scores

    /**
     * Uploads the daily score for each of the last 7 days (and removes days with
     * no meals left). Called automatically when meals change. Fails quietly:
     * the next change or refresh tries again.
     */
    fun syncScores(scoreFor: (LocalDate) -> DailyScoreResult?) {
        scoreSource = scoreFor
        if (!isSetUp) return
        val today = LocalDate.now()
        val days = (0L..6L).map { today.minusDays(it) }.associateWith(scoreFor)
        viewModelScope.launch {
            syncLock.withLock {
                try {
                    ensureRegistered()
                    for ((day, result) in days) {
                        if (result != null) api.uploadScore(userId, result) else api.deleteScore(userId, day)
                    }
                    connected = true
                    if (selectedGroupId != null) loadBoard()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: IOException) {
                    connected = false
                }
            }
        }
    }

    fun clearMessages() {
        error = null
        message = null
    }
}
