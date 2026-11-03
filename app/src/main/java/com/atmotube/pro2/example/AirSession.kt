package com.atmotube.pro2.example

import android.app.Application
import android.bluetooth.BluetoothDevice
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope

/**
 * Owns the BLE link for as long as the user is in the app.
 *
 * A configuration change such as rotation destroys and recreates [MainActivity], but the
 * ViewModel store keeps this object. The link is released in [onCleared], which runs only
 * when the activity is actually finishing.
 */
class AirSession(app: Application) : AndroidViewModel(app) {
    val bleManager = AtmotubeBleManager(app.applicationContext, viewModelScope)
    val historyManager = HistoryManager(app.applicationContext, bleManager)
    var device: BluetoothDevice? = null

    override fun onCleared() {
        bleManager.disconnect().enqueue()
        super.onCleared()
    }
}
