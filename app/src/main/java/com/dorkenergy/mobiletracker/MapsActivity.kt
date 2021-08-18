package com.dorkenergy.mobiletracker


import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.location.Location
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.collections.ArrayList


private val TAG: String = "debug_main"
private var currentDeviceName = ""
private var currentDeviceMac = ""
private var mBluetoothSocket: BluetoothSocket? = null
private lateinit var mBluetoothAdapter: BluetoothAdapter
private val uuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
private val jsonArrayList = ArrayList<JSONObject>()
private var connectionInProgress= false
private var connectionActive = false
private var MyActionbar: Menu? = null
private var ServerURL = "<Add Your Server URL Here>" // This is where gps data gets posted.

//List of trackers seen so far
var knownTrackers = ArrayList<Tracker>()
var following: Tracker? = null

// User GPS Stuff
private lateinit var userMarker: Marker
private lateinit var lastLocation: Location
private lateinit var fusedLocationClient: FusedLocationProviderClient
private lateinit var locationCallback: LocationCallback
private lateinit var locationRequest: LocationRequest
private var locationUpdateState = false
private var selectedMarker:Marker?=null
const val LOCATION_PERMISSION_REQUEST_CODE = 1
const val REQUEST_CHECK_SETTINGS = 2
const val RED = 0xFFFF0000.toInt()

private val USER_MARKER_ICON = R.drawable.car


class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap

    override fun onCreate(savedInstanceState: Bundle?) {
        // keep screen on while app is active
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
       super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.

        val mapFragment = supportFragmentManager
                .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Monitor BT Connection Status
        val btReceiver: BroadcastReceiver = broadCastReceiver

        val filter = IntentFilter()
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        filter.addAction((BluetoothAdapter.ACTION_STATE_CHANGED))
        registerReceiver(btReceiver, filter)

        // Phone Location Callback
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(p0: LocationResult) {
                super.onLocationResult(p0)

                lastLocation = p0.lastLocation
                updateUserMarker(LatLng(lastLocation.latitude, lastLocation.longitude))
            }
        }

        createLocationRequest()
        resetStatusAll()
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        mMap.setOnMarkerClickListener { marker ->
            if (marker.isInfoWindowShown) {
                marker.hideInfoWindow()
            } else {
                selectedMarker = marker;
                if (marker != userMarker) {
                    // Figure out which tracker this marker belongs to
                    following =  markerToTracker(marker)
                    showToast("Following Tracker : $following.trackerid")
                    // Toggle the marker label
                    // selectedTracker!!.toggleMarkerLabel()
                }
            }
            true
        }
        mMap.setOnPolylineClickListener { polyline ->

            // Cycle next polyline color when polyline is clicked
            val thisTracker = polylineToTracker(polyline)
            thisTracker!!.nextPolylineColor()

        }
        mMap.setOnInfoWindowClickListener { marker ->
            following = markerToTracker(marker)
        }

        // Style the map using the raw.mapstyle JSON file
        try {
            // Customise the styling of the base map using a JSON object defined
            // in a raw resource file.
            val success = googleMap.setMapStyle(
                MapStyleOptions.loadRawResourceStyle(
                    this, R.raw.mapstyle
                )
            );

            if (!success) {
                Log.e(TAG, "Style parsing failed.");
            }
        } catch (e: IOException) {
            Log.e(TAG, "Can't find style. Error: ", e);
        }

        // Fire up the local GPS icon
        getUserLocation()
    }


    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
            return
        }
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            null /* Looper */
        )
    }

    private fun createLocationRequest() {
        locationRequest = LocationRequest()
        locationRequest.interval = 10000
        locationRequest.fastestInterval = 5000
        locationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY

        val builder = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)

        // 4
        val client = LocationServices.getSettingsClient(this)
        val task = client.checkLocationSettings(builder.build())

        // 5
        task.addOnSuccessListener {
            locationUpdateState = true
            startLocationUpdates()
        }
        task.addOnFailureListener { e ->
            if (e is ResolvableApiException) {
                // Location settings are not satisfied, but this can be fixed
                // by showing the user a dialog.
                try {
                    // Show the dialog by calling startResolutionForResult(),
                    // and check the result in onActivityResult().
                    e.startResolutionForResult(
                        this@MapsActivity,
                        REQUEST_CHECK_SETTINGS
                    )
                } catch (sendEx: IntentSender.SendIntentException) {
                    // Ignore the error.
                }
            }
        }
    }

    private fun updateUserMarker(location: LatLng) {

        val usericon = BitmapDescriptorFactory.fromResource(USER_MARKER_ICON)
        val markerOptions = MarkerOptions().position(location)
            .title("You")
            .icon(usericon)
        userMarker.remove()
        userMarker = mMap.addMarker(markerOptions)
    }

    private fun getUserLocation()  {

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
        }
        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            // Got last known location. In some rare situations this can be null.
            val loc = LatLng(location!!.latitude, location!!.longitude);
            val usericon = BitmapDescriptorFactory.fromResource(USER_MARKER_ICON)


            if(location != null) {
                userMarker = mMap.addMarker(
                    MarkerOptions().position(loc).title("You").icon(usericon)
                )
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(loc, 18f))
            }

        }
    }


     private fun startConnection(){

        CoroutineScope(IO).launch {
            Log.i(TAG, "Launching connect coroutine")
            connectionInProgress=true

            setActionBarTitle(currentDeviceName)
            setActionBarSubtitle("Connecting...")
            val device: BluetoothDevice = mBluetoothAdapter.getRemoteDevice(currentDeviceMac)
            mBluetoothSocket = device.createInsecureRfcommSocketToServiceRecord(uuid)
            BluetoothAdapter.getDefaultAdapter().cancelDiscovery()
            try {
                mBluetoothSocket?.connect()
                Log.i(TAG, "Connection successful")
                connectionActive = true
                startGPSReceiver()
            } catch (e: IOException) {
                Log.i(TAG, "$e")
                showToast("Connection Failed")
                setActionBarSubtitle("Connection Failed")
                connectionActive = false
            }
            connectionInProgress=false

        }

    }

    private fun disconnectDevice(){
        mBluetoothSocket?.close()
        connectionActive=false
        setActionBarSubtitle("Disconnected")
        Log.i(TAG, "Disconnected")
        val item: MenuItem = MyActionbar!!.findItem(R.id.connect)
        item.setIcon(R.drawable.disconnect)


    }
    private suspend fun processGPSMessage(message: String){

        runOnUiThread(Runnable {
            Log.i(TAG, "Processing Message")

            val pair = message.split(",").toTypedArray()
            val trackerid = pair[0]
            val lat = pair[2].toDouble()
            val lng = pair[3].toDouble()
            var new = true
            var thistracker: Tracker
            val newpoint = LatLng(lat, lng)

            //Is this a new tracker?
            knownTrackers.forEach {
                if (it.trackerid == trackerid) {
                    thistracker = it
                    thistracker.addPolylinePoint(newpoint)
                    new = false
                }
            }
            if (new) {
                val context = this@MapsActivity
                val newtracker = Tracker(context, trackerid, mMap)
                newtracker.createPolyline()
                knownTrackers.add(newtracker)
                newtracker.addPolylinePoint(newpoint)
                following=newtracker
            }
            //Add new point to Tracker object
            createJson(message)

        } //public void run() {
        )

    }

    private fun startGPSReceiver() {

        CoroutineScope(IO).launch {
            Log.i(TAG, "LISTENING...")
            val delimiter = "%"
            val mmInStream: InputStream?
            var tmpIn: InputStream? = null
            tmpIn = mBluetoothSocket?.inputStream
            mmInStream = tmpIn

            val buffer = ByteArray(1)
            var bytes = 0
            var outstring = ""
            var receivedMessage = ""
            var isGPS = false
            while (connectionActive) {

                try {
                    bytes = mmInStream!!.read(buffer)
                    outstring += String(buffer)
                    if (String(buffer) == delimiter) {
                        receivedMessage = outstring.dropLast(1)
                        Log.i(TAG, "RECEIVED: $receivedMessage")
                        val mLoraMessage = loraMessage(receivedMessage)
                        if (mLoraMessage.validateMessage()) {
                            processGPSMessage(receivedMessage)
                        }
                        outstring = ""

                    }

                } catch (e: IOException) {
                    Log.i(TAG, "$e")
                }

            }
        }
    }

// JSON SECTION

    private fun createJson(gpscoords: String) {

        val data = gpscoords.split(",").toTypedArray()
        val jsondata = JSONObject()

        val sdf = SimpleDateFormat("yyyy.MM.dd HH:mm:ss")
        val currentDateandTime: String = sdf.format(Date())

        jsondata.put("ts", currentDateandTime)
        jsondata.put("id", data[0])
        jsondata.put("destination", data[1])
        jsondata.put("lat", data[2])
        jsondata.put("lng", data[3])
        jsondata.put("message", data[4])
        jsonArrayList.add(jsondata)
        processJSONArray()

    }
    private fun processJSONArray(){
        CoroutineScope(IO).launch{
            val purgeList = ArrayList<JSONObject>()
            if(isOnline()) {
                // Start uploading data to server

                var t = 0
                for(jsondata in jsonArrayList){
                    val success = uploadJSON(jsondata)
                    if(success) {
                        purgeList.add(jsondata)
                        Log.i(TAG, "Purge json data")
                    }
                }
                //Now Purge the original List
                for (jsondata in purgeList){
                    jsonArrayList.remove(jsondata)
                }
            }
        }
    }

    private fun uploadJSON(jsondata: JSONObject):Boolean{

        try {
            Log.i(TAG, "STARTING UPLOAD : $jsondata")
            val url = URL(ServerURL) //Enter URL here
            val httpURLConnection: HttpURLConnection = url.openConnection() as HttpURLConnection
            httpURLConnection.doOutput = true;
            httpURLConnection.requestMethod =
                "POST"; // here you are telling that it is a POST request
            httpURLConnection.setRequestProperty(
                "Content-Type",
                "application/json"
            ); // here you are setting the `Content-Type` for the data you are sending which is `application/json`
            httpURLConnection.connect();
            val os = DataOutputStream(httpURLConnection.outputStream)
            val outString = (jsondata.toString()).replace("\\r", "");

            os.writeBytes(outString)


            os.flush()
            os.close()

            val response = httpURLConnection.responseCode
            httpURLConnection.disconnect()
            Log.i(TAG, "UPLOAD RESPONSE = $response")
            if (response == 200){
                Log.i(TAG, "UPLOAD SUCCESSFUL")
            }
            return response == 200

        } catch (e: IOException) {
            Log.i(TAG, "IOEXCEPTION THROWN $e")
            return false
        }

    }
// UTILITY FUNCTIONS

    private fun markerToTracker(m: Marker) : Tracker? {

        knownTrackers.forEach(){
            if (it.currentmarker == m){
                return it
            }
        }
        return null
    }

    private fun polylineToTracker(p: Polyline): Tracker?{
        knownTrackers.forEach(){
            if (it.polyline == p) {
                return it
            }
        }
        return null
    }

    private fun  setActionBarTitle(text: String){
        GlobalScope.launch(Main) {
            val actionbar = this@MapsActivity.supportActionBar
            actionbar!!.title = text
        }
    }
    private fun  setActionBarSubtitle(text: String){
        GlobalScope.launch(Main) {
            val actionbar = this@MapsActivity.supportActionBar
            actionbar!!.subtitle = text

        }
    }
    private fun showToast(text: String){
        GlobalScope.launch(Main) {
            Toast.makeText(this@MapsActivity, text, Toast.LENGTH_SHORT).show()
        }
    }
    private fun resetStatusAll(){

        setActionBarTitle("LoRa Tracker")
        setActionBarSubtitle("")
        currentDeviceName = ""
        currentDeviceMac = ""

    }


    fun isOnline(): Boolean {
        val connectivityManager =
            this.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (connectivityManager != null) {
            val capabilities =
                connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
            if (capabilities != null) {
                if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                    //Log.i(TAG, "NetworkCapabilities.TRANSPORT_CELLULAR")
                    return true
                } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    //Log.i(TAG, "NetworkCapabilities.TRANSPORT_WIFI")
                    return true
                } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                    //Log.i(TAG, "NetworkCapabilities.TRANSPORT_ETHERNET")
                    return true
                }
            }
        }
        return false
    }



    private val broadCastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent!!.action;
            when (action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    setActionBarSubtitle("Connected")
                    val item: MenuItem = MyActionbar!!.findItem(R.id.connect)
                    item.setIcon(R.drawable.connect)
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    disconnectDevice()
                }
            }
            if (BluetoothAdapter.ACTION_STATE_CHANGED == intent.action) {
                val previousState =
                    intent.getIntExtra(
                        BluetoothAdapter.EXTRA_PREVIOUS_STATE, -1
                    )
                val state =
                    intent.getIntExtra(
                        BluetoothAdapter.EXTRA_STATE, -1
                    )
                if (previousState == BluetoothAdapter.STATE_OFF || state == BluetoothAdapter.STATE_OFF) {
                    if (state == BluetoothAdapter.STATE_OFF || state == BluetoothAdapter.STATE_TURNING_OFF) {
                        showToast("Bluetooth WAS Turned OFF")
                        disconnectDevice()
                    }
                }

            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        MyActionbar = menu
        menuInflater.inflate(R.menu.actionbar_menu, menu)
        val actionbar = this@MapsActivity.supportActionBar
        actionbar!!.setBackgroundDrawable(ColorDrawable(Color.parseColor("#303030")));


        return true
    }

    fun editMarkerDialog() {

        val builder = AlertDialog.Builder(this)
        // Get the layout inflater
        val inflater = this.layoutInflater;

        // Inflate and set the layout for the dialog
        // Pass null as the parent view because its going in the dialog layout
        builder.setView(inflater.inflate(R.layout.follow_marker, null))
            // Add action buttons
            .setPositiveButton("OK",
                DialogInterface.OnClickListener { dialog, id ->
                    // sign in the user ...
                })
            .setNegativeButton("Cancel",
                DialogInterface.OnClickListener { dialog, id ->
                    dialog.dismiss()
                })
        builder.create()
        builder.show()

    }

    override fun onDestroy() {
        super.onDestroy()
        disconnectDevice()
        unregisterReceiver(broadCastReceiver)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.connect -> {
                if (!connectionActive) {
                    val intent = Intent(this, DeviceSelect::class.java)
                    startActivityForResult(intent, 0)
                    return true
                } else {
                    disconnectDevice()
                    return true
                }
            }
            R.id.trackerpref -> {
                editMarkerDialog()
                return true
            }
//            R.id.cancel -> {
//                return true
//            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == 0) {
            if (resultCode == Activity.RESULT_OK) { //BluetoothSelect returned normally
                val returnString = data!!.getStringExtra("message")

                val pair = returnString!!.split(",").toTypedArray()
                currentDeviceName = pair[0]
                currentDeviceMac = pair[1]
                // Start coroutine to connect to selected device
                Log.i(TAG, "Attempt to connect: $currentDeviceName")
                startConnection()
            } else {
                super.onActivityResult(requestCode, resultCode, data)
            }
        }

        if (requestCode == REQUEST_CHECK_SETTINGS) {
            if (resultCode == Activity.RESULT_OK) {
                locationUpdateState = true
                startLocationUpdates()
            }
        }
    }
}

