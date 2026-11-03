package com.atmotube.pro2.example.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.atmotube.pro2.example.GnssMode
import com.atmotube.pro2.example.GnssSensorState
import com.atmotube.pro2.example.GnssStatus
import com.atmotube.pro2.example.PmMode
import com.atmotube.pro2.example.PmSensorState
import com.atmotube.pro2.example.PmStatus
import com.atmotube.pro2.example.gnssModeChoices
import com.atmotube.pro2.example.gnssTimerEditor
import com.atmotube.pro2.example.pmModeChoices

@Composable
fun DeviceSettingsScreen(
    connected: Boolean,
    busy: Boolean,
    pm: PmStatus?,
    gps: GnssStatus?,
    message: String?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onPmMode: (PmMode) -> Unit,
    onPmCharging: (Boolean) -> Unit,
    onGpsMode: (GnssMode) -> Unit,
    onGpsTimer: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onBack) { Text("Back") }
        Text("Device settings", style = MaterialTheme.typography.headlineSmall)
        Text(
            "PM interval and GPS mode are stored on the Atmotube, the same controls as the official app. Readings on the main screen stay live either way.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!connected) {
            Text(
                "Connect to the device to read or change these.",
                style = MaterialTheme.typography.bodyLarge,
            )
        } else if (busy && pm == null && gps == null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(modifier = Modifier.padding(2.dp), strokeWidth = 2.dp)
                Text("Reading device settings")
            }
        }
        message?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("PM sensor", style = MaterialTheme.typography.titleMedium)
                Text(
                    when (pm?.state) {
                        PmSensorState.ON -> "PM sensor is on"
                        PmSensorState.OFF -> "PM sensor is off"
                        null -> "PM status unknown"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Always on measures about once a second. The minute modes wake the sensor on that interval. On demand waits until something asks for a measurement.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                pmModeChoices.forEach { mode ->
                    ModeButton(mode.label, selected = pm?.mode == mode, enabled = connected && pm != null && !busy) {
                        onPmMode(mode)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Keep PM on while charging", modifier = Modifier.weight(1f))
                    Switch(
                        checked = pm?.charging == true,
                        onCheckedChange = onPmCharging,
                        enabled = connected && pm != null && !busy,
                    )
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("GPS", style = MaterialTheme.typography.titleMedium)
                Text(
                    when (gps?.state) {
                        GnssSensorState.ON -> "GPS is on"
                        GnssSensorState.OFF -> "GPS is off"
                        null -> "GPS status unknown"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Always on tracks continuously. Timer starts when the device moves and turns off after a quiet period. Always off leaves the GPS module off.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                gnssModeChoices.forEach { mode ->
                    ModeButton(mode.label, selected = gps?.mode == mode, enabled = connected && gps != null && !busy) {
                        onGpsMode(mode)
                    }
                }
                val minutes = gps?.let { gnssTimerEditor(it) } ?: 10
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Quiet time")
                        Text(
                            "Turns off after $minutes minutes without motion",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(
                        onClick = { onGpsTimer(minutes - 1) },
                        enabled = connected && gps?.mode == GnssMode.TIMER && minutes > 1 && !busy,
                    ) { Text("−") }
                    TextButton(
                        onClick = { onGpsTimer(minutes + 1) },
                        enabled = connected && gps?.mode == GnssMode.TIMER && minutes < 60 && !busy,
                    ) { Text("+") }
                }
            }
        }

        OutlinedButton(onClick = onRefresh, enabled = connected && !busy, modifier = Modifier.fillMaxWidth()) {
            Text("Refresh")
        }
    }
}

@Composable
private fun ModeButton(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(label) }
    }
}
