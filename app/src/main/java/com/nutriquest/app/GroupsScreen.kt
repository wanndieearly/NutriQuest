package com.nutriquest.app

// ============================================================================
//  F8 — Groups tab (Joe)
//
//  Set a nickname → create a group or join one with a 6-character code →
//  see today's or this week's ranking, with a crown for the leader, streaks
//  and emoji reactions. Refreshes every 10 seconds while the tab is open.
//  Only daily scores are shared: never weight, photos or food details.
// ============================================================================

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
internal fun GroupsScreen(vm: GroupsViewModel, profileName: String) {
    var showSettings by rememberSaveable { mutableStateOf(!vm.isSetUp) }

    // Load once when the tab opens, then poll the leaderboard every 10 s while it's visible
    LaunchedEffect(vm.isSetUp) { if (vm.isSetUp) vm.refresh() }
    LaunchedEffect(vm.selectedGroupId, vm.period, vm.isSetUp) {
        while (vm.isSetUp) {
            delay(10_000)
            vm.pollLeaderboard()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Groups 🏆", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = NutriGreen)
                    Text("Compete on daily diet scores. Only scores are shared, never weight or photos.", color = Color.Gray, fontSize = 13.sp)
                }
                if (vm.isSetUp) TextButton(onClick = { showSettings = !showSettings }) { Text(if (showSettings) "Done" else "⚙️") }
            }
            ConnectionStatus(vm)
        }

        if (showSettings || !vm.isSetUp) {
            item {
                SettingsCard(vm, profileName) { showSettings = false }
            }
        }

        if (vm.isSetUp) {
            vm.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            vm.message?.let { item { Text(it, color = NutriGreen, fontWeight = FontWeight.Medium) } }

            if (vm.groups.size > 1) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(vm.groups, key = { it.groupId }) { group ->
                            FilterChip(
                                selected = group.groupId == vm.selectedGroupId,
                                onClick = { vm.selectGroup(group.groupId) },
                                label = { Text(group.name) }
                            )
                        }
                    }
                }
            }

            val board = vm.leaderboard
            if (board != null) {
                item { GroupHeader(board) }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LeaderboardPeriod.entries.forEach { p ->
                            FilterChip(selected = vm.period == p, onClick = { vm.changePeriod(p) }, label = { Text(p.label) })
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = vm::refresh, enabled = !vm.busy) { Text(if (vm.busy) "…" else "↻ Refresh") }
                    }
                }
                items(board.members, key = { it.userId }) { entry ->
                    MemberRow(entry, board, isYou = entry.userId == vm.userId, onReact = { vm.react(entry.userId, it) })
                }
                item {
                    Text(
                        if (board.period == LeaderboardPeriod.WEEK) "Weekly score = average of each day's score since Monday. Resets every Monday."
                        else "Scores update as friends log meals. Tap an emoji to react.",
                        fontSize = 11.sp, color = Color.Gray
                    )
                }
            } else if (vm.groups.isEmpty() && vm.connected == true) {
                item { Text("You're not in a group yet. Create one, or join with a friend's code.", color = Color.DarkGray) }
            }

            item { CreateOrJoinCard(vm) }

            if (board != null) {
                item {
                    TextButton(onClick = vm::leaveSelectedGroup, enabled = !vm.busy) {
                        Text("Leave “${board.name}”", color = Color.Gray, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionStatus(vm: GroupsViewModel) {
    if (!vm.isSetUp) return
    val (dot, text) = when (vm.connected) {
        true -> Color(0xFF2E7D32) to "Connected to ${vm.serverUrl}"
        false -> Color(0xFFB3261E) to "Can’t reach ${vm.serverUrl}. Is the server running?"
        null -> Color.Gray to "Connecting to ${vm.serverUrl}…"
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
        Surface(color = dot, shape = CircleShape, modifier = Modifier.size(8.dp)) {}
        Text("  $text", fontSize = 12.sp, color = Color.DarkGray)
    }
}

@Composable
private fun SettingsCard(vm: GroupsViewModel, profileName: String, onSaved: () -> Unit) {
    var nickname by rememberSaveable { mutableStateOf(vm.nickname.ifBlank { profileName }) }
    var server by rememberSaveable { mutableStateOf(vm.serverUrl) }
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFE5F0DF))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (vm.isSetUp) "Group settings" else "Set up groups", fontWeight = FontWeight.Bold, color = NutriGreen)
            OutlinedTextField(nickname, { nickname = it.take(30) }, label = { Text("Nickname friends will see") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(server, { server = it.trim() }, label = { Text("Server address") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text(
                "Emulator: http://10.0.2.2:8000 · Real phone on the same Wi-Fi: http://<your Mac's IP>:8000",
                fontSize = 11.sp, color = Color.DarkGray
            )
            if (!vm.isSetUp) vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = { vm.saveSettings(nickname, server); if (nickname.isNotBlank()) onSaved() },
                colors = ButtonDefaults.buttonColors(containerColor = NutriGreen),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save and connect") }
        }
    }
}

@Composable
private fun GroupHeader(board: Leaderboard) {
    val clipboard = LocalClipboardManager.current
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = NutriGreen)) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(board.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text("${board.members.size} member${if (board.members.size == 1) "" else "s"}", color = Color(0xFFDBE8D7), fontSize = 13.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("JOIN CODE", color = Color(0xFFE9D6A0), fontSize = 10.sp, letterSpacing = 1.5.sp)
                Text(board.code, color = Color.White, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = 2.sp)
                TextButton(onClick = { clipboard.setText(AnnotatedString(board.code)) }, contentPadding = PaddingValues(0.dp)) {
                    Text("Copy", color = Color.White, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun MemberRow(entry: LeaderboardEntry, board: Leaderboard, isYou: Boolean, onReact: (String) -> Unit) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = if (isYou) Color(0xFFFFF6E0) else Color.White),
        border = if (entry.crown) BorderStroke(2.dp, Color(0xFFE5A443)) else null
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when {
                        entry.crown -> "👑"
                        entry.rank != null -> "#${entry.rank}"
                        else -> "–"
                    },
                    fontSize = if (entry.crown) 26.sp else 18.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray,
                    modifier = Modifier.width(44.dp)
                )
                Column(Modifier.weight(1f)) {
                    Text(entry.nickname + if (isYou) "  (you)" else "", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    val details = buildList {
                        if (entry.streak > 0) add("⚡ ${entry.streak}-day streak")
                        if (board.period == LeaderboardPeriod.WEEK && entry.daysScored > 0) add("${entry.daysScored} day${if (entry.daysScored == 1) "" else "s"}")
                        if (entry.mealCount > 0) add("${entry.mealCount} meal${if (entry.mealCount == 1) "" else "s"}")
                    }
                    Text(details.joinToString("  ·  ").ifEmpty { "No meals logged yet" }, fontSize = 12.sp, color = Color.Gray)
                }
                Text(
                    entry.score?.toString() ?: "–",
                    fontSize = 28.sp, fontWeight = FontWeight.Bold,
                    color = entry.score?.let(::scoreColor) ?: Color.LightGray
                )
            }
            if (entry.reactions.isNotEmpty() || !isYou) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp, start = 44.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    entry.reactions.forEach { (emoji, count) ->
                        Text("$emoji $count", fontSize = 13.sp, modifier = Modifier.padding(end = 6.dp))
                    }
                    if (!isYou) {
                        Spacer(Modifier.weight(1f))
                        board.reactionChoices.take(4).forEach { emoji ->
                            val mine = entry.myReaction == emoji
                            Surface(
                                onClick = { onReact(emoji) },
                                shape = CircleShape,
                                color = if (mine) Color(0xFFFFE0B2) else Color(0xFFF2F2F2)
                            ) { Text(emoji, fontSize = 16.sp, modifier = Modifier.padding(6.dp)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateOrJoinCard(vm: GroupsViewModel) {
    var groupName by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    Card(shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Join a group", fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    code, { code = it.filter(Char::isLetterOrDigit).take(6).uppercase() },
                    label = { Text("6-character code") }, singleLine = true, modifier = Modifier.weight(1f)
                )
                Button(onClick = { vm.joinGroup(code); code = "" }, enabled = !vm.busy && code.length == 6,
                    colors = ButtonDefaults.buttonColors(containerColor = NutriGreen)) { Text("Join") }
            }
            HorizontalDivider()
            Text("Or start a new group", fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    groupName, { groupName = it.take(40) },
                    label = { Text("Group name, e.g. Hall 5 gym crew") }, singleLine = true, modifier = Modifier.weight(1f)
                )
                Button(onClick = { vm.createGroup(groupName); groupName = "" }, enabled = !vm.busy && groupName.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = NutriGreen)) { Text("Create") }
            }
        }
    }
}
