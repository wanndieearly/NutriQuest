
package com.nutriquest.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// --------------------------------------------------
// 1. Basic data model
// Food represents one food item and its nutrition data.
// --------------------------------------------------

data class Food(
    val name: String,
    val calories: Int,
    val protein: Int,
    val carbs: Int,
    val fat: Int
)

// A meal can contain multiple food items.
data class Meal(
    val name: String,
    val foods: List<Food>
) {
    // Calculate the total nutrition of this meal.
    val calories get() = foods.sumOf { it.calories }
    val protein get() = foods.sumOf { it.protein }
    val carbs get() = foods.sumOf { it.carbs }
    val fat get() = foods.sumOf { it.fat }
}

// --------------------------------------------------
// 2. Sample food database
// These values are for prototype demonstration only.
// --------------------------------------------------

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

// --------------------------------------------------
// 3. Main Activity
// Android starts the application from this Activity.
// --------------------------------------------------

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                NutriQuestHome()
            }
        }
    }
}

// --------------------------------------------------
// 4. Main Home Screen
// --------------------------------------------------

@Composable
fun NutriQuestHome() {

    // State: stores all meals recorded during this session.
    val meals = remember {
        mutableStateListOf<Meal>()
    }

    // Calculate today's nutrition from all recorded meals.
    val totalCalories = meals.sumOf { it.calories }
    val totalProtein = meals.sumOf { it.protein }
    val totalCarbs = meals.sumOf { it.carbs }
    val totalFat = meals.sumOf { it.fat }

    // Controls whether the Add Meal dialog is visible.
    var showAddMeal by remember { mutableStateOf(false) }

    val green = Color(0xFF285943)
    val cream = Color(0xFFF7F6EE)

    Scaffold(
        containerColor = cream
    ) { padding ->

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {

            // App title
            item {
                Text(
                    text = "🥑 NutriQuest",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = green
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

            // Mascot and main action
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFE5F0DF)
                    ),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "YOUR DAILY QUEST",
                                color = green,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Eat smart.\nPlay hard!",
                                fontSize = 23.sp,
                                fontWeight = FontWeight.Bold,
                                color = green
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Button(
                                onClick = {
                                    showAddMeal = true
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = green
                                )
                            ) {
                                Text("+ Add Meal")
                            }
                        }

                        Text(
                            text = "🥑",
                            fontSize = 65.sp
                        )
                    }
                }
            }

            // Daily score card
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

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                "Daily Nutrition Score",
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                "Scoring will be connected in Step 3.",
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        Text(
                            "-- / 100",
                            fontWeight = FontWeight.Bold,
                            color = green
                        )
                    }
                }
            }

            // Nutrition overview
            item {
                Text(
                    "Today's Nutrition",
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp)
                    ) {

                        Text(
                            "🔥 $totalCalories / 2200 kcal",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        LinearProgressIndicator(
                            progress = {
                                (totalCalories / 2200f).coerceIn(0f, 1f)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            color = green
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        NutritionRow(
                            "Protein",
                            totalProtein,
                            110,
                            Color(0xFF285943)
                        )

                        NutritionRow(
                            "Carbs",
                            totalCarbs,
                            275,
                            Color(0xFFE5A443)
                        )

                        NutritionRow(
                            "Fat",
                            totalFat,
                            73,
                            Color(0xFF6D9CD1)
                        )
                    }
                }
            }

            // Recent meals
            item {
                Text(
                    "Recent Meals 🍽️",
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (meals.isEmpty()) {
                item {
                    Text(
                        "No meals yet. Start your first quest!",
                        color = Color.Gray
                    )
                }
            } else {
                items(meals.size) { index ->
                    MealCard(meal = meals[index])
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

    // Show food selection dialog when user clicks Add Meal.
    if (showAddMeal) {
        AddMealDialog(
            onDismiss = {
                showAddMeal = false
            },
            onSave = { selectedFoods ->

                // Create a new meal from selected foods.
                meals.add(
                    Meal(
                        name = "My Meal",
                        foods = selectedFoods
                    )
                )

                showAddMeal = false
            }
        )
    }
}

// --------------------------------------------------
// 5. Nutrition progress row
// Displays one macro and its progress.
// --------------------------------------------------

@Composable
fun NutritionRow(
    name: String,
    amount: Int,
    target: Int,
    color: Color
) {
    Column(
        modifier = Modifier.padding(bottom = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                name,
                fontWeight = FontWeight.Medium
            )

            Text("$amount / $target g")
        }

        Spacer(modifier = Modifier.height(6.dp))

        LinearProgressIndicator(
            progress = {
                (amount.toFloat() / target).coerceIn(0f, 1f)
            },
            modifier = Modifier.fillMaxWidth(),
            color = color
        )
    }
}

// --------------------------------------------------
// 6. Meal card
// Shows foods and calculated nutrition.
// --------------------------------------------------

@Composable
fun MealCard(meal: Meal) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                meal.name,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                meal.foods.joinToString { it.name },
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                "${meal.calories} kcal  |  " +
                        "P: ${meal.protein}g  " +
                        "C: ${meal.carbs}g  " +
                        "F: ${meal.fat}g",
                color = Color(0xFF285943),
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// --------------------------------------------------
// 7. Add Meal dialog
// User can select multiple food items.
// --------------------------------------------------

@Composable
fun AddMealDialog(
    onDismiss: () -> Unit,
    onSave: (List<Food>) -> Unit
) {
    // Store the foods selected by the user.
    val selectedFoods = remember {
        mutableStateListOf<Food>()
    }

    val calories = selectedFoods.sumOf { it.calories }

    AlertDialog(
        onDismissRequest = onDismiss,

        title = {
            Text(
                "Build Your Meal 🍱",
                fontWeight = FontWeight.Bold
            )
        },

        text = {
            Column {
                Text("Select all foods in your meal.")

                Spacer(modifier = Modifier.height(8.dp))

                // Display all available food options.
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

                                    if (checked) {
                                        selectedFoods.add(food)
                                    } else {
                                        selectedFoods.remove(food)
                                    }
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

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    "${selectedFoods.size} foods selected · $calories kcal",
                    fontWeight = FontWeight.Bold
                )
            }
        },

        confirmButton = {
            TextButton(
                enabled = selectedFoods.isNotEmpty(),
                onClick = {
                    onSave(selectedFoods.toList())
                }
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
