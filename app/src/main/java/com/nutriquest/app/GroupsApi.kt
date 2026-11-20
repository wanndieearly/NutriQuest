package com.nutriquest.app

// ============================================================================
//  F8 — Groups server client (Joe)
//
//  Talks to the local NutriQuest groups server (backend/groups.py) over HTTP.
//  Emulator → your Mac:  http://10.0.2.2:8000
//  Real phone on the same Wi-Fi → http://<your Mac's IP>:8000
//  Uses Android's built-in HttpURLConnection and org.json, so no extra libraries.
// ============================================================================

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate

internal const val DEFAULT_GROUPS_SERVER = "http://10.0.2.2:8000"

internal data class GroupInfo(val groupId: String, val code: String, val name: String, val memberCount: Int)

internal enum class LeaderboardPeriod(val wireValue: String, val label: String) {
    DAY("day", "Today"),
    WEEK("week", "This week")
}

internal data class LeaderboardEntry(
    val userId: String,
    val nickname: String,
    /** Day score, or the week's average; null if no meals logged yet */
    val score: Int?,
    val daysScored: Int,
    val mealCount: Int,
    val streak: Int,
    val rank: Int?,
    val crown: Boolean,
    val reactions: Map<String, Int>,
    val myReaction: String?
)

internal data class Leaderboard(
    val groupId: String,
    val name: String,
    val code: String,
    val period: LeaderboardPeriod,
    val date: LocalDate,
    val start: LocalDate,
    val reactionChoices: List<String>,
    val members: List<LeaderboardEntry>
)

/** An error the user can read, e.g. "No group has that code." */
internal class GroupsServerException(message: String) : IOException(message)

internal class GroupsApi(baseUrl: String) {
    private val base = baseUrl.trim().trimEnd('/')

    suspend fun health() {
        request("GET", "/health")
    }

    suspend fun register(userId: String, nickname: String) {
        request("PUT", "/users/${enc(userId)}", JSONObject().put("nickname", nickname))
    }

    suspend fun myGroups(userId: String): List<GroupInfo> {
        val groups = request("GET", "/users/${enc(userId)}/groups").getJSONArray("groups")
        return List(groups.length()) { parseGroup(groups.getJSONObject(it)) }
    }

    suspend fun createGroup(userId: String, name: String): GroupInfo =
        parseGroup(request("POST", "/groups", JSONObject().put("userId", userId).put("name", name)))

    suspend fun joinGroup(userId: String, code: String): GroupInfo =
        parseGroup(request("POST", "/groups/join", JSONObject().put("userId", userId).put("code", code)))

    suspend fun leaveGroup(groupId: String, userId: String) {
        request("POST", "/groups/${enc(groupId)}/leave", JSONObject().put("userId", userId))
    }

    /** Uploads one day's score. Only the score numbers are sent: no food, photos or body data. */
    suspend fun uploadScore(userId: String, result: DailyScoreResult) {
        request(
            "PUT", "/scores",
            JSONObject()
                .put("userId", userId)
                .put("date", result.date.toString())
                .put("score", result.score)
                .put("mealCount", result.mealCount)
                .put("calorieScore", result.calorieScore)
                .put("macroScore", result.macroScore)
                .put("mealFit", result.mealFitAverage)
        )
    }

    suspend fun deleteScore(userId: String, date: LocalDate) {
        request("DELETE", "/scores/${enc(userId)}/$date")
    }

    suspend fun leaderboard(groupId: String, userId: String, date: LocalDate, period: LeaderboardPeriod): Leaderboard =
        parseLeaderboard(
            request("GET", "/groups/${enc(groupId)}/leaderboard?userId=${enc(userId)}&date=$date&period=${period.wireValue}")
        )

    /** Returns the reaction now set, or null if sending the same emoji again removed it. */
    suspend fun react(groupId: String, userId: String, toUserId: String, date: LocalDate, emoji: String): String? {
        val reply = request(
            "PUT", "/groups/${enc(groupId)}/reactions",
            JSONObject().put("userId", userId).put("toUserId", toUserId).put("date", date.toString()).put("emoji", emoji)
        )
        return if (reply.isNull("emoji")) null else reply.getString("emoji")
    }

    private suspend fun request(method: String, path: String, body: JSONObject? = null): JSONObject =
        withContext(Dispatchers.IO) {
            val connection = try {
                (URL(base + path).openConnection() as HttpURLConnection)
            } catch (_: Exception) {
                throw GroupsServerException("The server address \"$base\" isn’t valid. It should look like http://10.0.2.2:8000")
            }
            try {
                connection.requestMethod = method
                connection.connectTimeout = 4_000
                connection.readTimeout = 8_000
                connection.setRequestProperty("Accept", "application/json")
                if (body != null) {
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                }
                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (status !in 200..299) throw GroupsServerException(errorMessage(status, text))
                if (text.isBlank()) JSONObject() else JSONObject(text)
            } catch (error: GroupsServerException) {
                throw error
            } catch (_: IOException) {
                throw GroupsServerException("Can’t reach the NutriQuest server at $base. Is it running on your Mac?")
            } finally {
                connection.disconnect()
            }
        }

    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")
}

// ---------------------------------------------------------------------------
// JSON parsing (internal so the unit tests can check it)
// ---------------------------------------------------------------------------

internal fun parseGroup(json: JSONObject) = GroupInfo(
    groupId = json.getString("groupId"),
    code = json.getString("code"),
    name = json.getString("name"),
    memberCount = json.optInt("memberCount", 1)
)

internal fun parseLeaderboard(json: JSONObject): Leaderboard {
    val members = json.getJSONArray("members")
    val choices = json.optJSONArray("reactions") ?: JSONArray()
    return Leaderboard(
        groupId = json.getString("groupId"),
        name = json.getString("name"),
        code = json.getString("code"),
        period = LeaderboardPeriod.entries.firstOrNull { it.wireValue == json.optString("period") } ?: LeaderboardPeriod.DAY,
        date = LocalDate.parse(json.getString("date")),
        start = LocalDate.parse(json.optString("start", json.getString("date"))),
        reactionChoices = List(choices.length()) { choices.getString(it) },
        members = List(members.length()) { index ->
            val m = members.getJSONObject(index)
            val reactions = m.optJSONObject("reactions") ?: JSONObject()
            LeaderboardEntry(
                userId = m.getString("userId"),
                nickname = m.getString("nickname"),
                score = if (m.isNull("score")) null else m.getInt("score"),
                daysScored = m.optInt("daysScored"),
                mealCount = m.optInt("mealCount"),
                streak = m.optInt("streak"),
                rank = if (m.isNull("rank")) null else m.getInt("rank"),
                crown = m.optBoolean("crown"),
                reactions = reactions.keys().asSequence().associateWith { reactions.getInt(it) },
                myReaction = if (m.isNull("myReaction")) null else m.optString("myReaction")
            )
        }
    )
}

/** Turns a FastAPI error body into a readable sentence. */
internal fun errorMessage(status: Int, body: String): String {
    val detail = runCatching { JSONObject(body).opt("detail") }.getOrNull()
    return when {
        detail is String && detail.isNotBlank() -> detail
        status == 422 -> "The server didn’t accept that. Check what you typed."
        status == 404 -> "Not found on the server."
        else -> "The server had a problem (error $status). Try again."
    }
}
