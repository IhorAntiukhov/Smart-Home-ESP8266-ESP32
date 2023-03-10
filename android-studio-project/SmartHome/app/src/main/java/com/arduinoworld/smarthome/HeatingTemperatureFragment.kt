package com.arduinoworld.smarthome

import android.animation.Animator
import android.annotation.SuppressLint
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
import com.arduinoworld.smarthome.MainActivity.Companion.isNetworkConnected
import com.arduinoworld.smarthome.MainActivity.Companion.realtimeDatabase
import com.arduinoworld.smarthome.MainActivity.Companion.sharedPreferences
import com.arduinoworld.smarthome.databinding.FragmentHeatingTemperatureBinding
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener

class HeatingTemperatureFragment : Fragment() {
    private lateinit var binding: FragmentHeatingTemperatureBinding
    private lateinit var responseValueEventListener: ValueEventListener
    private var isTemperatureModeStarted = false
    private var decreaseInMinTemperature = 2
    private var increaseInMaxTemperature = 1
    private var heatingElements = 1
    private var response = '0'

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        with(binding) {
            val maxHeatingElements = sharedPreferences.getString("MaxHeatingElements", "2")!!.toInt()
            val isOverCurrentProtectionEnabled = sharedPreferences.getBoolean("isOverCurrentProtectionEnabled", true)
            heatingElements = sharedPreferences.getInt("TemperatureModeHeatingElements", 1)
            decreaseInMinTemperature = sharedPreferences.getString("HeatingDecreaseInMinTemperature", "2")!!.toInt()
            increaseInMaxTemperature = sharedPreferences.getString("HeatingIncreaseInMaxTemperature", "1")!!.toInt()
            isTemperatureModeStarted = sharedPreferences.getBoolean("isHeatingTemperatureModeStarted", false)

            progressBarTemperature.setProgressColor(Color.parseColor("#5347AE"))
            progressBarTemperature.setProgressWidth(40f)
            progressBarTemperature.setRounded(true)
            progressBarTemperature.setProgressBackgroundColor(Color.TRANSPARENT)
            progressBarTemperature.setMaxProgress(35f)

            if (maxHeatingElements > 1) {
                textHeatingElements.text = getString(R.string.heating_elements_text, heatingElements)
                if (heatingElements == maxHeatingElements) {
                    buttonIncrease.setImageResource(R.drawable.ic_increase_disabled)
                } else if (heatingElements == 1) {
                    buttonDecrease.setImageResource(R.drawable.ic_decrease_disabled)
                }

                buttonIncrease.setOnClickListener {
                    vibrate()
                    if (!isTemperatureModeStarted) {
                        if (heatingElements < maxHeatingElements) {
                            heatingElements += 1
                            if (heatingElements == maxHeatingElements) {
                                buttonIncrease.setImageResource(R.drawable.ic_increase_disabled)
                            }
                            buttonDecrease.setImageResource(R.drawable.ic_decrease)
                            textHeatingElements.text = getString(R.string.heating_elements_text, heatingElements)
                            editPreferences.putInt("TemperatureModeHeatingElements", heatingElements).apply()
                        } else {
                            Toast.makeText(requireActivity(), "???? ???????????????????? ???????????????????????? ???????????????????? ??????????!", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Toast.makeText(requireActivity(), "???? ???? ???????????? ???????????????? ???????????????????? ??????????, ????????\n?????????????? ??????????!", Toast.LENGTH_LONG).show()
                    }
                }

                buttonDecrease.setOnClickListener {
                    vibrate()
                    if (!isTemperatureModeStarted) {
                        if (heatingElements > 1) {
                            heatingElements -= 1
                            if (heatingElements == 1) {
                                buttonDecrease.setImageResource(R.drawable.ic_decrease_disabled)
                            }
                            buttonIncrease.setImageResource(R.drawable.ic_increase)
                            textHeatingElements.text = getString(R.string.heating_elements_text, heatingElements)
                            editPreferences.putInt("TemperatureModeHeatingElements", heatingElements).apply()
                        } else {
                            Toast.makeText(requireActivity(), "???? ???????????????????? ?????????????????????? ???????????????????? ??????????!", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Toast.makeText(requireActivity(), "???? ???? ???????????? ???????????????? ???????????????????? ??????????, ????????\n?????????????? ??????????!", Toast.LENGTH_LONG).show()
                    }
                }
            } else {
                layoutHeatingElements.visibility = View.GONE
            }

            if (isTemperatureModeStarted) {
                inputLayoutTemperature.visibility = View.GONE
                buttonStartTemperatureMode.visibility = View.GONE

                cardViewTemperatureRange.visibility = View.VISIBLE
                cardViewHeatingStarted.visibility = View.VISIBLE
                buttonStopTemperatureMode.visibility = View.VISIBLE

                textTemperatureRange.text = getString(R.string.min_max_temperature_text,
                    sharedPreferences.getInt("HeatingMinTemperature", 0),
                    sharedPreferences.getInt("HeatingMaxTemperature", 0))
                inputTemperature.setText(sharedPreferences.getInt("HeatingMaintainedTemperature", 0).toString())
                textHeatingStarted.text = getString(R.string.heating_stopped_text)

                cardViewTemperatureRange.alpha = 1f
                cardViewHeatingStarted.alpha = 1f
                buttonStopTemperatureMode.alpha = 1f
            }

            realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("HeatingAndBoiler").child("temperatureMode")
                .addListenerForSingleValueEvent(object: ValueEventListener {
                    @SuppressLint("SetTextI18n")
                    override fun onDataChange(snapshot: DataSnapshot) {
                        var temperatureMode = snapshot.getValue(String::class.java)!!
                        response = temperatureMode.last()
                        temperatureMode = temperatureMode.substring(0, temperatureMode.length - 2)

                        @Suppress("KotlinConstantConditions")
                        if (temperatureMode != " ") {
                            isTemperatureModeStarted = true

                            inputLayoutTemperature.visibility = View.GONE
                            buttonStartTemperatureMode.visibility = View.GONE

                            cardViewTemperatureRange.visibility = View.VISIBLE
                            cardViewHeatingStarted.visibility = View.VISIBLE
                            buttonStopTemperatureMode.visibility = View.VISIBLE

                            textTemperatureRange.text = getString(R.string.min_max_temperature_text,
                                temperatureMode.substring(0, temperatureMode.indexOf(" ")).toInt(),
                                temperatureMode.substring(temperatureMode.indexOf(" ") + 1, temperatureMode.indexOf(" ", temperatureMode.indexOf(" ") + 1)).toInt())
                            inputTemperature.setText((temperatureMode.substring(0, temperatureMode.indexOf(" ")).toInt() + decreaseInMinTemperature).toString())

                            heatingElements = temperatureMode[temperatureMode.length - 2] - '0'
                            buttonIncrease.setImageResource(R.drawable.ic_increase)
                            buttonDecrease.setImageResource(R.drawable.ic_decrease)
                            if (heatingElements == maxHeatingElements) {
                                buttonIncrease.setImageResource(R.drawable.ic_increase_disabled)
                            } else if (heatingElements == 1) {
                                buttonDecrease.setImageResource(R.drawable.ic_decrease_disabled)
                            }
                            textHeatingElements.text = getString(R.string.heating_elements_text, heatingElements)

                            editPreferences.putBoolean("isHeatingTemperatureModeStarted", true)
                            editPreferences.putInt("TemperatureModeHeatingElements", heatingElements)
                            editPreferences.putInt("HeatingMaintainedTemperature", inputTemperature.text!!.toString().toInt())
                            editPreferences.putInt("HeatingMinTemperature", temperatureMode.substring(0, temperatureMode.indexOf(" ")).toInt())
                            editPreferences.putInt("HeatingMaxTemperature", temperatureMode.substring(temperatureMode.indexOf(" ") + 1, temperatureMode.indexOf(" ", temperatureMode.indexOf(" ") + 1)).toInt()).apply()

                            cardViewTemperatureRange.alpha = 1f
                            cardViewHeatingStarted.alpha = 1f
                            buttonStopTemperatureMode.alpha = 1f

                            realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("HeatingAndBoiler").child("temperatureMode").addValueEventListener(heatingStartedValueEventListener)
                        } else if (temperatureMode == " " && isTemperatureModeStarted) {
                            cardViewTemperatureRange.visibility = View.GONE
                            cardViewHeatingStarted.visibility = View.GONE
                            buttonStopTemperatureMode.visibility = View.GONE

                            inputLayoutTemperature.visibility = View.VISIBLE
                            buttonStartTemperatureMode.visibility = View.VISIBLE

                            editPreferences.putBoolean("isHeatingTemperatureModeStarted", false)
                            editPreferences.remove("HeatingMaintainedTemperature")
                            editPreferences.remove("HeatingMinTemperature")
                            editPreferences.remove("HeatingMaxTemperature").commit()

                            inputLayoutTemperature.alpha = 1f
                            buttonStartTemperatureMode.alpha = 1f
                        }

                        if (sharedPreferences.getBoolean("TemperatureModeResponse", false) && response == '1') {
                            textResponse.visibility = View.VISIBLE
                            textResponse.alpha = 1f
                            textResponse.text = getString(R.string.response_received_text)
                            editPreferences.putBoolean("TemperatureModeResponse", false).apply()
                            Handler(Looper.getMainLooper()).postDelayed({
                                hideResponseText()
                            }, 3000)
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {}

                })

            responseValueEventListener = object: ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.getValue(String::class.java) != null) {
                        response = snapshot.getValue(String::class.java)!!.last()
                        if (response == '1') {
                            textResponse.text = getString(R.string.response_received_text)
                            editPreferences.putBoolean("TemperatureModeResponse", false).apply()
                            Handler(Looper.getMainLooper()).postDelayed({
                                hideResponseText()
                            }, 3000)
                            realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("HeatingAndBoiler").child("temperatureMode")
                                .removeEventListener(responseValueEventListener)
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {}

            }

            buttonStartTemperatureMode.setOnClickListener {
                vibrate()
                if (isNetworkConnected(requireActivity())) {
                    if (!sharedPreferences.getBoolean("isHeatingStarted", false) && !sharedPreferences.getBoolean("isHeatingTimerStarted", false)) {
                        if (!(isOverCurrentProtectionEnabled && heatingElements == maxHeatingElements && maxHeatingElements > 1 && (sharedPreferences.getBoolean("isBoilerStarted", false) || sharedPreferences.getBoolean("isBoilerTimerStarted", false) || sharedPreferences.getBoolean("isBoilerTimeModeStarted", false)))) {
                            if (inputTemperature.text!!.isNotEmpty()) {
                                hideKeyboard(requireActivity())
                                if (sharedPreferences.getBoolean("isHeatingTimeModeStarted", false)) {
                                    val alertDialogBuilder = androidx.appcompat.app.AlertDialog.Builder(requireActivity())
                                    alertDialogBuilder.setTitle("???????????????????? ??????????????")
                                    alertDialogBuilder.setMessage("???? ???????????? ???????????????????? ???????????? ???? ?????????????????????? ?? ???? ???????????????")
                                    alertDialogBuilder.setPositiveButton("??????????????????????") { _, _ ->
                                        vibrate()
                                        startTemperatureMode()
                                    }
                                    alertDialogBuilder.setNegativeButton("????????????") { _, _ ->
                                        vibrate()
                                    }
                                    alertDialogBuilder.create().show()
                                } else {
                                    startTemperatureMode()
                                }
                            } else {
                                inputLayoutTemperature.isErrorEnabled = true
                                inputLayoutTemperature.error = "?????????????? ???????????????????????????? ??????????????????????"
                            }
                        } else {
                            if (sharedPreferences.getBoolean("isBoilerStarted", false)) {
                                showOverCurrentProtectionAlertDialog("?????? ?????? ???????????? ?????????????? ????????????, ?????? ?????????????????? ???????????????????? ?????????????????????? ???? ????????, ???? ???? ???????????? ?????????????????? ?????????? ???? ??????????????????????. ???? ???????????? ?????????????????? ?????? ?????????????? ?? ????????????????????.")
                            } else if (sharedPreferences.getBoolean("isBoilerTimerStarted", false)) {
                                showOverCurrentProtectionAlertDialog("?????? ?????? ???????????? ?????????????? ???????????? ??????????????, ?????? ?????????????????? ???????????????????? ?????????????????????? ???? ????????, ???? ???? ???????????? ?????????????????? ?????????? ???? ??????????????????????. ???? ???????????? ?????????????????? ?????? ?????????????? ?? ????????????????????.")
                            } else if (sharedPreferences.getBoolean("isBoilerTimeModeStarted", false)) {
                                showOverCurrentProtectionAlertDialog("?????? ?????? ???????????? ?????????????? ?????????? ???? ?????????????? ?????? ??????????????, ?????? ?????????????????? ???????????????????? ?????????????????????? ???? ????????, ???? ???? ???????????? ?????????????????? ?????????? ???? ??????????????????????. ???? ???????????? ?????????????????? ?????? ?????????????? ?? ????????????????????.")
                            }
                        }
                    } else {
                        if (sharedPreferences.getBoolean("isHeatingStarted", false)) {
                            Toast.makeText(requireActivity(), "???? ???? ???????????? ?????????????????? ??????????, ???????? ?????????????? ??????????!", Toast.LENGTH_LONG).show()
                        } else if (sharedPreferences.getBoolean("isHeatingTimerStarted", false)) {
                            Toast.makeText(requireActivity(), "???? ???? ???????????? ?????????????????? ??????????, ???????? ?????????????? ????????????!", Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    Toast.makeText(requireActivity(), "?????? ??????????????????????\n?? ??????????????????!", Toast.LENGTH_LONG).show()
                }
            }

            buttonStopTemperatureMode.setOnClickListener {
                vibrate()
                if (isNetworkConnected(requireActivity())) {
                    isTemperatureModeStarted = false

                    editPreferences.putBoolean("isHeatingTemperatureModeStarted", false)
                    editPreferences.remove("HeatingMaintainedTemperature")
                    editPreferences.remove("HeatingMinTemperature")
                    editPreferences.remove("HeatingMaxTemperature").commit()

                    realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("HeatingAndBoiler").child("heatingStarted").removeEventListener(heatingStartedValueEventListener)
                    realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("HeatingAndBoiler").child("temperatureMode").setValue(" 00")
                    realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("HeatingAndBoiler").child("temperatureMode").addValueEventListener(responseValueEventListener)

                    Handler(Looper.getMainLooper()).postDelayed({
                        if (sharedPreferences.getBoolean("isHeatingTimeModeStarted", false)) {
                            editPreferences.putBoolean("isHeatingTimeModeStarted", false).apply()
                            realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("HeatingAndBoiler").child("heatingOnOffTime").setValue(" 0")
                            realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("HeatingAndBoiler").child("timeHeatingElements").setValue(" ")
                        }
                    }, 250)

                    textResponse.visibility = View.VISIBLE
                    textResponse.animate().alpha(1f).setStartDelay(0L).setDuration(500).start()
                    textResponse.text = getString(R.string.waiting_for_response_text)

                    Handler(Looper.getMainLooper()).postDelayed({
                        if (response == '0') {
                            textResponse.text = getString(R.string.response_not_received_text)
                            editPreferences.putBoolean("TemperatureModeResponse", true).apply()
                        }
                    }, 5000)

                    var isAnimationStarted = true
                    cardViewTemperatureRange.animate().alpha(0f).translationX(-1100f).setDuration(500).setStartDelay(0).start()
                    cardViewHeatingStarted.animate().alpha(0f).translationX(-1100f).setDuration(500).setStartDelay(100).start()
                    buttonStopTemperatureMode.animate().alpha(0f).translationX(-1100f).setDuration(500).setStartDelay(200)
                        .setListener(object: Animator.AnimatorListener {
                            override fun onAnimationStart(animation: Animator) {}

                            override fun onAnimationEnd(animation: Animator) {
                                if (isAnimationStarted) {
                                    isAnimationStarted = false

                                    cardViewTemperatureRange.visibility = View.GONE
                                    cardViewHeatingStarted.visibility = View.GONE
                                    buttonStopTemperatureMode.visibility = View.GONE

                                    inputLayoutTemperature.visibility = View.VISIBLE
                                    buttonStartTemperatureMode.visibility = View.VISIBLE

                                    inputLayoutTemperature.translationX = 1100f
                                    buttonStartTemperatureMode.translationX = 1100f

                                    inputLayoutTemperature.animate().alpha(1f).translationX(0f).setDuration(500).setStartDelay(0).start()
                                    buttonStartTemperatureMode.animate().alpha(1f).translationX(0f).setDuration(500).setStartDelay(100).start()
                                }
                            }

                            override fun onAnimationCancel(animation: Animator) {}
                            override fun onAnimationRepeat(animation: Animator) {}

                        }).start()
                } else {
                    Toast.makeText(requireActivity(), "?????? ??????????????????????\n?? ??????????????????!", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun startTemperatureMode() {
        with(binding) {
            inputLayoutTemperature.isErrorEnabled = false
            isTemperatureModeStarted = true

            editPreferences.putBoolean("isHeatingTemperatureModeStarted", true)
            editPreferences.putInt("HeatingMaintainedTemperature", inputTemperature.text!!.toString().toInt())
            editPreferences.putInt("HeatingMinTemperature", inputTemperature.text!!.toString().toInt() - decreaseInMinTemperature)
            editPreferences.putInt("HeatingMaxTemperature", inputTemperature.text!!.toString().toInt() + increaseInMaxTemperature).apply()

            realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("HeatingAndBoiler").child("temperatureMode").setValue("${inputTemperature.text!!.toString().toInt() - decreaseInMinTemperature} ${inputTemperature.text!!.toString().toInt() + increaseInMaxTemperature} ${heatingElements}00")
            realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("HeatingAndBoiler").child("temperatureMode").addValueEventListener(heatingStartedValueEventListener)
            realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("HeatingAndBoiler").child("temperatureMode").addValueEventListener(responseValueEventListener)

            textResponse.visibility = View.VISIBLE
            textResponse.animate().alpha(1f).setStartDelay(0L).setDuration(500).start()
            textResponse.text = getString(R.string.waiting_for_response_text)

            Handler(Looper.getMainLooper()).postDelayed({
                if (response == '0') {
                    textResponse.text = getString(R.string.response_not_received_text)
                    editPreferences.putBoolean("TemperatureModeResponse", true).apply()
                }
            }, 5000)

            textTemperatureRange.text = getString(R.string.min_max_temperature_text,
                inputTemperature.text!!.toString().toInt() - decreaseInMinTemperature,
                inputTemperature.text!!.toString().toInt() + increaseInMaxTemperature)
            textHeatingStarted.text = getString(R.string.heating_stopped_text)

            var isAnimationStarted = true
            inputLayoutTemperature.animate().alpha(0f).translationX(-1100f).setDuration(500).setStartDelay(0).start()
            buttonStartTemperatureMode.animate().alpha(0f).translationX(-1100f).setDuration(500).setStartDelay(100)
                .setListener(object: Animator.AnimatorListener {
                    override fun onAnimationStart(animation: Animator) {}

                    override fun onAnimationEnd(animation: Animator) {
                        if (isAnimationStarted) {
                            isAnimationStarted = false

                            inputLayoutTemperature.visibility = View.GONE
                            buttonStartTemperatureMode.visibility = View.GONE

                            cardViewTemperatureRange.visibility = View.VISIBLE
                            cardViewHeatingStarted.visibility = View.VISIBLE
                            buttonStopTemperatureMode.visibility = View.VISIBLE

                            cardViewTemperatureRange.translationX = 1100f
                            cardViewHeatingStarted.translationX = 1100f
                            buttonStopTemperatureMode.translationX = 1100f

                            cardViewTemperatureRange.animate().alpha(1f).translationX(0f).setDuration(500).setStartDelay(0).start()
                            cardViewHeatingStarted.animate().alpha(1f).translationX(0f).setDuration(500).setStartDelay(100).start()
                            buttonStopTemperatureMode.animate().alpha(1f).translationX(0f).setDuration(500).setStartDelay(200).start()
                        }
                    }

                    override fun onAnimationCancel(animation: Animator) {}
                    override fun onAnimationRepeat(animation: Animator) {}

                }).start()
        }
    }

    override fun onStart() {
        super.onStart()

        realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("HeatingAndBoiler").child("temperature").addValueEventListener(temperatureValueEventListener)

        if (isTemperatureModeStarted) {
            realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("HeatingAndBoiler").child("temperatureMode").addValueEventListener(heatingStartedValueEventListener)
        }
    }

    override fun onStop() {
        super.onStop()

        realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("HeatingAndBoiler").child("temperature").removeEventListener(temperatureValueEventListener)

        if (isTemperatureModeStarted) {
            realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("HeatingAndBoiler").child("temperatureMode").removeEventListener(heatingStartedValueEventListener)
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

        override fun onCancelled(error: DatabaseError) {}

    }

    private val heatingStartedValueEventListener = object: ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            if (snapshot.getValue(String::class.java) != null) {
                if (snapshot.getValue(String::class.java)!![snapshot.getValue(String::class.java)!!.length - 2] == '1') {
                    binding.textHeatingStarted.text = getString(R.string.heating_started_text)
                } else {
                    binding.textHeatingStarted.text = getString(R.string.heating_stopped_text)
                }
            }
        }

        override fun onCancelled(error: DatabaseError) {}

    }

    private fun hideResponseText() {
        var isAnimationStarted = true
        binding.textResponse.animate().alpha(0f).setStartDelay(0).setDuration(500)
            .setListener(object: Animator.AnimatorListener {
                override fun onAnimationStart(animation: Animator) {}

                override fun onAnimationEnd(animation: Animator) {
                    if (isAnimationStarted) {
                        isAnimationStarted = false
                        binding.textResponse.visibility = View.GONE
                    }
                }

                override fun onAnimationCancel(animation: Animator) {}
                override fun onAnimationRepeat(animation: Animator) {}

            }).start()
    }

    private fun showOverCurrentProtectionAlertDialog(alertDialogMessage: String) {
        val alertDialogBuilder = androidx.appcompat.app.AlertDialog.Builder(requireActivity())
        alertDialogBuilder.setTitle("???????????? ???? ???????????????????? ????????")
        alertDialogBuilder.setMessage(alertDialogMessage)
        alertDialogBuilder.setPositiveButton("??????????????") { _, _ ->
            vibrate()
        }
        alertDialogBuilder.create().show()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentHeatingTemperatureBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    @Suppress("DEPRECATION")
    private fun vibrate() {
        if (MainActivity.vibrator.hasVibrator()) {
            if (MainActivity.isHapticFeedbackEnabled == "1") {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    binding.buttonStartTemperatureMode.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING)
                } else {
                    binding.buttonStartTemperatureMode.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING + HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
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