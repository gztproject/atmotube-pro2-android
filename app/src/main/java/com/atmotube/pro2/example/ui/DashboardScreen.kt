package com.atmotube.pro2.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.atmotube.pro2.example.AtmotubeGpsReading
import com.atmotube.pro2.example.AtmotubePmReading
import com.atmotube.pro2.example.AtmotubeReading
import com.atmotube.pro2.example.BANDS_NOTE
import com.atmotube.pro2.example.ConnectionState
import com.atmotube.pro2.example.Metric
import com.atmotube.pro2.example.batteryLabel
import com.atmotube.pro2.example.climateReadout
import com.atmotube.pro2.example.co2Metric
import com.atmotube.pro2.example.errorLabels
import com.atmotube.pro2.example.formatPm
import com.atmotube.pro2.example.gpsHasFix
import com.atmotube.pro2.example.noxMetric
import com.atmotube.pro2.example.pm1Metric
import com.atmotube.pro2.example.pm10Metric
import com.atmotube.pro2.example.pm25Metric
import com.atmotube.pro2.example.statusLabels
import com.atmotube.pro2.example.summarize
import com.atmotube.pro2.example.vocMetric
import java.util.Locale

@Composable
fun DashboardScreen(
    deviceName: String,
    connectionState: ConnectionState,
    firmwareVersion: String?,
    reading: AtmotubeReading?,
    pmReading: AtmotubePmReading?,
    gpsReading: AtmotubeGpsReading?,
    historyPending: Boolean,
    hasHistory: Boolean,
    isDownloadingHistory: Boolean,
    onDisconnect: () -> Unit,
    onReconnect: () -> Unit,
    onSyncHistory: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenTools: () -> Unit,
) {
    val locale = Locale.getDefault()
    val summary = summarize(pmReading?.pm25, pmReading?.pm10, locale)
    val climate = reading?.let {
        climateReadout(it.temperature, it.humidity, it.pressure, it.errorFlags, locale)
    }
    val errors = reading?.let { errorLabels(it.errorFlags) }.orEmpty()
    val statuses = reading?.let { statusLabels(it.errorFlags) }.orEmpty()
    val live = connectionState is ConnectionState.Ready

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = deviceName,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = connectionSubtitle(connectionState, firmwareVersion),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                reading?.let { batteryLabel(it.batteryLevel, it.errorFlags) }?.let { battery ->
                    Text(battery, style = MaterialTheme.typography.titleMedium)
                }
                if (live || connectionState is ConnectionState.Connected || connectionState is ConnectionState.Connecting) {
                    TextButton(onClick = onDisconnect) { Text("Disconnect") }
                } else if (connectionState is ConnectionState.Disconnected) {
                    TextButton(onClick = onReconnect) { Text("Reconnect") }
                }
            }
        }

        if (statuses.isNotEmpty()) {
            Text(
                text = statuses.joinToString(" · "),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (errors.isNotEmpty()) {
            Surface(
                color = AirLevelColor(com.atmotube.pro2.example.AirLevel.VERY_POOR).copy(alpha = 0.12f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = errors.joinToString(" · "),
                    modifier = Modifier.padding(12.dp),
                    color = com.atmotube.pro2.example.AirLevel.VERY_POOR.indicator(),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        SummaryCard(summary)

        if (reading == null) {
            Text(
                "Waiting for the first reading.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                MetricCard(
                    metric = pmReading?.let { pm25Metric(it.pm25, locale) } ?: waitingMetric("PM2.5"),
                    modifier = Modifier.weight(1f),
                )
                MetricCard(
                    metric = pmReading?.let { pm10Metric(it.pm10, locale) } ?: waitingMetric("PM10"),
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                MetricCard(co2Metric(reading.co2Ppm, locale), Modifier.weight(1f))
                MetricCard(
                    metric = pmReading?.let { pm1Metric(it.pm1, locale) } ?: waitingMetric("PM1"),
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                MetricCard(vocMetric(reading.vocIndex, reading.vocPpb, locale), Modifier.weight(1f))
                MetricCard(noxMetric(reading.noxIndex, locale), Modifier.weight(1f))
            }
            if (climate != null) {
                ClimateRow(climate)
            }
            if (pmReading != null) {
                ParticleCard(pmReading, locale)
            }
        }

        if (gpsReading != null && gpsHasFix(gpsReading)) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Location", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "%.5f, %.5f".format(locale, gpsReading.latitude, gpsReading.longitude),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "Altitude ${gpsReading.altitude} m · ${gpsReading.satellitesFixed} satellites",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (historyPending) {
            Text(
                "The device has new history.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Button(
            onClick = onSyncHistory,
            modifier = Modifier.fillMaxWidth(),
            enabled = live && !isDownloadingHistory,
        ) {
            if (isDownloadingHistory) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Syncing history")
            } else {
                Text(if (historyPending) "Sync new history" else "Sync history")
            }
        }
        if (hasHistory) {
            OutlinedButton(onClick = onOpenHistory, modifier = Modifier.fillMaxWidth()) {
                Text("View history")
            }
        }
        TextButton(onClick = onOpenTools, modifier = Modifier.fillMaxWidth()) {
            Text("Device tools")
        }
        Text(
            BANDS_NOTE,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SummaryCard(summary: com.atmotube.pro2.example.AirSummary) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "EU air quality index",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = summary.title,
                style = MaterialTheme.typography.headlineSmall,
                color = summary.level.indicator(),
            )
            Text(summary.detail, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun MetricCard(metric: Metric, modifier: Modifier, footnote: String? = null) {
    Card(modifier = modifier) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(metric.level.band()),
            )
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    metric.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(metric.value, style = MaterialTheme.typography.headlineSmall, maxLines = 1)
                if (metric.unit.isNotEmpty()) {
                    Text(
                        metric.unit,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    metric.caption,
                    style = MaterialTheme.typography.labelLarge,
                    color = metric.level.indicator(),
                )
                if (footnote != null) {
                    Text(
                        footnote,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ClimateRow(climate: com.atmotube.pro2.example.ClimateReadout) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ClimateCell("Temperature", climate.temperature, Modifier.weight(1f))
            ClimateCell("Humidity", climate.humidity, Modifier.weight(1f))
            ClimateCell("Pressure", climate.pressure + if (climate.pressure == "—" || climate.pressure == "Heating") "" else " hPa", Modifier.weight(1f))
        }
        climate.note?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = com.atmotube.pro2.example.AirLevel.FAIR.indicator())
        }
    }
}

@Composable
private fun ClimateCell(label: String, value: String, modifier: Modifier) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Text(value, style = MaterialTheme.typography.titleMedium, maxLines = 1)
        }
    }
}

@Composable
private fun ParticleCard(pm: AtmotubePmReading, locale: Locale) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Particles", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "0.5 / 1 / 2.5 / 10 µm: ${pm.pm05Particles} / ${pm.pm1Particles} / ${pm.pm25Particles} / ${pm.pm10Particles} per cm³",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Typical size ${formatPm(pm.typicalParticleSize, locale)} µm",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun waitingMetric(label: String) = Metric(label, "—", "µg/m³", com.atmotube.pro2.example.AirLevel.UNKNOWN, "Waiting")

@Composable
private fun AirLevelColor(level: com.atmotube.pro2.example.AirLevel) = level.indicator()

private fun connectionSubtitle(state: ConnectionState, firmware: String?): String {
    val status = when (state) {
        ConnectionState.Disconnected -> "Disconnected"
        ConnectionState.Connecting -> "Connecting"
        ConnectionState.Connected -> "Starting sensors"
        ConnectionState.Ready -> "Live"
        ConnectionState.Disconnecting -> "Disconnecting"
    }
    return if (firmware.isNullOrBlank()) status else "$status · $firmware"
}
