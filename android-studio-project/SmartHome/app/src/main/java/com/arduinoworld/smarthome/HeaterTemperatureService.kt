package com.arduinoworld.smarthome

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.arduinoworld.smarthome.MainActivity.Companion.firebaseAuth
import com.arduinoworld.smarthome.MainActivity.Companion.realtimeDatabase
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener

class HeaterTemperatureService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("Heater").child("temperature")
            .addValueEventListener(temperatureValueEventListener)

        return START_STICKY
    }

    private val temperatureValueEventListener = object: ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            if (snapshot.getValue(Int::class.java) == null) {
                onDestroy()
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val activityMainIntent = Intent(applicationContext, MainActivity::class.java)
                    val pendingIntent = PendingIntent.getActivity(applicationContext, 2910, activityMainIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

                    startForeground(2910, NotificationCompat.Builder(applicationContext, "HeaterTemperatureNotification")
                        .setOngoing(true)
                        .setSmallIcon(R.drawable.ic_smart_heater)
                        .setContentTitle(getString(R.string.text_smart_heater))
                        .setContentText(getString(R.string.heater_temperature_notification_text,
                            snapshot.getValue(Int::class.java)!!))
                        .setCategory(NotificationCompat.CATEGORY_SERVICE)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setContentIntent(pendingIntent).build()
                    )
                }
            }
        }

        override fun onCancelled(error: DatabaseError) {}

    }

    override fun onDestroy() {
        super.onDestroy()

        realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("Heater").child("temperature")
            .removeEventListener(temperatureValueEventListener)
    }

    override fun onBind(intent: Intent): IBinder {
        TODO("Return the communication channel to the service.")
    }
}