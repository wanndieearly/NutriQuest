package com.nutriquest.app

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.File
import java.time.LocalDate
import java.util.UUID

internal enum class AppPage(val label: String, val icon: String) {
    HOME("Home", "🏠"),
    DIARY("Diary", "📖"),
    CAMERA("Camera", "📷"),
    GROUPS("Groups", "🏆"),   // F8
    PROFILE("Profile", "👤")
}

internal data class MealDraft(
    val returnPage: AppPage,
    val photo: File? = null,
    val name: String = "",
    val items: List<MealItem> = emptyList(),
    val source: MealSource = MealSource.MANUAL,
    val id: UUID = UUID.randomUUID()
)

internal class MealLogViewModel(application: Application) : AndroidViewModel(application) {
    private val localChef = OnDeviceNutriRoast(application)
    private val profileStore = ProfileStore(application)
    var profile by mutableStateOf(profileStore.load())
        private set
    var profileDraft by mutableStateOf(ProfileDraft(profile))
        private set
    var profileMessage by mutableStateOf<String?>(null)
        private set
    var profileError by mutableStateOf<String?>(null)
        private set
    var savingProfile by mutableStateOf(false)
        private set
    var importingPhoto by mutableStateOf(false)
        private set
    var photoError by mutableStateOf<String?>(null)
        private set
    var roastModel by mutableStateOf<RoastModelState>(
        if (localChef.hasModel()) RoastModelState.Installed else RoastModelState.Missing
    )
        private set
    var page by mutableStateOf(AppPage.HOME)
        private set
    var draft by mutableStateOf<MealDraft?>(null)
        private set
    var editing by mutableStateOf(false)
        private set

    private val loggedMeals = mutableStateListOf<Meal>()
    val meals: List<Meal> get() = loggedMeals
    private val roasts = mutableStateMapOf<RoastKey, RoastState>()

    // ---- F6 meal storage + F7 scores (Joe) ----
    private val mealRepository = MealRepository(application)
    /** False until saved meals have been read from the database at start-up. */
    var mealsLoaded by mutableStateOf(false)
        private set
    var storageError by mutableStateOf<String?>(null)
        private set
    /** The day the Diary tab is showing. */
    var diaryDate by mutableStateOf(LocalDate.now())
        private set

    init {
        viewModelScope.launch {
            try {
                val saved = mealRepository.loadMeals()
                // Put saved meals first, skipping any already in the list
                loggedMeals.addAll(0, saved.filter { stored -> loggedMeals.none { it.id == stored.id } })
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (BuildConfig.DEBUG) Log.w("MealStorage", "Loading saved meals failed", error)
                storageError = "Saved meals couldn’t be loaded."
            } finally {
                mealsLoaded = true
            }
        }
    }

    fun showDiaryDay(date: LocalDate) {
        diaryDate = minOf(date, LocalDate.now())
    }

    /** F7: the daily score for [date] against the current profile targets (null if no meals). */
    fun scoreFor(date: LocalDate): DailyScoreResult? = dailyScore(date, loggedMeals, profile.scoreTargets())

    fun deleteMeal(meal: Meal) {
        if (!loggedMeals.remove(meal)) return
        roasts.clear()
        viewModelScope.launch {
            try {
                mealRepository.delete(meal)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                storageError = "That meal couldn’t be deleted from the phone."
            }
        }
    }

    private fun persist(meal: Meal) {
        viewModelScope.launch {
            try {
                mealRepository.save(meal)
                storageError = null
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (BuildConfig.DEBUG) Log.w("MealStorage", "Saving a meal failed", error)
                storageError = "This meal couldn’t be saved on the phone. It will disappear when the app closes."
            }
        }
    }
    // ---- end F6/F7 ----

    fun editProfile(value: ProfileDraft) {
        profileDraft = value
        profileMessage = null
        profileError = null
    }

    fun saveProfile() {
        if (savingProfile) return
        val snapshot = profileDraft
        val settings = snapshot.validated()
        if (settings == null) {
            profileError = "Enter positive whole numbers up to 100000 for all four targets."
            return
        }
        savingProfile = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { profileStore.save(settings) }
                profile = settings
                if (profileDraft == snapshot) profileDraft = ProfileDraft(settings)
                roasts.clear()
                profileMessage = "Profile saved on this device."
                profileError = null
            } catch (error: IOException) {
                profileError = error.message
            } finally {
                savingProfile = false
            }
        }
    }

    fun importRoastModel(uri: Uri) {
        if (roastModel == RoastModelState.Importing || roastModel == RoastModelState.Installed) return
        roastModel = RoastModelState.Importing
        viewModelScope.launch {
            roastModel = try {
                localChef.importModel(uri)
                RoastModelState.Installed
            } catch (error: CancellationException) {
                throw error
            } catch (error: IOException) {
                RoastModelState.ImportFailed(error.message ?: "The file couldn’t be imported.")
            } catch (_: SecurityException) {
                RoastModelState.ImportFailed("Android denied access to that file. Select it again.")
            }
        }
    }

    private fun contextMeals(scope: RoastScope, meals: List<Meal>): List<Meal> =
        if (scope == RoastScope.DAY) meals else loggedMeals.filter {
            it.loggedAt.toLocalDate() == meals.firstOrNull()?.loggedAt?.toLocalDate()
        }

    fun roastState(scope: RoastScope, meals: List<Meal>): RoastState =
        roasts[RoastKey(scope, meals.map { it.id }, profile, contextMeals(scope, meals).map { it.id })] ?: RoastState.Idle

    fun requestRoast(scope: RoastScope, meals: List<Meal>) {
        if (meals.isEmpty() || roastModel != RoastModelState.Installed) return
        val context = contextMeals(scope, meals).toList()
        val key = RoastKey(scope, meals.map { it.id }, profile, context.map { it.id })
        val feedback = goalFeedback(meals, context, key.profile)
        if (roasts[key] == RoastState.Loading) return
        roasts[key] = RoastState.Loading
        viewModelScope.launch {
            val reply = try {
                val reminder = when (key.profile.tone) {
                    RoastTone.ADAPTIVE, RoastTone.GENTLE -> feedback.reminder
                    RoastTone.CHEEKY, RoastTone.SPICY, RoastTone.COCKY -> ""
                }
                RoastState.Ready(localChef.roast(scope, meals, key.profile, context), reminder)
            } catch (error: CancellationException) {
                throw error
            } catch (error: IOException) {
                RoastState.Unavailable(error.message ?: "NutriRoast is unavailable.")
            } catch (error: Exception) {
                if (BuildConfig.DEBUG) Log.w("NutriRoast", "On-device generation failed", error)
                RoastState.Unavailable("This device couldn’t run the local chef. Close other apps and try again.")
            } catch (_: LinkageError) {
                RoastState.Unavailable("The local chef needs a compatible 64-bit Android device.")
            }
            if (roasts[key] == RoastState.Loading) roasts[key] = reply
        }
    }

    fun selectPage(page: AppPage) {
        if (this.page == page) return
        if (draft != null) cancelDraft()
        this.page = page
        photoError = null
    }

    fun startMeal() {
        draft = draft ?: MealDraft(returnPage = page)
        editing = true
    }

    fun usePhoto(photo: File) {
        if (page != AppPage.CAMERA || editing) {
            photo.delete()
            return
        }
        startMeal()
        draft = draft?.copy(photo = photo, source = MealSource.CAMERA)
    }

    fun importMealPhoto(uri: Uri) {
        if (importingPhoto) return
        val expectedId = draft?.id
        val expectedPage = page
        val wasEditing = editing
        importingPhoto = true
        photoError = null
        viewModelScope.launch {
            try {
                val photo = MealPhotoImporter(getApplication()).copy(uri)
                if (draft?.id != expectedId || page != expectedPage || editing != wasEditing) {
                    photo.delete()
                    return@launch
                }
                val previous = draft?.photo
                startMeal()
                draft = draft?.copy(photo = photo, source = MealSource.ALBUM)
                previous?.delete()
            } catch (error: CancellationException) {
                throw error
            } catch (error: IOException) {
                photoError = error.message ?: "That photo couldn’t be opened. Try another image."
            } catch (_: SecurityException) {
                photoError = "Android couldn’t read that photo. Select it again."
            } finally {
                importingPhoto = false
            }
        }
    }

    fun renameMeal(name: String) {
        draft = draft?.copy(name = name.take(60))
    }

    fun changeServings(food: Food, delta: Int) {
        val current = draft ?: return
        val oldCount = current.items.find { it.food.id == food.id }?.servings ?: 0
        val count = (oldCount + delta).coerceIn(0, 20)
        val otherItems = current.items.filterNot { it.food.id == food.id }
        val previous = current.items.find { it.food.id == food.id }
        val items = if (count == 0) otherItems else otherItems + (previous?.copy(servings = count) ?: MealItem(food, count))
        draft = current.copy(items = items)
    }

    fun changePortion(food: Food, grams: Int) {
        if (food.referenceGrams == null || grams !in 1..2000) return
        val current = draft ?: return
        draft = current.copy(items = current.items.map { if (it.food.id == food.id) it.copy(gramsPerServing = grams) else it })
    }

    fun setFoodNutrition(food: Food) {
        val current = draft ?: return
        val previous = current.items.find { it.food.id == food.id }
        val item = MealItem(food, previous?.servings ?: 1)
        draft = current.copy(items = current.items.filterNot { it.food.id == food.id } + item)
    }

    fun retakePhoto() {
        val current = draft ?: return
        current.photo?.delete()
        draft = current.copy(photo = null, source = MealSource.MANUAL)
        editing = false
        page = AppPage.CAMERA
    }

    fun cancelDraft() {
        val current = draft ?: return
        current.photo?.delete()
        draft = null
        editing = false
        page = current.returnPage
    }

    fun saveMeal() {
        if (importingPhoto) return
        val current = draft ?: return
        if (current.items.isEmpty()) return
        // F6: move the photo out of the temporary cache so it survives restarts
        val photo = try {
            mealRepository.keepPhoto(current.photo)
        } catch (_: IOException) {
            current.photo
        }
        val mealsToday = loggedMeals.count { it.loggedAt.toLocalDate() == LocalDate.now() }
        val meal = Meal(
            name = current.name.trim().ifEmpty { "Meal ${mealsToday + 1}" },
            items = current.items,
            photo = photo,
            source = current.source,
            score = mealScore(current.items, profile.scoreTargets())   // F7: meal fit score
        )
        loggedMeals.add(meal)
        persist(meal)                                                   // F6: save to the database
        diaryDate = meal.loggedAt.toLocalDate()
        roasts.clear()
        draft = null
        editing = false
        page = AppPage.DIARY
    }

    override fun onCleared() {
        // Only the unsaved draft photo is temporary; saved meal photos are kept (F6)
        draft?.photo?.delete()
    }
}
