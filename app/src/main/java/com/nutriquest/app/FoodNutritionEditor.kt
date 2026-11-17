package com.nutriquest.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
internal fun FoodNutritionEditor(item: MealItem, onDismiss: () -> Unit, onSave: (Food) -> Unit) {
    var draft by remember(item) { mutableStateOf(FoodNutritionDraft.from(item)) }
    val valid = draft.validated()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Your food, your label") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Enter nutrition for ONE serving from its label or your own calculation. Empty fields are not treated as zero.")
                OutlinedTextField(draft.name, { draft = draft.copy(name = it.take(60)) }, label = { Text("Food name") }, singleLine = true)
                NutritionField("Calories per serving (kcal)", draft.energy) { draft = draft.copy(energy = it) }
                NutritionField("Protein per serving (g)", draft.protein) { draft = draft.copy(protein = it) }
                NutritionField("Carbs per serving (g)", draft.carbs) { draft = draft.copy(carbs = it) }
                NutritionField("Fat per serving (g)", draft.fat) { draft = draft.copy(fat = it) }
                NutritionField("Serving weight (g, optional)", draft.grams) { draft = draft.copy(grams = it) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(draft.sweetSnack, { draft = draft.copy(sweetSnack = it) })
                    Text("Sweet snack or drink")
                }
                Text("Saved as your own entry, rounded to whole kcal and grams. No nutrition is inferred from the photo.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(enabled = valid != null, onClick = { valid?.let(onSave) }) { Text("Use nutrition") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
internal fun FoodPortionEditor(item: MealItem, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var grams by remember(item) { mutableStateOf(item.gramsPerServing?.toString().orEmpty()) }
    val weight = grams.toIntOrNull()?.takeIf { it in 1..2000 }
    val nutrition = weight?.let { listOf(item.copy(servings = 1, gramsPerServing = it)).nutrition() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${item.food.name} portion") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Weight of ONE serving. The photo does not measure weight.")
                NutritionField("Weight per serving (g)", grams) { grams = it }
                if (nutrition != null) Text("≈ ${nutrition.energyKcal} kcal · P ${nutrition.proteinGrams} g · C ${nutrition.carbohydrateGrams} g · F ${nutrition.fatGrams} g")
                Text(item.food.referenceDescription, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(enabled = weight != null, onClick = { weight?.let(onSave) }) { Text("Update portion") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun NutritionField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(value, { onChange(it.take(8)) }, label = { Text(label) }, modifier = Modifier.fillMaxWidth(),
        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
}
