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
import com.arduinoworld.smarthome.databinding.FragmentBoilerTimerBinding
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.*


class BoilerTimerFragment : Fragment() {
    private lateinit var binding: FragmentBoilerTimerBinding
    private lateinit var countDownTimer: CountDownTimer
    private lateinit var dateFormat: SimpleDateFormat
    private lateinit var mediaPlayer: MediaPlayer
    private lateinit var responseValueEventListener: ValueEventListener
    private var isTimerStarted = false
    private var timerDays = 0
    private var response = false

    @SuppressLint("DiscouragedPrivateApi")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        with(binding) {
            mediaPlayer = MediaPlayer.create(requireActivity(), R.raw.notification)
            dateFormat = SimpleDateFormat("dd/M/yyyy hh:mm:ss", Locale.US)

            val maxHeatingElements = sharedPreferences.getString("MaxHeatingElements", "2")!!.toInt()
            val isOverCurrentProtectionEnabled = sharedPreferences.getBoolean("isOverCurrentProtectionEnabled", true)
            var isBoilerStarted = sharedPreferences.getBoolean("isBoilerStarted", false)
            isTimerStarted = sharedPreferences.getBoolean("isBoilerTimerStarted", false)

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

            timerDays = sharedPreferences.getInt("BoilerNumberPickerDays", 0)
            numberPickerDays.value = sharedPreferences.getInt("BoilerNumberPickerDays", 0)
            numberPickerHours.value = sharedPreferences.getInt("BoilerNumberPickerHours", 0)
            numberPickerMinutes.value = sharedPreferences.getInt("BoilerNumberPickerMinutes", 0)

            if (isTimerStarted) {
                layoutNumberPickers.visibility = View.GONE
                buttonStartTimer.visibility = View.GONE
                buttonStartBoiler.visibility = View.GONE

                layoutTimeLeft.visibility = View.VISIBLE
                buttonStopTimer.visibility = View.VISIBLE

                layoutTimeLeft.alpha = 1f
                buttonStopTimer.alpha = 1f

                val fragmentDestroyTimeLeft = sharedPreferences.getLong("BoilerTimerTimeLeft", 0)
                val fragmentDestroyTime = dateFormat.parse(sharedPreferences.getString("BoilerFragmentDestroyTime", "").toString())!!
                val fragmentOpenTime = dateFormat.parse(dateFormat.format(Calendar.getInstance().time))!!

                val timeLeft = fragmentDestroyTimeLeft - (kotlin.math.abs(fragmentOpenTime.time - fragmentDestroyTime.time))
                if (timeLeft <= 0) {
                    isTimerStarted = false
                    editPreferences.putBoolean("isBoilerTimerStarted", false).apply()
                } else {
                    if (timerDays >= 1) {
                        startTimer(timeLeft, 60000)
                    } else {
                        startTimer(timeLeft, 1000)
                    }

                    layoutNumberPickers.visibility = View.GONE
                    buttonStartTimer.visibility = View.GONE
                    buttonStartBoiler.visibility = View.GONE

                    layoutTimeLeft.visibility = View.VISIBLE
                    buttonStopTimer.visibility = View.VISIBLE

                    layoutTimeLeft.alpha = 1f
                    buttonStopTimer.alpha = 1f
                }
            } else {
                if (isBoilerStarted) {
                    buttonStartTimer.visibility = View.GONE
                    buttonStartBoiler.visibility = View.GONE
                    buttonStopBoiler.visibility = View.VISIBLE
                    buttonStopBoiler.alpha = 1f
                }
            }

            realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("HeatingAndBoiler")
                .addListenerForSingleValueEvent(object: ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (snapshot.child("boilerTimerTime").getValue(Int::class.java)!! == 0 && isTimerStarted) {
                            editPreferences.putBoolean("isBoilerTimerStarted", false).apply()

                            layoutTimeLeft.visibility = View.GONE
                            buttonStopTimer.visibility = View.GONE

                            layoutNumberPickers.visibility = View.VISIBLE
                            buttonStartTimer.visibility = View.VISIBLE
                            buttonStartBoiler.visibility = View.VISIBLE
                        }
                        if (snapshot.child("boilerStarted").getValue(Boolean::class.java)!! && !isBoilerStarted) {
                            isBoilerStarted = true
                            editPreferences.putBoolean("isBoilerStarted", true).apply()

                            buttonStartTimer.visibility = View.GONE
                            buttonStartBoiler.visibility = View.GONE
                            buttonStopBoiler.visibility = View.VISIBLE
                            buttonStopBoiler.alpha = 1f
                        } else if (!snapshot.child("boilerStarted").getValue(Boolean::class.java)!! && isBoilerStarted) {
                            isBoilerStarted = false
                            editPreferences.putBoolean("isBoilerStarted", false).apply()

                            buttonStopBoiler.visibility = View.GONE
                            buttonStartTimer.visibility = View.VISIBLE
                            buttonStartBoiler.visibility = View.VISIBLE
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {}

                })

            buttonStartTimer.setOnClickListener {
                vibrate()
                if (isNetworkConnected(requireActivity())) {
                    if (!sharedPreferences.getBoolean("isBoilerTimeModeStarted", false)) {
                        if (!(isOverCurrentProtectionEnabled && maxHeatingElements > 1 && (((sharedPreferences.getBoolean("isHeatingStarted", false) || sharedPreferences.getBoolean("isHeatingTimerStarted", false))
                                    && sharedPreferences.getInt("TimerHeatingElements", 1) == maxHeatingElements) || (sharedPreferences.getBoolean("isHeatingTimeModeStarted", false)
                                    && sharedPreferences.getBoolean("isMaxHeatingElementsStartInTimeMode", false)) || (sharedPreferences.getBoolean("isHeatingTemperatureModeStarted", false)
                                    && sharedPreferences.getInt("TemperatureModeHeatingElements", 1) == maxHeatingElements)))) {
                            if (!(numberPickerDays.value == 0 && numberPickerHours.value == 0 && numberPickerMinutes.value == 0)) {
                                realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("HeatingAndBoiler").child("boilerTimerTime").setValue(
                                    numberPickerDays.value * 24 * 60 + numberPickerHours.value * 60 + numberPickerMinutes.value)

                                textResponse.visibility = View.VISIBLE
                                textResponse.animate().alpha(1f).setStartDelay(0L).setDuration(500).start()
                                textResponse.text = getString(R.string.waiting_for_response_text)
                                
                                Handler(Looper.getMainLooper()).postDelayed({
                                    if (!response) {
                                        textResponse.text = getString(R.string.response_not_received_text)

                                        realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("HeatingAndBoiler").child("response")
                                            .removeEventListener(responseValueEventListener)
                                        realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("HeatingAndBoiler").child("boilerTimerTime").setValue(0)
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

                                                editPreferences.putInt("BoilerNumberPickerDays", numberPickerDays.value)
                                                editPreferences.putInt("BoilerNumberPickerHours", numberPickerHours.value)
                                                editPreferences.putInt("BoilerNumberPickerMinutes", numberPickerMinutes.value)
                                                editPreferences.putBoolean("isBoilerTimerStarted", true).apply()

                                                var isAnimationStarted = true
                                                layoutNumberPickers.animate().alpha(0f).setDuration(500).setStartDelay(0).start()
                                                buttonStartTimer.animate().alpha(0f).translationX(-1100f).setDuration(500).setStartDelay(0).start()
                                                buttonStartBoiler.animate().alpha(0f).translationX(-1100f).setDuration(500).setStartDelay(100)
                                                    .setListener(object: Animator.AnimatorListener {
                                                        override fun onAnimationStart(animation: Animator) {}

                                                        override fun onAnimationEnd(animation: Animator) {
                                                            if (isAnimationStarted) {
                                                                isAnimationStarted = false

                                                                layoutNumberPickers.visibility = View.GONE
                                                                buttonStartTimer.visibility = View.GONE
                                                                buttonStartBoiler.visibility = View.GONE

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
                            if (sharedPreferences.getBoolean("isHeatingStarted", false) && sharedPreferences.getInt("TimerHeatingElements", 1) == maxHeatingElements) {
                                showOverCurrentProtectionAlertDialog("Так как сейчас запущен котёл, для избежания перегрузки электросети по току, вы не можете запустить таймер бойлера. Вы можете отключить эту функцию в настройках.")
                            } else if (sharedPreferences.getBoolean("isHeatingTimerStarted", false) && sharedPreferences.getInt("TimerHeatingElements", 1) == maxHeatingElements) {
                                showOverCurrentProtectionAlertDialog("Так как сейчас запущен таймер котла, для избежания перегрузки электросети по току, вы не можете запустить таймер бойлера. Вы можете отключить эту функцию в настройках.")
                            } else if (sharedPreferences.getBoolean("isHeatingTimeModeStarted", false) && sharedPreferences.getBoolean("isMaxHeatingElementsStartInTimeMode", false)) {
                                showOverCurrentProtectionAlertDialog("Так как сейчас запущен режим по времени для котла, для избежания перегрузки электросети по току, вы не можете запустить таймер бойлера. Вы можете отключить эту функцию в настройках.")
                            } else if (sharedPreferences.getBoolean("isHeatingTemperatureModeStarted", false) && sharedPreferences.getInt("TemperatureModeHeatingElements", 1) == maxHeatingElements) {
                                showOverCurrentProtectionAlertDialog("Так как сейчас запущен режим по температуре, для избежания перегрузки электросети по току, вы не можете запустить таймер бойлера. Вы можете отключить эту функцию в настройках.")
                            }
                        }
                    } else {
                        Toast.makeText(requireActivity(), "Вы не можете запустить таймер, пока запущен режим\nпо времени!", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(requireActivity(), "Нет подключения\nк Интернету!", Toast.LENGTH_LONG).show()
                }
            }

            buttonStopTimer.setOnClickListener {
                vibrate()
                if (isNetworkConnected(requireActivity())) {
                    realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("HeatingAndBoiler").child("boilerTimerTime").setValue(0)

                    textResponse.visibility = View.VISIBLE
                    textResponse.animate().alpha(1f).setStartDelay(0L).setDuration(500).start()
                    textResponse.text = getString(R.string.waiting_for_response_text)

                    Handler(Looper.getMainLooper()).postDelayed({
                        if (!response) {
                            textResponse.text = getString(R.string.response_not_received_text)
                            realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("HeatingAndBoiler").child("response")
                                .removeEventListener(responseValueEventListener)
                            realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("HeatingAndBoiler").child("boilerTimerTime").setValue(
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

                                    editPreferences.remove("BoilerNumberPickerDays")
                                    editPreferences.remove("BoilerNumberPickerHours")
                                    editPreferences.remove("BoilerNumberPickerMinutes")
                                    editPreferences.putBoolean("isBoilerTimerStarted", false).commit()

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
                                                    buttonStartBoiler.visibility = View.VISIBLE

                                                    buttonStartTimer.translationX = 1100f
                                                    buttonStartBoiler.translationX = 1100f

                                                    layoutNumberPickers.alpha = 0f
                                                    buttonStartTimer.alpha = 0f
                                                    buttonStartBoiler.alpha = 0f

                                                    layoutNumberPickers.animate().alpha(1f).setDuration(500).setStartDelay(0).start()
                                                    buttonStartTimer.animate().alpha(1f).translationX(0f).setDuration(500).setStartDelay(0).start()
                                                    buttonStartBoiler.animate().alpha(1f).translationX(0f).setDuration(500).setStartDelay(100).start()
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

            buttonStartBoiler.setOnClickListener {
                vibrate()
                if (isNetworkConnected(requireActivity())) {
                    if (!sharedPreferences.getBoolean("isBoilerTimeModeStarted", false)) {
                        if (!(isOverCurrentProtectionEnabled && maxHeatingElements > 1 && (((sharedPreferences.getBoolean("isHeatingStarted", false) || sharedPreferences.getBoolean("isHeatingTimerStarted", false))
                                    && sharedPreferences.getInt("TimerHeatingElements", 1) == maxHeatingElements) || (sharedPreferences.getBoolean("isHeatingTimeModeStarted", false)
                                    && sharedPreferences.getBoolean("isMaxHeatingElementsStartInTimeMode", false)) || (sharedPreferences.getBoolean("isHeatingTemperatureModeStarted", false)
                                    && sharedPreferences.getInt("TemperatureModeHeatingElements", 1) == maxHeatingElements)))) {
                            realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("HeatingAndBoiler").child("boilerStarted").setValue(true)
                            
                            textResponse.visibility = View.VISIBLE
                            textResponse.animate().alpha(1f).setStartDelay(0L).setDuration(500).start()
                            textResponse.text = getString(R.string.waiting_for_response_text)

                            Handler(Looper.getMainLooper()).postDelayed({
                                if (!response) {
                                    textResponse.text = getString(R.string.response_not_received_text)
                                    realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("HeatingAndBoiler").child("response")
                                        .removeEventListener(responseValueEventListener)
                                    realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("HeatingAndBoiler").child("boilerStarted").setValue(false)
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

                                            isBoilerStarted = true
                                            editPreferences.putBoolean("isBoilerStarted", true).apply()

                                            var isAnimationStarted = true
                                            buttonStartTimer.animate().alpha(0f).translationX(-1100f).setDuration(500).setStartDelay(0).start()
                                            buttonStartBoiler.animate().alpha(0f).translationX(-1100f).setDuration(500).setStartDelay(100)
                                                .setListener(object: Animator.AnimatorListener {
                                                    override fun onAnimationStart(animation: Animator) {}

                                                    override fun onAnimationEnd(animation: Animator) {
                                                        if (isAnimationStarted) {
                                                            isAnimationStarted = false

                                                            buttonStartTimer.visibility = View.GONE
                                                            buttonStartBoiler.visibility = View.GONE
                                                            buttonStopBoiler.visibility = View.VISIBLE

                                                            buttonStopBoiler.translationX = 1100f
                                                            buttonStopBoiler.animate().alpha(1f).translationX(0f).setDuration(500).setStartDelay(0).start()
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
                            if (sharedPreferences.getBoolean("isHeatingStarted", false) && sharedPreferences.getInt("TimerHeatingElements", 1) == maxHeatingElements) {
                                showOverCurrentProtectionAlertDialog("Так как сейчас запущен котёл, для избежания перегрузки электросети по току, вы не можете запустить бойлер. Вы можете отключить эту функцию в настройках.")
                            } else if (sharedPreferences.getBoolean("isHeatingTimerStarted", false) && sharedPreferences.getInt("TimerHeatingElements", 1) == maxHeatingElements) {
                                showOverCurrentProtectionAlertDialog("Так как сейчас запущен таймер котла, для избежания перегрузки электросети по току, вы не можете запустить бойлер. Вы можете отключить эту функцию в настройках.")
                            } else if (sharedPreferences.getBoolean("isHeatingTimeModeStarted", false) && sharedPreferences.getBoolean("isMaxHeatingElementsStartInTimeMode", false)) {
                                showOverCurrentProtectionAlertDialog("Так как сейчас запущен режим по времени для котла, для избежания перегрузки электросети по току, вы не можете запустить бойлер. Вы можете отключить эту функцию в настройках.")
                            } else if (sharedPreferences.getBoolean("isHeatingTemperatureModeStarted", false) && sharedPreferences.getInt("TemperatureModeHeatingElements", 1) == maxHeatingElements) {
                                showOverCurrentProtectionAlertDialog("Так как сейчас запущен режим по температуре, для избежания перегрузки электросети по току, вы не можете запустить бойлер. Вы можете отключить эту функцию в настройках.")
                            }
                        }
                    } else {
                        Toast.makeText(requireActivity(), "Вы не можете запустить бойлер, пока запущен режим\nпо времени!", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(requireActivity(), "Нет подключения\nк Интернету!", Toast.LENGTH_LONG).show()
                }
            }

            buttonStopBoiler.setOnClickListener {
                vibrate()
                if (isNetworkConnected(requireActivity())) {
                    realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("HeatingAndBoiler").child("boilerStarted").setValue(false)

                    textResponse.visibility = View.VISIBLE
                    textResponse.animate().alpha(1f).setStartDelay(0L).setDuration(500).start()
                    textResponse.text = getString(R.string.waiting_for_response_text)

                    Handler(Looper.getMainLooper()).postDelayed({
                        if (!response) {
                            textResponse.text = getString(R.string.response_not_received_text)
                            realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("HeatingAndBoiler").child("response")
                                .removeEventListener(responseValueEventListener)
                            realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("HeatingAndBoiler").child("boilerStarted").setValue(true)
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

                                    isBoilerStarted = false
                                    editPreferences.putBoolean("isBoilerStarted", false).apply()

                                    var isAnimationStarted = true
                                    buttonStopBoiler.animate().alpha(0f).translationX(-1100f).setDuration(500).setStartDelay(0)
                                        .setListener(object: Animator.AnimatorListener {
                                            override fun onAnimationStart(animation: Animator) {}

                                            override fun onAnimationEnd(animation: Animator) {
                                                if (isAnimationStarted) {
                                                    isAnimationStarted = false

                                                    buttonStopBoiler.visibility = View.GONE
                                                    buttonStartTimer.visibility = View.VISIBLE
                                                    buttonStartBoiler.visibility = View.VISIBLE

                                                    buttonStartTimer.translationX = 1100f
                                                    buttonStartBoiler.translationX = 1100f

                                                    buttonStartTimer.alpha = 0f
                                                    buttonStartBoiler.alpha = 0f

                                                    buttonStartTimer.animate().alpha(1f).translationX(0f).setDuration(500).setStartDelay(0).start()
                                                    buttonStartBoiler.animate().alpha(1f).translationX(0f).setDuration(500).setStartDelay(100).start()
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
                    editPreferences.putLong("BoilerTimerTimeLeft", millisUntilFinished)
                    editPreferences.putString("BoilerFragmentDestroyTime", dateFormat.format(Calendar.getInstance().time)).apply()
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

                    isTimerStarted = false
                    editPreferences.putBoolean("isBoilerTimerStarted", false).apply()

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
                                    buttonStartBoiler.visibility = View.VISIBLE

                                    buttonStartTimer.translationX = 1100f
                                    buttonStartBoiler.translationX = 1100f

                                    layoutNumberPickers.alpha = 0f
                                    buttonStartTimer.alpha = 0f
                                    buttonStartBoiler.alpha = 0f

                                    layoutNumberPickers.animate().alpha(1f).setDuration(500).setStartDelay(0).start()
                                    buttonStartTimer.animate().alpha(1f).translationX(0f).setDuration(500).setStartDelay(0).start()
                                    buttonStartBoiler.animate().alpha(1f).translationX(0f).setDuration(500).setStartDelay(100).start()
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
        binding = FragmentBoilerTimerBinding.inflate(layoutInflater, container, false)
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