package com.atmotube.pro2.example.ui

import android.bluetooth.BluetoothDevice
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class FoundDevice(
    val device: BluetoothDevice,
    val name: String,
    val rssi: Int,
)

@Composable
fun ScanScreen(
    devices: List<FoundDevice>,
    isScanning: Boolean,
    isConnecting: Boolean,
    lastAddress: String?,
    onScanClick: () -> Unit,
    onStopClick: () -> Unit,
    onDeviceClick: (BluetoothDevice) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Text("Atmotube Air", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Find your PRO 2 to see CO2, particles, and the rest of the air around you.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = if (isScanning) onStopClick else onScanClick,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isConnecting,
        ) {
            if (isConnecting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Connecting")
            } else {
                Text(if (isScanning) "Stop scanning" else "Scan")
            }
        }
        if (isScanning && !isConnecting) {
            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (devices.isEmpty()) {
            Text(
                text = if (isScanning) {
                    "Looking for a nearby PRO 2. It shows up with ATMO in the name."
                } else {
                    "Scan when the device is on and close to the phone."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(devices.sortedByDescending { it.rssi }, key = { it.device.address }) { found ->
                    val lastUsed = found.device.address == lastAddress
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isConnecting) { onDeviceClick(found.device) },
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = found.name,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = buildString {
                                    append(found.device.address)
                                    append(" · ")
                                    append(found.rssi)
                                    append(" dBm")
                                    if (lastUsed) append(" · Last used")
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
