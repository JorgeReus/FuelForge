package com.reus.nutri

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val Bg = Color(0xFF0B1326); private val Card = Color(0xFF171F33); private val High = Color(0xFF222A3D)
private val Track = Color(0xFF2D3449); private val Ink = Color(0xFFDAE2FD); private val Muted = Color(0xFFBCCBB9)
private val Green = Color(0xFF4BE277); private val Amber = Color(0xFFFFB95F); private val Mint = Color(0xFF50DFA4)

@Composable fun App() { MaterialTheme { Surface(Modifier.fillMaxSize(), color = Bg) { NutriApp() } } }

@Composable private fun NutriApp() {
    var page by remember { mutableIntStateOf(0) }
    Scaffold(containerColor = Bg, topBar = { Header() }, bottomBar = {
        NavigationBar(containerColor = Card) { listOf(Icons.Default.LocalDining to "Today", Icons.Default.QrCodeScanner to "Log & Scan", Icons.Default.Group to "Roster", Icons.Default.TrackChanges to "Goals").forEachIndexed { i, item ->
            NavigationBarItem(page == i, { page = i }, { Icon(item.first, item.second) }, label = { Text(item.second, fontSize = 10.sp) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = Green, selectedTextColor = Green, unselectedIconColor = Muted, unselectedTextColor = Muted, indicatorColor = High))
        } }
    }) { pad -> if (page == 1) Scanner(pad) else Dashboard(pad) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun Header() { TopAppBar(title = { Column { Text("FEAST FIT", color = Green, fontSize = 11.sp, fontWeight = FontWeight.Bold); Text("Today (Feast)", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold) } }, actions = { IconButton({}) { Icon(Icons.Default.Notifications, null, tint = Muted) }; Surface(color = Green, shape = CircleShape, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Person, null, tint = Color(0xFF003915), modifier = Modifier.padding(7.dp)) }; Spacer(Modifier.width(16.dp)) }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg)) }

@Composable private fun Dashboard(pad: PaddingValues) {
    var water by remember { mutableIntStateOf(2400) }
    var todos by remember { mutableStateOf<List<TodoItem>>(emptyList()) }
    var todoError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        try {
            todos = withContext(Dispatchers.Default) { loadLocalTodos() }
            val remote = withContext(Dispatchers.Default) { loadTodos() }
            saveLocalTodos(remote)
            todos = remote
        } catch (error: Exception) {
            todoError = error.message ?: "Unable to load todos"
        }
    }
    LazyColumn(Modifier.padding(pad).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Spacer(Modifier.height(4.dp)); Text("Good morning, Alex 👋", color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Bold); Text("●  Feast Window opens in 2h 15m", color = Mint, fontSize = 14.sp, fontWeight = FontWeight.SemiBold); CoachBanner() }
        item { Fuel(water) { water = (water + 250).coerceAtMost(3500) } }
        item { TodoCard(todos, todoError) }
        item { Workout() }
        item { Text("Meal Stream & Feast Window", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        items(listOf("Breakfast Fuel" to "520 kcal · LOGGED 08:15 AM", "Lunch Synthesis" to "680 kcal · LOGGED 12:40 PM", "Pre-Workout Fuel" to "220 kcal · NEXT 3:30 PM", "Anabolic Flank Steak Feast" to "850 kcal · WINDOW 6:30 PM")) { Meal(it.first, it.second) }
        item { Row(Modifier.fillMaxWidth().background(Color(0xFF131B2E), RoundedCornerShape(12.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Surface(color = Green, shape = CircleShape, modifier = Modifier.size(44.dp)) { Icon(Icons.Default.Person, null, tint = Color(0xFF003915), modifier = Modifier.padding(10.dp)) }; Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text("Sync with Coach", color = Ink, fontWeight = FontWeight.Bold); Text("Marcus is reviewing today's metrics", color = Muted, fontSize = 12.sp) }; Button({}) { Text("PING") } } }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable private fun TodoCard(items: List<TodoItem>, error: String?) {
    Card(colors = CardDefaults.cardColors(Card), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Supabase Todos", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            when {
                error != null -> Text("Could not load todos: $error", color = Amber, fontSize = 12.sp)
                items.isEmpty() -> Text("No todos yet", color = Muted, fontSize = 13.sp)
                else -> items.forEach { Text("• ${it.name}", color = Ink, fontSize = 14.sp) }
            }
        }
    }
}

@Composable private fun CoachBanner() { Row(Modifier.fillMaxWidth().padding(top = 10.dp).background(Card, RoundedCornerShape(12.dp)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) { Surface(color = Amber, shape = CircleShape, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.FitnessCenter, null, tint = Color(0xFF472A00), modifier = Modifier.padding(6.dp)) }; Spacer(Modifier.width(8.dp)); Text("Coach Marcus:  “Crush that leg day feast today!”", color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) } }

@Composable private fun Fuel(water: Int, addWater: () -> Unit) { Card(colors = CardDefaults.cardColors(Card), shape = RoundedCornerShape(14.dp)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Bolt, null, tint = Green); Spacer(Modifier.width(8.dp)); Text("Daily Fuel Gauge", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.weight(1f)); Tag("DAY 18 OF CYCLE", Green) }
    Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(120.dp), contentAlignment = Alignment.Center) { Canvas(Modifier.fillMaxSize()) { drawArc(Track, -90f, 360f, false, style = Stroke(10.dp.toPx())); drawArc(Green, -90f, 252f, false, style = Stroke(10.dp.toPx())) }; Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("730", color = Ink, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold); Text("KCAL LEFT", color = Muted, fontSize = 10.sp) } }; Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) { Text("INTAKE SUMMARY", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold); Text("1,720 / 2,450 kcal", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold); LinearProgressIndicator({ .7f }, color = Green, trackColor = Track, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)); Text("↗ On track for evening anabolic window", color = Green, fontSize = 11.sp) } }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Macro("PROTEIN", "165/190g", .87f, Green, Modifier.weight(1f)); Macro("CARBS", "210/260g", .8f, Amber, Modifier.weight(1f)); Macro("FATS", "55/70g", .78f, Mint, Modifier.weight(1f)) }
    Row(Modifier.fillMaxWidth().background(Color(0xFF131B2E), RoundedCornerShape(10.dp)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.WaterDrop, null, tint = Mint); Spacer(Modifier.width(8.dp)); Column(Modifier.weight(1f)) { Text("Hydration Level", color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold); Text("${water / 1000f}L of 3.5L Target", color = Muted, fontSize = 12.sp) }; Button(addWater, colors = ButtonDefaults.buttonColors(containerColor = High, contentColor = Mint)) { Text("+250ml", fontSize = 12.sp) } }
} } }

@Composable private fun Macro(label: String, value: String, progress: Float, color: Color, mod: Modifier) { Column(mod.background(High, RoundedCornerShape(8.dp)).padding(8.dp)) { Text(label, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold); Text(value, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold); LinearProgressIndicator({ progress }, color = color, trackColor = Track, modifier = Modifier.fillMaxWidth().padding(top = 5.dp)) } }
@Composable private fun Workout() { Card(colors = CardDefaults.cardColors(Card), shape = RoundedCornerShape(14.dp)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.FitnessCenter, null, tint = Green); Spacer(Modifier.width(8.dp)); Text("Assigned Workout", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.weight(1f)); Tag("HEAVY DAY", Amber) }; Text("Hypertrophy Lower Body", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold); Text("+ 15m Anabolic HIIT Finisher", color = Muted, fontSize = 13.sp); Text("◷  55 mins   •   420 kcal burn est.", color = Green, fontSize = 12.sp); Button({}, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Color(0xFF003915)), shape = CircleShape) { Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("START WORKOUT", fontWeight = FontWeight.Bold) } } } }
@Composable private fun Meal(title: String, detail: String) { Row(Modifier.fillMaxWidth().background(Card, RoundedCornerShape(12.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Surface(color = High, shape = RoundedCornerShape(9.dp), modifier = Modifier.size(42.dp)) { Icon(Icons.Default.Restaurant, null, tint = Green, modifier = Modifier.padding(10.dp)) }; Spacer(Modifier.width(10.dp)); Column { Text(title, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold); Text("Oatmeal bowl, chicken bowl, or planned feast", color = Muted, fontSize = 12.sp, maxLines = 1); Text(detail, color = Green, fontSize = 10.sp, fontWeight = FontWeight.Bold) } } }

@Composable private fun Scanner(pad: PaddingValues) { var portion by remember { mutableIntStateOf(1) }; var logged by remember { mutableStateOf(false) }; LazyColumn(Modifier.padding(pad).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { item { Spacer(Modifier.height(4.dp)); Text("Log & Scan", color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Bold); Text("Capture your next feast", color = Muted); Row(Modifier.fillMaxWidth().background(Color(0xFF060E20), RoundedCornerShape(12.dp)).padding(4.dp)) { listOf("AI SNAP", "BARCODE", "MANUAL").forEach { FilterChip(true, {}, { Text(it, fontSize = 11.sp) }, Modifier.weight(1f)) } } }; item { Card(colors = CardDefaults.cardColors(Card), shape = RoundedCornerShape(16.dp)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { Box(Modifier.fillMaxWidth().height(190.dp).background(Color(0xFF18243A), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.Restaurant, null, tint = Mint, modifier = Modifier.size(52.dp)); Text("98% MATCH", color = Green, fontWeight = FontWeight.Bold); Text("Omega-3 Rich Wild Salmon + Quinoa", color = Ink, fontSize = 12.sp) } }; Text("DETECTED FEAST", color = Amber, fontSize = 11.sp, fontWeight = FontWeight.Bold); Text("Flame-Grilled Salmon Power Bowl", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold) } } }; item { Card(colors = CardDefaults.cardColors(Color(0xFF131B2E)), shape = RoundedCornerShape(16.dp)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { Text("${640 * portion} KCAL TOTAL", color = Ink, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold); Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Macro("PROTEIN", "${48 * portion}g", .82f, Green, Modifier.weight(1f)); Macro("CARBS", "${52 * portion}g", .65f, Amber, Modifier.weight(1f)); Macro("FATS", "${24 * portion}g", .48f, Mint, Modifier.weight(1f)) }; Row(Modifier.fillMaxWidth().background(Card, RoundedCornerShape(10.dp)).padding(8.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("PORTION SCALE", color = Muted, fontSize = 11.sp); Text("$portion serving (${380 * portion}g)", color = Ink, fontWeight = FontWeight.Bold) }; IconButton({ portion = (portion - 1).coerceAtLeast(1) }) { Text("−", color = Ink, fontSize = 24.sp) }; Text("$portion.0", color = Green, fontWeight = FontWeight.Bold); IconButton({ portion = (portion + 1).coerceAtMost(4) }) { Icon(Icons.Default.Add, null, tint = Ink) } }; Button({ logged = true }, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Color(0xFF003915)), shape = CircleShape) { Text(if (logged) "✓ FEAST LOGGED" else "CONFIRM & LOG FEAST", fontWeight = FontWeight.Bold) } } } }; item { Text("Coach Marcus' Recipes", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold) }; items(listOf("High-Protein Anabolic Chili" to "580 kcal · 52g Protein", "Crispy Honey Lime Chicken" to "620 kcal · 58g Protein", "Loaded Sweet Potato Mash" to "410 kcal · 28g Protein")) { Recipe(it.first, it.second) } } }
@Composable private fun Recipe(title: String, detail: String) { Row(Modifier.fillMaxWidth().background(Card, RoundedCornerShape(14.dp)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(72.dp).background(High, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Restaurant, null, tint = Amber) }; Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text("MARCUS APPROVED", color = Amber, fontSize = 10.sp, fontWeight = FontWeight.Bold); Text(title, color = Ink, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(detail, color = Muted, fontSize = 12.sp) }; IconButton({}) { Icon(Icons.Default.Add, null, tint = Green) } } }
@Composable private fun Tag(text: String, color: Color) { Surface(color = color.copy(alpha = .18f), shape = RoundedCornerShape(50)) { Text(text, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) } }
