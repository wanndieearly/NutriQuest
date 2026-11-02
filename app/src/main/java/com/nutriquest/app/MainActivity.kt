package com.nutriquest.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// --------------------------------------------------
// Data models
// --------------------------------------------------

data class Food(
    val name: String,
    val calories: Int,
    val protein: Int,
    val carbs: Int,
    val fat: Int
)

data class Meal(
    val name: String,
    val foods: List<Food>
) {
    val calories: Int get() = foods.sumOf { it.calories }
    val protein: Int get() = foods.sumOf { it.protein }
    val carbs: Int get() = foods.sumOf { it.carbs }
    val fat: Int get() = foods.sumOf { it.fat }
}

// Demonstration data only. Replace with a nutrition database later.
val foodList = listOf(
    Food("Steamed rice", 260, 5, 57, 1),
    Food("Grilled chicken", 248, 47, 0, 5),
    Food("Boiled egg", 78, 6, 1, 5),
    Food("Vegetable salad", 80, 3, 12, 3),
    Food("Banana", 105, 1, 27, 0),
    Food("Oatmeal", 180, 6, 30, 4),
    Food("Tofu", 115, 12, 3, 7),
    Food("Milk tea", 200, 4, 30, 7)
)

private val NutriGreen = Color(0xFF285943)
private val NutriCream = Color(0xFFF7F6EE)

// --------------------------------------------------
// Main Activity
// --------------------------------------------------

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                NutriQuestApp()
            }
        }
    }
}

// --------------------------------------------------
// App shell: shared meal state + bottom navigation
// --------------------------------------------------

@Composable
fun NutriQuestApp() {
    // This list is shared by Home and Diary, so a meal added on one
    // page immediately appears on the other page.
    val meals = remember { mutableStateListOf<Meal>() }

    var selectedPage by remember { mutableStateOf("Home") }
    var showAddMeal by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = NutriCream,
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                val pages = listOf(
                    Triple("Home", "🏠", "Home"),
                    Triple("Diary", "📖", "Diary"),
                    Triple("Camera", "📷", "Camera"),
                    Triple("Profile", "👤", "Profile")
                )

                pages.forEach { (page, emoji, label) ->
                    NavigationBarItem(
                        selected = selectedPage == page,
                        onClick = { selectedPage = page },
                        icon = { Text(emoji, fontSize = 19.sp) },
                        label = { Text(label) }
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (selectedPage) {
                "Home" -> HomeScreen(
                    meals = meals,
                    onAddMeal = { showAddMeal = true }
                )

                "Diary" -> DiaryScreen(
                    meals = meals,
                    onAddMeal = { showAddMeal = true }
                )

                "Camera" -> CameraScreen(
                    onOpenDiary = { selectedPage = "Diary" }
                )

                else -> ProfileScreen()
            }
        }
    }

    if (showAddMeal) {
        AddMealDialog(
            onDismiss = { showAddMeal = false },
            onSave = { selectedFoods ->
                meals.add(
                    Meal(
                        name = "Meal ${meals.size + 1}",
                        foods = selectedFoods
                    )
                )
                showAddMeal = false
            }
        )
    }
}

// --------------------------------------------------
// Home Screen
// --------------------------------------------------

@Composable
fun HomeScreen(
    meals: List<Meal>,
    onAddMeal: () -> Unit
) {
    val totalCalories = meals.sumOf { it.calories }
    val totalProtein = meals.sumOf { it.protein }
    val totalCarbs = meals.sumOf { it.carbs }
    val totalFat = meals.sumOf { it.fat }

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
                text = "Hey, Food Explorer!",
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
                    Column {
                        Text("Daily Nutrition Score", fontWeight = FontWeight.Bold)
                        Text(
                            "Scoring will be connected in a later step.",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text("-- / 100", fontWeight = FontWeight.Bold, color = NutriGreen)
                }
            }
        }

        item {
            Text("Today's Nutrition", fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text(
                        "🔥 $totalCalories / 2200 kcal",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { (totalCalories / 2200f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = NutriGreen
                    )
                    Spacer(Modifier.height(18.dp))
                    NutritionRow("Protein", totalProtein, 110, Color(0xFF285943))
                    NutritionRow("Carbs", totalCarbs, 275, Color(0xFFE5A443))
                    NutritionRow("Fat", totalFat, 73, Color(0xFF6D9CD1))
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
            items(meals) { meal ->
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
fun DiaryScreen(
    meals: List<Meal>,
    onAddMeal: () -> Unit
) {
    val totalCalories = meals.sumOf { it.calories }
    val totalProtein = meals.sumOf { it.protein }
    val totalCarbs = meals.sumOf { it.carbs }
    val totalFat = meals.sumOf { it.fat }

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
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE5F0DF))
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text("TODAY'S SUMMARY", color = NutriGreen, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "$totalCalories kcal",
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        color = NutriGreen
                    )
                    Text("Daily target: 2,200 kcal", color = Color.DarkGray)
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { (totalCalories / 2200f).coerceIn(0f, 1f) },
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

        if (meals.isEmpty()) {
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
            items(meals.reversed()) { meal ->
                MealCard(meal)
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

// --------------------------------------------------
// Camera placeholder - CameraX will be added next
// --------------------------------------------------




@Composable
fun ProfileScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("👤", fontSize = 64.sp)
        Text("Profile", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("User goals and settings will be added later.", color = Color.Gray)
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
fun MealCard(meal: Meal) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(meal.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(6.dp))
            Text(meal.foods.joinToString { it.name }, color = Color.Gray)
            Spacer(Modifier.height(8.dp))
            Text(
                "${meal.calories} kcal  |  P: ${meal.protein}g  C: ${meal.carbs}g  F: ${meal.fat}g",
                color = NutriGreen,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// --------------------------------------------------
// Add Meal dialog
// --------------------------------------------------

@Composable
fun AddMealDialog(
    onDismiss: () -> Unit,
    onSave: (List<Food>) -> Unit
) {
    val selectedFoods = remember { mutableStateListOf<Food>() }
    val calories = selectedFoods.sumOf { it.calories }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Build Your Meal 🍱", fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text("Select all foods in your meal.")
                Spacer(Modifier.height(8.dp))
                Column {
                    foodList.forEach { food ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = selectedFoods.contains(food),
                                onCheckedChange = { checked ->
                                    if (checked) selectedFoods.add(food)
                                    else selectedFoods.remove(food)
                                }
                            )
                            Column {
                                Text(food.name)
                                Text(
                                    "${food.calories} kcal",
                                    color = Color.Gray,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "${selectedFoods.size} foods selected · $calories kcal",
                    fontWeight = FontWeight.Bold
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = selectedFoods.isNotEmpty(),
                onClick = { onSave(selectedFoods.toList()) }
            ) {
                Text("Save Meal")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
