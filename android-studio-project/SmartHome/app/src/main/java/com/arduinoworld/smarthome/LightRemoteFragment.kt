package com.arduinoworld.smarthome

import android.animation.Animator
import android.content.SharedPreferences
import android.graphics.Color
import android.os.*
import android.view.HapticFeedbackConstants
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.arduinoworld.smarthome.MainActivity.Companion.editPreferences
import com.arduinoworld.smarthome.MainActivity.Companion.firebaseAuth
import com.arduinoworld.smarthome.MainActivity.Companion.isNetworkConnected
import com.arduinoworld.smarthome.MainActivity.Companion.realtimeDatabase
import com.arduinoworld.smarthome.MainActivity.Companion.sharedPreferences
import com.arduinoworld.smarthome.databinding.FragmentLightRemoteBinding
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener

class LightRemoteFragment : Fragment() {
    private lateinit var binding: FragmentLightRemoteBinding
    private lateinit var handler: Handler
    private var lightState = false
    private var remoteSettingsMode = false
    private var isRemoteButtonSelectedEarlier = false
    private var selectedRemoteButtonId = 0
    private var remoteButtonConfiguredArrayList = arrayListOf(false, false, false, false, false, false, false, false, false, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        with(binding) {
            sharedPreferences.registerOnSharedPreferenceChangeListener(sharedPreferencesChangeListener)

            lightState = sharedPreferences.getBoolean("LightState", false)
            if (lightState) textLightState.text = getString(R.string.on_text)

            realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("SmartRemotes").child("lightRemoteButton")
                .addListenerForSingleValueEvent(object: ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        lightState = snapshot.getValue(String::class.java)!!.last() == '1'
                        if (lightState) {
                            textLightState.text = getString(R.string.on_text)
                        } else {
                            textLightState.text = getString(R.string.off_text)
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {}

                })

            buttonOnOff.setOnClickListener {
                if (!remoteSettingsMode) {
                    if (isNetworkConnected(requireActivity())) {
                        lightState = !lightState
                        if (lightState) {
                            textLightState.text = getString(R.string.on_text)
                        } else {
                            textLightState.text = getString(R.string.off_text)
                        }
                        editPreferences.putBoolean("LightState", lightState).apply()
                        sendRemoteButton(1, buttonOnOff)
                    } else {
                        Toast.makeText(requireActivity(), "Нет подключения\nк Интернету!", Toast.LENGTH_LONG).show()
                    }
                } else {
                    sendRemoteButton(1, buttonOnOff)
                }
            }
            buttonNightMode.setOnClickListener { sendRemoteButton(2, buttonNightMode) }
            buttonIncreaseBrightness.setOnClickListener { sendRemoteButton(3, buttonIncreaseBrightness) }
            buttonDecreaseBrightness.setOnClickListener { sendRemoteButton(4, buttonDecreaseBrightness) }
            buttonIncreaseColorTemperature.setOnClickListener { sendRemoteButton(5, buttonIncreaseColorTemperature) }
            buttonDecreaseColorTemperature.setOnClickListener { sendRemoteButton(6, buttonDecreaseColorTemperature) }
            buttonMaxBrightness.setOnClickListener { sendRemoteButton(7, buttonMaxBrightness) }
            buttonTimer.setOnClickListener { sendRemoteButton(8, buttonTimer) }
            buttonHalfBrightness.setOnClickListener { sendRemoteButton(9, buttonHalfBrightness) }
            buttonColorTemperatureMode.setOnClickListener { sendRemoteButton(10, buttonColorTemperatureMode) }

            buttonSaveRemote.setOnClickListener {
                vibrate()
                val stringRequest = StringRequest(
                    Request.Method.POST,
                    "http://192.168.4.1/?save_light_remote=1",
                    {
                        Toast.makeText(requireActivity(), "Пульт сохранён!", Toast.LENGTH_LONG).show()
                    },
                    {
                        Toast.makeText(requireActivity(), "Не удалось отправить команду на сохранение пульта!", Toast.LENGTH_LONG).show()
                    }
                )
                Volley.newRequestQueue(requireActivity()).add(stringRequest)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentLightRemoteBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    private fun sendRemoteButton(remoteButton: Int, button: ImageButton) {
        vibrate()
        if (!remoteSettingsMode) {
            if (lightState || remoteButton == 1) {
                if (isNetworkConnected(requireActivity())) {
                    val lightStateString: String = if (lightState) {
                        "1"
                    } else {
                        "0"
                    }

                    realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("SmartRemotes")
                        .child("lightRemoteButton").setValue(remoteButton.toString() + " " + (1000..9999).random().toString() + lightStateString)
                } else {
                    Toast.makeText(requireActivity(), "Нет подключения\nк Интернету!", Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(requireActivity(), "Вы не можете управлять освещением, пока оно выключено!", Toast.LENGTH_LONG).show()
            }
        } else {
            selectedRemoteButtonId = remoteButton
            updateRemoteButtons()

            if (remoteButton == 3 || remoteButton == 4 || remoteButton == 5 || remoteButton == 6) {
                button.setColorFilter(Color.parseColor("#473D95"), android.graphics.PorterDuff.Mode.SRC_IN)
            } else {
                button.backgroundTintList = ContextCompat.getColorStateList(requireActivity(), R.color.dark_color)
            }
            val stringRequest = StringRequest(
                Request.Method.POST,
                "http://192.168.4.1/?light_remote=${remoteButton}",
                {},
                {
                    Toast.makeText(requireActivity(), "Не удалось отправить\nномер кнопки!", Toast.LENGTH_LONG).show()
                }
            )
            Volley.newRequestQueue(requireActivity()).add(stringRequest)

            if (!isRemoteButtonSelectedEarlier) {
                isRemoteButtonSelectedEarlier = true

                handler = Handler(Looper.getMainLooper())
                sendRequestRunnable.run()
            }
        }
    }

    private val sharedPreferencesChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            with(binding) {
                if (key == "OpenRemoteSettingsMode") {
                    if (sharedPreferences.getBoolean("OpenRemoteSettingsMode", false)) {
                        editPreferences.putBoolean("OpenRemoteSettingsMode", false).apply()
                        remoteSettingsMode = true
                        var isAnimationStarted = true
                        layoutSelectButton.visibility = View.VISIBLE
                        buttonSaveRemote.visibility = View.VISIBLE
                        textLightState.animate().alpha(0f).setDuration(500).setStartDelay(0).start()
                        layoutSelectButton.animate().alpha(1f).setDuration(500).setStartDelay(0).start()
                        buttonSaveRemote.animate().alpha(1f).setDuration(500).setStartDelay(0)
                            .setListener(object: Animator.AnimatorListener {
                                override fun onAnimationStart(animation: Animator) {}

                                override fun onAnimationEnd(animation: Animator) {
                                    if (isAnimationStarted) {
                                        isAnimationStarted = false

                                        textLightState.visibility = View.GONE
                                        imageFirstCircleRemote.visibility = View.INVISIBLE
                                        updateRemoteButtons()
                                    }
                                }

                                override fun onAnimationCancel(animation: Animator) {}
                                override fun onAnimationRepeat(animation: Animator) {}

                            }).start()
                    }
                } else if (key == "CloseRemoteSettingsMode") {
                    if (sharedPreferences.getBoolean("CloseRemoteSettingsMode", false)) {
                        editPreferences.putBoolean("CloseRemoteSettingsMode", false).apply()
                        isRemoteButtonSelectedEarlier = false
                        remoteSettingsMode = false
                        selectedRemoteButtonId = 0

                        var isAnimationStarted = true
                        layoutSelectButton.animate().alpha(0f).setDuration(500).setStartDelay(0).start()
                        buttonSaveRemote.animate().alpha(0f).setDuration(500).setStartDelay(0)
                            .setListener(object: Animator.AnimatorListener {
                                override fun onAnimationStart(animation: Animator) {}

                                override fun onAnimationEnd(animation: Animator) {
                                    if (isAnimationStarted) {
                                        isAnimationStarted = false

                                        layoutSelectButton.visibility = View.GONE
                                        buttonSaveRemote.visibility = View.GONE

                                        textLightState.visibility = View.VISIBLE
                                        imageFirstCircleRemote.visibility = View.VISIBLE

                                        textLightState.animate().alpha(1f).setDuration(500).setStartDelay(0).start()

                                        remoteButtonConfiguredArrayList = arrayListOf(true, true, true, true, true, true, true, true, true, true)
                                        updateRemoteButtons()
                                        remoteButtonConfiguredArrayList = arrayListOf(false, false, false, false, false, false, false, false, false, false)

                                        buttonIncreaseBrightness.setColorFilter(Color.parseColor("#FFFFFF"), android.graphics.PorterDuff.Mode.SRC_IN)
                                        buttonDecreaseBrightness.setColorFilter(Color.parseColor("#FFFFFF"), android.graphics.PorterDuff.Mode.SRC_IN)
                                        buttonIncreaseColorTemperature.setColorFilter(Color.parseColor("#FFFFFF"), android.graphics.PorterDuff.Mode.SRC_IN)
                                        buttonDecreaseColorTemperature.setColorFilter(Color.parseColor("#FFFFFF"), android.graphics.PorterDuff.Mode.SRC_IN)
                                    }
                                }

                                override fun onAnimationCancel(animation: Animator) {}
                                override fun onAnimationRepeat(animation: Animator) {}

                            }).start()
                    }
                }
            }
        }

    private fun updateRemoteButtons() {
        with(binding) {
            if (remoteButtonConfiguredArrayList[0]) {
                buttonOnOff.backgroundTintList = ContextCompat.getColorStateList(requireActivity(), R.color.primary_color)
            } else {
                buttonOnOff.backgroundTintList = ContextCompat.getColorStateList(requireActivity(), R.color.secondary_color)
            }
            if (remoteButtonConfiguredArrayList[1]) {
                buttonNightMode.backgroundTintList = ContextCompat.getColorStateList(requireActivity(), R.color.primary_color)
            } else {
                buttonNightMode.backgroundTintList = ContextCompat.getColorStateList(requireActivity(), R.color.secondary_color)
            }
            if (remoteButtonConfiguredArrayList[2]) {
                buttonIncreaseBrightness.setColorFilter(Color.parseColor("#5347AE"), android.graphics.PorterDuff.Mode.SRC_IN)
            } else {
                buttonIncreaseBrightness.setColorFilter(Color.parseColor("#6A61AD"), android.graphics.PorterDuff.Mode.SRC_IN)
            }
            if (remoteButtonConfiguredArrayList[3]) {
                buttonDecreaseBrightness.setColorFilter(Color.parseColor("#5347AE"), android.graphics.PorterDuff.Mode.SRC_IN)
            } else {
                buttonDecreaseBrightness.setColorFilter(Color.parseColor("#6A61AD"), android.graphics.PorterDuff.Mode.SRC_IN)
            }
            if (remoteButtonConfiguredArrayList[4]) {
                buttonIncreaseColorTemperature.setColorFilter(Color.parseColor("#5347AE"), android.graphics.PorterDuff.Mode.SRC_IN)
            } else {
                buttonIncreaseColorTemperature.setColorFilter(Color.parseColor("#6A61AD"), android.graphics.PorterDuff.Mode.SRC_IN)
            }
            if (remoteButtonConfiguredArrayList[5]) {
                buttonDecreaseColorTemperature.setColorFilter(Color.parseColor("#5347AE"), android.graphics.PorterDuff.Mode.SRC_IN)
            } else {
                buttonDecreaseColorTemperature.setColorFilter(Color.parseColor("#6A61AD"), android.graphics.PorterDuff.Mode.SRC_IN)
            }
            if (remoteButtonConfiguredArrayList[6]) {
                buttonMaxBrightness.backgroundTintList = ContextCompat.getColorStateList(requireActivity(), R.color.primary_color)
            } else {
                buttonMaxBrightness.backgroundTintList = ContextCompat.getColorStateList(requireActivity(), R.color.secondary_color)
            }
            if (remoteButtonConfiguredArrayList[7]) {
                buttonTimer.backgroundTintList = ContextCompat.getColorStateList(requireActivity(), R.color.primary_color)
            } else {
                buttonTimer.backgroundTintList = ContextCompat.getColorStateList(requireActivity(), R.color.secondary_color)
            }
            if (remoteButtonConfiguredArrayList[8]) {
                buttonHalfBrightness.backgroundTintList = ContextCompat.getColorStateList(requireActivity(), R.color.primary_color)
            } else {
                buttonHalfBrightness.backgroundTintList = ContextCompat.getColorStateList(requireActivity(), R.color.secondary_color)
            }
            if (remoteButtonConfiguredArrayList[9]) {
                buttonColorTemperatureMode.backgroundTintList = ContextCompat.getColorStateList(requireActivity(), R.color.primary_color)
            } else {
                buttonColorTemperatureMode.backgroundTintList = ContextCompat.getColorStateList(requireActivity(), R.color.secondary_color)
            }
        }
    }

    private val sendRequestRunnable : Runnable = object : Runnable {
        override fun run() {
            if (remoteSettingsMode) {
                val isButtonConfiguredRequest = StringRequest(
                    Request.Method.POST,
                    "http://192.168.4.1/light_button_configured",
                    {
                        val result = it.toString().substring(0, it.length - 2)
                        if (result.last() == '1') {
                            if (!remoteButtonConfiguredArrayList[result.substring(0, result.length - 1).toInt() - 1]) {
                                remoteButtonConfiguredArrayList[result.substring(0, result.length - 1).toInt() - 1] = true
                                updateRemoteButtons()
                                Toast.makeText(requireActivity(), "Кнопка настроена!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    {}
                )
                Volley.newRequestQueue(requireActivity()).add(isButtonConfiguredRequest)
                handler.postDelayed(this, 500)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        sharedPreferences.unregisterOnSharedPreferenceChangeListener(sharedPreferencesChangeListener)
    }

    @Suppress("DEPRECATION")
    private fun vibrate() {
        if (MainActivity.vibrator.hasVibrator()) {
            if (MainActivity.isHapticFeedbackEnabled == "1") {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    binding.buttonOnOff.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING)
                } else {
                    binding.buttonOnOff.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING + HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
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