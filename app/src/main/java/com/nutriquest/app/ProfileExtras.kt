package com.nutriquest.app

// ============================================================================
//  F1 / F2 / F9 — Extra Profile cards (Joe)
//
//  AccountCard    who is signed in on this phone, account ID, run setup again
//  AboutYouCard   body stats + "Use these targets" from the F2 presets
//  RemindersCard  daily reminder on/off, time, notification permission, test
// ============================================================================

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.format.DateTimeFormatter

// ---------------------------------------------------------------- account

@Composable
internal fun AccountCard(account: AccountViewModel, profileName: String) {
    ProfileSection("Your account") {
        Text(
            "Signed in on this phone as ${profileName.ifBlank { "Food Explorer" }}",
            fontWeight = FontWeight.SemiBold
        )
        Text("Account ID ${account.userId.take(8)}…", fontSize = 13.sp, color = Color.Gray)
        Text(
            "Your meals stay on this phone. Groups use this ID with your nickname to share daily scores only.",
            style = MaterialTheme.typography.bodySmall
        )
        OutlinedButton(onClick = account::restartSetup) { Text("Run setup again") }
    }
}

// ------------------------------------------------------- body stats (F1/F2)

@Composable
internal fun AboutYouCard(account: AccountViewModel, goal: DietGoal, onUseTargets: (SuggestedTargets) -> Unit) {
    val draft = account.bodyDraft
    var filled by remember { mutableStateOf(false) }
    ProfileSection("About you") {
        Text("Used to suggest targets for your diet goal. Never shared.", style = MaterialTheme.typography.bodySmall)
        BodyStatsFields(draft) { account.editBody(it); filled = false }
        Button(onClick = { account.saveBodyDraft() }, colors = ButtonDefaults.buttonColors(containerColor = NutriGreen)) {
            Text("Save body stats")
        }
        account.bodyMessage?.let { Text(it, color = NutriGreen) }
        account.bodyError?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        draft.validated()?.let { stats ->
            val targets = suggestTargets(stats, goal)
            SuggestedTargetsBox(targets, goal)
            OutlinedButton(onClick = { onUseTargets(targets); filled = true }) { Text("Use these targets") }
            if (filled) Text("Targets filled in below. Tap Save profile to keep them.", color = NutriGreen, fontSize = 13.sp)
        }
    }
}

/** The body-stats inputs, shared by the Profile card and the setup screens. */
@Composable
internal fun BodyStatsFields(draft: BodyStatsDraft, onChange: (BodyStatsDraft) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f)) { ProfileNumber("Age", draft.age) { onChange(draft.copy(age = it.take(3))) } }
        Box(Modifier.weight(1f)) { ProfileNumber("Height (cm)", draft.heightCm) { onChange(draft.copy(heightCm = it.take(3))) } }
        Box(Modifier.weight(1f)) { ProfileNumber("Weight (kg)", draft.weightKg) { onChange(draft.copy(weightKg = it.take(3))) } }
    }
    ProfileChoice("Sex (for the energy formula)", Sex.entries, draft.sex, { it.label }) { onChange(draft.copy(sex = it)) }
    ProfileChoice("Activity level", ActivityLevel.entries, draft.activity, { it.label }) { onChange(draft.copy(activity = it)) }
    Text(draft.activity.detail, fontSize = 12.sp, color = Color.Gray)
}

/** Suggested daily targets with the working shown. */
@Composable
internal fun SuggestedTargetsBox(targets: SuggestedTargets, goal: DietGoal) {
    Surface(color = Color(0xFFE5F0DF), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("SUGGESTED FOR “${goal.label.uppercase()}”", color = NutriGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                TargetFigure("%,d".format(targets.energyKcal), "kcal")
                TargetFigure("${targets.proteinGrams} g", "protein")
                TargetFigure("${targets.carbohydrateGrams} g", "carbs")
                TargetFigure("${targets.fatGrams} g", "fat")
            }
            Text(targets.explanation, fontSize = 12.sp, color = Color.DarkGray)
            Text("An estimate, not medical advice. Adjust it to suit you.", fontSize = 11.sp, color = Color.Gray)
        }
    }
}

@Composable
private fun TargetFigure(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = NutriGreen)
        Text(label, fontSize = 12.sp, color = Color.DarkGray)
    }
}

// ------------------------------------------------------------ reminders (F9)

private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")

@Composable
internal fun RemindersCard(account: AccountViewModel) {
    val context = LocalContext.current
    val settings = account.reminders
    var note by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }   // action waiting for the permission answer

    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) pending?.invoke() else note = "Notifications are off for NutriQuest. Turn them on in Android Settings → Apps."
        pending = null
    }
    /** Runs [action] once notifications are allowed, asking first on Android 13+. */
    fun withPermission(action: () -> Unit) {
        note = null
        if (ReminderNotifier.canNotify(context)) {
            action()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pending = action
            askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            note = "Notifications are off for NutriQuest. Turn them on in Android Settings → Apps."
        }
    }

    ProfileSection("Daily reminder") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Evening nudge", fontWeight = FontWeight.SemiBold)
                Text("Today's score and a goal reminder in your roast language.", fontSize = 13.sp, color = Color.Gray)
            }
            Switch(
                checked = settings.enabled,
                onCheckedChange = { on ->
                    if (on) withPermission { account.updateReminders(settings.copy(enabled = true)) }
                    else account.updateReminders(settings.copy(enabled = false))
                }
            )
        }
        ProfileChoice("Time", ReminderSettings.CHOICES, settings.time, { it.format(timeFormat) }) {
            account.updateReminders(settings.copy(time = it))
        }
        Text(
            if (settings.enabled) "On: every day at ${settings.time.format(timeFormat)}." else "Off.",
            fontSize = 13.sp, color = if (settings.enabled) NutriGreen else Color.Gray
        )
        OutlinedButton(onClick = { withPermission { account.sendTestReminder(); note = "Test reminder sent. Check the notification shade." } }) {
            Text("Send test now")
        }
        note?.let { Text(it, fontSize = 13.sp, color = Color.DarkGray) }
    }
}
