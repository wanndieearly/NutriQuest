package com.nutriquest.app

import android.content.Context
import org.json.JSONObject
import java.io.IOException

internal enum class DietGoal(val label: String, val prompt: String) {
    BALANCE("Balanced eating", "enjoy a balanced variety of foods"),
    MUSCLE("Build muscle", "support strength training"),
    ROUTINE("Regular meals", "build a regular meal routine"),
    CUSTOM("My own goal", "work toward a personal food goal"),
    WEIGHT_MANAGEMENT("Weight management", "stay consistent with my own weight-management plan"),
    FEWER_SWEETS("Fewer sweet snacks", "cut down on sweet snacks")
}

internal enum class RoastTone(val label: String, val prompt: String) {
    ADAPTIVE("Adaptive", "celebrate progress and gently challenge choices that conflict with the stated goal"),
    GENTLE("Gentle", "be supportive with a very gentle joke"),
    CHEEKY("Cheeky", "be playful and lightly sarcastic"),
    SPICY("Extra spicy", "use theatrical tough love about the gap between intentions and logged choices, then push the user to follow their plan"),
    COCKY("寸嘴 · Savage", "be a cocky, deliberately annoying accountability rival: mock excuses and empty promises, then dare the user to follow their own plan")
}

internal enum class RoastRole(val label: String, val emoji: String, val prompt: String) {
    CHEF("Food critic", "👨‍🍳", "a witty Hong Kong food critic"),
    GYM_BUDDY("Gym buddy", "💪", "an enthusiastic gym buddy cheering on the plate"),
    AUNTIE("Cantonese auntie", "🫖", "an affectionate, chatty Hong Kong auntie"),
    CAPTAIN("Space captain", "🚀", "a space captain reporting on the meal mission")
}

internal enum class RoastLanguage(val label: String, val instruction: String) {
    MIXED("廣東話 + English", "用繁體香港廣東話夾一個短 English phrase。"),
    CANTONESE("廣東話", "只用繁體香港廣東話，唔好加英文。"),
    TRADITIONAL("繁體中文", "只用自然的繁體中文，不要使用英文。"),
    ENGLISH("English", "Write only in natural English.")
}

internal data class UserProfile(
    val name: String = "",
    val goal: DietGoal = DietGoal.BALANCE,
    val goalNotes: String = "",
    val energyKcal: Int = 2200,
    val proteinGrams: Int = 110,
    val carbohydrateGrams: Int = 275,
    val fatGrams: Int = 73,
    val tone: RoastTone = RoastTone.ADAPTIVE,
    val role: RoastRole = RoastRole.CHEF,
    val language: RoastLanguage = RoastLanguage.MIXED
) {
    fun toJson(): JSONObject = JSONObject()
        .put("schemaVersion", 1)
        .put("name", name)
        .put("goal", goal.name)
        .put("goalNotes", goalNotes)
        .put("energyKcal", energyKcal)
        .put("proteinGrams", proteinGrams)
        .put("carbohydrateGrams", carbohydrateGrams)
        .put("fatGrams", fatGrams)
        .put("tone", tone.name)
        .put("role", role.name)
        .put("language", language.name)

    companion object {
        fun fromJson(json: JSONObject): UserProfile {
            val defaults = UserProfile()
            return UserProfile(
                name = json.optString("name").take(30),
                goal = DietGoal.entries.find { it.name == json.optString("goal") } ?: defaults.goal,
                goalNotes = json.optString("goalNotes").take(180),
                energyKcal = json.optInt("energyKcal", defaults.energyKcal).takeIf { it in 1..100_000 } ?: defaults.energyKcal,
                proteinGrams = json.optInt("proteinGrams", defaults.proteinGrams).takeIf { it in 1..100_000 } ?: defaults.proteinGrams,
                carbohydrateGrams = json.optInt("carbohydrateGrams", defaults.carbohydrateGrams).takeIf { it in 1..100_000 } ?: defaults.carbohydrateGrams,
                fatGrams = json.optInt("fatGrams", defaults.fatGrams).takeIf { it in 1..100_000 } ?: defaults.fatGrams,
                tone = RoastTone.entries.find { it.name == json.optString("tone") } ?: defaults.tone,
                role = RoastRole.entries.find { it.name == json.optString("role") } ?: defaults.role,
                language = RoastLanguage.entries.find { it.name == json.optString("language") } ?: defaults.language
            )
        }
    }
}

internal data class ProfileDraft(
    val settings: UserProfile,
    val energy: String = settings.energyKcal.toString(),
    val protein: String = settings.proteinGrams.toString(),
    val carbs: String = settings.carbohydrateGrams.toString(),
    val fat: String = settings.fatGrams.toString()
) {
    fun validated(): UserProfile? {
        val targets = listOf(energy, protein, carbs, fat).map { value ->
            value.toIntOrNull()?.takeIf { it in 1..100_000 } ?: return null
        }
        return settings.copy(
            name = settings.name.trim().take(30),
            goalNotes = settings.goalNotes.trim().take(180),
            energyKcal = targets[0],
            proteinGrams = targets[1],
            carbohydrateGrams = targets[2],
            fatGrams = targets[3]
        )
    }
}

internal class ProfileStore(context: Context) {
    private val preferences = context.getSharedPreferences("nutriquest_profile", Context.MODE_PRIVATE)

    fun load(): UserProfile = runCatching {
        UserProfile.fromJson(JSONObject(preferences.getString("profile", "{}").orEmpty()))
    }.getOrDefault(UserProfile())

    fun save(profile: UserProfile) {
        if (!preferences.edit().putString("profile", profile.toJson().toString()).commit()) {
            throw IOException("Your profile couldn’t be saved. Try again.")
        }
    }
}
