package com.arduinoworld.smarthome

import android.animation.Animator
import android.annotation.SuppressLint
import android.media.MediaPlayer
import android.os.*
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.NumberPicker
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.arduinoworld.smarthome.MainActivity.Companion.editPreferences
import com.arduinoworld.smarthome.MainActivity.Companion.firebaseAuth
import com.arduinoworld.smarthome.MainActivity.Companion.isHapticFeedbackEnabled
import com.arduinoworld.smarthome.MainActivity.Companion.isNetworkConnected
import com.arduinoworld.smarthome.MainActivity.Companion.realtimeDatabase
import com.arduinoworld.smarthome.MainActivity.Companion.sharedPreferences
import com.arduinoworld.smarthome.MainActivity.Companion.vibrator
import com.arduinoworld.smarthome.databinding.FragmentHeatingTimerBinding
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.*


class HeatingTimerFragment : Fragment() {
    private lateinit var binding: FragmentHeatingTimerBinding
    private lateinit var countDownTimer: CountDownTimer
    private lateinit var dateFormat: SimpleDateFormat
    private lateinit var mediaPlayer: MediaPlayer
    private lateinit var responseValueEventListener: ValueEventListener
    private var isTimerStarted = false
    private var timerDays = 0
    private var heatingElements = 1
    private var maxHeatingElements = 2
    private var response = false

    @SuppressLint("DiscouragedPrivateApi")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        with(binding) {
            mediaPlayer = MediaPlayer.create(requireActivity(), R.raw.notification)
            dateFormat = SimpleDateFormat("dd/M/yyyy hh:mm:ss", Locale.US)

            val isOverCurrentProtectionEnabled = sharedPreferences.getBoolean("isOverCurrentProtectionEnabled", true)
            heatingElements = sharedPreferences.getInt("TimerHeatingElements", 1)
            maxHeatingElements = sharedPreferences.getString("MaxHeatingElements", "2")!!.toInt()
            var isHeatingStarted = sharedPreferences.getBoolean("isHeatingStarted", false)
            isTimerStarted = sharedPreferences.getBoolean("isHeatingTimerStarted", false)

            numberPickerDays.minValue = 0
            numberPickerHours.minValue = 0
            numberPickerMinutes.minValue = 0

            numberPickerDays.maxValue = 7
            numberPickerHours.maxValue = 23
            numberPickerMinutes.maxValue = 59

            val field = NumberPicker::class.java.getDeclaredField("mInputText")
            field.isAccessible = true
            (field.get(numberPickerDays) as EditText).filters = arrayOfNulls(0)

            numberPickerDays.setFormatter { value ->
                String.format("%02d", value)
            }
            numberPickerHours.setFormatter { value ->
                String.format("%02d", value)
            }
            numberPickerMinutes.setFormatter { value ->
                String.format("%02d", value)
            }

            timerDays = sharedPreferences.getInt("HeatingNumberPickerDays", 0)
            numberPickerDays.value = sharedPreferences.getInt("HeatingNumberPickerDays", 0)
            numberPickerHours.value = sharedPreferences.getInt("HeatingNumberPickerHours", 0)
            numberPickerMinutes.value = sharedPreferences.getInt("HeatingNumberPickerMinutes", 0)

            if (maxHeatingElements > 1) {
                textHeatingElements.text = getString(R.string.heating_elements_text, heatingElements)
                if (heatingElements == maxHeatingElements) {
                    buttonIncrease.setImageResource(R.drawable.ic_increase_disabled)
                } else if (heatingElements == 1) {
                    buttonDecrease.setImageResource(R.drawable.ic_decrease_disabled)
                }

                buttonIncrease.setOnClickListener {
                    vibrate()
                    if (!isTimerStarted && !isHeatingStarted) {
                        if (heatingElements < maxHeatingElements) {
                            heatingElements += 1
                            if (heatingElements == maxHeatingElements) {
                                buttonIncrease.setImageResource(R.drawable.ic_increase_disabled)
                            }
                            buttonDecrease.setImageResource(R.drawable.ic_decrease)
                            textHeatingElements.text = getString(R.string.heating_elements_text, heatingElements)
                        } else {
                            Toast.makeText(requireActivity(), "Вы установили максимальное количество тэнов!", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        if (isTimerStarted) {
                            Toast.makeText(requireActivity(), "Вы не можете изменять количество тэнов, пока\nзапущен таймер!", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(requireActivity(), "Вы не можете изменять количество тэнов, пока\nзапущен котёл!", Toast.LENGTH_LONG).show()
                        }
                    }
                }

                buttonDecrease.setOnClickListener {
                    vibrate()
                    if (!isTimerStarted && !isHeatingStarted) {
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
                    } else {
                        if (isTimerStarted) {
                            Toast.makeText(requireActivity(), "Вы не можете изменять количество тэнов, пока\nзапущен таймер!", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(requireActivity(), "Вы не можете изменять количество тэнов, пока\nзапущен котёл!", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } else {
                layoutHeatingElements.visibility = View.GONE
            }

            if (isTimerStarted) {
                val fragmentDestroyTimeLeft = sharedPreferences.getLong("HeatingTimerTimeLeft", 0)
                val fragmentDestroyTime = dateFormat.parse(sharedPreferences.getString("HeatingFragmentDestroyTime", "").toString())!!
                val fragmentOpenTime = dateFormat.parse(dateFormat.format(Calendar.getInstance().time))!!

                val timeLeft = fragmentDestroyTimeLeft - (kotlin.math.abs(fragmentOpenTime.time - fragmentDestroyTime.time))
                if (timeLeft <= 0) {
                    isTimerStarted = false
                    editPreferences.putBoolean("isHeatingTimerStarted", false).apply()
                } else {
                    if (timerDays >= 1) {
                        startTimer(timeLeft, 60000)
                    } else {
                        startTimer(timeLeft, 1000)
                    }

                    buttonIncrease.setImageResource(R.drawable.ic_increase_disabled)
                    buttonDecrease.setImageResource(R.drawable.ic_decrease_disabled)

                    layoutNumberPickers.visibility = View.GONE
                    buttonStartTimer.visibility = View.GONE
                    buttonStartHeating.visibility = View.GONE

                    layoutTimeLeft.visibility = View.VISIBLE
                    buttonStopTimer.visibility = View.VISIBLE

                    layoutTimeLeft.alpha = 1f
                    buttonStopTimer.alpha = 1f
                }
            } else {
                if (isHeatingStarted) {
                    buttonStartTimer.visibility = View.GONE
                    buttonStartHeating.visibility = View.GONE
                    buttonStopHeating.visibility = View.VISIBLE
                    buttonStopHeating.alpha = 1f

                    buttonIncrease.setImageResource(R.drawable.ic_increase_disabled)
                    buttonDecrease.setImageResource(R.drawable.ic_decrease_disabled)
                }
            }

            realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("HeatingAndBoiler")
                .addListenerForSingleValueEvent(object: ValueEventListener {
                    @SuppressLint("NotifyDataSetChanged")
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (snapshot.child("heatingTimerTime").getValue(Int::class.java)!! == 0 && isTimerStarted) {
                            isTimerStarted = false
                            editPreferences.putBoolean("isHeatingTimerStarted", false).apply()

                            layoutTimeLeft.visibility = View.GONE
                            buttonStopTimer.visibility = View.GONE

                            layoutNumberPickers.visibility = View.VISIBLE
                            buttonStartTimer.visibility = View.VISIBLE
                            buttonStartHeating.visibility = View.VISIBLE
                        }
                        if (snapshot.child("heatingStarted").getValue(Boolean::class.java)!! && !isHeatingStarted) {
                            isHeatingStarted = true
                            editPreferences.putBoolean("isHeatingStarted", true).apply()

                            buttonStartTimer.visibility = View.GONE
                            buttonStartHeating.visibility = View.GONE
                            buttonStopHeating.visibility = View.VISIBLE
                            buttonStopHeating.alpha = 1f

                            buttonIncrease.setImageResource(R.drawable.ic_increase_disabled)
                            buttonDecrease.setImageResource(R.drawable.ic_decrease_disabled)
                        } else if (!snapshot.child("heatingStarted").getValue(Boolean::class.java)!! && isHeatingStarted) {
                            isHeatingStarted = false
                            editPreferences.putBoolean("isHeatingStarted", false).apply()

                            buttonStopHeating.visibility = View.GONE
                            buttonStartTimer.visibility = View.VISIBLE
                            buttonStartHeating.visibility = View.VISIBLE

                            buttonIncrease.setImageResource(R.drawable.ic_increase)
                            buttonDecrease.setImageResource(R.drawable.ic_decrease)
                            if (maxHeatingElements > 1) {
                                heatingElements = snapshot.child("heatingElements").getValue(Int::class.java)!!
                                editPreferences.putInt("TimerHeatingElements", heatingElements).apply()

                                if (heatingElements == maxHeatingElements) {
                                    buttonDecrease.setImageResource(R.drawable.ic_decrease)
                                    buttonIncrease.setImageResource(R.drawable.ic_increase_disabled)
                                } else if (heatingElements == 1) {
                                    buttonIncrease.setImageResource(R.drawable.ic_increase)
                                    buttonDecrease.setImageResource(R.drawable.ic_decrease_disabled)
                                }
                                textHeatingElements.text = getString(R.string.heating_elements_text, heatingElements)
                            }
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {}

                })

            buttonStartTimer.setOnClickListener {
                vibrate()
                if (isNetworkConnected(requireActivity())) {
                    if (!sharedPreferences.getBoolean("isHeatingTimeModeStarted", false) && !sharedPreferences.getBoolean("isHeatingTemperatureModeStarted", false)) {
                        if (!(isOverCurrentProtectionEnabled && heatingElements == maxHeatingElements && maxHeatingElements > 1 &&
                                    (sharedPreferences.getBoolean("isBoilerStarted", false) || sharedPreferences.getBoolean("isBoilerTimerStarted", false) || sharedPreferences.getBoolean("isBoilerTimeModeStarted", false)))) {
                            if (!(numberPickerDays.value == 0 && numberPickerHours.value == 0 && numberPickerMinutes.value == 0)) {
                                realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("HeatingAndBoiler").child("heatingElements").setValue(heatingElements)
                                Handler(Looper.getMainLooper()).postDelayed({
                                    realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("HeatingAndBoiler").child("heatingTimerTime").setValue(
                                        numberPickerDays.value * 24 * 60 + numberPickerHours.value * 60 + numberPickerMinutes.value)
                                }, 250)

                                textResponse.visibility = View.VISIBLE
                                textResponse.animate().alpha(1f).setStartDelay(0L).setDuration(500).start()
                                textResponse.text = getString(R.string.waiting_for_response_text)

                                Handler(Looper.getMainLooper()).postDelayed({
                                    if (!response) {
                                        textResponse.text = getString(R.string.response_not_received_text)

                                        realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("HeatingAndBoiler").child("response")
                                            .removeEventListener(responseValueEventListener)
                                        realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("HeatingAndBoiler").child("heatingTimerTime").setValue(0)
                                    }
                                }, 5000)

                                responseValueEventListener = object: ValueEventListener {
                                    override fun onDataChange(snapshot: DataSnapshot) {
                                        if (snapshot.getValue(Boolean::class.java) != null) {
                                            response = snapshot.getValue(Boolean::class.java)!!
                                            if (response) {
                                                binding.textResponse.text = getString(R.string.response_received_text)
                                                Handler(Looper.getMainLooper()).postDelayed({
                                                    textResponse.animate().alpha(0f).setStartDelay(0).setDuration(500).start()
                                                }, 3000)
                                                realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("HeatingAndBoiler").child("response")
                                                    .removeEventListener(responseValueEventListener)

                                                isTimerStarted = true
                                                timerDays = numberPickerDays.value

                                                if (timerDays >= 1) {
                                                    startTimer((numberPickerDays.value.toLong() * 24 * 60 + numberPickerHours.value.toLong() * 60 + numberPickerMinutes.value) * 60 * 1000, 60000)
                                                } else {
                                                    startTimer((numberPickerHours.value.toLong() * 60 + numberPickerMinutes.value) * 60 * 1000, 1000)
                                                }

                                                editPreferences.putInt("HeatingNumberPickerDays", numberPickerDays.value)
                                                editPreferences.putInt("HeatingNumberPickerHours", numberPickerHours.value)
                                                editPreferences.putInt("HeatingNumberPickerMinutes", numberPickerMinutes.value)
                                                editPreferences.putInt("TimerHeatingElements", heatingElements)
                                                editPreferences.putBoolean("isHeatingTimerStarted", true).apply()

                                                buttonIncrease.setImageResource(R.drawable.ic_increase_disabled)
                                                buttonDecrease.setImageResource(R.drawable.ic_decrease_disabled)

                                                var isAnimationStarted = true
                                                layoutNumberPickers.animate().alpha(0f).setDuration(500).setStartDelay(0).start()
                                                buttonStartTimer.animate().alpha(0f).translationX(-1100f).setDuration(500).setStartDelay(0).start()
                                                buttonStartHeating.animate().alpha(0f).translationX(-1100f).setDuration(500).setStartDelay(100)
                                                    .setListener(object: Animator.AnimatorListener {
                                                        override fun onAnimationStart(animation: Animator) {}

                                                        override fun onAnimationEnd(animation: Animator) {
                                                            if (isAnimationStarted) {
                                                                isAnimationStarted = false

                                                                layoutNumberPickers.visibility = View.GONE
                                                                buttonStartTimer.visibility = View.GONE
                                                                buttonStartHeating.visibility = View.GONE

                                                                layoutTimeLeft.visibility = View.VISIBLE
                                                                buttonStopTimer.visibility = View.VISIBLE

                                                                layoutTimeLeft.translationX = 1100f
                                                                buttonStopTimer.translationX = 1100f

                                                                layoutTimeLeft.animate().alpha(1f).translationX(0f).setDuration(500).setStartDelay(0).start()
                                                                buttonStopTimer.animate().alpha(1f).translationX(0f).setDuration(500).setStartDelay(100).start()
                                                            }
                                                        }

                                                        override fun onAnimationCancel(animation: Animator) {}
                                                        override fun onAnimationRepeat(animation: Animator) {}

                                                    }).start()
                                            }
                                        }
                                    }

                                    override fun onCancelled(error: DatabaseError) {}

                                }

                                realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("HeatingAndBoiler").child("response").setValue(false)
                                realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("HeatingAndBoiler").child("response")
                                    .addValueEventListener(responseValueEventListener)
                            } else {
                                Toast.makeText(requireActivity(), "Установите время\nработы таймера!", Toast.LENGTH_LONG).show()
                            }
                        } else {
                            if (sharedPreferences.getBoolean("isBoilerStarted", false)) {
                                showOverCurrentProtectionAlertDialog("Так как сейчас запущен бойлер, для избежания перегрузки электросети по току, вы не можете запустить таймер котла. Вы можете отключить эту функцию в настройках.")
                            } else if (sharedPreferences.getBoolean("isBoilerTimerStarted", false)) {
                                showOverCurrentProtectionAlertDialog("Так как сейчас запущен таймер бойлера, для избежания перегрузки электросети по току, вы не можете запустить таймер котла. Вы можете отключить эту функцию в настройках.")
                            } else if (sharedPreferences.getBoolean("isBoilerTimeModeStarted", false)) {
                                showOverCurrentProtectionAlertDialog("Так как сейчас запущен режим по времени для бойлера, для избежания перегрузки электросети по току, вы не можете запустить таймер котла. Вы можете отключить эту функцию в настройках.")
                            }
                        }
                    } else {
                        if (isHeatingStarted) {
                            Toast.makeText(requireActivity(), "Вы не можете запустить таймер, пока запущен режим котёл!", Toast.LENGTH_LONG).show()
                        } else if (sharedPreferences.getBoolean("isHeatingTimeModeStarted",  false)) {
                            Toast.makeText(requireActivity(), "Вы не можете запустить таймер, пока запущен режим\nпо времени!", Toast.LENGTH_LONG).show()
                        } else if (sharedPreferences.getBoolean("isHeatingTemperatureModeStarted", false)) {
                            Toast.makeText(requireActivity(), "Вы не можете запустить таймер, пока запущен режим\nпо температуре!", Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    Toast.makeText(requireActivity(), "Нет подключения\nк Интернету!", Toast.LENGTH_LONG).show()
                }
            }

            buttonStopTimer.setOnClickListener {
                vibrate()
                if (isNetworkConnected(requireActivity())) {
                    realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("HeatingAndBoiler").child("heatingTimerTime").setValue(0)

                    textResponse.visibility = View.VISIBLE
                    textResponse.animate().alpha(1f).setStartDelay(0L).setDuration(500).start()
                    textResponse.text = getString(R.string.waiting_for_response_text)

                    Handler(Looper.getMainLooper()).postDelayed({
                        if (!response) {
                            textResponse.text = getString(R.string.response_not_received_text)

                            realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("HeatingAndBoiler").child("response")
                                .removeEventListener(responseValueEventListener)
                            realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("HeatingAndBoiler").child("heatingTimerTime").setValue(
                                numberPickerDays.value * 24 * 60 + numberPickerHours.value * 60 + numberPickerMinutes.value)
                        }
                    }, 5000)

                    responseValueEventListener = object: ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            if (snapshot.getValue(Boolean::class.java) != null) {
                                response = snapshot.getValue(Boolean::class.java)!!
                                if (response) {
                                    binding.textResponse.text = getString(R.string.response_received_text)
                                    Handler(Looper.getMainLooper()).postDelayed({
                                        textResponse.animate().alpha(0f).setStartDelay(0).setDuration(500).start()
                                    }, 3000)
                                    realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("HeatingAndBoiler").child("response")
                                        .removeEventListener(responseValueEventListener)

                                    isTimerStarted = false
                                    countDownTimer.cancel()
                                    editPreferences.putBoolean("isHeatingTimerStarted", false).apply()

                                    buttonIncrease.setImageResource(R.drawable.ic_increase)
                                    buttonDecrease.setImageResource(R.drawable.ic_decrease)
                                    if (heatingElements == maxHeatingElements) {
                                        buttonDecrease.setImageResource(R.drawable.ic_decrease)
                                        buttonIncrease.setImageResource(R.drawable.ic_increase_disabled)
                                    } else if (heatingElements == 1) {
                                        buttonIncrease.setImageResource(R.drawable.ic_increase)
                                        buttonDecrease.setImageResource(R.drawable.ic_decrease_disabled)
                                    }

                                    var isAnimationStarted = true
                                    layoutTimeLeft.animate().alpha(0f).translationX(-1100f).setDuration(500).setStartDelay(0).start()
                                    buttonStopTimer.animate().alpha(0f).translationX(-1100f).setDuration(500).setStartDelay(100)
                                        .setListener(object: Animator.AnimatorListener {
                                            override fun onAnimationStart(animation: Animator) {}

                                            override fun onAnimationEnd(animation: Animator) {
                                                if (isAnimationStarted) {
                                                    isAnimationStarted = false

                                                    layoutTimeLeft.visibility = View.GONE
                                                    buttonStopTimer.visibility = View.GONE

                                                    layoutNumberPickers.visibility = View.VISIBLE
                                                    buttonStartTimer.visibility = View.VISIBLE
                                                    buttonStartHeating.visibility = View.VISIBLE

                                                    buttonStartTimer.translationX = 1100f
                                                    buttonStartHeating.translationX = 1100f

                                                    layoutNumberPickers.alpha = 0f
                                                    buttonStartTimer.alpha = 0f
                                                    buttonStartHeating.alpha = 0f

                                                    layoutNumberPickers.animate().alpha(1f).setDuration(500).setStartDelay(0).start()
                                                    buttonStartTimer.animate().alpha(1f).translationX(0f).setDuration(500).setStartDelay(0).start()
                                                    buttonStartHeating.animate().alpha(1f).translationX(0f).setDuration(500).setStartDelay(100).start()
                                                }
                                            }

                                            override fun onAnimationCancel(animation: Animator) {}
                                            override fun onAnimationRepeat(animation: Animator) {}

                                        }).start()
                                }
                            }
                        }

                        override fun onCancelled(error: DatabaseError) {}

                    }

                    realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("HeatingAndBoiler").child("response").setValue(false)
                    realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("HeatingAndBoiler").child("response")
                        .addValueEventListener(responseValueEventListener)
                } else {
                    Toast.makeText(requireActivity(), "Нет подключения\nк Интернету!", Toast.LENGTH_LONG).show()
                }
            }

            buttonStartHeating.setOnClickListener {
                vibrate()
                if (isNetworkConnected(requireActivity())) {
                    if (!sharedPreferences.getBoolean("isHeatingTimeModeStarted", false) && !sharedPreferences.getBoolean("isHeatingTemperatureModeStarted", false)) {
                        if (!(isOverCurrentProtectionEnabled && heatingElements == maxHeatingElements && maxHeatingElements > 1 && (sharedPreferences.getBoolean("isBoilerStarted", false) || sharedPreferences.getBoolean("isBoilerTimerStarted", false) || sharedPreferences.getBoolean("isBoilerTimeModeStarted", false)))) {
                            realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("HeatingAndBoiler").child("heatingElements").setValue(heatingElements)
                            Handler(Looper.getMainLooper()).postDelayed({
                                realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("HeatingAndBoiler").child("heatingStarted").setValue(true)
                            }, 250)

                            textResponse.visibility = View.VISIBLE
                            textResponse.animate().alpha(1f).setStartDelay(0L).setDuration(500).start()
                            textResponse.text = getString(R.string.waiting_for_response_text)

                            Handler(Looper.getMainLooper()).postDelayed({
                                if (!response) {
                                    textResponse.text = getString(R.string.response_not_received_text)

                                    realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("HeatingAndBoiler").child("response")
                                        .removeEventListener(responseValueEventListener)
                                    realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("HeatingAndBoiler").child("heatingStarted").setValue(false)
                                }
                            }, 5000)

                            responseValueEventListener = object: ValueEventListener {
                                override fun onDataChange(snapshot: DataSnapshot) {
                                    if (snapshot.getValue(Boolean::class.java) != null) {
                                        response = snapshot.getValue(Boolean::class.java)!!
                                        if (response) {
                                            binding.textResponse.text = getString(R.string.response_received_text)
                                            Handler(Looper.getMainLooper()).postDelayed({
                                                textResponse.animate().alpha(0f).setStartDelay(0).setDuration(500).start()
                                            }, 3000)
                                            realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("HeatingAndBoiler").child("response")
                                                .removeEventListener(responseValueEventListener)

                                            isHeatingStarted = true
                                            editPreferences.putBoolean("isHeatingStarted", true).apply()

                                            buttonIncrease.setImageResource(R.drawable.ic_increase_disabled)
                                            buttonDecrease.setImageResource(R.drawable.ic_decrease_disabled)

                                            var isAnimationStarted = true
                                            buttonStartTimer.animate().alpha(0f).translationX(-1100f).setDuration(500).setStartDelay(0).start()
                                            buttonStartHeating.animate().alpha(0f).translationX(-1100f).setDuration(500).setStartDelay(100)
                                                .setListener(object: Animator.AnimatorListener {
                                                    override fun onAnimationStart(animation: Animator) {}

                                                    override fun onAnimationEnd(animation: Animator) {
                                                        if (isAnimationStarted) {
                                                            isAnimationStarted = false

                                                            buttonStartTimer.visibility = View.GONE
                                                            buttonStartHeating.visibility = View.GONE
                                                            buttonStopHeating.visibility = View.VISIBLE

                                                            buttonStopHeating.translationX = 1100f
                                                            buttonStopHeating.animate().alpha(1f).translationX(0f).setDuration(500).setStartDelay(0).start()
                                                        }
                                                    }

                                                    override fun onAnimationCancel(animation: Animator) {}
                                                    override fun onAnimationRepeat(animation: Animator) {}

                                                }).start()
                                        }
                                    }
                                }

                                override fun onCancelled(error: DatabaseError) {}

                            }

                            realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("HeatingAndBoiler").child("response").setValue(false)
                            realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("HeatingAndBoiler").child("response")
                                .addValueEventListener(responseValueEventListener)
                        } else {
                            if (sharedPreferences.getBoolean("isBoilerStarted", false)) {
                                showOverCurrentProtectionAlertDialog("Так как сейчас запущен бойлер, для избежания перегрузки электросети по току, вы не можете запустить котёл. Вы можете отключить эту функцию в настройках.")
                            } else if (sharedPreferences.getBoolean("isBoilerTimerStarted", false)) {
                                showOverCurrentProtectionAlertDialog("Так как сейчас запущен таймер бойлера, для избежания перегрузки электросети по току, вы не можете запустить котёл. Вы можете отключить эту функцию в настройках.")
                            } else if (sharedPreferences.getBoolean("isBoilerTimeModeStarted", false)) {
                                showOverCurrentProtectionAlertDialog("Так как сейчас запущен режим по времени для бойлера, для избежания перегрузки электросети по току, вы не можете запустить котёл. Вы можете отключить эту функцию в настройках.")
                            }
                        }
                    } else {
                        if (sharedPreferences.getBoolean("isHeatingTimeModeStarted",  false)) {
                            Toast.makeText(requireActivity(), "Вы не можете запустить котёл, пока запущен режим\nпо времени!", Toast.LENGTH_LONG).show()
                        } else if (sharedPreferences.getBoolean("isHeatingTemperatureModeStarted", false)) {
                            Toast.makeText(requireActivity(), "Вы не можете запустить котёл, пока запущен режим\nпо температуре!", Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    Toast.makeText(requireActivity(), "Нет подключения\nк Интернету!", Toast.LENGTH_LONG).show()
                }
            }

            buttonStopHeating.setOnClickListener {
                vibrate()
                if (isNetworkConnected(requireActivity())) {
                    realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("HeatingAndBoiler").child("heatingStarted").setValue(false)

                    textResponse.visibility = View.VISIBLE
                    textResponse.animate().alpha(1f).setStartDelay(0L).setDuration(500).start()
                    textResponse.text = getString(R.string.waiting_for_response_text)

                    Handler(Looper.getMainLooper()).postDelayed({
                        if (!response) {
                            textResponse.text = getString(R.string.response_not_received_text)

                            realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("HeatingAndBoiler").child("response")
                                .removeEventListener(responseValueEventListener)
                            realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("HeatingAndBoiler").child("heatingStarted").setValue(true)
                        }
                    }, 5000)

                    responseValueEventListener = object: ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            if (snapshot.getValue(Boolean::class.java) != null) {
                                response = snapshot.getValue(Boolean::class.java)!!
                                if (response) {
                                    binding.textResponse.text = getString(R.string.response_received_text)
                                    Handler(Looper.getMainLooper()).postDelayed({
                                        textResponse.animate().alpha(0f).setStartDelay(0).setDuration(500).start()
                                    }, 3000)
                                    realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("HeatingAndBoiler").child("response")
                                        .removeEventListener(responseValueEventListener)

                                    isHeatingStarted = false
                                    editPreferences.putBoolean("isHeatingStarted", false).apply()

                                    buttonIncrease.setImageResource(R.drawable.ic_increase)
                                    buttonDecrease.setImageResource(R.drawable.ic_decrease)
                                    if (heatingElements == maxHeatingElements) {
                                        buttonDecrease.setImageResource(R.drawable.ic_decrease)
                                        buttonIncrease.setImageResource(R.drawable.ic_increase_disabled)
                                    } else if (heatingElements == 1) {
                                        buttonIncrease.setImageResource(R.drawable.ic_increase)
                                        buttonDecrease.setImageResource(R.drawable.ic_decrease_disabled)
                                    }

                                    var isAnimationStarted = true
                                    buttonStopHeating.animate().alpha(0f).translationX(-1100f).setDuration(500).setStartDelay(0)
                                        .setListener(object: Animator.AnimatorListener {
                                            override fun onAnimationStart(animation: Animator) {}

                                            override fun onAnimationEnd(animation: Animator) {
                                                if (isAnimationStarted) {
                                                    isAnimationStarted = false

                                                    buttonStopHeating.visibility = View.GONE
                                                    buttonStartTimer.visibility = View.VISIBLE
                                                    buttonStartHeating.visibility = View.VISIBLE

                                                    buttonStartTimer.translationX = 1100f
                                                    buttonStartHeating.translationX = 1100f

                                                    buttonStartTimer.alpha = 0f
                                                    buttonStartHeating.alpha = 0f

                                                    buttonStartTimer.animate().alpha(1f).translationX(0f).setDuration(500).setStartDelay(0).start()
                                                    buttonStartHeating.animate().alpha(1f).translationX(0f).setDuration(500).setStartDelay(100).start()
                                                }
                                            }

                                            override fun onAnimationCancel(animation: Animator) {}
                                            override fun onAnimationRepeat(animation: Animator) {}

                                        }).start()
                                }
                            }
                        }

                        override fun onCancelled(error: DatabaseError) {}

                    }

                    realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("HeatingAndBoiler").child("response").setValue(false)
                    realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("HeatingAndBoiler").child("response")
                        .addValueEventListener(responseValueEventListener)
                } else {
                    Toast.makeText(requireActivity(), "Нет подключения\nк Интернету!", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun startTimer(timeLeftInMillis: Long, countDownInterval: Long) {
        with(binding) {
            countDownTimer = object: CountDownTimer(timeLeftInMillis, countDownInterval) {
                override fun onTick(millisUntilFinished: Long) {
                    editPreferences.putLong("HeatingTimerTimeLeft", millisUntilFinished)
                    editPreferences.putString("HeatingFragmentDestroyTime", dateFormat.format(Calendar.getInstance().time)).apply()
                    if (timerDays >= 1) {
                        textHours.text = String.format("%02d", millisUntilFinished / (1000 * 60 * 60 * 24))
                        textMinutes.text = String.format("%02d", (millisUntilFinished / (1000 * 60 * 60)) % 24)
                        textSeconds.text = String.format("%02d", (millisUntilFinished / (1000 * 60)) % 60)
                    } else {
                        textHours.text = String.format("%02d", (millisUntilFinished / (1000 * 60 * 60)) % 24)
                        textMinutes.text = String.format("%02d", (millisUntilFinished / (1000 * 60)) % 60)
                        textSeconds.text = String.format("%02d", (millisUntilFinished / 1000) % 60)
                    }
                }

                override fun onFinish() {
                    mediaPlayer.start()

                    buttonIncrease.setImageResource(R.drawable.ic_increase)
                    buttonDecrease.setImageResource(R.drawable.ic_decrease)
                    if (heatingElements == maxHeatingElements) {
                        buttonDecrease.setImageResource(R.drawable.ic_decrease)
                        buttonIncrease.setImageResource(R.drawable.ic_increase_disabled)
                    } else if (heatingElements == 1) {
                        buttonIncrease.setImageResource(R.drawable.ic_increase)
                        buttonDecrease.setImageResource(R.drawable.ic_decrease_disabled)
                    }

                    isTimerStarted = false
                    editPreferences.putBoolean("isHeatingTimerStarted", false).apply()

                    var isAnimationStarted = true
                    layoutTimeLeft.animate().alpha(1f).translationX(0f).setDuration(500).setStartDelay(0).start()
                    buttonStopTimer.animate().alpha(1f).translationX(0f).setDuration(500).setStartDelay(100)
                        .setListener(object: Animator.AnimatorListener {
                            override fun onAnimationStart(animation: Animator) {}

                            override fun onAnimationEnd(animation: Animator) {
                                if (isAnimationStarted) {
                                    isAnimationStarted = false

                                    layoutTimeLeft.visibility = View.GONE
                                    buttonStopTimer.visibility = View.GONE

                                    layoutNumberPickers.visibility = View.VISIBLE
                                    buttonStartTimer.visibility = View.VISIBLE
                                    buttonStartHeating.visibility = View.VISIBLE

                                    buttonStartTimer.translationX = 1100f
                                    buttonStartHeating.translationX = 1100f

                                    layoutNumberPickers.alpha = 0f
                                    buttonStartTimer.alpha = 0f
                                    buttonStartHeating.alpha = 0f

                                    layoutNumberPickers.animate().alpha(1f).setDuration(500).setStartDelay(0).start()
                                    buttonStartTimer.animate().alpha(1f).translationX(0f).setDuration(500).setStartDelay(0).start()
                                    buttonStartHeating.animate().alpha(1f).translationX(0f).setDuration(500).setStartDelay(100).start()
                                }
                            }

                            override fun onAnimationCancel(animation: Animator) {}
                            override fun onAnimationRepeat(animation: Animator) {}

                        }).start()
                }

            }.start()
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
        binding = FragmentHeatingTimerBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    @Suppress("DEPRECATION")
    private fun vibrate() {
        if (vibrator.hasVibrator()) {
            if (isHapticFeedbackEnabled == "1") {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    binding.buttonStartTimer.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING)
                } else {
                    binding.buttonStartTimer.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING + HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
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