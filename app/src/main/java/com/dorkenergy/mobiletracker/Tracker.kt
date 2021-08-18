package com.dorkenergy.mobiletracker


import android.R.id
import android.content.Context
import android.content.res.TypedArray
import android.graphics.Bitmap
import android.util.Log
import android.widget.Toast
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.*
import com.google.maps.android.ui.IconGenerator
import java.util.Calendar.getInstance


class Tracker(val _context: Context, val _trackerid: String, val _mMap: GoogleMap) {


    private var TAG = "debug_tracker"

    val context = _context
    val trackerid = _trackerid
    val mMap = _mMap
    var polyline : Polyline? = null
    var title: String = trackerid
    var pointlist: MutableList<LatLng> = ArrayList()
    var currentmarker: Marker? = null
    var markericon: Int = 2
    var visible : Boolean = true
    var createtrail : Boolean = true
    var trailcolor : Int = 0 // This is an index to an array in array.xml
    var showlabel : Boolean = false
    // Load the res/colors.xml rainbow array into colorsArray
    private val colorsArray = context.resources.getIntArray(R.array.rainbow)

    // Load the res/arrays.xml markericons array into iconArray
    private var iconArray: TypedArray = context.getResources().obtainTypedArray(R.array.markericons)

    private val POLYLINE_STROKE_WIDTH_PX = 6



    fun createPolyline() {
        val t = colorsArray[trailcolor]
        var newpolyline = mMap.addPolyline(
            PolylineOptions()
                .clickable(true)
        )
        newpolyline.endCap = RoundCap()
        newpolyline.width = POLYLINE_STROKE_WIDTH_PX.toFloat()
        newpolyline.jointType = JointType.ROUND
        newpolyline.color = t

        polyline = newpolyline
    }


    fun addPolylinePoint(newpoints: LatLng) {
        pointlist.add(newpoints)
        Log.i(TAG, "Polyline Points ${polyline!!.points}")
        updatePolyline()
    }

    fun nextPolylineColor() {
        val t = colorsArray.size
        if (trailcolor < t-1) {
            trailcolor++
        } else {
            trailcolor = 0
        }
        polyline!!.color=colorsArray[trailcolor]
    }

    private fun updatePolyline(){

        Log.i(TAG, "Showlabel = $showlabel")

        if (currentmarker != null) {
            currentmarker!!.remove()
        }
        polyline!!.points = pointlist
        Log.i(TAG, "Trailcolor = $trailcolor")
        polyline!!.color = colorsArray[trailcolor]

        val current = pointlist.last()

        // This get the resource ID for a marker icon defined in res/arrays markericons
        // The markericons array references items in res/drawable
        // You can then use that to change an marker icon on the map
        var resourceId = iconArray.getResourceId(markericon, -1)
        // Like here ...
        val trackericon = makeIcon()

        currentmarker = mMap.addMarker(
            MarkerOptions().position(current).title(trackerid).icon(trackericon)
        )
        if (showlabel) {
            currentmarker!!.showInfoWindow();
        }
        if(following != null) {
            if (following!!.trackerid == this.trackerid) {
                mMap.moveCamera(CameraUpdateFactory.newLatLng(current))
            }
        }
        //mMap.moveCamera(CameraUpdateFactory.zoomTo(18.0f))
    }
    fun Context.toast(message: CharSequence) =
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    fun toggleMarkerLabel(){
        if (!showlabel) {
            showlabel=true
            currentmarker!!.showInfoWindow()
        } else {
            showlabel=false
            currentmarker!!.hideInfoWindow()
        }
    }

    fun makeIcon():BitmapDescriptor{
        val icongen = IconGenerator(context)
        icongen.setStyle(IconGenerator.STYLE_GREEN)
        icongen.setContentPadding(1,1,1,1)
        val bitmap: Bitmap = icongen.makeIcon(trackerid)
        val icon = BitmapDescriptorFactory.fromBitmap(bitmap)
        return icon

    }

}