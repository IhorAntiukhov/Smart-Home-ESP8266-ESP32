package com.arduinoworld.smarthome

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL


@SuppressLint("MissingFirebaseInstanceTokenRefresh")
class MessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)

        if (sharedPreferences.getBoolean("isHeaterNotificationsEnabled", true) && message.data.containsKey("heaterStarted")) {
            val contentText = if (message.data["heaterStarted"]!! == "1") {
                getString(R.string.heater_started_text)
            } else {
                getString(R.string.heater_stopped_text)
            }
            showNotification("", R.drawable.ic_smart_heater, getString(R.string.text_smart_heater), contentText)
        } else if (message.data.containsKey("photoUrl")) {
            showNotification(message.data["photoUrl"]!!, R.drawable.ic_smart_doorbell, getString(R.string.text_smart_doorbell), getString(R.string.smart_doorbell_notification_text))
        }
    }

    private fun showNotification(photoUrl: String, icon: Int, contentTitle: String, contentText: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val activityMainIntent = Intent(applicationContext, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(applicationContext, (1000..9999).random(), activityMainIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

            val builder: NotificationCompat.Builder = NotificationCompat.Builder(applicationContext, "SmartDoorbellNotification")
            builder.setSmallIcon(icon)
            builder.setContentTitle(contentTitle)
            builder.setContentText(contentText)
            if (photoUrl != "") {
                val bitmap = getBitmapFromURL(photoUrl)
                builder.setLargeIcon(bitmap)
                builder.setStyle(NotificationCompat.BigPictureStyle()
                    .bigPicture(bitmap)
                    .bigLargeIcon(null)
                    .setSummaryText(contentText))
            }
            builder.setDefaults(Notification.DEFAULT_ALL)
            builder.priority = NotificationCompat.PRIORITY_MAX
            builder.setContentIntent(pendingIntent)

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify((1000..9999).random(), builder.build())
        }
    }

    private fun getBitmapFromURL(src: String?): Bitmap? {
        return try {
            val url = URL(src)
            val connection: HttpURLConnection = url.openConnection() as HttpURLConnection
            connection.doInput = true
            connection.connect()
            val input: InputStream = connection.inputStream
            BitmapFactory.decodeStream(input)
        } catch (e: IOException) {
            null
        }
    }
}