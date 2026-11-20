package com.nutriquest.app

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetSocketAddress
import java.time.LocalDate

/**
 * F8 client tests. A tiny fake server (built into Java) stands in for the
 * Python groups server, so these run with `./gradlew test` and nothing else.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GroupsApiTest {
    private lateinit var server: HttpServer
    private val requests = mutableListOf<Triple<String, String, String>>()   // method, path+query, body
    private var nextStatus = 200
    private var nextBody = "{}"

    @Before fun startServer() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val body = exchange.requestBody.bufferedReader().readText()
            requests += Triple(exchange.requestMethod, exchange.requestURI.toString(), body)
            val bytes = nextBody.toByteArray()
            exchange.sendResponseHeaders(nextStatus, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
    }

    @After fun stopServer() = server.stop(0)

    private val api get() = GroupsApi("http://127.0.0.1:${server.address.port}/")

    private val sampleBoard = """
        {"groupId":"g1","name":"Hall 5 gym crew","code":"ABC234","period":"week","date":"2026-11-17","start":"2026-11-16",
         "reactions":["👏","🔥","💪","🥦","😂","😬"],
         "members":[
           {"userId":"u2","nickname":"Amy","score":91,"daysScored":2,"mealCount":6,"streak":4,"rank":1,"crown":true,
            "reactions":{"🔥":2,"👏":1},"myReaction":"🔥"},
           {"userId":"u3","nickname":"Sam","score":null,"daysScored":0,"mealCount":0,"streak":0,"rank":null,"crown":false,
            "reactions":{},"myReaction":null}
         ]}
    """.trimIndent()

    @Test fun parsesALeaderboard() {
        val board = parseLeaderboard(JSONObject(sampleBoard))
        assertEquals(LeaderboardPeriod.WEEK, board.period)
        assertEquals(LocalDate.of(2026, 11, 16), board.start)
        assertEquals(6, board.reactionChoices.size)
        val amy = board.members[0]
        assertEquals(91, amy.score)
        assertEquals(1, amy.rank)
        assertTrue(amy.crown)
        assertEquals(mapOf("🔥" to 2, "👏" to 1), amy.reactions)
        assertEquals("🔥", amy.myReaction)
        val sam = board.members[1]
        assertNull(sam.score)
        assertNull(sam.rank)
        assertNull(sam.myReaction)
    }

    @Test fun leaderboardRequestHasTheRightQuery() = runBlocking {
        nextBody = sampleBoard
        api.leaderboard("g1", "u1", LocalDate.of(2026, 11, 17), LeaderboardPeriod.WEEK)
        assertEquals("GET", requests.single().first)
        assertEquals("/groups/g1/leaderboard?userId=u1&date=2026-11-17&period=week", requests.single().second)
    }

    @Test fun uploadSendsOnlyScoreNumbers() = runBlocking {
        val result = DailyScoreResult(
            date = LocalDate.of(2026, 11, 16), score = 78, calorieScore = 88, macroScore = 70, proteinScore = 50,
            carbohydrateScore = 80, fatScore = 80, mealFitAverage = 66, mealCount = 3,
            nutrition = Nutrition(1800, 80, 220, 60), isFinal = false, explanation = "x"
        )
        api.uploadScore("u1", result)
        val (method, path, body) = requests.single()
        assertEquals("PUT", method)
        assertEquals("/scores", path)
        val json = JSONObject(body)
        assertEquals(setOf("userId", "date", "score", "mealCount", "calorieScore", "macroScore", "mealFit"), json.keys().asSequence().toSet())
        assertEquals("2026-11-16", json.getString("date"))
        assertEquals(78, json.getInt("score"))
    }

    @Test fun serverErrorMessagesAreShownToTheUser() = runBlocking {
        nextStatus = 404
        nextBody = """{"detail":"No group has that code. Check it and try again."}"""
        try {
            api.joinGroup("u1", "ZZZZZZ")
            fail("Expected an error")
        } catch (e: GroupsServerException) {
            assertEquals("No group has that code. Check it and try again.", e.message)
        }
    }

    @Test fun unreachableServerGivesAFriendlyMessage() = runBlocking {
        val port = server.address.port
        server.stop(0)
        try {
            GroupsApi("http://127.0.0.1:$port").health()
            fail("Expected an error")
        } catch (e: GroupsServerException) {
            assertTrue(e.message!!.startsWith("Can’t reach the NutriQuest server"))
        }
    }

    @Test fun validationErrorsBecomeASentence() {
        assertEquals("The server didn’t accept that. Check what you typed.", errorMessage(422, """{"detail":[{"msg":"bad"}]}"""))
        assertEquals("The server had a problem (error 500). Try again.", errorMessage(500, "oops"))
    }
}
