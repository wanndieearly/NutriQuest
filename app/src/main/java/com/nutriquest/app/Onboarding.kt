package com.nutriquest.app

// ============================================================================
//  F1 — First-launch setup (Joe)
//
//  Four short steps create the local account:
//    1 Name      (also your Groups nickname)
//    2 About you (age, sex, height, weight, activity)
//    3 Goal      (diet goal → suggested targets, F2)
//    4 Chef      (roast tone and character)
//  "Skip for now" keeps the defaults; everything can be changed in Profile.
// ============================================================================

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val STEPS = 4

@Composable
internal fun OnboardingScreen(
    initial: UserProfile,
    initialBody: BodyStatsDraft,
    onFinish: (SetupResult) -> Unit,
    onSkip: () -> Unit
) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var name by rememberSaveable { mutableStateOf(initial.name) }
    var body by remember { mutableStateOf(initialBody) }
    var goal by remember { mutableStateOf(initial.goal) }
    var tone by remember { mutableStateOf(initial.tone) }
    var role by remember { mutableStateOf(initial.role) }
    val stats = body.validated()

    BackHandler(enabled = step > 0) { step-- }

    Column(Modifier.fillMaxSize().background(NutriCream).imePadding()) {
        Column(
            Modifier.fillMaxWidth()
                .background(Brush.linearGradient(listOf(Color(0xFF173C31), NutriGreen)))
                .statusBarsPadding()
                .padding(horizontal = 22.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("STEP ${step + 1} OF $STEPS", color = Color(0xFFE9D6A0), fontSize = 12.sp, letterSpacing = 1.5.sp)
            Text(
                listOf("Welcome to NutriQuest 🥑", "About you", "Your goal", "Cast your chef")[step],
                color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold
            )
            Text(
                listOf(
                    "Create your account on this phone. No password, no internet.",
                    "We use this to estimate how much energy you need.",
                    "Pick a goal and we'll suggest daily targets.",
                    "Who roasts your meals, and how hard?"
                )[step],
                color = Color(0xFFDBE8D7)
            )
            LinearProgressIndicator(
                progress = { (step + 1f) / STEPS },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                color = Color(0xFFE9D6A0), trackColor = Color(0x33FFFFFF)
            )
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            when (step) {
                0 -> {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(30) },
                        label = { Text("What should we call you?") },
                        supportingText = { Text("Friends see this name in Groups.") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Your meals and photos stay on this phone. Only daily scores are shared with groups you join.", fontSize = 13.sp, color = Color.Gray)
                }
                1 -> {
                    BodyStatsFields(body) { body = it }
                    if (stats == null && (body.age + body.heightCm + body.weightKg).isNotBlank()) {
                        body.problem?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
                    }
                }
                2 -> {
                    ProfileChoice("Diet goal", DietGoal.entries, goal, { it.label }) { goal = it }
                    stats?.let { SuggestedTargetsBox(suggestTargets(it, goal), goal) }
                    Text("You can change the numbers any time in Profile → Daily targets.", fontSize = 13.sp, color = Color.Gray)
                }
                else -> {
                    ProfileChoice("Chef character", RoastRole.entries, role, { "${it.emoji} ${it.label}" }) { role = it }
                    ProfileChoice("Roast tone", RoastTone.entries, tone, { it.label }) { tone = it }
                    Surface(color = Color(0xFFFFEDD2), shape = RoundedCornerShape(16.dp)) {
                        Text(
                            "${role.emoji} ${role.label} · ${tone.label}\nEvery tone aims to keep you going. The banter targets choices, not you.",
                            modifier = Modifier.padding(14.dp), color = Color(0xFF673C15)
                        )
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (step == 0) TextButton(onClick = onSkip) { Text("Skip for now", color = Color.Gray) }
            else TextButton(onClick = { step-- }) { Text("Back", color = NutriGreen) }
            Spacer(Modifier.weight(1f))
            val canContinue = when (step) {
                0 -> name.isNotBlank()
                1 -> stats != null
                else -> true
            }
            Button(
                onClick = {
                    if (step < STEPS - 1) step++
                    else if (stats != null) onFinish(SetupResult(name, stats, goal, suggestTargets(stats, goal), tone, role))
                },
                enabled = canContinue,
                colors = ButtonDefaults.buttonColors(containerColor = NutriGreen)
            ) { Text(if (step < STEPS - 1) "Next" else "Start my quest") }
        }
    }
}
