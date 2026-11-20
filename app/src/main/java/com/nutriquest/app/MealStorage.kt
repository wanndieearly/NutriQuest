package com.nutriquest.app

// ============================================================================
//  F6 — Meal storage (Joe)
//
//  Saves every logged meal in a Room (SQLite) database on the phone and keeps
//  its photo in the app's private files folder, so meals survive closing the
//  app. MealLogViewModel loads them at start-up and saves each new meal.
//
//  Table "meals": one row per meal. The meal's food items (with their full
//  nutrition reference) are stored as a JSON text column, so a meal reloads
//  exactly as it was logged even if the food catalogue changes later.
// ============================================================================

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.time.ZonedDateTime
import java.util.UUID

// ---------------------------------------------------------------------------
// Database table, queries and database
// ---------------------------------------------------------------------------

@Entity(tableName = "meals", indices = [Index("loggedOn")])
internal data class MealEntity(
    @PrimaryKey val id: String,
    /** Full date-time with time zone, e.g. 2026-11-16T12:30+08:00[Asia/Hong_Kong] */
    val loggedAt: String,
    /** For sorting: milliseconds since 1970 */
    @ColumnInfo(name = "logged_at_ms") val loggedAtMillis: Long,
    /** The local day the user sees in the diary, e.g. 2026-11-16 (used to group by day) */
    val loggedOn: String,
    val name: String,
    val source: String,
    val photoPath: String?,
    val scorePoints: Int?,
    val scoreMaxPoints: Int?,
    val scoreRulesVersion: String?,
    /** The meal's items as JSON (see itemsToJson below) */
    val itemsJson: String
)

@Dao
internal interface MealDao {
    @Query("SELECT * FROM meals ORDER BY logged_at_ms")
    suspend fun allMeals(): List<MealEntity>

    @Query("SELECT * FROM meals WHERE loggedOn = :day ORDER BY logged_at_ms")
    suspend fun mealsOn(day: String): List<MealEntity>

    @Upsert
    suspend fun upsert(meal: MealEntity)

    @Query("DELETE FROM meals WHERE id = :id")
    suspend fun delete(id: String)
}

@Database(entities = [MealEntity::class], version = 1, exportSchema = false)
internal abstract class NutriQuestDatabase : RoomDatabase() {
    abstract fun meals(): MealDao

    companion object {
        @Volatile private var instance: NutriQuestDatabase? = null

        fun get(context: Context): NutriQuestDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, NutriQuestDatabase::class.java, "nutriquest.db")
                .build().also { instance = it }
        }
    }
}

// ---------------------------------------------------------------------------
// Repository: what the ViewModel calls
// ---------------------------------------------------------------------------

internal class MealRepository(
    private val dao: MealDao,
    private val photoFolder: File
) {
    constructor(context: Context) : this(
        NutriQuestDatabase.get(context).meals(),
        File(context.filesDir, "meal_photos")
    )

    /** All saved meals, oldest first. Rows that can't be read are skipped. */
    suspend fun loadMeals(): List<Meal> = withContext(Dispatchers.IO) {
        dao.allMeals().mapNotNull { row -> runCatching { row.toMeal() }.getOrNull() }
    }

    /**
     * Moves a photo from the temporary cache into the permanent photo folder.
     * Fast (a rename on the same disk). Returns the new file, or the original
     * if it's already permanent.
     */
    fun keepPhoto(photo: File?): File? {
        if (photo == null || !photo.exists()) return photo
        if (photo.parentFile == photoFolder) return photo
        if (!photoFolder.exists() && !photoFolder.mkdirs()) throw IOException("Couldn't create the photo folder.")
        val target = File(photoFolder, photo.name)
        if (!photo.renameTo(target)) {
            photo.copyTo(target, overwrite = true)
            photo.delete()
        }
        return target
    }

    suspend fun save(meal: Meal) = withContext(Dispatchers.IO) { dao.upsert(meal.toEntity()) }

    suspend fun delete(meal: Meal) = withContext(Dispatchers.IO) {
        dao.delete(meal.id.toString())
        meal.photo?.takeIf { it.parentFile == photoFolder }?.delete()
    }
}

// ---------------------------------------------------------------------------
// Converting between Meal (used by the app) and MealEntity (stored row)
// ---------------------------------------------------------------------------

internal fun Meal.toEntity() = MealEntity(
    id = id.toString(),
    loggedAt = loggedAt.toString(),
    loggedAtMillis = loggedAt.toInstant().toEpochMilli(),
    loggedOn = loggedAt.toLocalDate().toString(),
    name = name,
    source = source.wireValue,
    photoPath = photo?.absolutePath,
    scorePoints = score?.points,
    scoreMaxPoints = score?.maxPoints,
    scoreRulesVersion = score?.rulesVersion,
    itemsJson = itemsToJson(items)
)

internal fun MealEntity.toMeal() = Meal(
    id = UUID.fromString(id),
    loggedAt = ZonedDateTime.parse(loggedAt),
    name = name,
    items = itemsFromJson(itemsJson),
    photo = photoPath?.let(::File)?.takeIf { it.exists() },
    source = MealSource.entries.firstOrNull { it.wireValue == source } ?: MealSource.MANUAL,
    score = storedScore()
)

private fun MealEntity.storedScore(): MealScore? {
    val points = scorePoints ?: return null
    val max = scoreMaxPoints ?: return null
    val rules = scoreRulesVersion ?: return null
    return runCatching { MealScore(points, max, rules) }.getOrNull()
}

internal fun itemsToJson(items: List<MealItem>): String = JSONArray().apply {
    items.forEach { item ->
        val f = item.food
        put(JSONObject().apply {
            put("servings", item.servings)
            put("gramsPerServing", item.gramsPerServing ?: JSONObject.NULL)
            put("food", JSONObject().apply {
                put("id", f.id)
                put("name", f.name)
                put("emoji", f.emoji)
                put("calories", f.calories)
                put("protein", f.protein)
                put("carbs", f.carbs)
                put("fat", f.fat)
                put("chineseName", f.chineseName)
                put("aliases", JSONArray(f.aliases.toList()))
                put("referenceGrams", f.referenceGrams ?: JSONObject.NULL)
                put("defaultGrams", f.defaultGrams ?: JSONObject.NULL)
                put("nutritionSource", f.nutritionSource)
                put("sourceUrl", f.sourceUrl ?: JSONObject.NULL)
                put("referenceDescription", f.referenceDescription)
                put("sweetSnack", f.sweetSnack)
            })
        })
    }
}.toString()

internal fun itemsFromJson(json: String): List<MealItem> {
    val array = JSONArray(json)
    return List(array.length()) { index ->
        val row = array.getJSONObject(index)
        val f = row.getJSONObject("food")
        val aliases = f.optJSONArray("aliases")
        val food = Food(
            id = f.getString("id"),
            name = f.getString("name"),
            emoji = f.optString("emoji", "🍽️"),
            calories = f.getInt("calories"),
            protein = f.getInt("protein"),
            carbs = f.getInt("carbs"),
            fat = f.getInt("fat"),
            chineseName = f.optString("chineseName", f.getString("name")),
            aliases = if (aliases == null) emptySet() else List(aliases.length()) { aliases.getString(it) }.toSet(),
            referenceGrams = f.optIntOrNull("referenceGrams"),
            defaultGrams = f.optIntOrNull("defaultGrams"),
            nutritionSource = f.optString("nutritionSource", "demo_catalog"),
            sourceUrl = if (f.isNull("sourceUrl")) null else f.optString("sourceUrl"),
            referenceDescription = f.optString("referenceDescription", "Demo serving"),
            sweetSnack = f.optBoolean("sweetSnack", false)
        )
        MealItem(food, row.getInt("servings"), row.optIntOrNull("gramsPerServing"))
    }
}

private fun JSONObject.optIntOrNull(key: String): Int? = if (!has(key) || isNull(key)) null else getInt(key)
