package com.arduinoworld.smarthome

import android.animation.Animator
import android.annotation.SuppressLint
import android.os.*
import android.text.format.DateFormat.is24HourFormat
import android.view.HapticFeedbackConstants
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import com.arduinoworld.smarthome.databinding.FragmentBoilerTimeBinding
import com.google.android.material.timepicker.TimeFormat
import com.google.gson.reflect.TypeToken
import com.arduinoworld.smarthome.MainActivity.Companion.editPreferences
import com.arduinoworld.smarthome.MainActivity.Companion.firebaseAuth
import com.arduinoworld.smarthome.MainActivity.Companion.isHapticFeedbackEnabled
import com.arduinoworld.smarthome.MainActivity.Companion.isNetworkConnected
import com.arduinoworld.smarthome.MainActivity.Companion.realtimeDatabase
import com.arduinoworld.smarthome.MainActivity.Companion.sharedPreferences
import com.arduinoworld.smarthome.MainActivity.Companion.vibrator
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.gson.Gson
import java.util.*
import kotlin.collections.ArrayList

class BoilerTimeFragment : Fragment() {
    private lateinit var binding: FragmentBoilerTimeBinding
    private lateinit var recyclerAdapter: TimeRecyclerAdapter
    private lateinit var gson: Gson
    private lateinit var responseValueEventListener: ValueEventListener
    private var timestampsArrayList = ArrayList<HeatingOrBoilerTimestamp>()
    private var isTimeModeStarted = false
    private var clockFormat = 0
    private var response = '0'

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        with(binding) {
            val maxHeatingElements = sharedPreferences.getString("MaxHeatingElements", "2")!!.toInt()
            val isOverCurrentProtectionEnabled = sharedPreferences.getBoolean("isOverCurrentProtectionEnabled", true)
            isTimeModeStarted = sharedPreferences.getBoolean("isBoilerTimeModeStarted", false)

            val isSystem24Hour = is24HourFormat(requireActivity())
            clockFormat = if (isSystem24Hour) TimeFormat.CLOCK_24H else TimeFormat.CLOCK_12H

            gson = Gson()
            if (isTimeModeStarted) {
                layoutTime.visibility = View.GONE
                buttonStartTimeMode.visibility = View.GONE
                buttonStopTimeMode.visibility = View.VISIBLE
                buttonStopTimeMode.alpha = 1f
            }
            if (sharedPreferences.getString("BoilerTimestampsArrayList", "") != "")
                timestampsArrayList = gson.fromJson(sharedPreferences.getString("BoilerTimestampsArrayList", ""), object : TypeToken<ArrayList<HeatingOrBoilerTimestamp?>?>() {}.type)

            recyclerAdapter = TimeRecyclerAdapter(timestampsArrayList)
            recyclerAdapter.setOnItemClickListener(recyclerAdapterClickListener)
            recyclerView.apply {
                adapter = recyclerAdapter
                layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            }

            realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("HeatingAndBoiler").child("boilerOnOffTime")
                .addListenerForSingleValueEvent(object: ValueEventListener {
                    @SuppressLint("NotifyDataSetChanged")
                    override fun onDataChange(snapshot: DataSnapshot) {
                        var time = snapshot.getValue(String::class.java)!!
                        response = time.last()
                        time = time.substring(0, time.length - 1)

                        var timestampsString = ""
                        timestampsArrayList.forEach {
                            timestampsString += it.time.replace(":", "")
                        }

                        if (time != " ") {
                            timestampsArrayList.clear()
                            var onOffTime = false
                            var i = 0
                            while (i < time.length) {
                                onOffTime = !onOffTime
                                timestampsArrayList.add(HeatingOrBoilerTimestamp(time.substring(i, i + 2) +
                                        ":" + time.substring(i + 2, i + 4),
                                    onOffTime, 0))
                                i += 4
                            }
                            recyclerAdapter.notifyDataSetChanged()

                            isTimeModeStarted = true
                            editPreferences.putBoolean("isBoilerTimeModeStarted", true)
                            editPreferences.putString("BoilerTimestampsArrayList", gson.toJson(timestampsArrayList)).apply()

                            layoutTime.visibility = View.GONE
                            buttonStartTimeMode.visibility = View.GONE
                            buttonStopTimeMode.visibility = View.VISIBLE
                            buttonStopTimeMode.alpha = 1f
                        } else {
                            buttonStopTimeMode.visibility = View.GONE
                            layoutTime.visibility = View.VISIBLE
                            buttonStartTimeMode.visibility = View.VISIBLE
                            isTimeModeStarted = false
                            editPreferences.putBoolean("isBoilerTimeModeStarted", false).apply()
                        }

                        if (sharedPreferences.getBoolean("BoilerTimeModeResponse", false) && response == '1') {
                            textResponse.visibility = View.VISIBLE
                            textResponse.alpha = 1f
                            textResponse.text = getString(R.string.response_received_text)
                            editPreferences.putBoolean("BoilerTimeModeResponse", false).apply()
                            Handler(Looper.getMainLooper()).postDelayed({
                                textResponse.animate().alpha(0f).setStartDelay(0).setDuration(500).start()
                            }, 3000)
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {}

                })

            buttonAddTime.setOnClickListener {
                vibrate()
                val calendar = Calendar.getInstance()
                val timePicker = MaterialTimePicker.Builder()
                    .setTimeFormat(clockFormat)
                    .setTitleText("Выберите время")
                    .setHour(calendar.get(Calendar.HOUR_OF_DAY))
                    .setMinute(calendar.get(Calendar.MINUTE))
                    .build()

                timePicker.addOnPositiveButtonClickListener {
                    vibrate()
                    val boilerOnOffTime = if (timestampsArrayList.size > 0) {
                        !timestampsArrayList[timestampsArrayList.size - 1].onOff
                    } else {
                        true
                    }
                    var hour = timePicker.hour.toString()
                    var minute = timePicker.minute.toString()
                    if (hour.toInt() < 10) {
                        hour = "0$hour"
                    }
                    if (minute.toInt() < 10) {
                        minute = "0$minute"
                    }
                    timestampsArrayList.add(HeatingOrBoilerTimestamp("$hour:$minute", boilerOnOffTime, 0))
                    recyclerAdapter.notifyItemInserted(timestampsArrayList.size - 1)
                    editPreferences.putString("BoilerTimestampsArrayList", gson.toJson(timestampsArrayList)).apply()
                }
                timePicker.addOnNegativeButtonClickListener {
                    vibrate()
                }
                timePicker.show(requireActivity().supportFragmentManager, "timePicker")
            }

            buttonDeleteTime.setOnClickListener {
                vibrate()
                if (timestampsArrayList.size >= 1) {
                    timestampsArrayList.removeAt(timestampsArrayList.size - 1)
                    recyclerAdapter.notifyItemRemoved(timestampsArrayList.size)
                    if (timestampsArrayList.size > 0) {
                        editPreferences.putString("BoilerTimestampsArrayList", gson.toJson(timestampsArrayList)).apply()
                    } else {
                        editPreferences.putString("BoilerTimestampsArrayList", "").apply()
                    }
                } else {
                    Toast.makeText(requireActivity(), "Добавьте хотя бы\nодно время включения\n/выключения!", Toast.LENGTH_LONG).show()
                }
            }

            responseValueEventListener = object: ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.getValue(String::class.java) != null) {
                        response = snapshot.getValue(String::class.java)!!.last()
                        if (response == '1') {
                            textResponse.text = getString(R.string.response_received_text)
                            editPreferences.putBoolean("BoilerTimeModeResponse", false).apply()
                            Handler(Looper.getMainLooper()).postDelayed({
                                textResponse.animate().alpha(0f).setStartDelay(0).setDuration(500).start()
                            }, 3000)
                            realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("HeatingAndBoiler").child("boilerOnOffTime")
                                .removeEventListener(responseValueEventListener)
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {}

            }

            buttonStartTimeMode.setOnClickListener {
                vibrate()
                if (isNetworkConnected(requireActivity())) {
                    if (!sharedPreferences.getBoolean("isBoilerTimerStarted", false) && !sharedPreferences.getBoolean("isBoilerStarted", false)) {
                        if (!(isOverCurrentProtectionEnabled && maxHeatingElements > 1 && (((sharedPreferences.getBoolean("isHeatingStarted", false) || sharedPreferences.getBoolean("isHeatingTimerStarted", false))
                                    && sharedPreferences.getInt("TimerHeatingElements", 1) == maxHeatingElements) || (sharedPreferences.getBoolean("isHeatingTimeModeStarted", false)
                                    && sharedPreferences.getBoolean("isMaxHeatingElementsStartInTimeMode", false)) || (sharedPreferences.getBoolean("isHeatingTemperatureModeStarted", false)
                                    && sharedPreferences.getInt("TemperatureModeHeatingElements", 1) == maxHeatingElements)))) {
                            if (timestampsArrayList.size >= 1) {
                                isTimeModeStarted = true
                                editPreferences.putBoolean("isBoilerTimeModeStarted", true).apply()

                                var boilerOnOffTimeNodeValue = ""
                                timestampsArrayList.forEach {
                                    boilerOnOffTimeNodeValue += it.time.removeRange(2, 3)
                                }
                                realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("HeatingAndBoiler").child("boilerOnOffTime").setValue("${boilerOnOffTimeNodeValue}0")
                                realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("HeatingAndBoiler").child("boilerOnOffTime")
                                    .addValueEventListener(responseValueEventListener)

                                textResponse.visibility = View.VISIBLE
                                textResponse.animate().alpha(1f).setStartDelay(0L).setDuration(500).start()
                                textResponse.text = getString(R.string.waiting_for_response_text)

                                Handler(Looper.getMainLooper()).postDelayed({
                                    if (response == '0') {
                                        textResponse.text = getString(R.string.response_not_received_text)
                                        editPreferences.putBoolean("BoilerTimeModeResponse", true).apply()
                                    }
                                }, 5000)
                                
                                var isAnimationStarted = true
                                layoutTime.animate().alpha(0f).translationX(-1100f).setDuration(500).setStartDelay(0).start()
                                buttonStartTimeMode.animate().alpha(0f).translationX(-1100f).setDuration(500).setStartDelay(100)
                                    .setListener(object: Animator.AnimatorListener {
                                        override fun onAnimationStart(p0: Animator) {}

                                        override fun onAnimationEnd(p0: Animator) {
                                            if (isAnimationStarted) {
                                                isAnimationStarted = false

                                                layoutTime.visibility = View.GONE
                                                buttonStartTimeMode.visibility = View.GONE
                                                buttonStopTimeMode.visibility = View.VISIBLE

                                                buttonStopTimeMode.translationX = 1100f
                                                buttonStopTimeMode.animate().alpha(1f).translationX(0f).setDuration(500).setStartDelay(0).start()
                                            }
                                        }

                                        override fun onAnimationCancel(p0: Animator) {}
                                        override fun onAnimationRepeat(p0: Animator) {}

                                    }).start()
                            } else {
                                Toast.makeText(requireActivity(), "Добавьте хотя бы\nодно время включения\n/выключения!", Toast.LENGTH_LONG).show()
                            }
                        } else {
                            if (sharedPreferences.getBoolean("isHeatingStarted", false) && sharedPreferences.getInt("TimerHeatingElements", 1) == maxHeatingElements) {
                                showOverCurrentProtectionAlertDialog("Так как сейчас запущен котёл, для избежания перегрузки электросети по току, вы не можете запустить режим по времени для бойлера. Вы можете отключить эту функцию в настройках.")
                            } else if (sharedPreferences.getBoolean("isHeatingTimerStarted", false) && sharedPreferences.getInt("TimerHeatingElements", 1) == maxHeatingElements) {
                                showOverCurrentProtectionAlertDialog("Так как сейчас запущен таймер котла, для избежания перегрузки электросети по току, вы не можете запустить режим по времени для бойлера. Вы можете отключить эту функцию в настройках.")
                            } else if (sharedPreferences.getBoolean("isHeatingTimeModeStarted", false) && sharedPreferences.getBoolean("isMaxHeatingElementsStartInTimeMode", false)) {
                                showOverCurrentProtectionAlertDialog("Так как сейчас запущен режим по времени для котла, для избежания перегрузки электросети по току, вы не можете запустить режим по времени для бойлера. Вы можете отключить эту функцию в настройках.")
                            } else if (sharedPreferences.getBoolean("isHeatingTemperatureModeStarted", false) && sharedPreferences.getInt("TemperatureModeHeatingElements", 1) == maxHeatingElements) {
                                showOverCurrentProtectionAlertDialog("Так как сейчас запущен режим по температуре, для избежания перегрузки электросети по току, вы не можете запустить режим по времени для бойлера. Вы можете отключить эту функцию в настройках.")
                            }
                        }
                    } else {
                        if (sharedPreferences.getBoolean("isBoilerTimerStarted", false)) {
                            Toast.makeText(requireActivity(), "Вы не можете запустить режим пока запущен таймер!", Toast.LENGTH_LONG).show()
                        } else if (sharedPreferences.getBoolean("isBoilerStarted", false)) {
                            Toast.makeText(requireActivity(), "Вы не можете запустить режим пока запущен котёл!", Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    Toast.makeText(requireActivity(), "Нет подключения\nк Интернету!", Toast.LENGTH_LONG).show()
                }
            }

            buttonStopTimeMode.setOnClickListener {
                vibrate()
                if (isNetworkConnected(requireActivity())) {
                    isTimeModeStarted = false
                    editPreferences.putBoolean("isBoilerTimeModeStarted", false).apply()

                    realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("HeatingAndBoiler").child("boilerOnOffTime").setValue(" 0")
                    realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("HeatingAndBoiler").child("boilerOnOffTime")
                        .addValueEventListener(responseValueEventListener)

                    textResponse.visibility = View.VISIBLE
                    textResponse.animate().alpha(1f).setStartDelay(0L).setDuration(500).start()
                    textResponse.text = getString(R.string.waiting_for_response_text)

                    Handler(Looper.getMainLooper()).postDelayed({
                        if (response == '0') {
                            textResponse.text = getString(R.string.response_not_received_text)
                            editPreferences.putBoolean("BoilerTimeModeResponse", true).apply()
                        }
                    }, 5000)
                    
                    var isAnimationStarted = true
                    buttonStopTimeMode.animate().translationX(-1100f).alpha(0f).setDuration(500).setStartDelay(0)
                        .setListener(object: Animator.AnimatorListener {
                            override fun onAnimationStart(p0: Animator) {}

                            override fun onAnimationEnd(p0: Animator) {
                                if (isAnimationStarted) {
                                    isAnimationStarted = false

                                    buttonStopTimeMode.visibility = View.GONE
                                    layoutTime.visibility = View.VISIBLE
                                    buttonStartTimeMode.visibility = View.VISIBLE

                                    layoutTime.translationX = 1100f
                                    buttonStartTimeMode.translationX = 1100f

                                    layoutTime.animate().alpha(1f).translationX(0f).setDuration(500).setStartDelay(0).start()
                                    buttonStartTimeMode.animate().alpha(1f).translationX(0f).setDuration(500).setStartDelay(100).start()
                                }
                            }

                            override fun onAnimationCancel(p0: Animator) {}
                            override fun onAnimationRepeat(p0: Animator) {}

                        }).start()
                } else {
                    Toast.makeText(requireActivity(), "Нет подключения\nк Интернету!", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private val recyclerAdapterClickListener = object: TimeRecyclerAdapter.OnItemClickListener {
        override fun onItemClick(position: Int) {
            if (!isTimeModeStarted) {
                vibrate()
                val calendar = Calendar.getInstance()
                val timePicker = MaterialTimePicker.Builder()
                    .setTimeFormat(clockFormat)
                    .setTitleText("Выберите время")
                    .setHour(calendar.get(Calendar.HOUR_OF_DAY))
                    .setMinute(calendar.get(Calendar.MINUTE))
                    .build()

                timePicker.addOnPositiveButtonClickListener {
                    vibrate()
                    var hour = timePicker.hour.toString()
                    var minute = timePicker.minute.toString()
                    if (hour.toInt() < 10) {
                        hour = "0$hour"
                    }
                    if (minute.toInt() < 10) {
                        minute = "0$minute"
                    }
                    timestampsArrayList[position] = HeatingOrBoilerTimestamp("$hour:$minute", timestampsArrayList[position].onOff, 0)
                    recyclerAdapter.notifyItemChanged(position)
                    editPreferences.putString("BoilerTimestampsArrayList", gson.toJson(timestampsArrayList)).apply()
                }
                timePicker.addOnNegativeButtonClickListener {
                    vibrate()
                }
                timePicker.show(requireActivity().supportFragmentManager, "timePicker")
            }
        }

    }

    private fun showOverCurrentProtectionAlertDialog(alertDialogMessage: String) {
        val alertDialogBuilder = androidx.appcompat.app.AlertDialog.Builder(requireActivity())
        alertDialogBuilder.setTitle("Защита от перегрузки сети")
        alertDialogBuilder.setMessage(alertDialogMessage)
        alertDialogBuilder.setPositiveButton("Понятно") { _, _ ->
            vibrate()
        }
        alertDialogBuilder.create().show()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentBoilerTimeBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    @Suppress("DEPRECATION")
    private fun vibrate() {
        if (vibrator.hasVibrator()) {
            if (isHapticFeedbackEnabled == "1") {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    binding.buttonStartTimeMode.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING)
                } else {
                    binding.buttonStartTimeMode.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING + HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
                }
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    vibrator.vibrate(20)
                }
            }
        }
    }
}