package com.reus.nutri

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

class CheckInFormState(initial: DailyCheckIn) {
    private var checkIn = initial
    private val selected = mutableStateMapOf<CheckInMetric, CheckInChoice>()

    var weightKg by mutableStateOf(initial.weightGrams?.let { grams -> (grams / 1000.0).formatDecimal() } ?: "")
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
        val merged = prefill(toCheckIn(), snapshot)
        checkIn = merged
        if (weightKg.isBlank()) weightKg = merged.weightGrams?.let { (it / 1000.0).formatDecimal() } ?: ""
        if (sleepHours.isBlank() && sleepMinutes.isBlank()) {
            merged.sleepMinutes?.let {
                sleepHours = (it / 60).toString()
                sleepMinutes = (it % 60).toString()
            }
        }
    }

    fun toCheckIn(): DailyCheckIn = checkIn.copy(
        weightGrams = weightKg.toDoubleOrNull()?.let { (it * 1000).toInt() },
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
    val form = remember(importedInitial) { CheckInFormState(importedInitial) }
    val weightFocusRequester = remember { FocusRequester() }
    val sleepFocusRequester = remember { FocusRequester() }
    var saving by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var importedSnapshot by remember(healthSnapshot) { mutableStateOf(healthSnapshot) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        CheckInSheetContent(
            form = form,
            day = initial.recordedOn,
            importedSnapshot = importedSnapshot,
            saving = saving,
            status = status,
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
    onDismiss: () -> Unit,
    weightFocusRequester: FocusRequester,
    sleepFocusRequester: FocusRequester,
    onSave: () -> Unit,
    onHealthSnapshot: (HealthDailySnapshot) -> Unit,
) {
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

        HealthImportRow("Weight", importedSnapshot?.weightGrams?.let { "${it / 1000.0} kg" }, importedSnapshot?.sourceLabel) { weightFocusRequester.requestFocus() }
        OutlinedTextField(
            value = form.weightKg,
            onValueChange = { form.weightKg = it },
            label = { Text("Weight (kg)") },
            placeholder = { Text("Optional") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().focusRequester(weightFocusRequester),
        )

        HealthImportRow("Sleep", importedSnapshot?.sleepMinutes?.let { "${it / 60}h ${it % 60}m" }, importedSnapshot?.sourceLabel) { sleepFocusRequester.requestFocus() }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(form.sleepHours, { form.sleepHours = it }, label = { Text("Hours") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1f).focusRequester(sleepFocusRequester))
            OutlinedTextField(form.sleepMinutes, { form.sleepMinutes = it }, label = { Text("Minutes") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1f))
        }

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
            modifier = Modifier.fillMaxWidth(),
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

@Composable
private fun HealthImportRow(label: String, value: String?, source: String?, onEdit: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(value?.let { "Imported from ${source ?: "Health"}: $it" } ?: "No data yet", color = Muted, style = MaterialTheme.typography.bodySmall)
        }
        TextButton(onClick = onEdit) {
            Icon(Icons.Default.Edit, contentDescription = "Edit imported $label")
            Text("Edit")
        }
    }
}
