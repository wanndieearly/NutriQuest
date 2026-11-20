package com.nutriquest.app

// ============================================================================
//  F7 / F6 — Score and diary UI pieces (Joe)
//
//  DailyScoreCard  — the day's score with its breakdown (Home and Diary)
//  MealScoreChip   — small "Fit 82" badge on each meal card
//  DiaryDayPicker  — ‹ date › row to browse saved meals day by day
// ============================================================================

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Colour for a 0–100 score: green when good, amber when middling, red when low. */
internal fun scoreColor(score: Int): Color = when {
    score >= 80 -> Color(0xFF285943)
    score >= 50 -> Color(0xFFB7791F)
    else -> Color(0xFFB3261E)
}

private fun scoreEmoji(score: Int) = when {
    score >= 90 -> "👑"
    score >= 80 -> "🔥"
    score >= 65 -> "💪"
    score >= 50 -> "🙂"
    else -> "😬"
}

@Composable
internal fun DailyScoreCard(result: DailyScoreResult?, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (result?.isFinal == true) "DAILY SCORE · FINAL" else "DAILY SCORE · SO FAR",
                        color = NutriGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp
                    )
                    if (result == null) {
                        Text("No meals logged yet", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("Log a meal to start today's score.", color = Color.Gray, fontSize = 13.sp)
                    } else {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("${result.score}", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = scoreColor(result.score))
                            Text(" / 100", fontSize = 16.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 8.dp))
                        }
                        Text(result.explanation, fontSize = 13.sp, color = Color.DarkGray)
                    }
                }
                if (result != null) Text(scoreEmoji(result.score), fontSize = 44.sp)
            }
            if (result != null) {
                Spacer(Modifier.height(12.dp))
                ScorePart("Calories", "45%", result.calorieScore)
                ScorePart("Protein · carbs · fat", "35%", result.macroScore)
                ScorePart("Meal balance (${result.mealCount} meal${if (result.mealCount == 1) "" else "s"})", "20%", result.mealFitAverage)
                if (!result.isFinal) {
                    Text(
                        "Updates as you log. Locked in at midnight.",
                        fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ScorePart(label: String, weight: String, score: Int) {
    val progress by animateFloatAsState(score / 100f, label = "Score part")
    Column(Modifier.padding(bottom = 8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("$label  ·  $weight", fontSize = 13.sp)
            Text("$score", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = scoreColor(score))
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            color = scoreColor(score)
        )
    }
}

@Composable
internal fun MealScoreChip(score: MealScore?) {
    if (score == null) return
    val points = score.points * 100 / score.maxPoints
    Surface(color = scoreColor(points).copy(alpha = 0.12f), shape = RoundedCornerShape(50)) {
        Text(
            "Fit $points",
            color = scoreColor(points),
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
        )
    }
}

private val dayFormat = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

@Composable
internal fun DiaryDayPicker(date: LocalDate, onChange: (LocalDate) -> Unit) {
    val today = LocalDate.now()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { onChange(date.minusDays(1)) }) { Text("‹", fontSize = 22.sp, color = NutriGreen) }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                when (date) {
                    today -> "Today"
                    today.minusDays(1) -> "Yesterday"
                    else -> date.format(dayFormat)
                },
                fontWeight = FontWeight.Bold, fontSize = 17.sp
            )
            if (date == today || date == today.minusDays(1)) {
                Text(date.format(dayFormat), fontSize = 12.sp, color = Color.Gray)
            }
        }
        TextButton(onClick = { onChange(date.plusDays(1)) }, enabled = date.isBefore(today)) {
            Text("›", fontSize = 22.sp, color = if (date.isBefore(today)) NutriGreen else Color.LightGray)
        }
    }
    if (date != today) {
        TextButton(onClick = { onChange(today) }, modifier = Modifier.fillMaxWidth()) { Text("Back to today", color = NutriGreen) }
    }
}
