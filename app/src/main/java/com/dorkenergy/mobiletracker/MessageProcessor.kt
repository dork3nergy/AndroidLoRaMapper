package com.dorkenergy.mobiletracker

import android.util.Log

private val TAG: String = "debug_message"
private val IDPattern = Regex("[RT]\\d\\d")
private val GPSPattern1 = Regex("\\d\\d.\\d\\d\\d\\d\\d\\d")
private val GPSPattern2 = Regex("-\\d\\d.\\d\\d\\d\\d\\d\\d")


class loraMessage(newMessage:String){


    var mNewMessage = newMessage


    public fun validateMessage():Boolean{

        val value = mNewMessage.split(",").toTypedArray()
        val fields = value.size
        if(fields < 5){
            return false
        }
        var device :String = value[0]
        var destination : String = value[1]
        var lng : String = value[2].trim()
        var lat : String = value [3].trim()
        var message : String = value[4]

        Log.i(TAG,"${device},${destination},${lng},${lat},${message}")

        if(hasValidID(device)){
            if(hasValidGPS(lat)){
                if(hasValidGPS(lng)){
                    return true
                }
            }
        }
        return false
    }
    public fun hasValidDestination(dest:String):Boolean {
        val pattern = IDPattern
        if (pattern.matches(dest)) {
            return true
        }
        return false
    }

    private fun hasValidGPS(latlng : String):Boolean{

        val pattern1 = GPSPattern1
        val pattern2 = GPSPattern2
        if (pattern1.matches(latlng) || pattern2.matches(latlng)){
            return true
        }
        return false
    }

    private fun hasValidID(idTag :  String): Boolean {
        val pattern = IDPattern
        if (pattern.matches(idTag)) {
            return true
        }
        return false
    }



}