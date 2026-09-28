@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.reus.nutri
import androidx.compose.foundation.clickable

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
import androidx.compose.foundation.Image
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.painterResource
import nutri.composeapp.generated.resources.Res
import nutri.composeapp.generated.resources.fuel_forge_logo
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.launch
private val Bg = Color(0xFF0B1326); private val Card = Color(0xFF171F33); private val High = Color(0xFF222A3D)
val Track = Color(0xFF2D3449); val Ink = Color(0xFFDAE2FD); val Muted = Color(0xFFBCCBB9)
val Green = Color(0xFF4BE277); val Amber = Color(0xFFFFB95F); private val Mint = Color(0xFF50DFA4)
@Composable fun App() {
    MaterialTheme {
        Surface(Modifier.fillMaxSize(), color = Bg) {
            var signedIn by remember { mutableStateOf(supabase.auth.currentUserOrNull() != null) }
            var displayName by remember { mutableStateOf<String?>(null) }
            LaunchedEffect(Unit) {
                supabase.auth.sessionStatus.collect {
                    signedIn = supabase.auth.currentUserOrNull() != null
                }
            }
            LaunchedEffect(signedIn) {
                displayName = if (signedIn) fetchCurrentDisplayName() else null
            }
            if (signedIn) NutriApp(displayName ?: "there") else LoginScreen()
        }
    }
}

@Composable
private fun LoginScreen() {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(Res.drawable.fuel_forge_logo),
            contentDescription = "Fuel Forge",
            modifier = Modifier.size(220.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text("FUEL FORGE", color = Green, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = email,
            onValueChange = { email = it; error = null },
            label = { Text("Email") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Ink,
                unfocusedTextColor = Ink,
                focusedLabelColor = Green,
                unfocusedLabelColor = Muted,
                cursorColor = Green,
            ),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it; error = null },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Ink,
                unfocusedTextColor = Ink,
                focusedLabelColor = Green,
                unfocusedLabelColor = Muted,
                cursorColor = Green,
            ),
        )
        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = Amber, fontSize = 12.sp)
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                scope.launch {
                    busy = true
                    error = runCatching {
                        require(email.isNotBlank() && password.isNotBlank()) { "Email and password are required" }
                        supabase.auth.signInWith(Email) {
                            this.email = email.trim()
                            this.password = password
                        }
                    }.exceptionOrNull()?.message
                    busy = false
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Color(0xFF003915)),
        ) {
            Text(if (busy) "SIGNING IN…" else "SIGN IN")
        }
    }
}

@Composable
private fun NutriApp(displayName: String) {
    var page by remember { mutableIntStateOf(0) }
    Scaffold(containerColor = Bg, topBar = { Header() }, bottomBar = {
        NavigationBar(containerColor = Card) {
            listOf(
                Icons.Default.LocalDining to "Today",
                Icons.Default.QrCodeScanner to "Log & Scan",
                Icons.Default.Group to "Roster",
                Icons.Default.TrackChanges to "Goals",
            ).forEachIndexed { i, item ->
                NavigationBarItem(
                    page == i,
                    { page = i },
                    { Icon(item.first, item.second) },
                    label = { Text(item.second, fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Green,
                        selectedTextColor = Green,
                        unselectedIconColor = Muted,
                        unselectedTextColor = Muted,
                        indicatorColor = High,
                    ),
                )
            }
        }
    }) { pad -> if (page == 1) Scanner(pad) else Dashboard(pad, displayName) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun Header() { TopAppBar(title = { Column { Text("FEAST FIT", color = Green, fontSize = 11.sp, fontWeight = FontWeight.Bold); Text("Today (Feast)", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold) } }, actions = { IconButton({}) { Icon(Icons.Default.Notifications, null, tint = Muted) }; Surface(color = Green, shape = CircleShape, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Person, null, tint = Color(0xFF003915), modifier = Modifier.padding(7.dp)) }; Spacer(Modifier.width(16.dp)) }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg)) }

@Composable private fun Dashboard(pad: PaddingValues, displayName: String) {
    val today = remember { kotlin.time.Clock.System.now().toString().take(10) }
    var water by remember { mutableIntStateOf(2400) }
    var selectedMeal by remember { mutableStateOf<MealDto?>(null) }
    var confirmedMealIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    if (selectedMeal != null) {
        MealDetailScreen(
            meal = selectedMeal!!,
            confirmed = selectedMeal!!.id in confirmedMealIds,
            onBack = { selectedMeal = null },
            onConfirmationChanged = { confirmed ->
                if (confirmed) {
                    localDatabase.mealConfirmationQueries.confirmMeal(selectedMeal!!.id, today)
                    confirmedMealIds = confirmedMealIds + selectedMeal!!.id
                } else {
                    localDatabase.mealConfirmationQueries.unconfirmMeal(selectedMeal!!.id, today)
                    confirmedMealIds = confirmedMealIds - selectedMeal!!.id
                }
            },
        )
        return
    }
    var showCheckIn by remember { mutableStateOf(false) }
    var checkIn by remember { mutableStateOf<DailyCheckIn?>(null) }
    var checkInStatus by remember { mutableStateOf<String?>(null) }
    val checkInRepository = remember { createCheckInRepository() }
    val signedIn = supabase.auth.currentUserOrNull() != null
    val weekday = remember(today) { isoWeekday(today) }
    var meals by remember { mutableStateOf<List<MealDto>>(emptyList()) }
    LaunchedEffect(signedIn) {
        if (signedIn) {
            when (val result = checkInRepository.refresh(today)) {
                is RefreshResult.Found -> { checkIn = result.checkIn; checkInStatus = "Synced" }
                is RefreshResult.NotFound -> checkInStatus = if (result.local == null) null else "Saved locally"
                is RefreshResult.SignedOut -> checkInStatus = "Sign in required"
                is RefreshResult.Offline -> checkInStatus = if (result.local == null) "Offline" else "Saved locally · Sync pending"
            }
        }
    }
    LaunchedEffect(today) {
        confirmedMealIds = localDatabase.mealConfirmationQueries.confirmedMealIdsForDay(today)
            .executeAsList().toSet()
    }
    LaunchedEffect(signedIn, weekday) {
        meals = if (signedIn) fetchMealsForWeekday(weekday) else emptyList()
    }
    val eatenMeals = meals.filter { it.id in confirmedMealIds }
    val consumedCalories = eatenMeals.sumOf { it.calories }
    val consumedProtein = eatenMeals.sumOf { it.protein_grams }
    val consumedCarbs = eatenMeals.sumOf { it.carbs_grams }
    val consumedFats = eatenMeals.sumOf { it.fats_grams }
    LazyColumn(Modifier.padding(pad).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Spacer(Modifier.height(4.dp)); Text("Good morning, $displayName 👋", color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Bold); Text("●  Feast Window opens in 2h 15m", color = Mint, fontSize = 14.sp, fontWeight = FontWeight.SemiBold); CoachBanner() }
        item { DailyCheckInCard(checkIn, signedIn, checkInStatus) { if (signedIn) showCheckIn = true } }
        item { Workout() }
        item { Fuel(water, consumedCalories, consumedProtein, consumedCarbs, consumedFats) { water = (water + 250).coerceAtMost(3500) } }
        if (meals.isEmpty()) {
            item { Text("No meals planned for today.", color = Muted, fontSize = 13.sp) }
        } else {
            items(meals) { meal ->
                Meal(
                    meal.name,
                    meal.description,
                    "${meal.calories} kcal · ${meal.meal_type.replace('_', ' ').uppercase()}",
                    onClick = { selectedMeal = meal },
                )
            }
        }
        item { Row(Modifier.fillMaxWidth().background(Color(0xFF131B2E), RoundedCornerShape(12.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Surface(color = Green, shape = CircleShape, modifier = Modifier.size(44.dp)) { Icon(Icons.Default.Person, null, tint = Color(0xFF003915), modifier = Modifier.padding(10.dp)) }; Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text("Sync with Coach", color = Ink, fontWeight = FontWeight.Bold); Text("Marcus is reviewing today's metrics", color = Muted, fontSize = 12.sp) }; Button({}) { Text("PING") } } }
        item { Spacer(Modifier.height(8.dp)) }
    }
    if (showCheckIn && signedIn) {
        CheckInSheet(
            initial = checkIn ?: DailyCheckIn(newIdentifier(), today),
            repository = checkInRepository,
            onDismiss = { showCheckIn = false },
            onSaved = { checkIn = it },
        )
    }
}

@Composable
private fun MealDetailScreen(
    meal: MealDto,
    confirmed: Boolean,
    onBack: () -> Unit,
    onConfirmationChanged: (Boolean) -> Unit,
) {
    var ingredients by remember(meal.id) { mutableStateOf<List<MealIngredientDto>>(emptyList()) }
    var portionMultiplier by remember(meal.id) { mutableFloatStateOf(1f) }
    var swapIngredient by remember { mutableStateOf<MealIngredientDto?>(null) }
    LaunchedEffect(meal.id) { ingredients = fetchMealIngredients(meal.id) }
    if (swapIngredient != null) {
        SwapIngredientScreen(
            ingredient = swapIngredient!!,
            onBack = { swapIngredient = null },
            onApply = { selections ->
                val original = swapIngredient!!
                val replacements = selections.map { (name, percentage) ->
                    original.copy(
                        id = "${original.id}-$percentage-${name.hashCode()}",
                        ingredient_name = name,
                        quantity = original.quantity * percentage / 100.0,
                    )
                }
                ingredients = ingredients.flatMap {
                    if (it.id == original.id) replacements else listOf(it)
                }
                swapIngredient = null
            },
        )
        return
    }
    LazyColumn(
        Modifier.fillMaxSize().background(Bg).padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 136.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) { Text("‹  Back", color = Green) }
                Spacer(Modifier.weight(1f))
                Image(
                    painter = painterResource(Res.drawable.fuel_forge_logo),
                    contentDescription = "Fuel Forge",
                    modifier = Modifier.size(width = 96.dp, height = 44.dp),
                )
            }
        }
        item {
            Text("Detailed Meal Nutrition Breakdown", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(meal.name, color = Ink, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
            Text(meal.meal_type.replace('_', ' ').uppercase(), color = Green, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Button(
                onClick = { onConfirmationChanged(!confirmed) },
                colors = ButtonDefaults.buttonColors(containerColor = if (confirmed) Amber else Green),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (confirmed) "MEAL CONFIRMED · TAP TO UNDO" else "CONFIRM MEAL EATEN")
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Macro("ENERGY", "${(meal.calories * portionMultiplier).toInt()}", 1f, Ink, Modifier.weight(1f))
                Macro("PROTEIN", "${(meal.protein_grams * portionMultiplier).toInt()}g", 1f, Green, Modifier.weight(1f))
                Macro("CARBS", "${(meal.carbs_grams * portionMultiplier).toInt()}g", 1f, Amber, Modifier.weight(1f))
                Macro("FATS", "${(meal.fats_grams * portionMultiplier).toInt()}g", 1f, Mint, Modifier.weight(1f))
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(Card), shape = RoundedCornerShape(14.dp)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("PORTION SCALER", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("Adjust portions for this meal", color = Muted, fontSize = 12.sp)
                    }
                    TextButton(onClick = { portionMultiplier = (portionMultiplier - .25f).coerceAtLeast(.5f) }) { Text("−", color = Ink, fontSize = 22.sp) }
                    Text("${"%.2f".format(portionMultiplier)}x", color = Green, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { portionMultiplier = (portionMultiplier + .25f).coerceAtMost(2.5f) }) { Text("+", color = Ink, fontSize = 22.sp) }
                }
            }
        }
        item {
            Text("FOOD GROUPS & EXCHANGES", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        if (ingredients.isEmpty()) {
            item { Text(meal.description, color = Ink, fontSize = 16.sp, lineHeight = 24.sp) }
        } else {
            ingredients.groupBy { it.category }.forEach { (category, categoryIngredients) ->
                item {
                    Card(colors = CardDefaults.cardColors(Card), shape = RoundedCornerShape(14.dp)) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = categoryColor(category).copy(alpha = .18f),
                                    shape = RoundedCornerShape(9.dp),
                                    modifier = Modifier.size(36.dp),
                                ) {
                                    Icon(
                                        categoryIcon(category),
                                        contentDescription = categoryLabel(category),
                                        tint = categoryColor(category),
                                        modifier = Modifier.padding(8.dp),
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                Text(categoryLabel(category), color = categoryColor(category), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("INGREDIENT", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text("PORTION", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            categoryIngredients.forEach { ingredient ->
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text(ingredient.ingredient_name, color = Ink, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                    Text(portionText(ingredient, portionMultiplier), color = Muted, fontSize = 14.sp)
                                    IconButton(onClick = { swapIngredient = ingredient }) {
                                        Icon(Icons.Default.SwapHoriz, contentDescription = "Swap ingredient", tint = Green)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun portionText(ingredient: MealIngredientDto, multiplier: Float = 1f): String {
    val household = ingredient.household_portion.takeIf { it.isNotBlank() }
    val grams = ingredient.grams_quantity?.let { value ->
        val scaled = value * multiplier
        "${if (scaled % 1.0 == 0.0) scaled.toInt() else "%.1f".format(scaled)} g"
    }
    return listOfNotNull(household, grams).joinToString(" · ").ifBlank {
        val amount = ingredient.quantity * multiplier
        "${if (amount % 1.0 == 0.0) amount.toInt() else "%.1f".format(amount)} ${ingredient.unit}"
    }
}
private fun categoryLabel(category: String): String = when (category) {
    "animal_protein_moderate_fat" -> "PROTEÍNA DE ORIGEN ANIMAL · MODERADA EN GRASA"
    "animal_protein_low_fat" -> "PROTEÍNA DE ORIGEN ANIMAL · BAJA EN GRASA"
    "vegetable" -> "VERDURA"
    "cereals_tubers" -> "CEREALES Y TUBÉRCULOS"
    "legumes" -> "LEGUMINOSAS"
    else -> category.replace('_', ' ').uppercase()
}

@Composable
private fun SwapIngredientScreen(
    ingredient: MealIngredientDto,
    onBack: () -> Unit,
    onApply: (List<Pair<String, Int>>) -> Unit,
) {
    val alternatives = swapAlternatives(ingredient.category)
    val selected = remember { mutableStateMapOf<String, Int>() }
    val total = selected.values.sum()
    LazyColumn(
        Modifier.fillMaxSize().background(Bg).padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 136.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("‹  Back", color = Green) }
                Spacer(Modifier.weight(1f))
                Text("SWAP INGREDIENT", color = Green, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        item {
            Text("Choose equivalents", color = Ink, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            Text("Current: ${ingredient.ingredient_name}", color = Muted, fontSize = 14.sp)
            Text(categoryLabel(ingredient.category), color = Green, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text("Selected: $total% of the meal target", color = if (total == 100) Green else Amber, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        items(alternatives) { alternative ->
            val percent = selected[alternative] ?: 0
            Card(colors = CardDefaults.cardColors(Card), shape = RoundedCornerShape(14.dp)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = Green.copy(alpha = .18f), shape = RoundedCornerShape(9.dp), modifier = Modifier.size(42.dp)) {
                        Icon(categoryIcon(ingredient.category), null, tint = Green, modifier = Modifier.padding(10.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(alternative, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(portionText(ingredient, percent / 100f), color = Muted, fontSize = 12.sp)
                    }
                    TextButton(onClick = {
                        val next = (percent - 25).coerceAtLeast(0)
                        if (next == 0) selected.remove(alternative) else selected[alternative] = next
                    }) { Text("−", color = Ink, fontSize = 20.sp) }
                    Text("$percent%", color = Green, fontWeight = FontWeight.Bold)
                    TextButton(onClick = {
                        if (total < 100) selected[alternative] = (percent + 25).coerceAtMost(100 - total + percent)
                    }) { Text("+", color = Ink, fontSize = 20.sp) }
                }
            }
        }
        item {
            Button(
                onClick = { onApply(selected.toList()) },
                enabled = total == 100,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Color(0xFF003915)),
            ) {
                Text("APPLY 100% EXCHANGE")
            }
        }
    }
}

private fun swapAlternatives(category: String): List<String> = when (category) {
    "animal_protein_moderate_fat" -> listOf("Bistec magro", "Carne molida 93/7", "Lomo de cerdo")
    "animal_protein_low_fat" -> listOf("Pechuga de pollo", "Pescado blanco", "Claras de huevo")
    "vegetable" -> listOf("Calabacita", "Brócoli", "Espinaca", "Pepino")
    "cereals_tubers" -> listOf("Arroz cocido", "Papa cocida", "Tortilla de maíz", "Avena")
    "legumes" -> listOf("Lentejas cocidas", "Garbanzos cocidos", "Frijoles cocidos")
    else -> listOf("Alternativa equivalente")
}
private fun categoryIcon(category: String): ImageVector = when (category) {
    "animal_protein_moderate_fat", "animal_protein_low_fat" -> Icons.Default.FitnessCenter
    "vegetable" -> Icons.Default.LocalFlorist
    "cereals_tubers" -> Icons.Default.BakeryDining
    "legumes" -> Icons.Default.Spa
    else -> Icons.Default.Restaurant
}

private fun categoryColor(category: String): Color = when (category) {
    "animal_protein_moderate_fat" -> Amber
    "animal_protein_low_fat" -> Green
    "vegetable" -> Mint
    "cereals_tubers" -> Amber
    "legumes" -> Ink
    else -> Muted
}
@Composable private fun DailyCheckInCard(checkIn: DailyCheckIn?, signedIn: Boolean, status: String?, onOpen: () -> Unit) {

    Card(colors = CardDefaults.cardColors(Card), shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Daily check-in", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(
                    when { !signedIn -> "Sign in required"; checkIn == null -> "Take today's check-in"; else -> checkInSummary(checkIn) },
                    color = if (!signedIn) Amber else Muted,
                    fontSize = 12.sp,
                )
                status?.let { Text(it, color = if (it.contains("pending") || it == "Offline") Amber else Green, fontSize = 11.sp) }
            }
            Button(onClick = onOpen, enabled = signedIn, colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Color(0xFF003915))) {
                Text(if (checkIn == null) "CHECK IN" else "EDIT")
            }
        }
    }
}

private fun checkInSummary(checkIn: DailyCheckIn): String = buildList {
    checkIn.weightGrams?.let { add("${it / 1000.0} kg") }
    checkIn.sleepMinutes?.let { add("${it / 60}h ${it % 60}m sleep") }
    if (checkIn.comments.isNotBlank()) add("Notes added")
}.joinToString(" · ").ifBlank { "Completed today" }

@Composable private fun CoachBanner() { Row(Modifier.fillMaxWidth().padding(top = 10.dp).background(Card, RoundedCornerShape(12.dp)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) { Surface(color = Amber, shape = CircleShape, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.FitnessCenter, null, tint = Color(0xFF472A00), modifier = Modifier.padding(6.dp)) }; Spacer(Modifier.width(8.dp)); Text("Coach Marcus:  “Crush that leg day feast today!”", color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) } }

@Composable
private fun Fuel(
    water: Int,
    consumedCalories: Int,
    consumedProtein: Int,
    consumedCarbs: Int,
    consumedFats: Int,
    addWater: () -> Unit,
) {
    val calorieTarget = 2450
    val proteinTarget = 190
    val carbsTarget = 260
    val fatsTarget = 70
    val caloriesLeft = (calorieTarget - consumedCalories).coerceAtLeast(0)
    Card(colors = CardDefaults.cardColors(Card), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Bolt, null, tint = Green)
                Spacer(Modifier.width(8.dp))
                Text("Daily Fuel Gauge", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Tag("DAY 18 OF CYCLE", Green)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(120.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawArc(Track, -90f, 360f, false, style = Stroke(10.dp.toPx()))
                        drawArc(Green, -90f, (consumedCalories.toFloat() / calorieTarget * 360f).coerceIn(0f, 360f), false, style = Stroke(10.dp.toPx()))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$caloriesLeft", color = Ink, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                        Text("KCAL LEFT", color = Muted, fontSize = 10.sp)
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("CONFIRMED INTAKE", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("$consumedCalories / $calorieTarget kcal", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    LinearProgressIndicator({ (consumedCalories.toFloat() / calorieTarget).coerceIn(0f, 1f) }, color = Green)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Macro("PROTEIN", "$consumedProtein/${proteinTarget}g", (consumedProtein.toFloat() / proteinTarget).coerceIn(0f, 1f), Green, Modifier.weight(1f))
                Macro("CARBS", "$consumedCarbs/${carbsTarget}g", (consumedCarbs.toFloat() / carbsTarget).coerceIn(0f, 1f), Amber, Modifier.weight(1f))
                Macro("FATS", "$consumedFats/${fatsTarget}g", (consumedFats.toFloat() / fatsTarget).coerceIn(0f, 1f), Mint, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth().background(Color(0xFF131B2E), RoundedCornerShape(10.dp)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.WaterDrop, null, tint = Mint)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("Hydration Level", color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("${water / 1000f}L of 3.5L Target", color = Muted, fontSize = 12.sp)
                }
                Button(addWater, colors = ButtonDefaults.buttonColors(containerColor = High, contentColor = Mint)) { Text("+250ml", fontSize = 12.sp) }
            }
        }
    }
}

@Composable private fun Macro(label: String, value: String, progress: Float, color: Color, mod: Modifier) { Column(mod.background(High, RoundedCornerShape(8.dp)).padding(8.dp)) { Text(label, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold); Text(value, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold); LinearProgressIndicator({ progress }, color = color, trackColor = Track, modifier = Modifier.fillMaxWidth().padding(top = 5.dp)) } }
@Composable private fun Workout() { Card(colors = CardDefaults.cardColors(Card), shape = RoundedCornerShape(14.dp)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.FitnessCenter, null, tint = Green); Spacer(Modifier.width(8.dp)); Text("Assigned Workout", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.weight(1f)); Tag("HEAVY DAY", Amber) }; Text("Hypertrophy Lower Body", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold); Text("+ 15m Anabolic HIIT Finisher", color = Muted, fontSize = 13.sp); Text("◷  55 mins   •   420 kcal burn est.", color = Green, fontSize = 12.sp); Button({}, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Color(0xFF003915)), shape = CircleShape) { Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("START WORKOUT", fontWeight = FontWeight.Bold) } } } }
@Composable private fun Meal(title: String, description: String, detail: String, onClick: () -> Unit) { Row(Modifier.fillMaxWidth().clickable(onClick = onClick).background(Card, RoundedCornerShape(12.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Surface(color = High, shape = RoundedCornerShape(9.dp), modifier = Modifier.size(42.dp)) { Icon(Icons.Default.Restaurant, null, tint = Green, modifier = Modifier.padding(10.dp)) }; Spacer(Modifier.width(10.dp)); Column { Text(title, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold); Text(description, color = Muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis); Text(detail, color = Green, fontSize = 10.sp, fontWeight = FontWeight.Bold) } } }

@Composable private fun Scanner(pad: PaddingValues) { var portion by remember { mutableIntStateOf(1) }; var logged by remember { mutableStateOf(false) }; LazyColumn(Modifier.padding(pad).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { item { Spacer(Modifier.height(4.dp)); Text("Log & Scan", color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Bold); Text("Capture your next feast", color = Muted); Row(Modifier.fillMaxWidth().background(Color(0xFF060E20), RoundedCornerShape(12.dp)).padding(4.dp)) { listOf("AI SNAP", "BARCODE", "MANUAL").forEach { FilterChip(true, {}, { Text(it, fontSize = 11.sp) }, Modifier.weight(1f)) } } }; item { Card(colors = CardDefaults.cardColors(Card), shape = RoundedCornerShape(16.dp)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { Box(Modifier.fillMaxWidth().height(190.dp).background(Color(0xFF18243A), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.Restaurant, null, tint = Mint, modifier = Modifier.size(52.dp)); Text("98% MATCH", color = Green, fontWeight = FontWeight.Bold); Text("Omega-3 Rich Wild Salmon + Quinoa", color = Ink, fontSize = 12.sp) } }; Text("DETECTED FEAST", color = Amber, fontSize = 11.sp, fontWeight = FontWeight.Bold); Text("Flame-Grilled Salmon Power Bowl", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold) } } }; item { Card(colors = CardDefaults.cardColors(Color(0xFF131B2E)), shape = RoundedCornerShape(16.dp)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { Text("${640 * portion} KCAL TOTAL", color = Ink, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold); Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Macro("PROTEIN", "${48 * portion}g", .82f, Green, Modifier.weight(1f)); Macro("CARBS", "${52 * portion}g", .65f, Amber, Modifier.weight(1f)); Macro("FATS", "${24 * portion}g", .48f, Mint, Modifier.weight(1f)) }; Row(Modifier.fillMaxWidth().background(Card, RoundedCornerShape(10.dp)).padding(8.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("PORTION SCALE", color = Muted, fontSize = 11.sp); Text("$portion serving (${380 * portion}g)", color = Ink, fontWeight = FontWeight.Bold) }; IconButton({ portion = (portion - 1).coerceAtLeast(1) }) { Text("−", color = Ink, fontSize = 24.sp) }; Text("$portion.0", color = Green, fontWeight = FontWeight.Bold); IconButton({ portion = (portion + 1).coerceAtMost(4) }) { Icon(Icons.Default.Add, null, tint = Ink) } }; Button({ logged = true }, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Color(0xFF003915)), shape = CircleShape) { Text(if (logged) "✓ FEAST LOGGED" else "CONFIRM & LOG FEAST", fontWeight = FontWeight.Bold) } } } }; item { Text("Coach Marcus' Recipes", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold) }; items(listOf("High-Protein Anabolic Chili" to "580 kcal · 52g Protein", "Crispy Honey Lime Chicken" to "620 kcal · 58g Protein", "Loaded Sweet Potato Mash" to "410 kcal · 28g Protein")) { Recipe(it.first, it.second) } } }
@Composable private fun Recipe(title: String, detail: String) { Row(Modifier.fillMaxWidth().background(Card, RoundedCornerShape(14.dp)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(72.dp).background(High, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Restaurant, null, tint = Amber) }; Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text("MARCUS APPROVED", color = Amber, fontSize = 10.sp, fontWeight = FontWeight.Bold); Text(title, color = Ink, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(detail, color = Muted, fontSize = 12.sp) }; IconButton({}) { Icon(Icons.Default.Add, null, tint = Green) } } }
@Composable private fun Tag(text: String, color: Color) { Surface(color = color.copy(alpha = .18f), shape = RoundedCornerShape(50)) { Text(text, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) } }
