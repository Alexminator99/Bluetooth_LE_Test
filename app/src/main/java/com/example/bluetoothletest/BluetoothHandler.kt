package com.example.bluetoothletest

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.welie.blessed.*
import timber.log.Timber
import java.util.*


open class SingletonHolder<out T : Any, in A>(creator: (A) -> T) {
    private var creator: ((A) -> T)? = creator

    @Volatile
    private var instance: T? = null

    fun getInstance(arg: A): T {
        val checkInstance = instance
        if (checkInstance != null) {
            return checkInstance
        }

        return synchronized(this) {
            val checkInstanceAgain = instance
            if (checkInstanceAgain != null) {
                checkInstanceAgain
            } else {
                val created = creator!!(arg)
                instance = created
                creator = null
                created
            }
        }
    }
}

class BluetoothHandler private constructor(context: Context) {
    // Local variables
    lateinit var central: BluetoothCentralManager
    private val handler = Handler(Looper.getMainLooper())

    private var peripheralGlobal: BluetoothPeripheral? = null

    // Callback for peripherals
    private val peripheralCallback: BluetoothPeripheralCallback =
        object : BluetoothPeripheralCallback() {
            override fun onServicesDiscovered(peripheral: BluetoothPeripheral) {
                // Request a higher MTU, iOS always asks for 185
                peripheral.requestMtu(243)

                // Request a new connection priority
                peripheral.requestConnectionPriority(ConnectionPriority.HIGH)

                peripheralGlobal = peripheral
            }

            override fun onNotificationStateUpdate(
                peripheral: BluetoothPeripheral,
                characteristic: BluetoothGattCharacteristic,
                status: GattStatus
            ) {
                if (status == GattStatus.SUCCESS) {
                    val isNotifying = peripheral.isNotifying(characteristic)
                    Timber.i("SUCCESS: Notify set to '%s' for %s", isNotifying, characteristic.uuid)
                }
            }

            override fun onCharacteristicWrite(
                peripheral: BluetoothPeripheral,
                value: ByteArray,
                characteristic: BluetoothGattCharacteristic,
                status: GattStatus
            ) {
                if (status == GattStatus.SUCCESS) {
                    Timber.i(
                        "SUCCESS: Writing <%s> to <%s>",
                        BluetoothBytesParser.bytes2String(value),
                        characteristic.uuid
                    )
                } else {
                    Timber.i(
                        "ERROR: Failed writing <%s> to <%s> (%s)",
                        BluetoothBytesParser.bytes2String(value),
                        characteristic.uuid,
                        status
                    )
                }
            }

            override fun onCharacteristicUpdate(
                peripheral: BluetoothPeripheral,
                value: ByteArray,
                characteristic: BluetoothGattCharacteristic,
                status: GattStatus
            ) {
                if (status != GattStatus.SUCCESS) return
                val characteristicUUID = characteristic.uuid
                val parser = BluetoothBytesParser(value)
                if (characteristicUUID == HEARTRATE_MEASUREMENT_CHARACTERISTIC_UUID) {
                    val measurement = HeartRateMeasurement(characteristic)
                    val intent = Intent(MEASUREMENT_HEARTRATE)
                    intent.putExtra(MEASUREMENT_HEARTRATE_EXTRA, measurement)
                    sendMeasurement(intent, peripheral)
                    Timber.d("%s", measurement)
                }
            }

            override fun onMtuChanged(
                peripheral: BluetoothPeripheral,
                mtu: Int,
                status: GattStatus
            ) {
                Timber.i("new MTU set: %d", mtu)
            }

            private fun sendMeasurement(intent: Intent, peripheral: BluetoothPeripheral) {
                intent.putExtra(MEASUREMENT_EXTRA_PERIPHERAL, peripheral.address)
                context.sendBroadcast(intent)
            }

        }

    // Callback for central
    private val bluetoothCentralManagerCallback: BluetoothCentralManagerCallback =
        object : BluetoothCentralManagerCallback() {
            override fun onConnectedPeripheral(peripheral: BluetoothPeripheral) {
                Timber.i("connected to '%s'", peripheral.name)
            }

            override fun onConnectionFailed(peripheral: BluetoothPeripheral, status: HciStatus) {
                Timber.e("connection '%s' failed with status %s", peripheral.name, status)
            }

            override fun onDisconnectedPeripheral(
                peripheral: BluetoothPeripheral,
                status: HciStatus
            ) {
                Timber.i("disconnected '%s' with status %s", peripheral.name, status)

                // Reconnect to this device when it becomes available again
                handler.postDelayed({
                    central.autoConnectPeripheral(
                        peripheral,
                        peripheralCallback
                    )
                }, 5000)
            }

            override fun onDiscoveredPeripheral(
                peripheral: BluetoothPeripheral,
                scanResult: ScanResult
            ) {
                Timber.i("Found peripheral '%s'", peripheral.name)
                central.stopScan()
                central.connectPeripheral(peripheral, peripheralCallback)
            }

            override fun onBluetoothAdapterStateChanged(state: Int) {
                Timber.i("bluetooth adapter changed state to %d", state)
                if (state == BluetoothAdapter.STATE_ON) {
                    // Bluetooth is on now, start scanning again
                    // Scan for peripherals with a certain service UUIDs
                    central.startPairingPopupHack()
                    startScan()
                }
            }

            override fun onScanFailed(scanFailure: ScanFailure) {
                Timber.i("scanning failed with error %s", scanFailure)
            }
        }

    /**
     * Only enables indications in the reader
     * @param enableIndications Boolean whether it should enable notifications
     * @return Boolean false if there is no device connected, true otherwise
     */
    fun enableIndications(enableIndications: Boolean): Boolean {

        if (peripheralGlobal != null) {
            val services = peripheralGlobal!!.services

            var characteristic: BluetoothGattCharacteristic? = null

            services.forEach {
                if (it.uuid == HRS_SERVICE_UUID) {
                    characteristic = it.getCharacteristic(HEARTRATE_MEASUREMENT_CHARACTERISTIC_UUID)
                    return@forEach
                }
            }

            if (characteristic == null) {
                return false
            }

            return if (enableIndications) {
                val descriptor = characteristic!!.descriptors[0]
                peripheralGlobal!!.writeDescriptor(
                    descriptor,
                    BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                )
                Timber.i("indication on in: ${peripheralGlobal!!.name}")
                true
            } else {
                val descriptor = characteristic!!.descriptors[0]
                peripheralGlobal!!.writeDescriptor(
                    descriptor,
                    BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                )
                Timber.i("indication off in: ${peripheralGlobal!!.name}")
                true
            }
        }

        return false
    }

    private fun startScan() {
        handler.postDelayed({
            central.scanForPeripheralsWithServices(
                arrayOf(
                    HRS_SERVICE_UUID,
                )
            )
        }, 1000)
    }

    companion object : SingletonHolder<BluetoothHandler, Context>(::BluetoothHandler) {
        // Intent constants
        const val MEASUREMENT_HEARTRATE = "blessed.measurement.heartrate"
        const val MEASUREMENT_HEARTRATE_EXTRA = "blessed.measurement.heartrate.extra"
        const val MEASUREMENT_EXTRA_PERIPHERAL = "blessed.measurement.peripheral"

        // UUIDs for the Heart Rate service (HRS)
        private val HRS_SERVICE_UUID = UUID.fromString("b76504bb-47bd-0290-cf4b-66ea981d1b95")
        private val HEARTRATE_MEASUREMENT_CHARACTERISTIC_UUID =
            UUID.fromString("7D1E1CEB-0CCD-14B8-AB43-8ADCFEF7B446")
    }

    init {
        // Plant a tree
        Timber.plant(Timber.DebugTree())

        // Create BluetoothCentral
        central = BluetoothCentralManager(
            context,
            bluetoothCentralManagerCallback,
            Handler(Looper.getMainLooper())
        )

        // Scan for peripherals with a certain service UUIDs
        central.startPairingPopupHack()
        startScan()
    }
}



