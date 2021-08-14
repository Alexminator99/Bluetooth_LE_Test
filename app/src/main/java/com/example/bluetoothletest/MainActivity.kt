package com.example.bluetoothletest

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private var measurementValue: TextView? = null
    private var buttonEnableIndications: Button? = null
    private var enableIndications = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        measurementValue = findViewById(R.id.textViewHeartbeatValue)
        buttonEnableIndications = findViewById(R.id.buttonEnableIndications);

        registerReceiver(
            heartRateDataReceiver,
            IntentFilter(BluetoothHandler.MEASUREMENT_HEARTRATE)
        )

        buttonEnableIndications?.setOnClickListener {

            if (enableIndications) {
                val state = BluetoothHandler.getInstance(this).enableIndications(true)

                if (state) {
                    buttonEnableIndications?.text = "Disable Notifications"
                    enableIndications = false
                } else buttonEnableIndications?.text = "Enable Notifications"
            } else {
                val state = BluetoothHandler.getInstance(this).enableIndications(false)

                if (state) {
                    buttonEnableIndications?.text = "Enable Notifications"
                    enableIndications = true
                } else buttonEnableIndications?.text = "Disable Notifications"
            }


        }
    }

    private val heartRateDataReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val measurement =
                intent.getSerializableExtra(BluetoothHandler.MEASUREMENT_HEARTRATE_EXTRA) as HeartRateMeasurement?
                    ?: return
            measurementValue?.text = measurement.toString()
        }
    }
}