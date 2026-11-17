package com.nutriquest.app

import android.content.Context
import android.net.Uri
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.RepetitionPenaltyConfig
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.security.MessageDigest
import kotlin.random.Random

internal sealed interface RoastModelState {
    data object Missing : RoastModelState
    data object Importing : RoastModelState
    data object Installed : RoastModelState
    data class ImportFailed(val message: String) : RoastModelState
}

internal class OnDeviceNutriRoast(context: Context) {
    private val app = context.applicationContext
    private val model = File(app.noBackupFilesDir, "nutriroast-qwen3-0.6b-v1.litertlm")
    private val modelAccess = Mutex()

    fun hasModel(): Boolean = model.isFile && model.length() == MODEL_SIZE

    suspend fun importModel(uri: Uri) = withContext(Dispatchers.IO) {
        modelAccess.withLock {
            val temporary = File.createTempFile("roast-import-", ".part", model.parentFile)
            try {
                val hash = MessageDigest.getInstance("SHA-256")
                val input = app.contentResolver.openInputStream(uri)
                    ?: throw IOException("Android couldn’t open that file.")
                var bytes = 0L
                input.use { source ->
                    temporary.outputStream().use { destination ->
                        val buffer = ByteArray(256 * 1024)
                        while (true) {
                            ensureActive()
                            val count = source.read(buffer)
                            if (count < 0) break
                            bytes += count
                            if (bytes > MODEL_SIZE) throw wrongModel()
                            hash.update(buffer, 0, count)
                            destination.write(buffer, 0, count)
                        }
                    }
                }
                val checksum = hash.digest().joinToString("") { "%02x".format(it) }
                if (bytes != MODEL_SIZE || checksum != MODEL_SHA256) throw wrongModel()
                ensureActive()
                Files.move(temporary.toPath(), model.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
            } finally {
                temporary.delete()
            }
        }
    }

    suspend fun roast(scope: RoastScope, meals: List<Meal>, profile: UserProfile, dayMeals: List<Meal> = meals): String = withContext(Dispatchers.Default) {
        modelAccess.withLock {
            if (!hasModel()) throw IOException("Import the on-device model from Profile first.")
            val instructions = roastInstructions(scope, meals, profile, dayMeals)
            val seed = Random.nextInt(1, Int.MAX_VALUE - 1)
            Engine(
                EngineConfig(
                    modelPath = model.path,
                    backend = Backend.CPU(),
                    maxNumTokens = 1536,
                    cacheDir = app.cacheDir.path
                )
            ).use { engine ->
                engine.initialize()
                repeat(2) { attempt ->
                    engine.createConversation(
                        ConversationConfig(
                            systemInstruction = Contents.of(instructions.system),
                            initialMessages = emptyList(),
                            samplerConfig = SamplerConfig(topK = 20, topP = 0.85, temperature = 0.65, seed = seed + attempt),
                            extraContext = mapOf("enable_thinking" to false),
                            maxOutputToken = 128
                        )
                    ).use { conversation ->
                        val generated = conversation.sendMessage(
                            instructions.request,
                            repetitionPenaltyConfig = RepetitionPenaltyConfig(repetitionPenalty = 1.15f, windowSize = 64)
                        ).toString()
                        val comments = generated.lineSequence().map { it.trim().replace("**", "") }.filter { line ->
                            line.length in 8..350 && acceptableLanguage(line, profile.language, meals) &&
                                !mentionsUnloggedFood(line, meals) &&
                                !containsRoastDirections(line) &&
                                !repeatedPhrase.containsMatchIn(line)
                        }.toList()
                        val comment = comments.firstOrNull { mentionsLoggedFood(it, meals) } ?: comments.firstOrNull()
                        if (comment != null) {
                            return@withLock if (profile.language == RoastLanguage.MIXED && !englishWord.containsMatchIn(comment)) {
                                comment + mixedRoastSignOff(profile.tone, scope)
                            } else {
                                comment
                            }
                        }
                    }
                }
            }
            throw IOException("The local chef couldn’t finish a comment. Try again.")
        }
    }

    private fun wrongModel() = IOException("Choose the exact Qwen3-0.6B model linked in the setup guide.")

    private companion object {
        const val MODEL_SIZE = 344_671_744L
        const val MODEL_SHA256 = "03e7da1eb1108b50dffaa9bb52cc7bcbad2eb0c66ca990267f480c1e545d2856"
        val repeatedPhrase = Regex("(.{4,18})\\1{2,}")
        val englishWord = Regex("[A-Za-z]{2,}")

    }
}
