package com.nutriquest.app

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.UUID

internal enum class RoastScope {
    MEAL, DAY
}

internal data class RoastKey(val scope: RoastScope, val mealIds: List<UUID>, val profile: UserProfile, val contextMealIds: List<UUID> = mealIds)

internal sealed interface RoastState {
    data object Idle : RoastState
    data object Loading : RoastState
    data class Ready(val comment: String, val goalReminder: String = "") : RoastState
    data class Unavailable(val message: String) : RoastState
}

@Composable
internal fun NutriRoastCard(
    scope: RoastScope,
    state: RoastState,
    modelInstalled: Boolean,
    role: RoastRole,
    onRequest: () -> Unit
) {
    Surface(color = Color(0xFFFFEDD2), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text("${role.emoji} NutriRoast · ${role.label}", color = Color(0xFF673C15), fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            if (!modelInstalled) {
                Text("Import the chef model once to roast meals offline.")
                TextButton(onClick = onRequest) { Text("Set up local chef") }
                return@Column
            }
            AnimatedContent(state, label = "Chef reply") { reply ->
                when (reply) {
                    RoastState.Idle -> Text(
                        if (scope == RoastScope.MEAL) "Your goal. This meal. Time for an honest nudge."
                        else "How did today's choices match your plan?"
                    )
                    RoastState.Loading -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text("Chef is tasting on this device…")
                    }
                    is RoastState.Ready -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(reply.comment)
                        if (reply.goalReminder.isNotBlank()) {
                            HorizontalDivider(color = Color(0xFFE2CEAF))
                            Text("🎯 ${reply.goalReminder}", style = MaterialTheme.typography.bodySmall, color = NutriGreen)
                        }
                    }
                    is RoastState.Unavailable -> Text(reply.message)
                }
            }
            if (state !is RoastState.Loading) {
                TextButton(onClick = onRequest) {
                    Text(when {
                        state is RoastState.Unavailable -> "Try again"
                        state is RoastState.Ready -> "Another nudge"
                        scope == RoastScope.MEAL -> "Roast this meal"
                        else -> "Roast my day"
                    })
                }
            }
        }
    }
}

@Composable
internal fun RoastModelCard(state: RoastModelState, onImport: (Uri) -> Unit) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(onImport)
    }
    Surface(color = Color(0xFFFFEDD2), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("🔥 Local chef", color = Color(0xFF673C15), fontWeight = FontWeight.Bold)
            when (state) {
                RoastModelState.Installed -> Text("Qwen3-0.6B is ready for offline roasts.")
                RoastModelState.Importing -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("Copying and verifying the chef model…")
                }
                else -> {
                    Text("Choose the Qwen3-0.6B .litertlm file from the setup guide (about 329 MiB).")
                    if (state is RoastModelState.ImportFailed) {
                        Text(state.message, color = MaterialTheme.colorScheme.error)
                    }
                    Button(onClick = { picker.launch(arrayOf("*/*")) }) {
                        Text("Import roast model")
                    }
                }
            }
        }
    }
}
