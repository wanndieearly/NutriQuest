package com.nutriquest.app

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
internal fun ProfileScreen(log: MealLogViewModel) {
    val draft = log.profileDraft
    val settings = draft.settings
    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Surface(shape = RoundedCornerShape(26.dp)) {
                Column(
                    Modifier.fillMaxWidth()
                        .background(Brush.linearGradient(listOf(Color(0xFF173C31), NutriGreen)))
                        .padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("YOUR QUEST, YOUR RULES", color = Color(0xFFE9D6A0), style = MaterialTheme.typography.labelMedium)
                    Text("Make it personal 🥑", color = Color.White, style = MaterialTheme.typography.headlineMedium)
                    Text("Set your goals. Cast your chef. Choose the banter.", color = Color(0xFFDBE8D7))
                }
            }
        }
        item {
            ProfileSection("Your food quest") {
                OutlinedTextField(
                    value = settings.name,
                    onValueChange = { log.editProfile(draft.copy(settings = settings.copy(name = it.take(30)))) },
                    label = { Text("What should we call you?") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                ProfileChoice(
                    "Diet goal", DietGoal.entries, settings.goal, { it.label }
                ) { log.editProfile(draft.copy(settings = settings.copy(goal = it))) }
                OutlinedTextField(
                    value = settings.goalNotes,
                    onValueChange = { log.editProfile(draft.copy(settings = settings.copy(goalNotes = it.take(180)))) },
                    label = { Text("My diet goal") },
                    placeholder = { Text("e.g. Cook more lunches and try new vegetables") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )
            }
        }
        item {
            ProfileSection("Daily targets") {
                Text("Choose your own targets. These guide the progress bars.", style = MaterialTheme.typography.bodySmall)
                ProfileNumber("Daily calories (kcal)", draft.energy) { log.editProfile(draft.copy(energy = it)) }
                ProfileNumber("Protein (g)", draft.protein) { log.editProfile(draft.copy(protein = it)) }
                ProfileNumber("Carbs (g)", draft.carbs) { log.editProfile(draft.copy(carbs = it)) }
                ProfileNumber("Fat (g)", draft.fat) { log.editProfile(draft.copy(fat = it)) }
            }
        }
        item {
            ProfileSection("Cast your local chef") {
                ProfileChoice(
                    "Roast tone", RoastTone.entries, settings.tone, { it.label }
                ) { log.editProfile(draft.copy(settings = settings.copy(tone = it))) }
                if (settings.tone == RoastTone.COCKY) {
                    Text(
                        "講咗就要做，唔好淨係得把口。😏\nA cocky rival who calls out excuses and pushes you to keep your own plan.",
                        color = Color(0xFF9A3E19),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                ProfileChoice(
                    "Chef character", RoastRole.entries, settings.role, { "${it.emoji} ${it.label}" }
                ) { log.editProfile(draft.copy(settings = settings.copy(role = it))) }
                ProfileChoice(
                    "Roast language", RoastLanguage.entries, settings.language, { it.label }
                ) { log.editProfile(draft.copy(settings = settings.copy(language = it))) }
                Text("Every tone aims to keep you going. The banter targets choices and excuses.", style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            log.profileMessage?.let { Text(it, color = NutriGreen) }
            log.profileError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = log::saveProfile,
                enabled = !log.savingProfile,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (log.savingProfile) "Saving…" else "Save profile") }
        }
        item { RoastModelCard(log.roastModel, log::importRoastModel) }
    }
}

@Composable
private fun ProfileSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = Color.White, shape = RoundedCornerShape(22.dp)) {
        Column(
            Modifier.fillMaxWidth().animateContentSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = NutriGreen)
            content()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ProfileChoice(
    label: String,
    choices: List<T>,
    selected: T,
    choiceLabel: (T) -> String,
    onSelect: (T) -> Unit
) {
    Column {
        Text(label, fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            choices.forEach { choice ->
                FilterChip(
                    selected = selected == choice,
                    onClick = { onSelect(choice) },
                    label = { Text(choiceLabel(choice)) }
                )
            }
        }
    }
}

@Composable
private fun ProfileNumber(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.take(6)) },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
}
