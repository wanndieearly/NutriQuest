package com.nutriquest.app

import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.UUID

@Composable
internal fun MealEntryScreen(
    draft: MealDraft,
    onBack: () -> Unit,
    onRetake: () -> Unit,
    onNameChange: (String) -> Unit,
    onServingChange: (Food, Int) -> Unit,
    onPortionChange: (Food, Int) -> Unit,
    onFoodNutrition: (Food) -> Unit,
    onImportPhoto: (Uri) -> Unit,
    importingPhoto: Boolean,
    photoError: String?,
    onSave: () -> Unit
) {
    var search by rememberSaveable { mutableStateOf("") }
    var nutritionEditor by remember { mutableStateOf<MealItem?>(null) }
    var portionEditor by remember { mutableStateOf<MealItem?>(null) }
    fun editUnknown(label: String) {
        nutritionEditor = MealItem(Food("custom_${UUID.randomUUID()}", label, "🍽️", 0, 0, 0, 0, nutritionSource = "user_entered", referenceDescription = "Needs nutrition"), 1)
    }
    nutritionEditor?.let { item ->
        FoodNutritionEditor(item, { nutritionEditor = null }) { onFoodNutrition(it); nutritionEditor = null }
    }
    portionEditor?.let { item ->
        FoodPortionEditor(item, { portionEditor = null }) { onPortionChange(item.food, it); portionEditor = null }
    }
    val nutrition = draft.items.nutrition()
    val servings = draft.items.sumOf { it.servings }
    val context = LocalContext.current
    val detector = remember(context) { FoodDetector(context) }
    val photo = draft.photo
    val scan by produceState<FoodScan>(FoodScan.Idle, photo, detector) {
        value = if (photo == null) FoodScan.Idle else {
            value = FoodScan.Running
            detector.scan(photo)
        }
    }
    val typing = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = NutriCream,
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBack) { Text("‹ Back") }
                Text(
                    "NUTRIQUEST  /  MEAL LOG",
                    modifier = Modifier.padding(start = 8.dp),
                    color = NutriGreen,
                    letterSpacing = 1.6.sp,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        bottomBar = {
            AnimatedVisibility(!typing) { MealEntryFooter(nutrition, servings, !importingPhoto, onSave) }
        }
    ) { insets ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(insets),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Surface(shape = RoundedCornerShape(26.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .background(Brush.linearGradient(listOf(Color(0xFF173C31), NutriGreen)))
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "THE QUEST KITCHEN",
                                color = Color(0xFFE9D6A0),
                                letterSpacing = 2.sp,
                                style = MaterialTheme.typography.labelSmall
                            )
                            Spacer(Modifier.height(5.dp))
                            Text(
                                "Build your plate",
                                color = Color.White,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Pick your foods, then tap + for extra servings.",
                                color = Color(0xFFDBE8D7),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Text("🥑", fontSize = 44.sp, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
            item {
                AlbumPickerButton(enabled = !importingPhoto, onPick = onImportPhoto)
                photoError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
            if (draft.photo != null) {
                item {
                    MealPhoto(draft.photo, modifier = Modifier.fillMaxWidth().height(164.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Photo attached",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelLarge,
                            color = NutriGreen
                        )
                        TextButton(onClick = onRetake) { Text("Retake photo") }
                    }
                }
                item { ScanSuggestions(scan, draft.items, onServingChange, ::editUnknown) }
            }
            item {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = onNameChange,
                    label = { Text("Meal name (optional)") },
                    placeholder = { Text("e.g. Campus lunch") },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
            item {
                Column(Modifier.padding(top = 8.dp, bottom = 2.dp)) {
                    Text("Pick your ingredients", style = MaterialTheme.typography.titleLarge, color = NutriGreen)
                    Text(
                        "Reference estimates · confirm food type and portion",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(search, { search = it }, label = { Text("Search foods / 搜尋食物") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    TextButton(onClick = { editUnknown("") }) { Text("+ Enter a food / nutrition label") }
                }
            }
            val choices = (foodList + draft.items.map { it.food }).distinctBy { it.id }.filter { food ->
                search.isBlank() || food.names().any { it.contains(search.trim(), ignoreCase = true) }
            }
            items(choices, key = { it.id }) { catalog ->
                val item = draft.items.find { it.food.id == catalog.id } ?: MealItem(catalog, 0)
                FoodPickerRow(item, onServingChange, { nutritionEditor = item.copy(servings = 1) }, { portionEditor = item })
            }
        }
    }
}

@Composable
private fun FoodPickerRow(item: MealItem, onServingChange: (Food, Int) -> Unit, onEditNutrition: () -> Unit, onEditPortion: () -> Unit) {
    val food = item.food
    val count = item.servings
    val nutrition = listOf(item.copy(servings = 1)).nutrition()
    val background by animateColorAsState(if (count > 0) Color(0xFFE5F0DF) else Color.White, label = "Ingredient added")
    Surface(
        color = background,
        shape = RoundedCornerShape(18.dp)
    ) {
        Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(38.dp).background(Color(0xFFF2ECDD), CircleShape),
                contentAlignment = Alignment.Center
            ) { Text(food.emoji, fontSize = 20.sp) }
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(food.name, fontWeight = FontWeight.SemiBold)
                Text(
                    "≈ ${nutrition.energyKcal} kcal / ${item.portionLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (count > 0) {
                IconButton(
                    modifier = Modifier.semantics { contentDescription = "Remove ${food.name}" },
                    onClick = { onServingChange(food, -1) }
                ) { Text("−", fontSize = 20.sp, color = NutriGreen) }
                AnimatedContent(count, label = "Serving count") {
                    Text(it.toString(), color = NutriGreen, fontWeight = FontWeight.Bold)
                }
            }
            FilledTonalIconButton(
                enabled = count < 20,
                modifier = Modifier.semantics { contentDescription = "Add ${food.name}" },
                onClick = { onServingChange(food, 1) }
            ) { Text("+", fontSize = 21.sp) }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onEditNutrition) { Text("Edit nutrition", style = MaterialTheme.typography.labelSmall) }
            if (count > 0 && food.referenceGrams != null) {
                TextButton(onClick = onEditPortion) { Text("Portion: ${item.portionLabel}", style = MaterialTheme.typography.labelSmall) }
            } else {
                Text(if (food.nutritionSource == "demo_catalog") "Demo serving" else if (food.nutritionSource == "user_entered") "Your entry" else "Reference estimate", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(12.dp))
            }
        }
        }
    }
}

@Composable
private fun ScanSuggestions(
    scan: FoodScan,
    items: List<MealItem>,
    onServingChange: (Food, Int) -> Unit,
    onUnknown: (String) -> Unit
) {
    Surface(color = Color(0xFFE5F0DF), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text("🔎 Food scout", color = NutriGreen, fontWeight = FontWeight.Bold)
            when (scan) {
                FoodScan.Idle, FoodScan.Running -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp))
                    Text("Looking for foods…")
                }
                is FoodScan.Unavailable -> Text(scan.reason)
                is FoodScan.Ready -> {
                    if (scan.guesses.isEmpty()) Text("No foods spotted. Build your plate below.")
                    scan.guesses.forEach { guess ->
                        val food = guess.food
                        if (food == null) {
                            TextButton(onClick = { onUnknown(guess.label) }) { Text("Enter nutrition for ${guess.label}") }
                        } else if (items.none { it.food.id == food.id }) {
                            TextButton(onClick = { onServingChange(food, 1) }) { Text("Add ${food.name} suggestion") }
                            Text("${food.referenceDescription} · confirm portion below", style = MaterialTheme.typography.bodySmall)
                        } else {
                            Text("${food.name} · added")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MealEntryFooter(nutrition: Nutrition, servings: Int, enabled: Boolean, onSave: () -> Unit) {
    Surface(color = Color.White, tonalElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${nutrition.energyKcal} kcal", fontWeight = FontWeight.Bold, color = NutriGreen)
                Text(if (servings == 1) "1 serving" else "$servings servings")
            }
            Text(
                "Protein ${nutrition.proteinGrams} g   ·   Carbs ${nutrition.carbohydrateGrams} g   ·   Fat ${nutrition.fatGrams} g",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
            )
            Button(
                enabled = servings > 0 && enabled,
                onClick = onSave,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save meal") }
        }
    }
}
