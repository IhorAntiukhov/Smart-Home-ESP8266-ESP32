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
import com.arduinoworld.smarthome.databinding.FragmentHeatingTimeBinding
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

class HeatingTimeFragment : Fragment() {
    private lateinit var binding: FragmentHeatingTimeBinding
    private lateinit var gson: Gson
    private var maxHeatingElements = 2
    private var timestampsArrayList = ArrayList<HeatingOrBoilerTimestamp>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        with(binding) {
            maxHeatingElements = sharedPreferences.getString("MaxHeatingElements", "2")!!.toInt()
            val isOverCurrentProtectionEnabled = sharedPreferences.getBoolean("isOverCurrentProtectionEnabled", true)
            var heatingElements = 1
            var isMaxHeatingElementsStartInTimeMode = sharedPreferences.getBoolean("isMaxHeatingElementsStartInTimeMode", false)
            var heatingOnOffTime = false
            val isTimeModeStarted = sharedPreferences.getBoolean("isHeatingTimeModeStarted", false)

            val isSystem24Hour = is24HourFormat(requireActivity())
            val clockFormat = if (isSystem24Hour) TimeFormat.CLOCK_24H else TimeFormat.CLOCK_12H
            val calendar = Calendar.getInstance()

            gson = Gson()
            if (isTimeModeStarted) {
                layoutTime.visibility = View.GONE
                layoutHeatingElements.visibility = View.GONE
                buttonStartTimeMode.visibility = View.GONE
                buttonStopTimeMode.visibility = View.VISIBLE
                buttonStopTimeMode.alpha = 1f
            }
            if (sharedPreferences.getString("HeatingTimestampsArrayList", "") != "")
                timestampsArrayList = gson.fromJson(sharedPreferences.getString("HeatingTimestampsArrayList", ""), object : TypeToken<ArrayList<HeatingOrBoilerTimestamp?>?>() {}.type)

            val recyclerAdapter = HeatingTimeRecyclerAdapter(timestampsArrayList)
            recyclerView.apply {
                adapter = recyclerAdapter
                layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            }

            if (maxHeatingElements > 1) {
                buttonIncrease.setOnClickListener {
                    vibrate()
                    if (heatingElements < maxHeatingElements) {
                        heatingElements += 1
                        if (heatingElements == maxHeatingElements) {
                            buttonIncrease.setImageResource(R.drawable.ic_increase_disabled)
                        }
                        buttonDecrease.setImageResource(R.drawable.ic_decrease)
                        textHeatingElements.text = getString(R.string.heating_elements_text, heatingElements)
                        editPreferences.putInt("HeatingElements", heatingElements).apply()
                        realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("HeatingAndBoiler").child("heatingElements").setValue(heatingElements)
                    } else {
                        Toast.makeText(requireActivity(), "Вы установили максимальное количество тэнов!", Toast.LENGTH_LONG).show()
                    }
                }

                buttonDecrease.setOnClickListener {
                    vibrate()
                    if (heatingElements > 1) {
                        heatingElements -= 1
                        if (heatingElements == 1) {
                            buttonDecrease.setImageResource(R.drawable.ic_decrease_disabled)
                        }
                        buttonIncrease.setImageResource(R.drawable.ic_increase)
                        textHeatingElements.text = getString(R.string.heating_elements_text, heatingElements)
                    } else {
                        Toast.makeText(requireActivity(), "Вы установили минимальное количество тэнов!", Toast.LENGTH_LONG).show()
                    }
                }
            } else {
                layoutHeatingElements.visibility = View.GONE
            }

            realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("HeatingAndBoiler")
                .addListenerForSingleValueEvent(object: ValueEventListener {
                    @SuppressLint("NotifyDataSetChanged")
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val time = snapshot.child("heatingOnOffTime").getValue(String::class.java)!!
                        val timeHeatingElementsString = snapshot.child("timeHeatingElements").getValue(String::class.java)!!

                        var timestampsString = ""
                        timestampsArrayList.forEach {
                            timestampsString += it.time.replace(":", "")
                        }
                        var heatingElementsString = ""
                        timestampsArrayList.forEach {
                            heatingElementsString += it.heatingElements.toString()
                        }

                        if (time != " " && !isTimeModeStarted) {
                            val timeHeatingElementsArrayList = ArrayList<Int>()
                            for (i in timeHeatingElementsString.indices) {
                                timeHeatingElementsArrayList.add(timeHeatingElementsString[i] - '0')
                            }
                            if (timeHeatingElementsArrayList.contains(maxHeatingElements)) isMaxHeatingElementsStartInTimeMode = true

                            timestampsArrayList.clear()
                            var onOffTime = false
                            var i = 0
                            while (i < time.length) {
                                onOffTime = !onOffTime
                                timestampsArrayList.add(HeatingOrBoilerTimestamp(time.substring(i, i + 2) +
                                        ":" + time.substring(i + 2, i + 4),
                                    onOffTime, timeHeatingElementsArrayList[i / 4]))
                                i += 4
                            }
                            recyclerAdapter.notifyDataSetChanged()

                            editPreferences.putBoolean("isHeatingTimeModeStarted", true)
                            editPreferences.putString("HeatingTimestampsArrayList", gson.toJson(timestampsArrayList)).apply()

                            layoutTime.visibility = View.GONE
                            layoutHeatingElements.visibility = View.GONE
                            buttonStartTimeMode.visibility = View.GONE
                            buttonStopTimeMode.visibility = View.VISIBLE
                            buttonStopTimeMode.alpha = 1f
                        } else if (time == " " && isTimeModeStarted) {
                            buttonStopTimeMode.visibility = View.GONE
                            layoutHeatingElements.visibility = View.VISIBLE
                            layoutTime.visibility = View.VISIBLE
                            buttonStartTimeMode.visibility = View.VISIBLE
                            editPreferences.putBoolean("isHeatingTimeModeStarted", false).apply()
                        } else if (!(timestampsString == time && heatingElementsString == timeHeatingElementsString) && isTimeModeStarted) {
                            val timeHeatingElementsArrayList = ArrayList<Int>()
                            for (i in timeHeatingElementsString.indices) {
                                timeHeatingElementsArrayList.add(timeHeatingElementsString[i] - '0')
                            }
                            if (timeHeatingElementsArrayList.contains(maxHeatingElements)) isMaxHeatingElementsStartInTimeMode = true

                            timestampsArrayList.clear()
                            var onOffTime = false
                            var i = 0
                            while (i < time.length) {
                                onOffTime = !onOffTime
                                timestampsArrayList.add(HeatingOrBoilerTimestamp(time.substring(i, i + 2) +
                                        ":" + time.substring(i + 2, i + 4),
                                    onOffTime, timeHeatingElementsArrayList[i / 4]))
                                i += 4
                            }
                            recyclerAdapter.notifyDataSetChanged()

                            editPreferences.putString("HeatingTimestampsArrayList", gson.toJson(timestampsArrayList)).apply()
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {}

                })

            buttonAddTime.setOnClickListener {
                vibrate()
                val timePicker = MaterialTimePicker.Builder()
                    .setTimeFormat(clockFormat)
                    .setTitleText("Выберите время")
                    .setHour(calendar.get(Calendar.HOUR_OF_DAY))
                    .setMinute(calendar.get(Calendar.MINUTE))
                    .build()

                timePicker.addOnPositiveButtonClickListener {
                    vibrate()
                    heatingOnOffTime = !heatingOnOffTime
                    var hour = timePicker.hour.toString()
                    var minute = timePicker.minute.toString()
                    if (hour.toInt() < 10) {
                        hour = "0$hour"
                    }
                    if (minute.toInt() < 10) {
                        minute = "0$minute"
                    }
                    var timeHeatingElements = heatingElements
                    if (!heatingOnOffTime) {
                        timeHeatingElements = 0
                    }
                    if (timeHeatingElements == maxHeatingElements) isMaxHeatingElementsStartInTimeMode = true
                    timestampsArrayList.add(HeatingOrBoilerTimestamp("$hour:$minute", heatingOnOffTime, timeHeatingElements))
                    recyclerAdapter.notifyItemInserted(timestampsArrayList.size - 1)
                }
                timePicker.addOnNegativeButtonClickListener {
                    vibrate()
                }
                timePicker.show(requireActivity().supportFragmentManager, "timePicker")
            }

            buttonDeleteTime.setOnClickListener {
                vibrate()
                if (timestampsArrayList.size >= 1) {
                    val timestampsArrayListSize = timestampsArrayList.size
                    timestampsArrayList.clear()
                    recyclerAdapter.notifyItemRangeRemoved(0, timestampsArrayListSize)
                    heatingOnOffTime = false
                    isMaxHeatingElementsStartInTimeMode = false
                    editPreferences.putString("HeatingTimestampsArrayList", "").apply()
                } else {
                    Toast.makeText(requireActivity(), "Добавьте хотя бы\nодно время включения\n/выключения!", Toast.LENGTH_LONG).show()
                }
            }

            var isAnimationStarted: Boolean
            buttonStartTimeMode.setOnClickListener {
                vibrate()
                if (isNetworkConnected(requireActivity())) {
                    if (!sharedPreferences.getBoolean("isHeatingStarted", false) && !sharedPreferences.getBoolean("isHeatingTimerStarted", false)) {
                        if (!(isOverCurrentProtectionEnabled && isMaxHeatingElementsStartInTimeMode && maxHeatingElements > 1 && (sharedPreferences.getBoolean("isBoilerStarted", false) || sharedPreferences.getBoolean("isBoilerTimerStarted", false) || sharedPreferences.getBoolean("isBoilerTimeModeStarted", false)))) {
                            if (timestampsArrayList.size >= 1) {
                                if (sharedPreferences.getBoolean("isHeatingTemperatureModeStarted", false)) {
                                    val alertDialogBuilder = androidx.appcompat.app.AlertDialog.Builder(requireActivity())
                                    alertDialogBuilder.setTitle("Совмещение режимов")
                                    alertDialogBuilder.setMessage("Вы хотите совместить режимы по времени и по температуре?")
                                    alertDialogBuilder.setPositiveButton("Подтвердить") { _, _ ->
                                        vibrate()
                                        startTimeMode()
                                    }
                                    alertDialogBuilder.setNegativeButton("Отмена") { _, _ ->
                                        vibrate()
                                    }
                                    alertDialogBuilder.create().show()
                                } else {
                                    startTimeMode()
                                }
                            } else {
                                Toast.makeText(requireActivity(), "Добавьте хотя бы\nодно время включения\n/выключения!", Toast.LENGTH_LONG).show()
                            }
                        } else {
                            if (sharedPreferences.getBoolean("isBoilerStarted", false)) {
                                showOverCurrentProtectionAlertDialog("Так как сейчас запущен бойлер, для избежания перегрузки электросети по току, вы не можете запустить режим по времени для котла. Вы можете отключить эту функцию в настройках.")
                            } else if (sharedPreferences.getBoolean("isBoilerTimerStarted", false)) {
                                showOverCurrentProtectionAlertDialog("Так как сейчас запущен таймер бойлера, для избежания перегрузки электросети по току, вы не можете запустить режим по времени для котла. Вы можете отключить эту функцию в настройках.")
                            } else if (sharedPreferences.getBoolean("isBoilerTimeModeStarted", false)) {
                                showOverCurrentProtectionAlertDialog("Так как сейчас запущен режим по времени для бойлера, для избежания перегрузки электросети по току, вы не можете запустить режим по времени для котла. Вы можете отключить эту функцию в настройках.")
                            }
                        }
                    } else {
                        if (sharedPreferences.getBoolean("isHeatingStarted", false)) {
                            Toast.makeText(requireActivity(), "Вы не можете запустить режим, пока запущен котёл!", Toast.LENGTH_LONG).show()
                        } else if (sharedPreferences.getBoolean("isHeatingTimerStarted", false)) {
                            Toast.makeText(requireActivity(), "Вы не можете запустить режим, пока запущен таймер!", Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    Toast.makeText(requireActivity(), "Нет подключения\nк Интернету!", Toast.LENGTH_LONG).show()
                }
            }

            buttonStopTimeMode.setOnClickListener {
                vibrate()
                if (isNetworkConnected(requireActivity())) {
                    editPreferences.putBoolean("isHeatingTimeModeStarted", false).apply()

                    realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("HeatingAndBoiler").child("timeHeatingElements").setValue(" ")
                    Handler(Looper.getMainLooper()).postDelayed({
                        realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("HeatingAndBoiler").child("heatingOnOffTime").setValue(" ")
                        Handler(Looper.getMainLooper()).postDelayed({
                            if (sharedPreferences.getBoolean("isHeatingTemperatureModeStarted", false)) {
                                editPreferences.putBoolean("isHeatingTemperatureModeStarted", false).apply()
                                realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("HeatingAndBoiler").child("temperatureMode").setValue(" ")
                            }
                        }, 250)
                    }, 250)

                    isAnimationStarted = true
                    buttonStopTimeMode.animate().translationX(-800f).alpha(0f).setDuration(500).setStartDelay(0)
                        .setListener(object: Animator.AnimatorListener {
                            override fun onAnimationStart(p0: Animator) {}

                            override fun onAnimationEnd(p0: Animator) {
                                if (isAnimationStarted) {
                                    isAnimationStarted = false

                                    buttonStopTimeMode.visibility = View.GONE
                                    layoutTime.visibility = View.VISIBLE
                                    if (maxHeatingElements > 1) layoutHeatingElements.visibility = View.VISIBLE
                                    buttonStartTimeMode.visibility = View.VISIBLE

                                    if (maxHeatingElements > 1) layoutHeatingElements.alpha = 0f
                                    layoutTime.translationX = 1100f
                                    buttonStartTimeMode.translationX = 1100f

                                    layoutTime.animate().alpha(1f).translationX(0f).setDuration(500).setStartDelay(0).start()
                                    if (maxHeatingElements > 1) layoutHeatingElements.animate().alpha(1f).setDuration(500).setStartDelay(0).start()
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

    private fun startTimeMode() {
        with(binding) {
            var heatingElementsNodeValue = ""
            timestampsArrayList.forEach {
                heatingElementsNodeValue += it.heatingElements
            }
            var heatingOnOffTimeNodeValue = ""
            timestampsArrayList.forEach {
                heatingOnOffTimeNodeValue += it.time.removeRange(2, 3)
            }

            editPreferences.putBoolean("isHeatingTimeModeStarted", true)
            editPreferences.putString("HeatingTimestampsArrayList", gson.toJson(timestampsArrayList))
            editPreferences.putBoolean("isMaxHeatingElementsStartInTimeMode", heatingElementsNodeValue.contains(maxHeatingElements.toString())).apply()

            realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("HeatingAndBoiler").child("timeHeatingElements").setValue(heatingElementsNodeValue)
            Handler(Looper.getMainLooper()).postDelayed({
                realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("HeatingAndBoiler").child("heatingOnOffTime").setValue(heatingOnOffTimeNodeValue)
            }, 250)

            var isAnimationStarted = true
            layoutTime.animate().alpha(0f).translationX(-1100f).setDuration(500).setStartDelay(0).start()
            layoutHeatingElements.animate().alpha(0f).setDuration(500).setStartDelay(0).start()
            buttonStartTimeMode.animate().alpha(0f).translationX(-1100f).setDuration(500).setStartDelay(100)
                .setListener(object: Animator.AnimatorListener {
                    override fun onAnimationStart(p0: Animator) {}

                    override fun onAnimationEnd(p0: Animator) {
                        if (isAnimationStarted) {
                            isAnimationStarted = false

                            layoutTime.visibility = View.GONE
                            layoutHeatingElements.visibility = View.GONE
                            buttonStartTimeMode.visibility = View.GONE
                            buttonStopTimeMode.visibility = View.VISIBLE

                            buttonStopTimeMode.translationX = 1100f
                            buttonStopTimeMode.animate().alpha(1f).translationX(0f).setDuration(500).setStartDelay(0).start()
                        }
                    }

                    override fun onAnimationCancel(p0: Animator) {}
                    override fun onAnimationRepeat(p0: Animator) {}

                }).start()
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
        binding = FragmentHeatingTimeBinding.inflate(layoutInflater, container, false)
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