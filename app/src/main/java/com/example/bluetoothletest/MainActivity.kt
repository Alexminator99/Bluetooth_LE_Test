package com.example.bluetoothletest

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import timber.log.Timber
import java.util.*


private const val REQUEST_ENABLE_BT = 1
private const val ACCESS_LOCATION_REQUEST = 2

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

    override fun onResume() {
        super.onResume()
        if (BluetoothAdapter.getDefaultAdapter() != null) {
            if (!isBluetoothEnabled()) {
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
            } else {
                checkPermissions()
            }
        } else {
            Timber.e("This device has no Bluetooth hardware")
        }
    }

    private fun isBluetoothEnabled(): Boolean {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter() ?: return false
        return bluetoothAdapter.isEnabled
    }

    private fun initBluetoothHandler() {
        BluetoothHandler.getInstance(this@MainActivity)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(heartRateDataReceiver)
    }

    private fun checkPermissions() {
        val missingPermissions = getMissingPermissions(getRequiredPermissions())
        if (missingPermissions.isNotEmpty()) {
            requestPermissions(missingPermissions, ACCESS_LOCATION_REQUEST)
        } else {
            permissionsGranted()
        }
    }

    private fun getMissingPermissions(requiredPermissions: Array<String>): Array<String> {
        val missingPermissions: MutableList<String> = ArrayList()
        for (requiredPermission in requiredPermissions) {
            if (applicationContext.checkSelfPermission(requiredPermission) != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(requiredPermission)
            }
        }
        return missingPermissions.toTypedArray()
    }

    private fun getRequiredPermissions(): Array<String> {
        val targetSdkVersion = applicationInfo.targetSdkVersion
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && targetSdkVersion >= Build.VERSION_CODES.Q) arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION
        ) else arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    private fun permissionsGranted() {
        // Check if Location services are on because they are required to make scanning work
        if (checkLocationServices()) {
            initBluetoothHandler()
        }
    }

    private fun areLocationServicesEnabled(): Boolean {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager?
        if (locationManager == null) {
            Timber.e("could not get location manager")
            return false
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            locationManager.isLocationEnabled
        } else {
            val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            val isNetworkEnabled =
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            isGpsEnabled || isNetworkEnabled
        }
    }

    private fun checkLocationServices(): Boolean {
        return if (!areLocationServicesEnabled()) {
            AlertDialog.Builder(this@MainActivity)
                .setTitle("Location services are not enabled")
                .setMessage("Scanning for Bluetooth peripherals requires locations services to be enabled.") // Want to enable?
                .setPositiveButton("Enable") { dialogInterface, _ ->
                    dialogInterface.cancel()
                    startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }
                .setNegativeButton(
                    "Cancel"
                ) { dialog, _ -> // if this button is clicked, just close
                    // the dialog box and do nothing
                    dialog.cancel()
                }
                .create()
                .show()
            false
        } else {
            true
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String?>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        // Check if all permission were granted
        var allGranted = true
        for (result in grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                allGranted = false
                break
            }
        }
        if (allGranted) {
            permissionsGranted()
        } else {
            AlertDialog.Builder(this@MainActivity)
                .setTitle("Location permission is required for scanning Bluetooth peripherals")
                .setMessage("Please grant permissions")
                .setPositiveButton("Retry") { dialogInterface, _ ->
                    dialogInterface.cancel()
                    checkPermissions()
                }
                .create()
                .show()
        }
    }
}