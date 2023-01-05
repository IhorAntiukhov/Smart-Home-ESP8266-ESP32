package com.arduinoworld.smarthome

import android.animation.Animator
import android.annotation.SuppressLint
import android.os.*
import android.text.format.DateFormat
import android.view.HapticFeedbackConstants
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import com.arduinoworld.smarthome.MainActivity.Companion.editPreferences
import com.arduinoworld.smarthome.MainActivity.Companion.firebaseAuth
import com.arduinoworld.smarthome.MainActivity.Companion.isHapticFeedbackEnabled
import com.arduinoworld.smarthome.MainActivity.Companion.isNetworkConnected
import com.arduinoworld.smarthome.MainActivity.Companion.realtimeDatabase
import com.arduinoworld.smarthome.MainActivity.Companion.sharedPreferences
import com.arduinoworld.smarthome.MainActivity.Companion.vibrator
import com.arduinoworld.smarthome.databinding.FragmentHeaterTimeBinding
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.*
import kotlin.collections.ArrayList

class HeaterTimeFragment : Fragment() {
    private lateinit var binding: FragmentHeaterTimeBinding
    private lateinit var recyclerAdapter: TimeRecyclerAdapter
    private lateinit var gson: Gson
    private var timestampsArrayList = ArrayList<HeatingOrBoilerTimestamp>()
    private var isTimeModeStarted = false
    private var clockFormat = 0

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        with(binding) {
            isTimeModeStarted = sharedPreferences.getBoolean("isHeaterTimeModeStarted", false)

            val isSystem24Hour = DateFormat.is24HourFormat(requireActivity())
            clockFormat = if (isSystem24Hour) TimeFormat.CLOCK_24H else TimeFormat.CLOCK_12H

            gson = Gson()
            if (isTimeModeStarted) {
                layoutTime.visibility = View.GONE
                buttonStartTimeMode.visibility = View.GONE
                buttonStopTimeMode.visibility = View.VISIBLE
                buttonStopTimeMode.alpha = 1f
            }
            if (sharedPreferences.getString("HeaterTimestampsArrayList", "") != "")
                timestampsArrayList = gson.fromJson(sharedPreferences.getString("HeaterTimestampsArrayList", ""), object : TypeToken<ArrayList<HeatingOrBoilerTimestamp?>?>() {}.type)

            recyclerAdapter = TimeRecyclerAdapter(timestampsArrayList)
            recyclerAdapter.setOnItemClickListener(recyclerAdapterClickListener)
            recyclerView.apply {
                adapter = recyclerAdapter
                layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            }

            realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("Heater").child("heaterOnOffTime")
                .addListenerForSingleValueEvent(object: ValueEventListener {
                    @SuppressLint("NotifyDataSetChanged")
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val time = snapshot.getValue(String::class.java)!!

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
                            editPreferences.putBoolean("isHeaterTimeModeStarted", true)
                            editPreferences.putString("HeaterTimestampsArrayList", gson.toJson(timestampsArrayList)).apply()

                            layoutTime.visibility = View.GONE
                            buttonStartTimeMode.visibility = View.GONE
                            buttonStopTimeMode.visibility = View.VISIBLE
                            buttonStopTimeMode.alpha = 1f
                        } else {
                            buttonStopTimeMode.visibility = View.GONE
                            layoutTime.visibility = View.VISIBLE
                            buttonStartTimeMode.visibility = View.VISIBLE
                            isTimeModeStarted = false
                            editPreferences.putBoolean("isHeaterTimeModeStarted", false).apply()
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
                    val heaterOnOffTime = if (timestampsArrayList.size > 0) {
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
                    timestampsArrayList.add(HeatingOrBoilerTimestamp("$hour:$minute", heaterOnOffTime, 0))
                    recyclerAdapter.notifyItemInserted(timestampsArrayList.size - 1)
                    editPreferences.putString("HeaterTimestampsArrayList", gson.toJson(timestampsArrayList)).apply()
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
                        editPreferences.putString("HeaterTimestampsArrayList", gson.toJson(timestampsArrayList)).apply()
                    } else {
                        editPreferences.putString("HeaterTimestampsArrayList", "").apply()
                    }
                } else {
                    Toast.makeText(requireActivity(), "Добавьте хотя бы\nодно время включения\n/выключения!", Toast.LENGTH_LONG).show()
                }
            }

            buttonStartTimeMode.setOnClickListener {
                vibrate()
                if (isNetworkConnected(requireActivity())) {
                    if (!sharedPreferences.getBoolean("isHeaterStarted", false)) {
                        if (timestampsArrayList.size >= 1) {
                            if (sharedPreferences.getBoolean("isHeaterTemperatureModeStarted", false)) {
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
                        Toast.makeText(requireActivity(), "Вы не можете запустить режим, пока запущен обогреватель!", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(requireActivity(), "Нет подключения\nк Интернету!", Toast.LENGTH_LONG).show()
                }
            }

            buttonStopTimeMode.setOnClickListener {
                vibrate()
                if (isNetworkConnected(requireActivity())) {
                    isTimeModeStarted = false
                    editPreferences.putBoolean("isHeaterTimeModeStarted", false).apply()

                    realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("Heater").child("heaterOnOffTime").setValue(" ")
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (sharedPreferences.getBoolean("isHeaterTemperatureModeStarted", false)) {
                            editPreferences.putBoolean("isHeaterTemperatureModeStarted", false).apply()
                            realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("Heater").child("temperatureMode").setValue(" ")
                        }
                    }, 250)

                    var isAnimationStarted = true
                    buttonStopTimeMode.animate().translationX(-800f).alpha(0f).setDuration(500).setStartDelay(0)
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

    private fun startTimeMode() {
        with(binding) {
            var heaterOnOffTimeNodeValue = ""
            timestampsArrayList.forEach {
                heaterOnOffTimeNodeValue += it.time.removeRange(2, 3)
            }
            realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("Heater").child("heaterOnOffTime").setValue(heaterOnOffTimeNodeValue)

            isTimeModeStarted = true
            editPreferences.putBoolean("isHeaterTimeModeStarted", true).apply()

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
                    editPreferences.putString("HeaterTimestampsArrayList", gson.toJson(timestampsArrayList)).apply()
                }
                timePicker.addOnNegativeButtonClickListener {
                    vibrate()
                }
                timePicker.show(requireActivity().supportFragmentManager, "timePicker")
            }
        }

    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentHeaterTimeBinding.inflate(layoutInflater, container, false)
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