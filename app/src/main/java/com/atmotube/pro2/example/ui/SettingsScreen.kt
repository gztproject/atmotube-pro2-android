package com.atmotube.pro2.example.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.atmotube.pro2.example.AirLevel
import com.atmotube.pro2.example.BandCuts
import com.atmotube.pro2.example.BandProfile
import com.atmotube.pro2.example.Pollutant
import com.atmotube.pro2.example.PollutantAlert
import com.atmotube.pro2.example.alertLevelChoices
import com.atmotube.pro2.example.defaultCuts
import com.atmotube.pro2.example.defaultNote
import com.atmotube.pro2.example.defaultProfile
import com.atmotube.pro2.example.formatCut
import com.atmotube.pro2.example.levelLabel
import com.atmotube.pro2.example.parseCut

@Composable
fun SettingsScreen(
    profile: BandProfile,
    onBack: () -> Unit,
    onSave: (BandProfile) -> Unit,
) {
    var drafts by remember(profile) { mutableStateOf(profile.toDrafts()) }
    var levelDialog by remember { mutableStateOf<Pollutant?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onBack) { Text("Back") }
        Text("Bands and alerts", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Each number is the top of that band. A reading at or above the last number is Extremely poor. Alerts are sent while the phone stays connected to the device.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Pollutant.entries.forEach { pollutant ->
            val draft = drafts.getValue(pollutant)
            PollutantCard(
                pollutant = pollutant,
                draft = draft,
                onDraft = { drafts = drafts + (pollutant to it) },
                onPickLevel = { levelDialog = pollutant },
            )
        }
        Button(
            onClick = {
                val (next, updated) = draftsToProfile(drafts)
                drafts = updated
                if (next != null) onSave(next)
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Save")
        }
        OutlinedButton(
            onClick = { onSave(defaultProfile()) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Reset defaults")
        }
    }

    val dialogPollutant = levelDialog
    if (dialogPollutant != null) {
        AlertDialog(
            onDismissRequest = { levelDialog = null },
            title = { Text("Alert from") },
            text = {
                Column {
                    alertLevelChoices.forEach { level ->
                        TextButton(
                            onClick = {
                                val draft = drafts.getValue(dialogPollutant)
                                drafts = drafts + (dialogPollutant to draft.copy(from = level))
                                levelDialog = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(levelLabel(level))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { levelDialog = null }) { Text("Close") }
            },
        )
    }
}

@Composable
private fun PollutantCard(
    pollutant: Pollutant,
    draft: BandDraft,
    onDraft: (BandDraft) -> Unit,
    onPickLevel: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(pollutant.label, style = MaterialTheme.typography.titleMedium)
            Text(
                "${defaultNote(pollutant)} Unit: ${pollutant.unit}.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                CutField("Good below", draft.good, Modifier.weight(1f)) { onDraft(draft.copy(good = it, error = null)) }
                CutField("Fair below", draft.fair, Modifier.weight(1f)) { onDraft(draft.copy(fair = it, error = null)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                CutField("Moderate below", draft.moderate, Modifier.weight(1f)) { onDraft(draft.copy(moderate = it, error = null)) }
                CutField("Poor below", draft.poor, Modifier.weight(1f)) { onDraft(draft.copy(poor = it, error = null)) }
            }
            CutField("Very poor below", draft.veryPoor, Modifier.fillMaxWidth()) {
                onDraft(draft.copy(veryPoor = it, error = null))
            }
            draft.error?.let {
                Text(it, color = AirLevel.POOR.indicator(), style = MaterialTheme.typography.bodySmall)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Notify", style = MaterialTheme.typography.bodyLarge)
                    TextButton(onClick = onPickLevel) {
                        Text("Alert from ${levelLabel(draft.from)}")
                    }
                }
                Switch(
                    checked = draft.enabled,
                    onCheckedChange = { onDraft(draft.copy(enabled = it)) },
                )
            }
        }
    }
}

@Composable
private fun CutField(
    label: String,
    value: String,
    modifier: Modifier,
    onValue: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        modifier = modifier,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
}

private data class BandDraft(
    val good: String,
    val fair: String,
    val moderate: String,
    val poor: String,
    val veryPoor: String,
    val enabled: Boolean,
    val from: AirLevel,
    val error: String? = null,
)

private fun BandProfile.toDrafts(): Map<Pollutant, BandDraft> =
    Pollutant.entries.associateWith { pollutant ->
        val cuts = cuts[pollutant] ?: defaultCuts(pollutant)
        val alert = alerts[pollutant] ?: PollutantAlert()
        BandDraft(
            good = formatCut(cuts.goodBelow),
            fair = formatCut(cuts.fairBelow),
            moderate = formatCut(cuts.moderateBelow),
            poor = formatCut(cuts.poorBelow),
            veryPoor = formatCut(cuts.veryPoorBelow),
            enabled = alert.enabled,
            from = alert.from,
        )
    }

private fun draftsToProfile(drafts: Map<Pollutant, BandDraft>): Pair<BandProfile?, Map<Pollutant, BandDraft>> {
    val cuts = mutableMapOf<Pollutant, BandCuts>()
    val alerts = mutableMapOf<Pollutant, PollutantAlert>()
    val next = drafts.toMutableMap()
    var ok = true
    for (pollutant in Pollutant.entries) {
        val draft = drafts.getValue(pollutant)
        val numbers = listOf(draft.good, draft.fair, draft.moderate, draft.poor, draft.veryPoor).map { parseCut(it) }
        val band = if (numbers.all { it != null }) {
            BandCuts(numbers[0]!!, numbers[1]!!, numbers[2]!!, numbers[3]!!, numbers[4]!!).validated()
        } else {
            null
        }
        if (band == null) {
            ok = false
            next[pollutant] = draft.copy(error = "Enter five rising cutoffs, lowest first.")
        } else {
            next[pollutant] = draft.copy(error = null)
            cuts[pollutant] = band
            alerts[pollutant] = PollutantAlert(draft.enabled, draft.from)
        }
    }
    val profile = if (ok) BandProfile(cuts, alerts) else null
    return profile to next
}
