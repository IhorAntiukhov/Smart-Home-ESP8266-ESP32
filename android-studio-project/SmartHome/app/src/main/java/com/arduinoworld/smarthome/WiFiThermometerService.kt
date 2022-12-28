package com.arduinoworld.smarthome

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.arduinoworld.smarthome.MainActivity.Companion.editPreferences
import com.arduinoworld.smarthome.MainActivity.Companion.firebaseAuth
import com.arduinoworld.smarthome.MainActivity.Companion.realtimeDatabase
import com.arduinoworld.smarthome.MainActivity.Companion.sharedPreferences
import com.github.mikephil.charting.data.Entry
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Calendar

class WiFiThermometerService : Service() {
    private lateinit var gson: Gson
    private var temperatureEntriesArrayList = ArrayList<Entry>()
    private var humidityEntriesArrayList = ArrayList<Entry>()
    private var timestampsArrayList = ArrayList<String>()
    private var readingsCount = 0
    private var temperature = 0
    private var humidity = 0
    private var batteryLevel = 0

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        gson = Gson()

        if (sharedPreferences.getString("TemperatureEntriesArrayList", "") != "") {
            temperatureEntriesArrayList = gson.fromJson(
                sharedPreferences.getString("TemperatureEntriesArrayList", ""),
                object : TypeToken<ArrayList<Entry?>?>() {}.type)
            humidityEntriesArrayList = gson.fromJson(
                sharedPreferences.getString("HumidityEntriesArrayList", ""),
                object : TypeToken<ArrayList<Entry?>?>() {}.type)
            timestampsArrayList = gson.fromJson(
                sharedPreferences.getString("WiFiThermometerTimestampsArrayList", ""),
                object : TypeToken<ArrayList<String?>?>() {}.type)
        }
        
        realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("WiFiThermometer").addChildEventListener(childValueEventListener)

        return START_STICKY
    }

    private val childValueEventListener = object: ChildEventListener {
        override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
            showNotification(snapshot)
        }

        override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
            showNotification(snapshot)
        }

        override fun onChildRemoved(snapshot: DataSnapshot) {}
        override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
        override fun onCancelled(error: DatabaseError) {}

    }

    private fun showNotification(snapshot: DataSnapshot) {
        if (snapshot.key!! == "temperatureHumidity") {
            if (snapshot.getValue(String::class.java) == null) {
                onDestroy()
            } else {
                val temperatureHumidity = snapshot.getValue(String::class.java)!!
                temperature = temperatureHumidity.substring(0, temperatureHumidity.indexOf(" ")).toInt()
                humidity = temperatureHumidity.substring(temperatureHumidity.indexOf(" ") + 1, temperatureHumidity.length).toInt()
                readingsCount += 1
                if (readingsCount >= sharedPreferences.getString("WiFiThermometerGraphInterval", "1")!!.toInt()) {
                    readingsCount = 0
                    temperatureEntriesArrayList.add(Entry((temperatureEntriesArrayList.size).toFloat(), temperatureHumidity.substring(0, temperatureHumidity.indexOf(" ")).toFloat()))
                    humidityEntriesArrayList.add(Entry((humidityEntriesArrayList.size).toFloat(), temperatureHumidity.substring(temperatureHumidity.indexOf(" ") + 1, temperatureHumidity.length).toFloat()))
                    val calendar = Calendar.getInstance()
                    timestampsArrayList.add(String.format("%02d:%02d", calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE)))
                    editPreferences.putString("TemperatureEntriesArrayList", gson.toJson(temperatureEntriesArrayList))
                    editPreferences.putString("HumidityEntriesArrayList", gson.toJson(humidityEntriesArrayList))
                    editPreferences.putString("WiFiThermometerTimestampsArrayList", gson.toJson(timestampsArrayList)).apply()
                }
            }
        }
        val notificationText = if (sharedPreferences.getBoolean("ShowBatteryLevelInNotification", true)) {
            if (snapshot.key!! == "batteryLevel") {
                batteryLevel = snapshot.getValue(Int::class.java)!!
            }
            getString(R.string.wifi_thermometer_notification_text, temperature, humidity, batteryLevel)
        } else {
            getString(R.string.wifi_thermometer_notification_without_level_text, temperature, humidity)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val activityMainIntent = Intent(applicationContext, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(applicationContext, 7824, activityMainIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

            startForeground(7824, NotificationCompat.Builder(applicationContext, "WiFiThermometerNotification")
                .setOngoing(true)
                .setSmallIcon(R.drawable.ic_wifi_thermometer)
                .setContentTitle(getString(R.string.text_wifi_thermometer))
                .setContentText(notificationText)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent).build()
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("WiFiThermometer").removeEventListener(childValueEventListener)
    }

    override fun onBind(intent: Intent): IBinder {
        TODO("Return the communication channel to the service.")
    }
}