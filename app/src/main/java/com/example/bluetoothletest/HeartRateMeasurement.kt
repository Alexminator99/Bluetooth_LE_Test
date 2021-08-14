package com.example.bluetoothletest

import android.bluetooth.BluetoothGattCharacteristic
import com.welie.blessed.BluetoothBytesParser
import java.io.Serializable
import java.nio.ByteOrder


class HeartRateMeasurement(characteristic: BluetoothGattCharacteristic) : Serializable {
    private val messagePart: Int
    private val battery: Int
    private val time: Int
    private val systemStatus: Int
    private val fpgaStatus: Int
    private val btStatus: Int

    init {
        val parser = BluetoothBytesParser(characteristic.value)

        messagePart =
            parser.getIntValue(BluetoothBytesParser.FORMAT_UINT8, 0, ByteOrder.nativeOrder())
        time = parser.getIntValue(BluetoothBytesParser.FORMAT_UINT32, 1, ByteOrder.nativeOrder())
        battery = parser.getIntValue(BluetoothBytesParser.FORMAT_UINT16, 5, ByteOrder.nativeOrder())
        systemStatus =
            parser.getIntValue(BluetoothBytesParser.FORMAT_UINT32, 7, ByteOrder.nativeOrder())
        fpgaStatus =
            parser.getIntValue(BluetoothBytesParser.FORMAT_UINT32, 11, ByteOrder.nativeOrder())
        btStatus =
            parser.getIntValue(BluetoothBytesParser.FORMAT_UINT32, 15, ByteOrder.nativeOrder())

        /*messagePart = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0)
        time = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT32, 1)
        battery = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 5)
        systemStatus = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT32, 7)
        fpgaStatus = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT32, 11)
        btStatus = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT32, 15)*/
    }

    override fun toString(): String {
        return "HeartRateMeasurement(messagePart=$messagePart, battery=$battery, time=$time, systemStatus=$systemStatus, fpgaStatus=$fpgaStatus, btStatus=$btStatus)"
    }
}