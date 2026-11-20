package com.nutriquest.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import java.time.LocalDate

internal val NutriGreen = Color(0xFF285943)
internal val NutriCream = Color(0xFFF7F6EE)

// --------------------------------------------------
// Main Activity
// --------------------------------------------------

class MainActivity : ComponentActivity() {
    private val mealLog by viewModels<MealLogViewModel>()
    private val groups by viewModels<GroupsViewModel>()   // F8
    private val account by viewModels<AccountViewModel>() // F1 / F9

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = NutriGreen,
                    secondary = Color(0xFF53634D),
                    tertiary = Color(0xFF825825),
                    surface = NutriCream,
                    secondaryContainer = Color(0xFFE5F0DF)
                )
            ) {
                NutriQuestApp(mealLog, groups, account)
            }
        }
    }
}

// --------------------------------------------------
// App shell: shared meal state + bottom navigation
// --------------------------------------------------

@Composable
private fun NutriQuestApp(log: MealLogViewModel, groups: GroupsViewModel, account: AccountViewModel) {
    val draft = log.draft
    // F8: upload daily scores to the groups server whenever meals or targets change
    LaunchedEffect(log.mealsLoaded, log.meals.size, log.profile, groups.nickname, groups.serverUrl) {
        if (log.mealsLoaded) groups.syncScores(log::scoreFor)
    }
    // F1: first-launch setup creates the local account
    if (!account.setupDone) {
        OnboardingScreen(
            initial = log.profile,
            initialBody = account.bodyDraft,
            onFinish = { result -> completeSetup(result, log, groups, account) },
            onSkip = account::skipSetup
        )
        return
    }
    BackHandler(enabled = draft != null, onBack = log::cancelDraft)
    val pages = rememberSaveableStateHolder()
    AnimatedContent(
        targetState = log.editing,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            (fadeIn(tween(210)) + slideInVertically(tween(240)) { it / 16 }) togetherWith fadeOut(tween(120))
        },
        label = "Kitchen entry"
    ) { editing ->
        if (editing) {
            if (draft != null) {
                MealEntryScreen(
                    draft = draft,
                    onBack = log::cancelDraft,
                    onRetake = log::retakePhoto,
                    onNameChange = log::renameMeal,
                    onServingChange = log::changeServings,
                    onPortionChange = log::changePortion,
                    onFoodNutrition = log::setFoodNutrition,
                    onImportPhoto = log::importMealPhoto,
                    importingPhoto = log.importingPhoto,
                    photoError = log.photoError,
                    onSave = log::saveMeal
                )
            } else {
                Spacer(Modifier.fillMaxSize())
            }
        } else {
            NutriQuestTabs(log, pages, groups, account)
        }
    }
}

/** F1: saves what the setup screens collected into the profile, the Groups nickname and the account. */
private fun completeSetup(result: SetupResult, log: MealLogViewModel, groups: GroupsViewModel, account: AccountViewModel) {
    log.editProfile(ProfileDraft(result.applyTo(log.profile)))
    log.saveProfile()
    groups.saveSettings(result.name, groups.serverUrl)
    account.finishSetup(result)
}

@Composable
private fun NutriQuestTabs(log: MealLogViewModel, pages: SaveableStateHolder, groups: GroupsViewModel, account: AccountViewModel) {
    Scaffold(
        containerColor = NutriCream,
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                AppPage.entries.forEach { page ->
                    NavigationBarItem(
                        selected = log.page == page,
                        onClick = { log.selectPage(page) },
                        icon = { Text(page.icon, fontSize = 19.sp) },
                        label = { Text(page.label) }
                    )
                }
            }
        }
    ) { padding ->
        AnimatedContent(
            targetState = log.page,
            modifier = Modifier.fillMaxSize().padding(padding),
            transitionSpec = {
                val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                (fadeIn(tween(200)) + slideInHorizontally(tween(220)) { direction * it / 12 }) togetherWith
                    fadeOut(tween(110))
            },
            label = "Bottom tabs"
        ) { page ->
            pages.SaveableStateProvider(page.name) {
                when (page) {
                    AppPage.HOME -> HomeScreen(log.meals, log.profile, log.scoreFor(LocalDate.now()), log::startMeal)
                    AppPage.DIARY -> DiaryScreen(log)
                    AppPage.CAMERA -> if (log.page == page && !log.editing) {
                        CameraScreen(
                            onPhotoCaptured = log::usePhoto,
                            onEnterManually = log::startMeal,
                            onImportPhoto = log::importMealPhoto,
                            importingPhoto = log.importingPhoto,
                            photoError = log.photoError
                        )
                    } else {
                        Spacer(Modifier.fillMaxSize())
                    }
                    AppPage.GROUPS -> GroupsScreen(groups, log.profile.name)   // F8
                    AppPage.PROFILE -> ProfileScreen(log, account)
                }
            }
        }
    }
}


// --------------------------------------------------
// Home Screen
// --------------------------------------------------

@Composable
private fun HomeScreen(
    meals: List<Meal>,
    profile: UserProfile,
    todayScore: DailyScoreResult?,
    onAddMeal: () -> Unit
) {
    val (totalCalories, totalProtein, totalCarbs, totalFat) = meals.todayNutrition()
    val variety = meals.loggedToday().flatMap { it.items }.map { it.food.id }.distinct().size

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "🥑 NutriQuest",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = NutriGreen
            )
            Text(
                text = "Hey, ${profile.name.ifBlank { "Food Explorer" }}!",
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 12.dp)
            )
            Text(
                text = "Ready for today's nutrition quest?",
                color = Color.Gray
            )
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE5F0DF)),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "YOUR DAILY QUEST",
                            color = NutriGreen,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Eat smart.\nPlay hard!",
                            fontSize = 23.sp,
                            fontWeight = FontWeight.Bold,
                            color = NutriGreen
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = onAddMeal,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NutriGreen
                            )
                        ) {
                            Text("+ Add Meal")
                        }
                    }
                    Text("🥑", fontSize = 65.sp)
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("⭐", fontSize = 35.sp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Ingredient explorer", fontWeight = FontWeight.Bold)
                        Text(
                            if (variety >= 3) "Quest complete! Three ingredients explored."
                            else "Log three different ingredients today.",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                        val questProgress by animateFloatAsState((variety / 3f).coerceAtMost(1f), label = "Daily quest")
                        LinearProgressIndicator(
                            progress = { questProgress },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            color = NutriGreen
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text("${variety.coerceAtMost(3)} / 3", fontWeight = FontWeight.Bold, color = NutriGreen)
                }
            }
        }

        item(key = "daily-score") {
            DailyScoreCard(todayScore)   // F7
        }

        item {
            Text("Today's Nutrition", fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text(
                        "🔥 $totalCalories / ${profile.energyKcal} kcal",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { (totalCalories / profile.energyKcal.toFloat()).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = NutriGreen
                    )
                    Spacer(Modifier.height(18.dp))
                    NutritionRow("Protein", totalProtein, profile.proteinGrams, Color(0xFF285943))
                    NutritionRow("Carbs", totalCarbs, profile.carbohydrateGrams, Color(0xFFE5A443))
                    NutritionRow("Fat", totalFat, profile.fatGrams, Color(0xFF6D9CD1))
                }
            }
        }

        item {
            Text("Recent Meals 🍽️", fontSize = 21.sp, fontWeight = FontWeight.Bold)
        }

        if (meals.isEmpty()) {
            item {
                Text("No meals yet. Start your first quest!", color = Color.Gray)
            }
        } else {
            items(meals.asReversed().take(5), key = { it.id.toString() }) { meal ->
                MealCard(meal)
            }
        }

        item {
            Text(
                "Nutrition values are estimates for demonstration.",
                color = Color.Gray,
                fontSize = 11.sp
            )
        }
    }
}

// --------------------------------------------------
// Diary Screen
// --------------------------------------------------

@Composable
private fun DiaryScreen(log: MealLogViewModel) {
    val profile = log.profile
    val date = log.diaryDate                                    // F6: day being browsed
    val isToday = date == LocalDate.now()
    val dayMeals = log.meals.filter { it.loggedAt.toLocalDate() == date }
    val onAddMeal = log::startMeal
    val (totalCalories, totalProtein, totalCarbs, totalFat) = dayMeals.flatMap { it.items }.nutrition()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                "Food Diary 📖",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = NutriGreen
            )
            Text(
                "Your meals, your progress.",
                color = Color.Gray,
                modifier = Modifier.padding(top = 4.dp)
            )
            DiaryDayPicker(date, log::showDiaryDay)
            log.storageError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE5F0DF))
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text(if (isToday) "TODAY'S SUMMARY" else "DAY SUMMARY", color = NutriGreen, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "$totalCalories kcal",
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        color = NutriGreen
                    )
                    Text("Daily target: ${profile.energyKcal} kcal", color = Color.DarkGray)
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { (totalCalories / profile.energyKcal.toFloat()).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = NutriGreen
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MacroValue("Protein", totalProtein, "g")
                        MacroValue("Carbs", totalCarbs, "g")
                        MacroValue("Fat", totalFat, "g")
                    }
                }
            }
        }

        if (dayMeals.isNotEmpty()) {
            item(key = "diary-score") {
                DailyScoreCard(log.scoreFor(date))   // F7
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Meal Records",
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onAddMeal) {
                    Text("+ Add meal", color = NutriGreen)
                }
            }
        }

        if (dayMeals.isNotEmpty()) {
            item(key = "day-roast") {
                DiaryRoast(log, RoastScope.DAY, dayMeals)
            }
        }
        if (dayMeals.isEmpty() && !isToday) {
            item {
                Text("No meals logged on this day.", color = Color.Gray)
            }
        } else if (dayMeals.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("🍽️", fontSize = 42.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("Your diary is empty", fontWeight = FontWeight.Bold)
                        Text(
                            "Add your first meal to start tracking.",
                            color = Color.Gray
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = onAddMeal,
                            colors = ButtonDefaults.buttonColors(containerColor = NutriGreen)
                        ) {
                            Text("Add your first meal")
                        }
                    }
                }
            }
        } else {
            items(dayMeals.asReversed(), key = { it.id.toString() }) { meal ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    MealCard(meal)
                    DiaryRoast(log, RoastScope.MEAL, listOf(meal))
                    TextButton(onClick = { log.deleteMeal(meal) }) {   // F6
                        Text("🗑 Delete meal", color = Color.Gray, fontSize = 12.sp)
                    }
                }
            }
        }

        item {
            Text(
                "Nutrition values are estimates for demonstration only.",
                color = Color.Gray,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun MacroValue(label: String, value: Int, unit: String) {
    Column {
        Text("$value$unit", fontWeight = FontWeight.Bold, color = NutriGreen)
        Text(label, fontSize = 12.sp, color = Color.DarkGray)
    }
}


@Composable
private fun DiaryRoast(log: MealLogViewModel, scope: RoastScope, meals: List<Meal>) {
    val ready = log.roastModel == RoastModelState.Installed
    NutriRoastCard(scope, log.roastState(scope, meals), ready, log.profile.role) {
        if (ready) log.requestRoast(scope, meals) else log.selectPage(AppPage.PROFILE)
    }
}

// --------------------------------------------------
// Reusable nutrition row and meal card
// --------------------------------------------------

@Composable
fun NutritionRow(name: String, amount: Int, target: Int, color: Color) {
    Column(Modifier.padding(bottom = 14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(name, fontWeight = FontWeight.Medium)
            Text("$amount / $target g")
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { (amount.toFloat() / target).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
            color = color
        )
    }
}

@Composable
private fun MealCard(meal: Meal) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(meal.name, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                MealScoreChip(meal.score)   // F7
            }
            Spacer(Modifier.height(6.dp))
            meal.photo?.let {
                MealPhoto(it, modifier = Modifier.fillMaxWidth().height(108.dp), maxSize = 480)
                Spacer(Modifier.height(8.dp))
            }
            Text(meal.items.joinToString { "${it.servings} × ${it.food.name} (${it.portionLabel})" }, color = Color.Gray)
            Spacer(Modifier.height(8.dp))
            Text(
                "${meal.calories} kcal  |  P: ${meal.protein}g  C: ${meal.carbs}g  F: ${meal.fat}g",
                color = NutriGreen,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

private fun List<Meal>.loggedToday(): List<Meal> {
    val today = LocalDate.now()
    return filter { it.loggedAt.toLocalDate() == today }
}

private fun List<Meal>.todayNutrition() = loggedToday().flatMap { it.items }.nutrition()
