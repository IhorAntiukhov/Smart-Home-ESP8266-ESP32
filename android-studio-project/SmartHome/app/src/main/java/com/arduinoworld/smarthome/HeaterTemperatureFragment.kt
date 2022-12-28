package com.arduinoworld.smarthome

import android.animation.Animator
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.*
import android.view.HapticFeedbackConstants
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.arduinoworld.smarthome.MainActivity.Companion.editPreferences
import com.arduinoworld.smarthome.MainActivity.Companion.firebaseAuth
import com.arduinoworld.smarthome.MainActivity.Companion.hideKeyboard
import com.arduinoworld.smarthome.MainActivity.Companion.isHapticFeedbackEnabled
import com.arduinoworld.smarthome.MainActivity.Companion.isNetworkConnected
import com.arduinoworld.smarthome.MainActivity.Companion.realtimeDatabase
import com.arduinoworld.smarthome.MainActivity.Companion.sharedPreferences
import com.arduinoworld.smarthome.MainActivity.Companion.vibrator
import com.arduinoworld.smarthome.databinding.FragmentHeaterTemperatureBinding
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener

class HeaterTemperatureFragment : Fragment() {
    private lateinit var binding: FragmentHeaterTemperatureBinding
    private var decreaseInMinTemperature = 0
    private var increaseInMaxTemperature = 0
    private var isTemperatureModeStarted = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        with(binding) {
            decreaseInMinTemperature = sharedPreferences.getString("HeaterDecreaseInMinTemperature", "2")!!.toInt()
            increaseInMaxTemperature = sharedPreferences.getString("HeaterIncreaseInMaxTemperature", "1")!!.toInt()
            isTemperatureModeStarted = sharedPreferences.getBoolean("isHeaterTemperatureModeStarted", false)

            progressBarTemperature.setProgressColor(Color.parseColor("#5347AE"))
            progressBarTemperature.setProgressWidth(40f)
            progressBarTemperature.setRounded(true)
            progressBarTemperature.setProgressBackgroundColor(Color.TRANSPARENT)
            progressBarTemperature.setMaxProgress(35f)

            if (isTemperatureModeStarted) {
                inputLayoutTemperature.visibility = View.GONE
                buttonStartTemperatureMode.visibility = View.GONE
                buttonStartHeater.visibility = View.GONE

                cardViewTemperatureRange.visibility = View.VISIBLE
                cardViewHeaterStarted.visibility = View.VISIBLE
                buttonStopTemperatureMode.visibility = View.VISIBLE

                textTemperatureRange.text = getString(R.string.min_max_temperature_text,
                    sharedPreferences.getInt("HeaterMinTemperature", 0),
                    sharedPreferences.getInt("HeaterMaxTemperature", 0))
                inputTemperature.setText(sharedPreferences.getInt("HeaterMaintainedTemperature", 0).toString())
                textHeaterStarted.text = getString(R.string.heating_stopped_text)

                cardViewTemperatureRange.alpha = 1f
                cardViewHeaterStarted.alpha = 1f
                buttonStopTemperatureMode.alpha = 1f
            }

            if (isServiceRunning(HeaterTemperatureService::class.java)) {
                buttonShowNotification.visibility = View.GONE
                buttonHideNotification.visibility = View.VISIBLE
                buttonHideNotification.alpha = 1f
            }

            realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("Heater").child("temperatureMode")
                .addListenerForSingleValueEvent(object: ValueEventListener {
                    @SuppressLint("SetTextI18n")
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val temperatureMode = snapshot.getValue(String::class.java)!!

                        @Suppress("KotlinConstantConditions")
                        if (temperatureMode != " ") {
                            isTemperatureModeStarted = true

                            inputLayoutTemperature.visibility = View.GONE
                            buttonStartTemperatureMode.visibility = View.GONE
                            buttonStartHeater.visibility = View.GONE

                            cardViewTemperatureRange.visibility = View.VISIBLE
                            cardViewHeaterStarted.visibility = View.VISIBLE
                            buttonStopTemperatureMode.visibility = View.VISIBLE

                            textTemperatureRange.text = getString(R.string.min_max_temperature_text,
                                temperatureMode.substring(0, temperatureMode.indexOf(" ")).toInt(),
                                temperatureMode.substring(temperatureMode.indexOf(" ") + 1, temperatureMode.indexOf(" ", temperatureMode.indexOf(" ") + 1)).toInt())
                            inputTemperature.setText((temperatureMode.substring(0, temperatureMode.indexOf(" ")).toInt() + decreaseInMinTemperature).toString())

                            editPreferences.putBoolean("isHeaterTemperatureModeStarted", true)
                            editPreferences.putInt("HeaterMaintainedTemperature", inputTemperature.text!!.toString().toInt())
                            editPreferences.putInt("HeaterMinTemperature", temperatureMode.substring(0, temperatureMode.indexOf(" ")).toInt())
                            editPreferences.putInt("HeaterMaxTemperature", temperatureMode.substring(temperatureMode.indexOf(" ") + 1, temperatureMode.indexOf(" ", temperatureMode.indexOf(" ") + 1)).toInt()).apply()

                            cardViewTemperatureRange.alpha = 1f
                            cardViewHeaterStarted.alpha = 1f
                            buttonStopTemperatureMode.alpha = 1f
                        } else if (temperatureMode == " " && isTemperatureModeStarted) {
                            cardViewTemperatureRange.visibility = View.GONE
                            cardViewHeaterStarted.visibility = View.GONE
                            buttonStopTemperatureMode.visibility = View.GONE

                            inputLayoutTemperature.visibility = View.VISIBLE
                            buttonStartTemperatureMode.visibility = View.VISIBLE
                            buttonStartHeater.visibility = View.VISIBLE

                            editPreferences.putBoolean("isHeaterTemperatureModeStarted", false)
                            editPreferences.remove("HeaterMaintainedTemperature")
                            editPreferences.remove("HeaterMinTemperature")
                            editPreferences.remove("HeaterMaxTemperature").commit()

                            inputLayoutTemperature.alpha = 1f
                            buttonStartTemperatureMode.alpha = 1f
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {}

                })

            buttonStartTemperatureMode.setOnClickListener { 
                vibrate()
                if (isNetworkConnected(requireActivity())) {
                    if (!sharedPreferences.getBoolean("isHeaterStarted", false)) {
                        if (inputTemperature.text!!.isNotEmpty()) {
                            hideKeyboard(requireActivity())
                            if (sharedPreferences.getBoolean("isHeaterTimeModeStarted", false)) {
                                val alertDialogBuilder = androidx.appcompat.app.AlertDialog.Builder(requireActivity())
                                alertDialogBuilder.setTitle("Совмещение режимов")
                                alertDialogBuilder.setMessage("Вы хотите совместить режимы по температуре и по времени?")
                                alertDialogBuilder.setPositiveButton("Подтвердить") { _, _ ->
                                    vibrate()
                                    startTemperatureMode()
                                }
                                alertDialogBuilder.setNegativeButton("Отмена") { _, _ ->
                                    vibrate()
                                }
                                alertDialogBuilder.create().show()
                            } else {
                                startTemperatureMode()
                            }
                        } else {
                            inputLayoutTemperature.isErrorEnabled = true
                            inputLayoutTemperature.error = "Введите поддерживаемую температуру"
                        }
                    } else {
                        Toast.makeText(requireActivity(), "Вы не можете запустить режим, пока запущен обогреватель!", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(requireActivity(), "Нет подключения\nк Интернету!", Toast.LENGTH_LONG).show()
                }
            }

            buttonStopTemperatureMode.setOnClickListener {
                vibrate()
                if (isNetworkConnected(requireActivity())) {
                    inputLayoutTemperature.isErrorEnabled = false

                    editPreferences.putBoolean("isHeaterTemperatureModeStarted", false)
                    editPreferences.remove("HeaterMaintainedTemperature")
                    editPreferences.remove("HeaterMinTemperature")
                    editPreferences.remove("HeaterMaxTemperature").commit()

                    realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("Heater").child("temperatureMode").setValue(" ")
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (sharedPreferences.getBoolean("isHeaterTimeModeStarted", false)) {
                            editPreferences.putBoolean("isHeaterTimeModeStarted", false).apply()
                            realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("Heater").child("heaterOnOffTime").setValue(" ")
                        }
                    }, 250)

                    realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("Heater").child("temperatureMode").removeEventListener(heatingStartedValueEventListener)

                    var isAnimationStarted = true
                    cardViewTemperatureRange.animate().alpha(0f).translationX(-1100f).setDuration(500).setStartDelay(0).start()
                    cardViewHeaterStarted.animate().alpha(0f).translationX(-1100f).setDuration(500).setStartDelay(100).start()
                    buttonStopTemperatureMode.animate().alpha(0f).translationX(-1100f).setDuration(500).setStartDelay(200)
                        .setListener(object: Animator.AnimatorListener {
                            override fun onAnimationStart(animation: Animator) {}

                            override fun onAnimationEnd(animation: Animator) {
                                if (isAnimationStarted) {
                                    isAnimationStarted = false

                                    cardViewTemperatureRange.visibility = View.GONE
                                    cardViewHeaterStarted.visibility = View.GONE
                                    buttonStopTemperatureMode.visibility = View.GONE

                                    inputLayoutTemperature.visibility = View.VISIBLE
                                    buttonStartTemperatureMode.visibility = View.VISIBLE
                                    buttonStartHeater.visibility = View.VISIBLE

                                    inputLayoutTemperature.translationX = 1100f
                                    buttonStartTemperatureMode.translationX = 1100f
                                    buttonStartHeater.translationX = 1100f

                                    inputLayoutTemperature.alpha = 0f
                                    buttonStartTemperatureMode.alpha = 0f
                                    buttonStartHeater.alpha = 0f

                                    inputLayoutTemperature.animate().alpha(1f).translationX(0f).setDuration(500).setStartDelay(0).start()
                                    buttonStartTemperatureMode.animate().alpha(1f).translationX(0f).setDuration(500).setStartDelay(100).start()
                                    buttonStartHeater.animate().alpha(1f).translationX(0f).setDuration(500).setStartDelay(200).start()
                                }
                            }

                            override fun onAnimationCancel(animation: Animator) {}
                            override fun onAnimationRepeat(animation: Animator) {}
                        }).start()
                } else {
                    Toast.makeText(requireActivity(), "Нет подключения\nк Интернету!", Toast.LENGTH_LONG).show()
                }
            }

            buttonStartHeater.setOnClickListener {
                vibrate()
                if (isNetworkConnected(requireActivity())) {
                    editPreferences.putBoolean("isHeaterStarted", true).apply()
                    realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("Heater").child("heaterStarted").setValue(true)

                    var isAnimationStarted = true
                    inputLayoutTemperature.animate().alpha(0f).translationX(-1100f).setDuration(500).setStartDelay(0).start()
                    buttonStartTemperatureMode.animate().alpha(0f).translationX(-1100f).setDuration(500).setStartDelay(100).start()
                    buttonStartHeater.animate().alpha(0f).translationX(-1100f).setDuration(500).setStartDelay(200)
                        .setListener(object: Animator.AnimatorListener {
                            override fun onAnimationStart(animation: Animator) {}

                            override fun onAnimationEnd(animation: Animator) {
                                if (isAnimationStarted) {
                                    isAnimationStarted = false

                                    inputLayoutTemperature.visibility = View.GONE
                                    buttonStartTemperatureMode.visibility = View.GONE
                                    buttonStartHeater.visibility = View.GONE
                                    buttonStopHeater.visibility = View.VISIBLE

                                    buttonStopHeater.translationX = 1100f
                                    buttonStopHeater.animate().alpha(1f).translationX(0f).setDuration(500).setStartDelay(0).start()
                                }
                            }

                            override fun onAnimationCancel(animation: Animator) {}
                            override fun onAnimationRepeat(animation: Animator) {}

                        }).start()
                } else {
                    Toast.makeText(requireActivity(), "Нет подключения\nк Интернету!", Toast.LENGTH_LONG).show()
                }
            }

            buttonStopHeater.setOnClickListener {
                vibrate()
                if (isNetworkConnected(requireActivity())) {
                    isTemperatureModeStarted = false
                    editPreferences.putBoolean("isHeaterStarted", false).apply()
                    realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("Heater").child("heaterStarted").setValue(false)

                    var isAnimationStarted = true
                    buttonStopHeater.animate().alpha(0f).translationX(-1100f).setDuration(500).setStartDelay(0)
                        .setListener(object: Animator.AnimatorListener {
                            override fun onAnimationStart(animation: Animator) {}

                            override fun onAnimationEnd(animation: Animator) {
                                if (isAnimationStarted) {
                                    isAnimationStarted = false

                                    buttonStopHeater.visibility = View.GONE
                                    inputLayoutTemperature.visibility = View.VISIBLE
                                    buttonStartTemperatureMode.visibility = View.VISIBLE
                                    buttonStartHeater.visibility = View.VISIBLE

                                    inputLayoutTemperature.translationX = 1100f
                                    buttonStartTemperatureMode.translationX = 1100f
                                    buttonStartHeater.translationX = 1100f

                                    inputLayoutTemperature.alpha = 0f
                                    buttonStartTemperatureMode.alpha = 0f
                                    buttonStartHeater.alpha = 0f

                                    inputLayoutTemperature.animate().alpha(1f).translationX(0f).setDuration(500).setStartDelay(0).start()
                                    buttonStartTemperatureMode.animate().alpha(1f).translationX(0f).setDuration(500).setStartDelay(100).start()
                                    buttonStartHeater.animate().alpha(1f).translationX(0f).setDuration(500).setStartDelay(200).start()
                                }
                            }

                            override fun onAnimationCancel(animation: Animator) {}
                            override fun onAnimationRepeat(animation: Animator) {}

                        }).start()
                } else {
                    Toast.makeText(requireActivity(), "Нет подключения\nк Интернету!", Toast.LENGTH_LONG).show()
                }
            }

            buttonShowNotification.setOnClickListener {
                vibrate()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    if (!sharedPreferences.getBoolean("isHeaterTemperatureNotificationChannelCreated", false)) {
                        val notificationManager = requireActivity().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        val notificationChannel = NotificationChannel("HeaterTemperatureNotification", "Уведомление обогревателя", NotificationManager.IMPORTANCE_HIGH)
                        notificationChannel.description = "Уведомление с температурой, которое отображается постоянно"
                        notificationChannel.enableLights(false)
                        notificationChannel.enableVibration(false)
                        notificationChannel.setSound(null, null)
                        notificationChannel.group = "SmartHome"
                        notificationChannel.lockscreenVisibility = View.VISIBLE
                        notificationManager.createNotificationChannel(notificationChannel)
                        editPreferences.putBoolean("isHeaterTemperatureNotificationChannelCreated", true)
                    }
                    val service = Intent(requireActivity(), HeaterTemperatureService::class.java)
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
                val service = Intent(requireActivity(), HeaterTemperatureService::class.java)
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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        binding = FragmentHeaterTemperatureBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()

        realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("Heater").child("temperature").addValueEventListener(temperatureValueEventListener)

        if (isTemperatureModeStarted) {
            realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("Heater").child("temperatureMode").addValueEventListener(heatingStartedValueEventListener)
        }
    }

    override fun onStop() {
        super.onStop()

        realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("Heater").child("temperature").removeEventListener(temperatureValueEventListener)

        if (isTemperatureModeStarted) {
            realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("Heater").child("temperatureMode").removeEventListener(heatingStartedValueEventListener)
        }
    }

    private val temperatureValueEventListener = object: ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            if (snapshot.getValue(Int::class.java) != null) {
                val temperature = snapshot.getValue(Int::class.java)!!
                binding.textTemperature.text = getString(R.string.temperature_text, temperature)
                binding.progressBarTemperature.setProgress(temperature.toFloat())
            }
        }

        override fun onCancelled(error: DatabaseError) {
            Toast.makeText(requireActivity(), "Не удалось получить температуру!", Toast.LENGTH_LONG).show()
        }

    }

    private val heatingStartedValueEventListener = object: ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            if (snapshot.getValue(String::class.java) != null) {
                if (snapshot.getValue(String::class.java)!!.last() == '1') {
                    binding.textHeaterStarted.text = getString(R.string.heater_started_text)
                } else if (snapshot.getValue(String::class.java)!!.last() == '0') {
                    binding.textHeaterStarted.text = getString(R.string.heater_stopped_text)
                }
            }
        }

        override fun onCancelled(error: DatabaseError) {
            Toast.makeText(requireActivity(), "Не удалось получить то, запущен ли котёл по температуре!", Toast.LENGTH_LONG).show()
        }

    }
    
    private fun startTemperatureMode() {
        with(binding) {
            isTemperatureModeStarted = true
            inputLayoutTemperature.isErrorEnabled = false

            editPreferences.putBoolean("isHeaterTemperatureModeStarted", true)
            editPreferences.putInt("HeaterMaintainedTemperature", inputTemperature.text!!.toString().toInt())
            editPreferences.putInt("HeaterMinTemperature", inputTemperature.text!!.toString().toInt() - decreaseInMinTemperature)
            editPreferences.putInt("HeaterMaxTemperature", inputTemperature.text!!.toString().toInt() + increaseInMaxTemperature).apply()

            realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("Heater").child("temperatureMode").setValue("${inputTemperature.text!!.toString().toInt() - decreaseInMinTemperature}" +
                    " ${inputTemperature.text!!.toString().toInt() + increaseInMaxTemperature} 0")

            realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("Heater").child("temperatureMode").addValueEventListener(heatingStartedValueEventListener)

            textTemperatureRange.text = getString(R.string.min_max_temperature_text,
                inputTemperature.text!!.toString().toInt() - decreaseInMinTemperature,
                inputTemperature.text!!.toString().toInt() + increaseInMaxTemperature)
            textHeaterStarted.text = getString(R.string.heater_stopped_text)

            var isAnimationStarted = true
            inputLayoutTemperature.animate().alpha(0f).translationX(-1100f).setDuration(500).setStartDelay(0).start()
            buttonStartTemperatureMode.animate().alpha(0f).translationX(-1100f).setDuration(500).setStartDelay(100).start()
            buttonStartHeater.animate().alpha(0f).translationX(-1100f).setDuration(500).setStartDelay(200)
                .setListener(object: Animator.AnimatorListener {
                    override fun onAnimationStart(animation: Animator) {}

                    override fun onAnimationEnd(animation: Animator) {
                        if (isAnimationStarted) {
                            isAnimationStarted = false

                            inputLayoutTemperature.visibility = View.GONE
                            buttonStartTemperatureMode.visibility = View.GONE
                            buttonStartHeater.visibility = View.GONE

                            cardViewTemperatureRange.visibility = View.VISIBLE
                            cardViewHeaterStarted.visibility = View.VISIBLE
                            buttonStopTemperatureMode.visibility = View.VISIBLE

                            cardViewTemperatureRange.translationX = 1100f
                            cardViewHeaterStarted.translationX = 1100f
                            buttonStopTemperatureMode.translationX = 1100f

                            cardViewTemperatureRange.animate().alpha(1f).translationX(0f).setDuration(500).setStartDelay(0).start()
                            cardViewHeaterStarted.animate().alpha(1f).translationX(0f).setDuration(500).setStartDelay(100).start()
                            buttonStopTemperatureMode.animate().alpha(1f).translationX(0f).setDuration(500).setStartDelay(200).start()
                        }
                    }

                    override fun onAnimationCancel(animation: Animator) {}
                    override fun onAnimationRepeat(animation: Animator) {}
                }).start()
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

    @Suppress("DEPRECATION")
    private fun vibrate() {
        if (vibrator.hasVibrator()) {
            if (isHapticFeedbackEnabled == "1") {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    binding.buttonStartTemperatureMode.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING)
                } else {
                    binding.buttonStartTemperatureMode.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING + HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
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