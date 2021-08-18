package com.dorkenergy.mobiletracker

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.os.Bundle
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import java.util.*


private var returnstring = ""
private const val TAG = "debug_deviceselect"
private val REQUEST_ENABLE_BLUETOOTH = 1
private const val ENABLE_REQUEST = 0
private var mBluetoothAdapter: BluetoothAdapter? = null


class DeviceSelect : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_select)
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        if(!mBluetoothAdapter!!.isEnabled) {
            val enableBluetoothIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBluetoothIntent, REQUEST_ENABLE_BLUETOOTH)
        }
        populateListview()
    }

    private fun populateListview() {
        var pairedname = ArrayList<String>()
        var pairedmac = ArrayList<String>()
        val arrayAdapter: ArrayAdapter<*>



        val pairedDevices: Set<BluetoothDevice>? = mBluetoothAdapter?.bondedDevices

        pairedDevices?.forEach { device ->

            pairedname.add("${device.name}")
            pairedmac.add("${device.address}")
        }

        val adapter = ArrayAdapter(this,
            R.layout.device_select_item, pairedname) // Format this list using activity_list_item.xml



        val listView: ListView = findViewById(R.id.device_listview)
        listView.adapter = adapter

        listView.onItemClickListener = AdapterView.OnItemClickListener { adapterView, view, position, id ->
            returnstring = "${pairedname[position]},${pairedmac[position]}" as String
            sendDataBackToPreviousActivity()
            finish()
        }
    }


    // Send back string (MAC address) to MapsActivity
    private fun sendDataBackToPreviousActivity() {
        val intent = Intent().apply {
            putExtra("message", returnstring)
            // Put your data here if you want.
        }
        setResult(Activity.RESULT_OK, intent)
    }

    // Deal with what happens if the users presses deny turing bluetooth ON

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_CANCELED) {
            returnstring = "BLUETOOTH_DENIED" as String
            sendDataBackToPreviousActivity()
            finish()
        }
        if (resultCode == RESULT_OK) {
            populateListview()
        }
        }
}