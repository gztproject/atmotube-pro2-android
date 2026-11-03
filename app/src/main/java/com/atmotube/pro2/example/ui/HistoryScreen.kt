package com.atmotube.pro2.example.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.atmotube.pro2.example.AirLevel
import com.atmotube.pro2.example.HistoryMeasurement
import com.atmotube.pro2.example.downsample
import com.atmotube.pro2.example.formatCo2
import com.atmotube.pro2.example.formatPm
import com.atmotube.pro2.example.formatTemperature
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    measurements: List<HistoryMeasurement>,
    canShare: Boolean,
    isDownloading: Boolean,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onSync: () -> Unit,
) {
    val locale = Locale.getDefault()
    val timeFormat = remember { SimpleDateFormat("HH:mm", locale) }
    val dayFormat = remember { SimpleDateFormat("d MMM HH:mm", locale) }
    val newestFirst = remember(measurements) { measurements.sortedByDescending { it.timestamp } }
    val span = remember(newestFirst) {
        if (newestFirst.size < 2) {
            null
        } else {
            val oldest = newestFirst.last().timestamp
            val newest = newestFirst.first().timestamp
            "${dayFormat.format(Date(oldest * 1000))} – ${dayFormat.format(Date(newest * 1000))}"
        }
    }
    val co2Series = remember(newestFirst) {
        downsample(newestFirst.asReversed().mapNotNull { sample ->
            sample.co2Ppm?.takeIf { formatCo2(it, Locale.US) != "—" && formatCo2(it, Locale.US) != "Off" && formatCo2(it, Locale.US) != "Heating" }?.toFloat()
        })
    }
    val pmSeries = remember(newestFirst) {
        downsample(newestFirst.asReversed().mapNotNull { sample ->
            val text = formatPm(sample.pm25, Locale.US)
            sample.pm25?.takeIf { text != "—" && text != "Off" && text != "Heating" }?.toFloat()
        })
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        TextButton(onClick = onBack) { Text("Back") }
        Text("History", style = MaterialTheme.typography.headlineSmall)
        Text(
            text = buildString {
                append(newestFirst.size)
                append(if (newestFirst.size == 1) " reading" else " readings")
                if (span != null) {
                    append(" · ")
                    append(span)
                }
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = onSync, enabled = !isDownloading, modifier = Modifier.weight(1f)) {
                if (isDownloading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Syncing")
                } else {
                    Text("Sync again")
                }
            }
            OutlinedButton(onClick = onShare, enabled = canShare, modifier = Modifier.weight(1f)) {
                Text("Share CSV")
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        if (newestFirst.isEmpty()) {
            Text(
                "Nothing synced yet. History stays on the device until you sync it.",
                style = MaterialTheme.typography.bodyLarge,
            )
            return
        }

        if (co2Series.size >= 2 || pmSeries.size >= 2) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (co2Series.size >= 2) {
                        Text("CO2", style = MaterialTheme.typography.labelLarge, color = AirLevel.POOR.indicator())
                        Sparkline(co2Series, AirLevel.POOR.indicator(), Modifier.fillMaxWidth().height(56.dp))
                    }
                    if (pmSeries.size >= 2) {
                        Text("PM2.5", style = MaterialTheme.typography.labelLarge, color = AirLevel.FAIR.indicator())
                        Sparkline(pmSeries, AirLevel.FAIR.indicator(), Modifier.fillMaxWidth().height(56.dp))
                    }
                    Text(
                        "Oldest on the left.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            itemsIndexed(newestFirst, key = { index, sample -> "$index-${sample.timestamp}" }) { _, sample ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        timeFormat.format(Date(sample.timestamp * 1000)),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.width(56.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text("CO2 ${formatCo2(sample.co2Ppm, locale)} ppm", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "PM2.5 ${formatPm(sample.pm25, locale)} µg/m³ · ${formatTemperature(sample.temperature, locale)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun Sparkline(
    values: List<Float>,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier,
) {
    Canvas(modifier = modifier) {
        if (values.size < 2) return@Canvas
        val min = values.minOrNull() ?: return@Canvas
        val max = values.maxOrNull() ?: return@Canvas
        val span = (max - min).takeIf { it > 0f } ?: 1f
        val path = Path()
        values.forEachIndexed { index, value ->
            val x = size.width * index / (values.size - 1)
            val y = size.height - ((value - min) / span) * size.height
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
        val lastX = size.width
        val lastY = size.height - ((values.last() - min) / span) * size.height
        drawCircle(color, radius = 4.dp.toPx(), center = Offset(lastX, lastY))
    }
}
