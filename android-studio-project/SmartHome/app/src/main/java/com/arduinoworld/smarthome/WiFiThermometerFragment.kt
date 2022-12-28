package com.arduinoworld.smarthome

import android.animation.Animator
import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.*
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.arduinoworld.smarthome.MainActivity.Companion.editPreferences
import com.arduinoworld.smarthome.MainActivity.Companion.firebaseAuth
import com.arduinoworld.smarthome.MainActivity.Companion.realtimeDatabase
import com.arduinoworld.smarthome.MainActivity.Companion.sharedPreferences
import com.arduinoworld.smarthome.databinding.FragmentWifiThermometerBinding
import com.github.mikephil.charting.components.AxisBase
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.LegendEntry
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken


class WiFiThermometerFragment : Fragment() {
    private lateinit var binding: FragmentWifiThermometerBinding

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        with(binding) {
            if (isServiceRunning(WiFiThermometerService::class.java)) {
                buttonShowNotification.visibility = View.GONE
                buttonHideNotification.visibility = View.VISIBLE
                buttonHideNotification.alpha = 1f
            }

            progressBarTemperature.setProgressColor(Color.RED)
            progressBarTemperature.setProgressWidth(40f)
            progressBarTemperature.setRounded(true)
            progressBarTemperature.setProgressBackgroundColor(Color.parseColor("#00FFFFFF"))
            progressBarTemperature.setMaxProgress(60f)

            progressBarHumidity.setProgressColor(Color.parseColor("#0071FA"))
            progressBarHumidity.setProgressWidth(40f)
            progressBarHumidity.setRounded(true)
            progressBarHumidity.setProgressBackgroundColor(Color.TRANSPARENT)
            progressBarHumidity.setMaxProgress(100f)

            with(graph) {
                setBackgroundColor(Color.TRANSPARENT)
                setNoDataText(getString(R.string.wifi_thermometer_graph_no_data_text))
                setNoDataTextColor(Color.parseColor("#6A61AD"))
                extraTopOffset = 10F
                setTouchEnabled(true)
                isDragEnabled = true
                isScaleXEnabled = true
                isScaleYEnabled = false
                axisRight.isEnabled = false
                description.isEnabled = false
            }

            with(graph.xAxis) {
                isEnabled = true
                setDrawAxisLine(true)
                setDrawGridLines(true)
                isGranularityEnabled = true
                position = XAxis.XAxisPosition.TOP
                gridColor = Color.parseColor("#5347AE")
                textColor = Color.parseColor("#5347AE")
                gridLineWidth = 1.5F
                granularity = 1f
                textSize = 12F
            }

            with(graph.axisLeft) {
                isEnabled = true
                setDrawAxisLine(false)
                setDrawGridLines(true)
                gridColor = Color.parseColor("#5347AE")
                textColor = Color.parseColor("#5347AE")
                gridLineWidth = 1.5F
                textSize = 12F
            }

            graph.axisLeft.axisMinimum = -10f
            graph.axisLeft.axisMaximum = 90f

            if (sharedPreferences.getString("TemperatureEntriesArrayList", "") != "") {
                val gson = Gson()
                val temperatureEntriesArrayList: ArrayList<Entry> = gson.fromJson(
                    sharedPreferences.getString("TemperatureEntriesArrayList", ""),
                    object : TypeToken<ArrayList<Entry?>?>() {}.type)
                val humidityEntriesArrayList: ArrayList<Entry> = gson.fromJson(
                    sharedPreferences.getString("HumidityEntriesArrayList", ""),
                    object : TypeToken<ArrayList<Entry?>?>() {}.type)
                val timestampsArrayList: ArrayList<String> = gson.fromJson(
                    sharedPreferences.getString("WiFiThermometerTimestampsArrayList", ""),
                    object : TypeToken<ArrayList<String?>?>() {}.type)

                val temperatureLegend = LegendEntry(getString(R.string.temperature_legend_label), Legend.LegendForm.CIRCLE, 10f, 2f, null, Color.RED)
                val humidityLegend = LegendEntry(getString(R.string.humidity_legend_label), Legend.LegendForm.CIRCLE, 10f, 2f, null, Color.parseColor("#0071FA"))
                with(graph.legend) {
                    isEnabled = true
                    textSize = 14F
                    xEntrySpace = 5F
                    yEntrySpace = 5F
                    form = Legend.LegendForm.CIRCLE
                    horizontalAlignment = Legend.LegendHorizontalAlignment.LEFT
                    setCustom(arrayOf(temperatureLegend, humidityLegend))
                }

                val temperatureDataSet = LineDataSet(temperatureEntriesArrayList, getString(R.string.temperature_legend_label))
                with(temperatureDataSet) {
                    lineWidth = 1.5F
                    circleRadius = 3F
                    valueTextSize = 12F
                    cubicIntensity = 1F
                    color = Color.RED
                    setCircleColor(Color.RED)
                    valueTextColor = Color.RED
                    setDrawCircleHole(false)
                }

                val humidityDataSet = LineDataSet(humidityEntriesArrayList, getString(R.string.humidity_legend_label))
                with(humidityDataSet) {
                    lineWidth = 1.5F
                    circleRadius = 3F
                    valueTextSize = 12F
                    cubicIntensity = 1F
                    color = Color.parseColor("#0071FA")
                    setCircleColor(Color.parseColor("#0071FA"))
                    valueTextColor = Color.parseColor("#0071FA")
                    setDrawCircleHole(false)
                }

                val dataSets = ArrayList<LineDataSet>()
                dataSets.add(temperatureDataSet)
                dataSets.add(humidityDataSet)
                graph.data = LineData(dataSets as List<ILineDataSet>?)

                graph.xAxis.valueFormatter = object: ValueFormatter() {
                    override fun getAxisLabel(value: Float, axis: AxisBase?): String {
                        return timestampsArrayList.getOrNull(value.toInt()) ?: value.toString()
                    }
                }

                graph.invalidate()
            }

            buttonShowNotification.setOnClickListener {
                vibrate()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val notificationManager = requireActivity().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    if (!sharedPreferences.getBoolean("isNotificationGroupCreated", false)) {
                        val notificationGroup = NotificationChannelGroup("SmartHome", "Умный Дом")
                        notificationManager.createNotificationChannelGroup(notificationGroup)
                        editPreferences.putBoolean("isNotificationGroupCreated", true)
                    }
                    if (!sharedPreferences.getBoolean("isWiFiThermometerNotificationChannelCreated", false)) {
                        val notificationChannel = NotificationChannel("WiFiThermometerNotification", "Уведомление WiFi термометра", NotificationManager.IMPORTANCE_HIGH)
                        notificationChannel.description = "Уведомление с температурой и влажностью, которое отображается постоянно"
                        notificationChannel.enableLights(false)
                        notificationChannel.enableVibration(false)
                        notificationChannel.setSound(null, null)
                        notificationChannel.group = "SmartHome"
                        notificationChannel.lockscreenVisibility = View.VISIBLE
                        notificationManager.createNotificationChannel(notificationChannel)
                        editPreferences.putBoolean("isWiFiThermometerNotificationChannelCreated", true).apply()
                    }
                    val service = Intent(requireActivity(), WiFiThermometerService::class.java)
                    requireActivity().startService(service)

                    var isAnimationStarted = true
                    buttonShowNotification.animate().alpha(0f).setDuration(500).setStartDelay(0)
                        .setListener(object: Animator.AnimatorListener {
                            override fun onAnimationStart(animation: Animator) {}

                            override fun onAnimationEnd(animation: Animator) {
                                if (isAnimationStarted) {
                                    isAnimationStarted = false

                                    buttonShowNotification.visibility = View.GONE
                                    buttonHideNotification.visibility = View.VISIBLE

                                    buttonHideNotification.animate().alpha(1f).setDuration(500).setStartDelay(0).start()
                                }
                            }

                            override fun onAnimationCancel(animation: Animator) {}
                            override fun onAnimationRepeat(animation: Animator) {}

                        }).start()
                } else {
                    Toast.makeText(requireActivity(), "Ваша версия Android меньше 8.0!", Toast.LENGTH_LONG).show()
                }
            }

            buttonHideNotification.setOnClickListener {
                vibrate()
                val service = Intent(requireActivity(), WiFiThermometerService::class.java)
                requireActivity().stopService(service)

                var isAnimationStarted = true
                buttonHideNotification.animate().alpha(0f).setDuration(500).setStartDelay(0)
                    .setListener(object: Animator.AnimatorListener {
                        override fun onAnimationStart(animation: Animator) {}

                        override fun onAnimationEnd(animation: Animator) {
                            if (isAnimationStarted) {
                                isAnimationStarted = false

                                buttonHideNotification.visibility = View.GONE
                                buttonShowNotification.visibility = View.VISIBLE

                                buttonShowNotification.animate().alpha(1f).setDuration(500).setStartDelay(0).start()
                            }
                        }

                        override fun onAnimationCancel(animation: Animator) {}
                        override fun onAnimationRepeat(animation: Animator) {}

                    }).start()
            }
        }
    }

    private val childValueEventListener = object: ChildEventListener {
        override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
            updateReadingsAndBatteryLevel(snapshot, true)
        }

        override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
            updateReadingsAndBatteryLevel(snapshot, false)
        }

        override fun onChildRemoved(snapshot: DataSnapshot) {}
        override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
        override fun onCancelled(error: DatabaseError) {}

    }

    private fun updateReadingsAndBatteryLevel(snapshot: DataSnapshot, delayEnabled: Boolean) {
        if (snapshot.key!! == "temperatureHumidity") {
            if (snapshot.getValue(String::class.java) != null) {
                val temperatureHumidity = snapshot.getValue(String::class.java)!!
                binding.textTemperature.text = getString(R.string.temperature_text, temperatureHumidity.substring(0, temperatureHumidity.indexOf(" ")).toInt())
                binding.textHumidity.text = getString(R.string.humidity_text, temperatureHumidity.substring(temperatureHumidity.indexOf(" ") + 1, temperatureHumidity.length).toInt())
                if (delayEnabled) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (temperatureHumidity.substring(temperatureHumidity.indexOf(" ") + 1, temperatureHumidity.length).toInt() > 0) {
                            binding.progressBarTemperature.setProgress(temperatureHumidity.substring(0, temperatureHumidity.indexOf(" ")).toFloat() + 15f)
                        }
                        binding.progressBarHumidity.setProgress(temperatureHumidity.substring(temperatureHumidity.indexOf(" ") + 1, temperatureHumidity.length).toFloat())
                    }, 600)
                } else {
                    if (temperatureHumidity.substring(temperatureHumidity.indexOf(" ") + 1, temperatureHumidity.length).toInt() > 0) {
                        binding.progressBarTemperature.setProgress(temperatureHumidity.substring(0, temperatureHumidity.indexOf(" ")).toFloat() + 15f)
                    }
                    binding.progressBarHumidity.setProgress(temperatureHumidity.substring(temperatureHumidity.indexOf(" ") + 1, temperatureHumidity.length).toFloat())
                }
            }
        }
        if (snapshot.key!! == "batteryLevel") {
            if (snapshot.getValue(Int::class.java) != null) {
                binding.textBatteryLevel.text = getString(R.string.humidity_text, snapshot.getValue(Int::class.java)!!)
            }
        }
    }

    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = requireActivity().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager?
        @Suppress("DEPRECATION")
        for (service in manager!!.getRunningServices(Int.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }

    override fun onStart() {
        super.onStart()

        realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("WiFiThermometer")
            .addChildEventListener(childValueEventListener)
    }

    override fun onStop() {
        super.onStop()

        realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("WiFiThermometer")
            .removeEventListener(childValueEventListener)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentWifiThermometerBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    @Suppress("DEPRECATION")
    private fun vibrate() {
        if (MainActivity.vibrator.hasVibrator()) {
            if (MainActivity.isHapticFeedbackEnabled == "1") {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    binding.buttonShowNotification.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING)
                } else {
                    binding.buttonShowNotification.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING + HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
                }
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    MainActivity.vibrator.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    MainActivity.vibrator.vibrate(20)
                }
            }
        }
    }
}