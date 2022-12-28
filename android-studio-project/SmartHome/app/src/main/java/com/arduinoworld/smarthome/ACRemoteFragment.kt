package com.arduinoworld.smarthome

import android.animation.Animator
import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.graphics.Color
import android.os.*
import android.text.format.DateFormat
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.arduinoworld.smarthome.MainActivity.Companion.editPreferences
import com.arduinoworld.smarthome.MainActivity.Companion.firebaseAuth
import com.arduinoworld.smarthome.MainActivity.Companion.isNetworkConnected
import com.arduinoworld.smarthome.MainActivity.Companion.realtimeDatabase
import com.arduinoworld.smarthome.MainActivity.Companion.sharedPreferences
import com.arduinoworld.smarthome.databinding.FragmentAcRemoteBinding
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.*


class ACRemoteFragment : Fragment() {
    companion object {
        var isTimeModeSelected = false
    }

    private lateinit var binding: FragmentAcRemoteBinding
    private lateinit var recyclerAdapter: TimeRecyclerAdapter
    private lateinit var handler: Handler
    private var temperature = 0
    private var acMode = 0
    private var fanSpeed = 0
    private var isTurboModeEnabled = false
    private var isLightEnabled = false
    private var isACStarted = false
    private var isTimeModeStarted = false
    private var remoteSettingsMode = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        with(binding) {
            sharedPreferences.registerOnSharedPreferenceChangeListener(sharedPreferencesChangeListener)

            val minTemperature = sharedPreferences.getString("MinTemperature", "16")!!.toInt()
            val maxTemperature = sharedPreferences.getString("MaxTemperature", "30")!!.toInt()
            temperature = sharedPreferences.getInt("ACTemperature", minTemperature)
            acMode = sharedPreferences.getInt("ACMode", 0)
            fanSpeed = sharedPreferences.getInt("ACFanSpeed", 0)
            isTurboModeEnabled = sharedPreferences.getBoolean("isTurboModeEnabled", false)
            isLightEnabled = sharedPreferences.getBoolean("isLightEnabled", false)
            isACStarted = sharedPreferences.getBoolean("isACStarted", false)
            isTimeModeStarted = sharedPreferences.getBoolean("isACTimeModeStarted", false)

            var acOnOffTime = false
            var timestampsArrayList = ArrayList<HeatingOrBoilerTimestamp>()
            val isSystem24Hour = DateFormat.is24HourFormat(requireActivity())
            val clockFormat = if (isSystem24Hour) TimeFormat.CLOCK_24H else TimeFormat.CLOCK_12H
            val calendar = Calendar.getInstance()

            val gson = Gson()
            if (sharedPreferences.getString("ACTimestampsArrayList", "") != "") {
                timestampsArrayList = gson.fromJson(sharedPreferences.getString("ACTimestampsArrayList", ""), object : TypeToken<ArrayList<HeatingOrBoilerTimestamp?>?>() {}.type)
                recyclerAdapter = TimeRecyclerAdapter(timestampsArrayList)
                recyclerView.apply {
                    adapter = recyclerAdapter
                    layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
                }
            }

            setACMode()
            setFanSpeed()
            if (isTurboModeEnabled) buttonTurbo.text = getString(R.string.turbo_on_text)
            if (isLightEnabled) buttonLight.text = getString(R.string.light_on_text)
            if (isACStarted) {
                buttonStartAC.visibility = View.GONE
                buttonStopAC.visibility = View.VISIBLE
                buttonStopAC.alpha = 1f
            }

            textTemperature.text = getString(R.string.temperature_text, temperature)
            if (acMode != 4) {
                if (temperature == maxTemperature) {
                    buttonIncreaseTemperature.setColorFilter(Color.parseColor("#6A61AD"))
                } else if (temperature == minTemperature) {
                    buttonDecreaseTemperature.setColorFilter(Color.parseColor("#6A61AD"))
                }
            } else {
                buttonIncreaseTemperature.setColorFilter(Color.parseColor("#6A61AD"))
                buttonDecreaseTemperature.setColorFilter(Color.parseColor("#6A61AD"))
                textTemperature.setTextColor(Color.parseColor("#6A61AD"))
            }

            realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("SmartRemotes")
                .addListenerForSingleValueEvent(object: ValueEventListener {
                    @SuppressLint("NotifyDataSetChanged")
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val acSettings = snapshot.child("acRemote").getValue(String::class.java)!!
                        if (acSettings.length > 4) {
                            temperature = acSettings.substring(acSettings.indexOf(" ") + 1, acSettings.indexOf(" ", acSettings.indexOf(" ") + 1)).toInt()
                            textTemperature.text = getString(R.string.temperature_text, temperature)

                            acMode = acSettings[acSettings.indexOf(" ", acSettings.indexOf(" ") + 1) + 1] - '0'
                            setACMode()
                            fanSpeed = acSettings[acSettings.indexOf(" ", acSettings.indexOf(" ") + 1) + 2] - '0'
                            setFanSpeed()

                            if (acMode != 4) {
                                buttonIncreaseTemperature.setColorFilter(Color.parseColor("#5347AE"))
                                buttonDecreaseTemperature.setColorFilter(Color.parseColor("#5347AE"))
                                if (temperature == maxTemperature) {
                                    buttonIncreaseTemperature.setColorFilter(Color.parseColor("#6A61AD"))
                                } else if (temperature == minTemperature) {
                                    buttonDecreaseTemperature.setColorFilter(Color.parseColor("#6A61AD"))
                                }
                            } else {
                                buttonIncreaseTemperature.setColorFilter(Color.parseColor("#6A61AD"))
                                buttonDecreaseTemperature.setColorFilter(Color.parseColor("#6A61AD"))
                                textTemperature.setTextColor(Color.parseColor("#6A61AD"))
                            }

                            isTurboModeEnabled = acSettings[acSettings.indexOf(" ", acSettings.indexOf(" ") + 1) + 3] == '1'
                            if (isTurboModeEnabled) {
                                buttonTurbo.text = getString(R.string.turbo_on_text)
                            } else {
                                buttonTurbo.text = getString(R.string.turbo_off_text)
                            }

                            isLightEnabled = acSettings[acSettings.indexOf(" ", acSettings.indexOf(" ") + 1) + 4] == '1'
                            if (isLightEnabled) {
                                buttonLight.text = getString(R.string.light_on_text)
                            } else {
                                buttonLight.text = getString(R.string.light_off_text)
                            }

                            isACStarted = acSettings.last() == '1'
                            if (isACStarted) {
                                buttonStartAC.visibility = View.GONE
                                buttonStopAC.visibility = View.VISIBLE
                                buttonStopAC.alpha = 1f
                            } else {
                                buttonStopAC.visibility = View.GONE
                                buttonStartAC.visibility = View.VISIBLE
                            }
                        }
                        val time = snapshot.child("acOnOffTime").getValue(String::class.java)!!
                        if (time != " ") {
                            val timestampsArrayListSize = timestampsArrayList.size
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
                            if (timestampsArrayListSize > 0) {
                                recyclerAdapter.notifyDataSetChanged()
                            } else {
                                recyclerAdapter = TimeRecyclerAdapter(timestampsArrayList)
                                recyclerView.apply {
                                    adapter = recyclerAdapter
                                    layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
                                }
                            }

                            isTimeModeStarted = true
                            buttonStartAC.visibility = View.GONE
                            editPreferences.putString("ACTimestampsArrayList", gson.toJson(timestampsArrayList)).apply()
                        } else {
                            val timestampsArrayListSize = timestampsArrayList.size
                            timestampsArrayList.clear()
                            if (timestampsArrayListSize > 0) {
                                recyclerAdapter.notifyDataSetChanged()
                            }
                            isTimeModeStarted = false
                            editPreferences.putString("ACTimestampsArrayList", "").apply()
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {}

                })

            buttonIncreaseTemperature.setOnClickListener {
                vibrate()
                if (temperature < maxTemperature && acMode != 4) {
                    temperature += 1
                    textTemperature.text = getString(R.string.temperature_text, temperature)

                    buttonDecreaseTemperature.setColorFilter(Color.parseColor("#5347AE"))
                    if (temperature == maxTemperature) {
                        buttonIncreaseTemperature.setColorFilter(Color.parseColor("#6A61AD"))
                    }

                    editPreferences.putInt("ACTemperature", temperature)
                    sendRemoteButton("temperature$temperature")
                } else {
                    if (temperature == maxTemperature) {
                        Toast.makeText(requireActivity(), "Вы установили максимальную температуру!", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(requireActivity(), "Вы не можете изменять температуру в авто режиме!", Toast.LENGTH_LONG).show()
                    }
                }
            }

            buttonDecreaseTemperature.setOnClickListener {
                vibrate()
                if (temperature > minTemperature && acMode != 4) {
                    temperature -= 1
                    textTemperature.text = getString(R.string.temperature_text, temperature)

                    buttonIncreaseTemperature.setColorFilter(Color.parseColor("#5347AE"))
                    if (temperature == minTemperature) {
                        buttonDecreaseTemperature.setColorFilter(Color.parseColor("#6A61AD"))
                    }

                    editPreferences.putInt("ACTemperature", temperature).apply()
                    sendRemoteButton("temperature$temperature")
                } else {
                    if (temperature == minTemperature) {
                        Toast.makeText(requireActivity(), "Вы установили минимальную температуру!", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(requireActivity(), "Вы не можете изменять температуру в авто режиме!", Toast.LENGTH_LONG).show()
                    }
                }
            }

            buttonACMode.setOnClickListener {
                vibrate()
                acMode += 1
                if (acMode == 4) {
                    buttonIncreaseTemperature.setColorFilter(Color.parseColor("#6A61AD"))
                    buttonDecreaseTemperature.setColorFilter(Color.parseColor("#6A61AD"))
                    textTemperature.setTextColor(Color.parseColor("#6A61AD"))
                }
                if (acMode > 4) {
                    acMode = 0
                    buttonIncreaseTemperature.setColorFilter(Color.parseColor("#5347AE"))
                    buttonDecreaseTemperature.setColorFilter(Color.parseColor("#5347AE"))
                    if (temperature == maxTemperature) {
                        buttonIncreaseTemperature.setColorFilter(Color.parseColor("#6A61AD"))
                    } else if (temperature == minTemperature) {
                        buttonDecreaseTemperature.setColorFilter(Color.parseColor("#6A61AD"))
                    }
                    textTemperature.setTextColor(Color.parseColor("#5347AE"))
                }
                setACMode()
                editPreferences.putInt("ACMode", acMode)
                sendRemoteButton("mode$acMode")
            }

            buttonFanSpeed.setOnClickListener {
                vibrate()
                fanSpeed += 1
                if (fanSpeed > 3) fanSpeed = 0
                setFanSpeed()
                editPreferences.putInt("ACFanSpeed", fanSpeed)
                sendRemoteButton("fanSpeed$fanSpeed")
            }

            buttonTurbo.setOnClickListener {
                vibrate()
                isTurboModeEnabled = !isTurboModeEnabled
                if (isTurboModeEnabled) {
                    buttonTurbo.text = getString(R.string.turbo_on_text)
                } else {
                    buttonTurbo.text = getString(R.string.turbo_off_text)
                }
                editPreferences.putBoolean("isTurboModeEnabled", isTurboModeEnabled)
                sendRemoteButton("turbo${booleanToString(isTurboModeEnabled)}")
            }

            buttonLight.setOnClickListener {
                vibrate()
                isLightEnabled = !isLightEnabled
                if (isLightEnabled) {
                    buttonLight.text = getString(R.string.light_on_text)
                } else {
                    buttonLight.text = getString(R.string.light_off_text)
                }
                editPreferences.putBoolean("isLightEnabled", isLightEnabled)
                sendRemoteButton("light${booleanToString(isLightEnabled)}")
            }

            buttonStartAC.setOnClickListener {
                vibrate()
                isACStarted = true
                if (timestampsArrayList.size > 1) {
                    editPreferences.putString("ACTimestampsArrayList", gson.toJson(timestampsArrayList))
                    var acOnOffTimeNodeValue = ""
                    timestampsArrayList.forEach {
                        acOnOffTimeNodeValue += it.time.removeRange(2, 3)
                    }
                    realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("SmartRemotes").child("acTime")
                        .setValue(acOnOffTimeNodeValue)
                }
                editPreferences.putBoolean("isACStarted", true)
                sendRemoteButton("on")

                var startDelay = 0L
                if (isTimeModeSelected) {
                    startDelay = 100L
                    layoutTime.animate().alpha(0f).translationX(-1100f).setDuration(500).setStartDelay(0).start()
                }
                var isAnimationStarted = true
                buttonTimeMode.animate().alpha(0f).translationX(-1100f).setDuration(500).setStartDelay(startDelay).start()
                buttonStartAC.animate().alpha(0f).translationX(-1100f).setDuration(500).setStartDelay(startDelay + 100)
                    .setListener(object: Animator.AnimatorListener {
                        override fun onAnimationStart(animation: Animator) {}

                        override fun onAnimationEnd(animation: Animator) {
                            if (isAnimationStarted) {
                                isAnimationStarted = false

                                layoutTime.visibility = View.GONE
                                buttonTimeMode.visibility = View.GONE
                                buttonStartAC.visibility = View.GONE
                                buttonStopAC.visibility = View.VISIBLE

                                buttonStopAC.translationX = 1100f
                                buttonStopAC.animate().alpha(1f).translationX(0f).setDuration(500).setStartDelay(0).start()
                            }
                        }

                        override fun onAnimationCancel(animation: Animator) {}
                        override fun onAnimationRepeat(animation: Animator) {}

                    }).start()
            }

            buttonStopAC.setOnClickListener {
                vibrate()
                isACStarted = false
                editPreferences.putBoolean("isACStarted", false)
                realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("SmartRemotes").child("acTime")
                    .setValue(" ")
                sendRemoteButton("off")

                var isAnimationStarted = true
                buttonStopAC.animate().alpha(0f).translationX(-1100f).setDuration(500).setStartDelay(0)
                    .setListener(object: Animator.AnimatorListener {
                        override fun onAnimationStart(animation: Animator) {}

                        override fun onAnimationEnd(animation: Animator) {
                            if (isAnimationStarted) {
                                isAnimationStarted = false

                                buttonStopAC.visibility = View.GONE
                                buttonTimeMode.visibility = View.VISIBLE
                                buttonStartAC.visibility = View.VISIBLE

                                buttonTimeMode.alpha = 0f
                                buttonStartAC.alpha = 0f

                                buttonTimeMode.translationX = 1100f
                                buttonStartAC.translationX = 1100f

                                buttonTimeMode.animate().alpha(1f).translationX(0f).setDuration(500).setStartDelay(0).start()
                                buttonStartAC.animate().alpha(1f).translationX(0f).setDuration(500).setStartDelay(100).start()
                            }
                        }

                        override fun onAnimationCancel(animation: Animator) {}
                        override fun onAnimationRepeat(animation: Animator) {}

                    }).start()
            }

            buttonTimeMode.setOnClickListener {
                vibrate()

                if (!(isACStarted && timestampsArrayList.size == 0)) {
                    isTimeModeSelected = true
                    var isAnimationStarted = true
                    layoutTemperature.animate().alpha(0f).translationX(-1100f).setDuration(500).setStartDelay(0).start()
                    layoutModeFanSpeed.animate().alpha(0f).translationX(-1100f).setDuration(500).setStartDelay(75).start()
                    layoutModeFanSpeedButtons.animate().alpha(0f).translationX(-1100f).setDuration(500).setStartDelay(150).start()
                    layoutTurboLight.animate().alpha(0f).translationX(-1100f).setDuration(500).setStartDelay(225).start()
                    buttonTimeMode.animate().alpha(0f).translationX(-1100f).setDuration(500).setStartDelay(300).start()
                    buttonStartAC.animate().alpha(0f).translationX(-1100f).setDuration(500).setStartDelay(375)
                        .setListener(object: Animator.AnimatorListener {
                            override fun onAnimationStart(animation: Animator) {}

                            override fun onAnimationEnd(animation: Animator) {
                                if (isAnimationStarted) {
                                    isAnimationStarted = false

                                    layoutTemperature.visibility = View.GONE
                                    layoutModeFanSpeed.visibility = View.GONE
                                    layoutModeFanSpeedButtons.visibility = View.GONE
                                    layoutTurboLight.visibility = View.GONE
                                    buttonTimeMode.visibility = View.GONE
                                    buttonStartAC.visibility = View.GONE
                                    buttonTimeMode.visibility = View.GONE
                                    buttonStartAC.visibility = View.GONE

                                    var startDelay = 0L
                                    if (timestampsArrayList.size > 0) {
                                        startDelay = 100L
                                        val marginLayoutParams = layoutTime.layoutParams as MarginLayoutParams
                                        marginLayoutParams.topMargin = TypedValue.applyDimension(
                                            TypedValue.COMPLEX_UNIT_DIP,
                                            3f,
                                            requireActivity().resources.displayMetrics
                                        ).toInt()
                                        layoutTime.layoutParams = marginLayoutParams

                                        recyclerView.visibility = View.VISIBLE
                                        recyclerView.translationX = 1100f
                                        recyclerView.animate().alpha(1f).translationX(0f).setDuration(500).setStartDelay(0).start()
                                    } else {
                                        val marginLayoutParams = layoutTime.layoutParams as MarginLayoutParams
                                        marginLayoutParams.topMargin = TypedValue.applyDimension(
                                            TypedValue.COMPLEX_UNIT_DIP,
                                            45f,
                                            requireActivity().resources.displayMetrics
                                        ).toInt()
                                        layoutTime.layoutParams = marginLayoutParams
                                    }
                                    if (isTimeModeStarted) {
                                        buttonStopTimeMode.visibility = View.VISIBLE
                                        buttonStopTimeMode.translationX = 1100f
                                        buttonStopTimeMode.animate().alpha(1f).translationX(0f).setDuration(500).setStartDelay(startDelay).start()
                                    } else {
                                        layoutTime.visibility = View.VISIBLE
                                        buttonStartTimeMode.visibility = View.VISIBLE

                                        layoutTime.translationX = 1100f
                                        buttonStartTimeMode.translationX = 1100f

                                        layoutTime.animate().alpha(1f).translationX(0f).setDuration(500).setStartDelay(startDelay).start()
                                        buttonStartTimeMode.animate().alpha(1f).translationX(0f).setDuration(500).setStartDelay(startDelay + 100).start()
                                    }
                                }
                            }

                            override fun onAnimationCancel(animation: Animator) {}
                            override fun onAnimationRepeat(animation: Animator) {}

                        }).start()
                } else {
                    Toast.makeText(requireActivity(), "Вы не можете запустить режим по времени, пока запущен кондиционер!", Toast.LENGTH_LONG).show()
                }
            }

            buttonDeleteTime.setOnClickListener {
                vibrate()
                if (timestampsArrayList.size >= 1) {
                    val marginLayoutParams = layoutTime.layoutParams as MarginLayoutParams
                    marginLayoutParams.topMargin = TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP,
                        45f,
                        requireActivity().resources.displayMetrics
                    ).toInt()
                    layoutTime.layoutParams = marginLayoutParams

                    recyclerView.visibility = View.GONE
                    val timestampsArrayListSize = timestampsArrayList.size
                    timestampsArrayList.clear()
                    recyclerAdapter.notifyItemRangeRemoved(0, timestampsArrayListSize)
                    acOnOffTime = false
                    editPreferences.putString("ACTimestampsArrayList", "").apply()
                } else {
                    Toast.makeText(requireActivity(), "Добавьте хотя бы\nодно время включения\n/выключения!", Toast.LENGTH_LONG).show()
                }
            }

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
                    acOnOffTime = !acOnOffTime
                    var hour = timePicker.hour.toString()
                    var minute = timePicker.minute.toString()
                    if (hour.toInt() < 10) {
                        hour = "0$hour"
                    }
                    if (minute.toInt() < 10) {
                        minute = "0$minute"
                    }
                    timestampsArrayList.add(HeatingOrBoilerTimestamp("$hour:$minute", acOnOffTime, 0))
                    recyclerView.visibility = View.VISIBLE
                    recyclerView.alpha = 1f
                    if (timestampsArrayList.size > 1) {
                        recyclerAdapter.notifyItemInserted(timestampsArrayList.size - 1)
                    } else {
                        recyclerAdapter = TimeRecyclerAdapter(timestampsArrayList)
                        recyclerView.apply {
                            adapter = recyclerAdapter
                            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
                        }

                        val marginLayoutParams = layoutTime.layoutParams as MarginLayoutParams
                        marginLayoutParams.topMargin = TypedValue.applyDimension(
                            TypedValue.COMPLEX_UNIT_DIP,
                            3f,
                            requireActivity().resources.displayMetrics
                        ).toInt()
                        layoutTime.layoutParams = marginLayoutParams
                    }
                }
                timePicker.addOnNegativeButtonClickListener {
                    vibrate()
                }
                timePicker.show(requireActivity().supportFragmentManager, "timePicker")
            }

            buttonStartTimeMode.setOnClickListener {
                vibrate()
                if (isNetworkConnected(requireActivity())) {
                    var acOnOffTimeNodeValue = ""
                    timestampsArrayList.forEach {
                        acOnOffTimeNodeValue += it.time.removeRange(2, 3)
                    }

                    isTimeModeStarted = true
                    editPreferences.putBoolean("isACTimeModeStarted", true)
                    editPreferences.putString("ACTimestampsArrayList", gson.toJson(timestampsArrayList)).apply()
                    realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("SmartRemotes").child("acOnOffTime").setValue(acOnOffTimeNodeValue)

                    var isAnimationStarted = true
                    layoutTime.animate().alpha(0f).translationX(-1100f).setDuration(500).setStartDelay(0).start()
                    buttonStartTimeMode.animate().alpha(0f).translationX(-1100f).setDuration(500).setStartDelay(100)
                        .setListener(object: Animator.AnimatorListener {
                            override fun onAnimationStart(animation: Animator) {}

                            override fun onAnimationEnd(animation: Animator) {
                                if (isAnimationStarted) {
                                    isAnimationStarted = false

                                    layoutTime.visibility = View.GONE
                                    buttonStartTimeMode.visibility = View.GONE
                                    buttonStopTimeMode.visibility = View.VISIBLE

                                    buttonStopTimeMode.translationX = 1100f
                                    buttonStopTimeMode.animate().alpha(1f).translationX(0f).setDuration(500).setStartDelay(0).start()
                                }
                            }

                            override fun onAnimationCancel(animation: Animator) {}
                            override fun onAnimationRepeat(animation: Animator) {}
                        })
                } else {
                    Toast.makeText(requireActivity(), "Нет подключения\nк Интернету!", Toast.LENGTH_LONG).show()
                }
            }

            buttonStopTimeMode.setOnClickListener {
                vibrate()
                if (isNetworkConnected(requireActivity())) {
                    isTimeModeStarted = false
                    editPreferences.putBoolean("isACTimeModeStarted", false).apply()
                    realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("SmartRemotes").child("acOnOffTime").setValue(" ")

                    var isAnimationStarted = true
                    buttonStopTimeMode.animate().alpha(0f).translationX(-1100f).setDuration(500).setStartDelay(0)
                        .setListener(object: Animator.AnimatorListener {
                            override fun onAnimationStart(animation: Animator) {}

                            override fun onAnimationEnd(animation: Animator) {
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

                            override fun onAnimationCancel(animation: Animator) {}
                            override fun onAnimationRepeat(animation: Animator) {}
                        })
                } else {
                    Toast.makeText(requireActivity(), "Нет подключения\nк Интернету!", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun sendRemoteButton(remoteButton: String) {
        if (isNetworkConnected(requireActivity())) {
            val acSettings = "$temperature $acMode$fanSpeed${booleanToString(isTurboModeEnabled)}${booleanToString(isLightEnabled)}${booleanToString(isACStarted)}"
            editPreferences.putString("ACSettings", acSettings).apply()
            realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("SmartRemotes").child("acRemote")
                .setValue("$remoteButton $acSettings")
        } else {
            Toast.makeText(requireActivity(), "Нет подключения\nк Интернету!", Toast.LENGTH_LONG).show()
        }
    }

    private fun setACMode() {
        with(binding) {
            when (acMode) {
                0 -> {
                    imageACMode.setImageResource(R.drawable.ic_cold_mode)
                    textACMode.text = getString(R.string.cold_mode_text)
                }
                1 -> {
                    imageACMode.setImageResource(R.drawable.ic_dry_mode)
                    textACMode.text = getString(R.string.dry_mode_text)
                }
                2 -> {
                    imageACMode.setImageResource(R.drawable.ic_fan_mode)
                    textACMode.text = getString(R.string.fan_mode_text)
                }
                3 -> {
                    imageACMode.setImageResource(R.drawable.ic_light_on)
                    textACMode.text = getString(R.string.heat_mode_text)
                }
                4 -> {
                    imageACMode.setImageResource(R.drawable.ic_auto)
                    textACMode.text = getString(R.string.auto_text)
                }
            }
        }
    }

    private fun setFanSpeed() {
        with(binding) {
            when (fanSpeed) {
                0 -> {
                    imageFanSpeed.setImageResource(R.drawable.ic_min_fan_speed)
                    textFanSpeed.text = getString(R.string.min_fan_speed_text)
                }
                1 -> {
                    imageFanSpeed.setImageResource(R.drawable.ic_medium_fan_speed)
                    textFanSpeed.text = getString(R.string.medium_fan_speed_text)
                }
                2 -> {
                    imageFanSpeed.setImageResource(R.drawable.ic_max_fan_speed)
                    textFanSpeed.text = getString(R.string.max_fan_speed_text)
                }
                3 -> {
                    imageFanSpeed.setImageResource(R.drawable.ic_auto)
                    textFanSpeed.text = getString(R.string.auto_text)
                }
            }
        }
    }

    private val sharedPreferencesChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            with(binding) {
                if (key == "CloseTimeMode") {
                    if (sharedPreferences.getBoolean("CloseTimeMode", false)) {
                        isTimeModeSelected = false
                        editPreferences.putBoolean("CloseTimeMode", false).apply()
                        var isAnimationStarted = true
                        recyclerView.animate().alpha(0f).translationX(-1100f).setDuration(500).setStartDelay(0).start()
                        layoutTime.animate().alpha(0f).translationX(-1100f).setDuration(500).setStartDelay(100).start()
                        if (isTimeModeStarted) {
                            buttonStopTimeMode.animate().alpha(0f).translationX(-1100f).setDuration(500).setStartDelay(200)
                                .setListener(object: Animator.AnimatorListener {
                                    override fun onAnimationStart(animation: Animator) {}

                                    override fun onAnimationEnd(animation: Animator) {
                                        if (isAnimationStarted) {
                                            isAnimationStarted = false

                                            closeTimeMode()
                                        }
                                    }

                                    override fun onAnimationCancel(animation: Animator) {}
                                    override fun onAnimationRepeat(animation: Animator) {}
                                }).start()
                        } else {
                            buttonStartTimeMode.animate().alpha(0f).translationX(-1100f).setDuration(500).setStartDelay(200)
                                .setListener(object: Animator.AnimatorListener {
                                    override fun onAnimationStart(animation: Animator) {}

                                    override fun onAnimationEnd(animation: Animator) {
                                        if (isAnimationStarted) {
                                            isAnimationStarted = false

                                            closeTimeMode()
                                        }
                                    }

                                    override fun onAnimationCancel(animation: Animator) {}
                                    override fun onAnimationRepeat(animation: Animator) {}
                                }).start()
                        }
                    }
                } else if (key == "OpenRemoteSettingsMode") {
                    if (sharedPreferences.getBoolean("OpenRemoteSettingsMode", false)) {
                        editPreferences.putBoolean("OpenRemoteSettingsMode", false).apply()
                        var isAnimationStarted = true
                        layoutTemperature.animate().alpha(0f).translationX(-1100f).setDuration(500).setStartDelay(0).start()
                        layoutModeFanSpeed.animate().alpha(0f).translationX(-1100f).setDuration(500).setStartDelay(75).start()
                        layoutModeFanSpeedButtons.animate().alpha(0f).translationX(-1100f).setDuration(500).setStartDelay(150).start()
                        layoutTurboLight.animate().alpha(0f).translationX(-1100f).setDuration(500).setStartDelay(225).start()
                        buttonTimeMode.animate().alpha(0f).translationX(-1100f).setDuration(500).setStartDelay(300).start()
                        buttonStartAC.animate().alpha(0f).translationX(-1100f).setDuration(500).setStartDelay(375)
                            .setListener(object: Animator.AnimatorListener {
                                override fun onAnimationStart(animation: Animator) {}

                                override fun onAnimationEnd(animation: Animator) {
                                    if (isAnimationStarted) {
                                        isAnimationStarted = false

                                        scrollView.visibility = View.GONE

                                        layoutConfigureACRemote.visibility = View.VISIBLE
                                        layoutConfigureACRemote.animate().alpha(1f).translationX(0f).setDuration(500).setStartDelay(0).start()

                                        if (!remoteSettingsMode) {
                                            remoteSettingsMode = true
                                            handler = Handler(Looper.getMainLooper())
                                            sendRequestRunnable.run()
                                        }
                                    }
                                }

                                override fun onAnimationCancel(animation: Animator) {}
                                override fun onAnimationRepeat(animation: Animator) {}

                            }).start()

                    }
                } else if (key == "CloseRemoteSettingsMode") {
                    if (sharedPreferences.getBoolean("CloseRemoteSettingsMode", false)) {
                        editPreferences.putBoolean("CloseRemoteSettingsMode", false).apply()
                        var isAnimationStarted = true
                        layoutConfigureACRemote.animate().alpha(0f).translationX(0f).setDuration(500).setStartDelay(0)
                            .setListener(object: Animator.AnimatorListener {
                                override fun onAnimationStart(animation: Animator) {}

                                override fun onAnimationEnd(animation: Animator) {
                                    if (isAnimationStarted) {
                                        isAnimationStarted = false
                                        remoteSettingsMode = false

                                        layoutConfigureACRemote.visibility = View.GONE
                                        scrollView.visibility = View.VISIBLE

                                        layoutTemperature.translationX = 1100f
                                        layoutModeFanSpeed.translationX = 1100f
                                        layoutModeFanSpeedButtons.translationX = 1100f
                                        layoutTurboLight.translationX = 1100f
                                        buttonTimeMode.translationX = 1100f
                                        buttonStartAC.translationX = 1100f

                                        layoutTemperature.animate().alpha(1f).translationX(0f).setDuration(500).setStartDelay(0).start()
                                        layoutModeFanSpeed.animate().alpha(1f).translationX(0f).setDuration(500).setStartDelay(75).start()
                                        layoutModeFanSpeedButtons.animate().alpha(1f).translationX(0f).setDuration(500).setStartDelay(150).start()
                                        layoutTurboLight.animate().alpha(1f).translationX(0f).setDuration(500).setStartDelay(225).start()
                                        buttonTimeMode.animate().alpha(1f).translationX(0f).setDuration(500).setStartDelay(300).start()
                                        buttonStartAC.animate().alpha(1f).translationX(0f).setDuration(500).setStartDelay(375).start()
                                    }
                                }

                                override fun onAnimationCancel(animation: Animator) {}
                                override fun onAnimationRepeat(animation: Animator) {}

                            }).start()
                    }
                }
            }
        }

    private fun closeTimeMode() {
        with(binding) {
            recyclerView.visibility = View.GONE
            layoutTime.visibility = View.GONE
            buttonStartTimeMode.visibility = View.GONE
            buttonStopTimeMode.visibility = View.GONE

            layoutTemperature.visibility = View.VISIBLE
            layoutModeFanSpeed.visibility = View.VISIBLE
            layoutModeFanSpeedButtons.visibility = View.VISIBLE
            layoutTurboLight.visibility = View.VISIBLE
            buttonTimeMode.visibility = View.VISIBLE

            layoutTemperature.translationX = 1100f
            layoutModeFanSpeed.translationX = 1100f
            layoutModeFanSpeedButtons.translationX = 1100f
            layoutTurboLight.translationX = 1100f
            buttonTimeMode.translationX = 1100f

            layoutTemperature.animate().alpha(1f).translationX(0f).setDuration(500).setStartDelay(0).start()
            layoutModeFanSpeed.animate().alpha(1f).translationX(0f).setDuration(500).setStartDelay(75).start()
            layoutModeFanSpeedButtons.animate().alpha(1f).translationX(0f).setDuration(500).setStartDelay(150).start()
            layoutTurboLight.animate().alpha(1f).translationX(0f).setDuration(500).setStartDelay(225).start()
            buttonTimeMode.animate().alpha(1f).translationX(0f).setDuration(500).setStartDelay(300).start()
            if (!isTimeModeStarted) {
                if (isACStarted) {
                    buttonStopAC.visibility = View.VISIBLE
                    buttonStopAC.translationX = 1100f
                    buttonStopAC.animate().alpha(1f).translationX(0f).setDuration(500).setStartDelay(375).start()
                } else {
                    buttonStartAC.visibility = View.VISIBLE
                    buttonStartAC.translationX = 1100f
                    buttonStartAC.animate().alpha(1f).translationX(0f).setDuration(500).setStartDelay(375).start()
                }
            }
        }
    }

    private val sendRequestRunnable : Runnable = object : Runnable {
        override fun run() {
            if (remoteSettingsMode) {
                val isButtonConfiguredRequest = StringRequest(
                    Request.Method.POST,
                    "http://192.168.4.1/ac_remote_configured",
                    {
                        val result = it.first()
                        if (result == '1') {
                            binding.textConfigureACRemote.text = getString(R.string.ac_remote_configured_text)
                        } else if (result == '2') {
                            binding.textConfigureACRemote.text = getString(R.string.ac_remote_not_supported_text)
                        }
                    },
                    {}
                )
                Volley.newRequestQueue(requireActivity()).add(isButtonConfiguredRequest)
                handler.postDelayed(this, 500)
            }
        }
    }

    private fun booleanToString(variable: Boolean): String {
        return if (variable) {
            "1"
        } else {
            "0"
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        sharedPreferences.unregisterOnSharedPreferenceChangeListener(sharedPreferencesChangeListener)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentAcRemoteBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    @Suppress("DEPRECATION")
    private fun vibrate() {
        if (MainActivity.vibrator.hasVibrator()) {
            if (MainActivity.isHapticFeedbackEnabled == "1") {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    binding.buttonStartAC.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING)
                } else {
                    binding.buttonStartAC.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING + HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
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