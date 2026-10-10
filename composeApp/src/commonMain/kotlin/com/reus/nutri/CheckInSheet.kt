package com.reus.nutri

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

class CheckInFormState(initial: DailyCheckIn) {
    private var checkIn = initial
    private val selected = mutableStateMapOf<CheckInMetric, CheckInChoice>()
    private val imported = initial

    var weightKg by mutableStateOf(initial.weightGrams?.let { grams -> (grams / 1000.0).formatDecimal() } ?: "")
    var heartRateBpm by mutableStateOf(initial.heartRateBpm?.toString() ?: "")
    var heightCm by mutableStateOf(initial.heightMillimeters?.let { (it / 10.0).formatDecimal() } ?: "")
    var bodyFatPercentage by mutableStateOf(initial.bodyFatPercentage?.formatDecimal() ?: "")
    var bodyWaterKg by mutableStateOf(initial.bodyWaterGrams?.let { (it / 1000.0).formatDecimal() } ?: "")
    var boneMassKg by mutableStateOf(initial.boneMassGrams?.let { (it / 1000.0).formatDecimal() } ?: "")
    var leanBodyMassKg by mutableStateOf(initial.leanBodyMassGrams?.let { (it / 1000.0).formatDecimal() } ?: "")
    var basalMetabolicRate by mutableStateOf(initial.basalMetabolicRateKcalPerDay?.toString() ?: "")
    var sleepHours by mutableStateOf(initial.sleepMinutes?.let { (it / 60).toString() } ?: "")
    var sleepMinutes by mutableStateOf(initial.sleepMinutes?.let { (it % 60).toString() } ?: "")
    var notes by mutableStateOf(initial.comments)

    init {
        CheckInMetric.entries.forEach { metric ->
            metric.choices.firstOrNull { it.score == metric.valueOf(initial) }?.let { selected[metric] = it }
        }
    }

    fun select(metric: CheckInMetric, choice: CheckInChoice) {
        selected[metric] = choice
    }

    fun toggle(metric: CheckInMetric, choice: CheckInChoice) {
        if (selected[metric] == choice) clear(metric) else select(metric, choice)
    }

    fun clear(metric: CheckInMetric) {
        selected.remove(metric)
    }

    fun choice(metric: CheckInMetric): CheckInChoice? = selected[metric]

    fun applyHealthSnapshot(snapshot: HealthDailySnapshot, prefill: (DailyCheckIn, HealthDailySnapshot) -> DailyCheckIn) {
        val current = toCheckIn()
        val merged = prefill(current, snapshot).copy(
            weightGrams = snapshot.weightGrams ?: current.weightGrams,
            heartRateBpm = snapshot.heartRateBpm ?: current.heartRateBpm,
            heightMillimeters = snapshot.heightMillimeters ?: current.heightMillimeters,
            bodyFatPercentage = snapshot.bodyFatPercentage ?: current.bodyFatPercentage,
            bodyWaterGrams = snapshot.bodyWaterGrams ?: current.bodyWaterGrams,
            boneMassGrams = snapshot.boneMassGrams ?: current.boneMassGrams,
            leanBodyMassGrams = snapshot.leanBodyMassGrams ?: current.leanBodyMassGrams,
            basalMetabolicRateKcalPerDay = snapshot.basalMetabolicRateKcalPerDay ?: current.basalMetabolicRateKcalPerDay,
            sleepMinutes = snapshot.sleepMinutes ?: current.sleepMinutes,
            neatMinutes = snapshot.activeMinutes ?: current.neatMinutes,
        )
        checkIn = merged
        snapshot.weightGrams?.let { weightKg = (it / 1000.0).formatDecimal() }
        snapshot.heartRateBpm?.let { heartRateBpm = it.toString() }
        snapshot.heightMillimeters?.let { heightCm = (it / 10.0).formatDecimal() }
        snapshot.bodyFatPercentage?.let { bodyFatPercentage = it.formatDecimal() }
        snapshot.bodyWaterGrams?.let { bodyWaterKg = (it / 1000.0).formatDecimal() }
        snapshot.boneMassGrams?.let { boneMassKg = (it / 1000.0).formatDecimal() }
        snapshot.leanBodyMassGrams?.let { leanBodyMassKg = (it / 1000.0).formatDecimal() }
        snapshot.basalMetabolicRateKcalPerDay?.let { basalMetabolicRate = it.toString() }
        snapshot.sleepMinutes?.let {
            sleepHours = (it / 60).toString()
            sleepMinutes = (it % 60).toString()
        }
    }

    fun toCheckIn(): DailyCheckIn = checkIn.copy(
        weightGrams = weightKg.toDoubleOrNull()?.let { (it * 1000).toInt() },
        heartRateBpm = heartRateBpm.toIntOrNull()?.takeIf { it > 0 },
        heightMillimeters = heightCm.toDoubleOrNull()?.takeIf { it > 0 }?.let { (it * 10).toInt() } ?: imported.heightMillimeters,
        bodyFatPercentage = bodyFatPercentage.toDoubleOrNull()?.takeIf { it in 0.0..100.0 } ?: imported.bodyFatPercentage,
        bodyWaterGrams = bodyWaterKg.toDoubleOrNull()?.takeIf { it > 0 }?.let { (it * 1000).toInt() } ?: imported.bodyWaterGrams,
        boneMassGrams = boneMassKg.toDoubleOrNull()?.takeIf { it > 0 }?.let { (it * 1000).toInt() } ?: imported.boneMassGrams,
        leanBodyMassGrams = leanBodyMassKg.toDoubleOrNull()?.takeIf { it > 0 }?.let { (it * 1000).toInt() } ?: imported.leanBodyMassGrams,
        basalMetabolicRateKcalPerDay = basalMetabolicRate.toIntOrNull()?.takeIf { it > 0 } ?: imported.basalMetabolicRateKcalPerDay,
        sleepMinutes = totalSleepMinutes(),
        soreness = selected[CheckInMetric.Soreness]?.score,
        performance = selected[CheckInMetric.Performance]?.score,
        motivation = selected[CheckInMetric.Motivation]?.score,
        hunger = selected[CheckInMetric.Hunger]?.score,
        fatigue = selected[CheckInMetric.Fatigue]?.score,
        stress = selected[CheckInMetric.Stress]?.score,
        sleepQuality = selected[CheckInMetric.SleepQuality]?.score,
        comments = notes.trim(),
    )

    private fun totalSleepMinutes(): Int? {
        if (sleepHours.isBlank() && sleepMinutes.isBlank()) return null
        val hours = sleepHours.toIntOrNull() ?: 0
        val minutes = sleepMinutes.toIntOrNull() ?: 0
        return (hours * 60 + minutes).coerceAtLeast(0)
    }

    private fun CheckInMetric.valueOf(value: DailyCheckIn): Int? = when (this) {
        CheckInMetric.Soreness -> value.soreness
        CheckInMetric.Performance -> value.performance
        CheckInMetric.Motivation -> value.motivation
        CheckInMetric.Hunger -> value.hunger
        CheckInMetric.Fatigue -> value.fatigue
        CheckInMetric.Stress -> value.stress
        CheckInMetric.SleepQuality -> value.sleepQuality
    }
}

private fun Double.formatDecimal(): String = if (this % 1 == 0.0) toInt().toString() else toString()

internal fun mergeHealthDefaults(initial: DailyCheckIn, healthSnapshot: HealthDailySnapshot?): DailyCheckIn =
    healthSnapshot?.let {
        initial.copy(
            weightGrams = initial.weightGrams ?: it.weightGrams,
            heartRateBpm = initial.heartRateBpm ?: it.heartRateBpm,
            heightMillimeters = initial.heightMillimeters ?: it.heightMillimeters,
            bodyFatPercentage = initial.bodyFatPercentage ?: it.bodyFatPercentage,
            bodyWaterGrams = initial.bodyWaterGrams ?: it.bodyWaterGrams,
            boneMassGrams = initial.boneMassGrams ?: it.boneMassGrams,
            leanBodyMassGrams = initial.leanBodyMassGrams ?: it.leanBodyMassGrams,
            basalMetabolicRateKcalPerDay = initial.basalMetabolicRateKcalPerDay ?: it.basalMetabolicRateKcalPerDay,
            sleepMinutes = initial.sleepMinutes ?: it.sleepMinutes,
            neatMinutes = initial.neatMinutes ?: it.activeMinutes,
        )
    } ?: initial

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckInSheet(
    initial: DailyCheckIn,
    repository: CheckInRepository,
    onDismiss: () -> Unit,
    onSaved: (DailyCheckIn) -> Unit,
    healthSnapshot: HealthDailySnapshot? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val importedInitial = remember(initial, healthSnapshot) {
        healthSnapshot?.let { repository.prefillFromHealth(initial, it) } ?: initial
    }
    val form = remember(
        importedInitial.id,
        importedInitial.recordedOn,
        importedInitial.weightGrams,
        importedInitial.heartRateBpm,
        importedInitial.heightMillimeters,
        importedInitial.bodyFatPercentage,
        importedInitial.bodyWaterGrams,
        importedInitial.boneMassGrams,
        importedInitial.leanBodyMassGrams,
        importedInitial.basalMetabolicRateKcalPerDay,
        importedInitial.sleepMinutes,
        importedInitial.soreness,
        importedInitial.performance,
        importedInitial.motivation,
        importedInitial.hunger,
        importedInitial.fatigue,
        importedInitial.stress,
        importedInitial.sleepQuality,
        importedInitial.comments,
) { CheckInFormState(importedInitial) }
    val weightFocusRequester = remember { FocusRequester() }
    val sleepFocusRequester = remember { FocusRequester() }
    var saving by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var healthSyncStatus by remember { mutableStateOf<String?>(null) }
    var importedSnapshot by remember(healthSnapshot) { mutableStateOf(healthSnapshot) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Card,
        contentColor = Ink,
    ) {
        CheckInSheetContent(
            form = form,
            day = initial.recordedOn,
            importedSnapshot = importedSnapshot,
            saving = saving,
            status = status,
            healthSyncStatus = healthSyncStatus,
            onDismiss = onDismiss,
            weightFocusRequester = weightFocusRequester,
            sleepFocusRequester = sleepFocusRequester,
            onSave = {
                saving = true
                status = null
                val saved = form.toCheckIn()
                scope.launch {
                    when (repository.save(saved)) {
                        SyncResult.Synced -> {
                            status = "Saved and synced"
                            onSaved(saved)
                        }
                        SyncResult.Pending -> {
                            status = "Saved locally · Sync pending"
                            onSaved(saved)
                        }
                        SyncResult.SignedOut -> status = "Sign in required"
                        SyncResult.OwnershipMismatch -> status = "Sync needs attention"
                    }
                    saving = false
                }
            },
            onHealthSnapshot = { snapshot ->
                importedSnapshot = snapshot
                form.applyHealthSnapshot(snapshot, repository::prefillFromHealth)
                healthSyncStatus = healthSyncMessage(initial.recordedOn, snapshot)
            },
        )
    }
}

@Composable
private fun CheckInSheetContent(
    form: CheckInFormState,
    day: String,
    importedSnapshot: HealthDailySnapshot?,
    saving: Boolean,
    status: String?,
    healthSyncStatus: String?,
    onDismiss: () -> Unit,
    weightFocusRequester: FocusRequester,
    sleepFocusRequester: FocusRequester,
    onSave: () -> Unit,
    onHealthSnapshot: (HealthDailySnapshot) -> Unit,
) {
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Ink,
        unfocusedTextColor = Ink,
        focusedLabelColor = Green,
        unfocusedLabelColor = Muted,
        focusedBorderColor = Green,
        unfocusedBorderColor = Track,
        cursorColor = Green,
    )
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Daily check-in", style = MaterialTheme.typography.headlineSmall)
                Text("A quick read on how you feel today", color = Muted, style = MaterialTheme.typography.bodyMedium)
            }
            IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close") }
        }
        HorizontalDivider(color = Track)

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Optional health import", style = MaterialTheme.typography.labelLarge)
                Text(
                    if (importedSnapshot == null) "Prefills weight, sleep, and movement when available"
                    else "Imported values are still editable",
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            HealthImportAction(day, onHealthSnapshot)
        }
        healthSyncStatus?.let { Text(it, color = Muted, style = MaterialTheme.typography.bodySmall) }

        HealthImportRow("Weight", importedSnapshot?.weightGrams?.let { "${it / 1000.0} kg" }, importedSnapshot?.sourceLabel) { weightFocusRequester.requestFocus() }
        OutlinedTextField(
            value = form.weightKg,
            onValueChange = { form.weightKg = it },
            label = { Text("Weight (kg)") },
            placeholder = { Text("Optional") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            colors = fieldColors,
        )

        HealthImportRow("Sleep", importedSnapshot?.sleepMinutes?.let { "${it / 60}h ${it % 60}m" }, importedSnapshot?.sourceLabel) { sleepFocusRequester.requestFocus() }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(form.sleepHours, { form.sleepHours = it }, label = { Text("Hours") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1f).focusRequester(sleepFocusRequester), colors = fieldColors)
            OutlinedTextField(form.sleepMinutes, { form.sleepMinutes = it }, label = { Text("Minutes") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1f), colors = fieldColors)
        }

        HealthImportRow("Steps", importedSnapshot?.steps?.toString(), importedSnapshot?.sourceLabel)
        HealthImportRow("Exercise", importedSnapshot?.activeMinutes?.let { "$it min active" }, importedSnapshot?.sourceLabel)
        HealthImportRow("Heart rate", importedSnapshot?.heartRateBpm?.let { "$it bpm" }, importedSnapshot?.sourceLabel)
        OutlinedTextField(
            value = form.heartRateBpm,
            onValueChange = { form.heartRateBpm = it.filter(Char::isDigit) },
            label = { Text("Heart rate (bpm)") },
            placeholder = { Text("Optional") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            colors = fieldColors,
        )

        HealthImportRow("Height", importedSnapshot?.heightMillimeters?.let { "${it / 10.0} cm" }, importedSnapshot?.sourceLabel)
        OutlinedTextField(form.heightCm, { form.heightCm = it }, label = { Text("Height (cm)") }, placeholder = { Text("Optional") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, colors = fieldColors)
        HealthImportRow("Body fat", importedSnapshot?.bodyFatPercentage?.let { "${it.formatDecimal()}%" }, importedSnapshot?.sourceLabel)
        OutlinedTextField(form.bodyFatPercentage, { form.bodyFatPercentage = it }, label = { Text("Body fat (%)") }, placeholder = { Text("Optional") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, colors = fieldColors)
        HealthImportRow("Lean body mass", importedSnapshot?.leanBodyMassGrams?.let { "${it / 1000.0} kg" }, importedSnapshot?.sourceLabel)
        OutlinedTextField(form.leanBodyMassKg, { form.leanBodyMassKg = it }, label = { Text("Lean body mass (kg)") }, placeholder = { Text("Optional") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, colors = fieldColors)
        HealthImportRow("Body water", importedSnapshot?.bodyWaterGrams?.let { "${it / 1000.0} kg" }, importedSnapshot?.sourceLabel)
        OutlinedTextField(form.bodyWaterKg, { form.bodyWaterKg = it }, label = { Text("Body water (kg)") }, placeholder = { Text("Optional") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, colors = fieldColors)
        HealthImportRow("Bone mass", importedSnapshot?.boneMassGrams?.let { "${it / 1000.0} kg" }, importedSnapshot?.sourceLabel)
        OutlinedTextField(form.boneMassKg, { form.boneMassKg = it }, label = { Text("Bone mass (kg)") }, placeholder = { Text("Optional") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, colors = fieldColors)
        HealthImportRow("Basal metabolic rate", importedSnapshot?.basalMetabolicRateKcalPerDay?.let { "$it kcal/day" }, importedSnapshot?.sourceLabel)
        OutlinedTextField(form.basalMetabolicRate, { form.basalMetabolicRate = it.filter(Char::isDigit) }, label = { Text("Basal metabolic rate (kcal/day)") }, placeholder = { Text("Optional") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, colors = fieldColors)
        val weightForDerived = form.weightKg.toDoubleOrNull()
        val bodyFatForDerived = form.bodyFatPercentage.toDoubleOrNull()?.takeIf { it in 0.0..100.0 }
        val heightForDerived = form.heightCm.toDoubleOrNull()?.div(100.0)?.takeIf { it > 0 }
        if (weightForDerived != null && bodyFatForDerived != null) {
            HealthImportRow("Fat mass (calculated)", "${(weightForDerived * bodyFatForDerived / 100).formatDecimal()} kg", null)
        }
        if (weightForDerived != null && heightForDerived != null) {
            HealthImportRow("BMI (calculated)", "${(weightForDerived / (heightForDerived * heightForDerived)).formatDecimal()}", null)
        }
        Text("Skeletal muscle is not a separate Health Connect record.", color = Muted, style = MaterialTheme.typography.bodySmall)

        CheckInMetric.entries.forEach { metric ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(metric.title, style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    metric.choices.forEach { choice ->
                        FilterChip(
                            selected = form.choice(metric) == choice,
                            onClick = {
                                form.toggle(metric, choice)
                            },
                            label = { Text(choice.label) },
                            modifier = Modifier.weight(1f).semantics {
                                contentDescription = "${metric.title}: ${choice.label}"
                                stateDescription = if (form.choice(metric) == choice) "Selected. Double tap to clear" else "Not selected"
                            },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Green.copy(alpha = .2f), selectedLabelColor = Green),
                        )
                    }
                }
            }
        }

        OutlinedTextField(
            value = form.notes,
            onValueChange = { form.notes = it },
            label = { Text("Anything else? (optional)") },
            minLines = 2,
            colors = fieldColors,
        )

        status?.let { Text(it, color = if (it.contains("pending")) Amber else Green, style = MaterialTheme.typography.labelMedium) }
        if (status != null && !saving) {
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Done") }
        }
        Button(onClick = onSave, enabled = !saving, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Color(0xFF003915))) {
            Icon(if (saving) Icons.Default.CheckCircle else Icons.Default.Save, contentDescription = null)
            Text(if (saving) "Saving…" else "Save check-in", modifier = Modifier.padding(start = 8.dp))
        }
        Spacer(Modifier.width(1.dp))
    }
}

private fun healthSyncMessage(day: String, snapshot: HealthDailySnapshot): String {
    val values = listOf(
        "weight" to snapshot.weightGrams,
        "sleep" to snapshot.sleepMinutes,
        "steps" to snapshot.steps,
        "exercise" to snapshot.activeMinutes,
        "heart rate" to snapshot.heartRateBpm,
        "height" to snapshot.heightMillimeters,
        "body fat" to snapshot.bodyFatPercentage,
        "body water" to snapshot.bodyWaterGrams,
        "bone mass" to snapshot.boneMassGrams,
        "lean body mass" to snapshot.leanBodyMassGrams,
        "basal metabolic rate" to snapshot.basalMetabolicRateKcalPerDay,
    )
    val found = values.filter { it.second != null }.map { it.first }
    val missing = values.filter { it.second == null }.map { it.first }
    val synced = if (found.isEmpty()) "No health values found" else "Synced ${found.joinToString()}"
    val missingDetails = missing.map { value ->
        if (value == "heart rate" && snapshot.heartRatePermissionGranted == false) "$value permission not granted"
        else if (value in snapshot.missingOptionalPermissions) "$value permission not granted"
        else value
    }
    return "$synced for $day${if (missingDetails.isEmpty()) "." else "; unavailable: ${missingDetails.joinToString()} on this day."}"
}

@Composable
private fun HealthImportRow(label: String, value: String?, source: String?, onEdit: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(value?.let { "Imported from ${source ?: "Health"}: $it" } ?: "No data yet", color = Muted, style = MaterialTheme.typography.bodySmall)
        }
        if (onEdit != null) {
            TextButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Edit imported $label")
                Text("Edit")
            }
        }
    }
}
