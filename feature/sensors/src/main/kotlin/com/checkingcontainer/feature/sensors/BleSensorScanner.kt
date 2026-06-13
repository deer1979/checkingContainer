package com.checkingcontainer.feature.sensors

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Escanea el advertising BLE y emite [SensorReading] de los sensores Yellow
 * Jacket conocidos. No conecta a nada: los sensores difunden sus datos.
 * El llamador debe haber concedido los permisos BLE (BLUETOOTH_SCAN en API 31+,
 * o BLUETOOTH + ACCESS_FINE_LOCATION en versiones previas).
 */
@Singleton
class BleSensorScanner @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val scanner by lazy {
        val mgr = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        mgr?.adapter?.bluetoothLeScanner
    }

    /** True si el adaptador Bluetooth existe y está encendido. */
    fun bluetoothDisponible(): Boolean {
        val mgr = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return mgr?.adapter?.isEnabled == true
    }

    @SuppressLint("MissingPermission")
    fun observe(): Flow<SensorReading> = callbackFlow {
        val ble = scanner
        if (ble == null) {
            close()
            return@callbackFlow
        }
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val record = result.scanRecord ?: return
                val name = result.device?.name ?: record.deviceName ?: "Sensor"
                for (uuid in YjackParser.UUIDS_CONOCIDOS) {
                    val data = record.getServiceData(ParcelUuid.fromString(uuid)) ?: continue
                    val reading = YjackParser.parse(uuid, data, name, result.rssi)
                    if (reading != null) trySend(reading)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.w(TAG, "scan failed: $errorCode")
            }
        }
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setLegacy(false)
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
            .build()
        // Sin filtros por UUID: algunos equipos meten el service data sin anunciarlo
        // en la lista de servicios; filtramos en onScanResult.
        fun arrancar() = runCatching { ble.startScan(null, settings, callback) }
        fun parar() = runCatching { ble.stopScan(callback) }

        arrancar().onFailure { close(it) }

        // Reinicio periódico cada 7s: Android (sobre todo Samsung) deja de entregar
        // los anuncios repetidos y la lectura se "congela". Reiniciar el escaneo
        // fuerza datos frescos. Es exactamente lo que hace la app oficial YJACK VIEW.
        val reinicio = launch {
            while (isActive) {
                delay(REINICIO_MS)
                parar()
                arrancar()
            }
        }

        awaitClose {
            reinicio.cancel()
            parar()
        }
    }

    private companion object {
        const val TAG = "BleSensorScanner"
        const val REINICIO_MS = 7_000L
    }
}
