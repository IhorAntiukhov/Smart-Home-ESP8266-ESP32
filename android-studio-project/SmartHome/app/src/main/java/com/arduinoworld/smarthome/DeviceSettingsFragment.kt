package com.arduinoworld.smarthome

import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.view.HapticFeedbackConstants
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.arduinoworld.smarthome.MainActivity.Companion.editPreferences
import com.arduinoworld.smarthome.MainActivity.Companion.firebaseAuth
import com.arduinoworld.smarthome.MainActivity.Companion.hideKeyboard
import com.arduinoworld.smarthome.MainActivity.Companion.isHapticFeedbackEnabled
import com.arduinoworld.smarthome.MainActivity.Companion.isNetworkConnected
import com.arduinoworld.smarthome.MainActivity.Companion.realtimeDatabase
import com.arduinoworld.smarthome.MainActivity.Companion.sharedPreferences
import com.arduinoworld.smarthome.MainActivity.Companion.vibrator
import com.arduinoworld.smarthome.databinding.FragmentDeviceSettingsBinding

class DeviceSettingsFragment : Fragment() {
    private lateinit var binding: FragmentDeviceSettingsBinding

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        with(binding) {
            inputWiFiSsid.setText(sharedPreferences.getString("WiFiSsid", ""))
            inputWiFiPassword.setText(sharedPreferences.getString("WiFiPassword", ""))

            inputLayoutWiFiSsid.translationX = 1100f
            inputLayoutWiFiPassword.translationX = 1100f
            buttonSendSettings.translationX = 1100f

            layoutDeviceSettings.animate().alpha(1f).translationY(0f).setDuration(500).setStartDelay(0).start()
            inputLayoutWiFiSsid.animate().alpha(1f).translationX(0f).setDuration(500).setStartDelay(0).start()
            inputLayoutWiFiPassword.animate().alpha(1f).translationX(0f).setDuration(500).setStartDelay(100).start()

            var resolution = 0
            var flashState = false
            var verticalFlip = false
            var horizontalMirror = false

            var buttonStartDelay = 0
            val deviceId = sharedPreferences.getInt("DeviceId", 0)
            when (deviceId) {
                0 -> {
                    if (sharedPreferences.getInt("WiFiThermometerInterval", 0) != 0) {
                        inputSensorInterval.setText(sharedPreferences.getInt("WiFiThermometerInterval", 0).toString())
                    }

                    buttonStartDelay = 300
                    inputLayoutSensorInterval.visibility = View.VISIBLE

                    inputLayoutSensorInterval.translationX = 1100f
                    inputLayoutSensorInterval.animate().alpha(1f).translationX(0f).setDuration(500).setStartDelay(200).start()
                }
                1 -> {
                    if (sharedPreferences.getInt("Timezone", 13) != 13) {
                        inputTimezone.setText(sharedPreferences.getInt("Timezone", 13).toString())
                    }
                    inputMaxHeatingElements.setText(sharedPreferences.getString("MaxHeatingElements", "2")!!)
                    if (sharedPreferences.getInt("HeatingTemperatureInterval", 0) != 0) {
                        inputSensorInterval.setText(sharedPreferences.getInt("HeatingTemperatureInterval", 0).toString())
                    }

                    buttonStartDelay = 500
                    inputLayoutMaxHeatingElements.visibility = View.VISIBLE
                    inputLayoutTimezone.visibility = View.VISIBLE
                    inputLayoutSensorInterval.visibility = View.VISIBLE

                    inputLayoutMaxHeatingElements.translationX = 1100f
                    inputLayoutTimezone.translationX = 1100f
                    inputLayoutSensorInterval.translationX = 1100f

                    inputLayoutTimezone.animate().alpha(1f).translationX(0f).setDuration(500).setStartDelay(200).start()
                    inputLayoutMaxHeatingElements.animate().alpha(1f).translationX(0f).setDuration(500).setStartDelay(300).start()
                    inputLayoutSensorInterval.animate().alpha(1f).translationX(0f).setDuration(500).setStartDelay(400).start()
                }
                2 -> {
                    if (sharedPreferences.getInt("Timezone", 13) != 13) {
                        inputTimezone.setText(sharedPreferences.getInt("Timezone", 13).toString())
                    }

                    buttonStartDelay = 300
                    inputLayoutTimezone.visibility = View.VISIBLE
                    inputLayoutTimezone.translationX = 1100f

                    inputLayoutTimezone.animate().alpha(1f).translationX(0f).setDuration(500).setStartDelay(200).start()
                }
                3 -> {
                    if (sharedPreferences.getInt("Timezone", 13) != 13) {
                        inputTimezone.setText(sharedPreferences.getInt("Timezone", 13).toString())
                    }
                    inputPhotoResolution.setText(sharedPreferences.getString("PhotoResolution", "SVGA"), false)
                    resolution = sharedPreferences.getInt("PhotoResolutionInt", 0)
                    flashState = sharedPreferences.getBoolean("FlashState", false)
                    verticalFlip = sharedPreferences.getBoolean("VerticalFlip", false)
                    horizontalMirror = sharedPreferences.getBoolean("HorizontalMirror", false)

                    if (flashState) buttonFlashOnOff.setImageResource(R.drawable.ic_flash_on)
                    if (verticalFlip) buttonVerticalFlip.background = AppCompatResources.getDrawable(requireActivity(), R.drawable.remote_button_background)
                    if (horizontalMirror) buttonHorizontalMirror.background = AppCompatResources.getDrawable(requireActivity(), R.drawable.remote_button_background)
                    switchStartSleep.isChecked = sharedPreferences.getBoolean("StartSleep", true)

                    buttonStartDelay = 600
                    inputLayoutTimezone.visibility = View.VISIBLE
                    inputLayoutPhotoResolution.visibility = View.VISIBLE
                    layoutPhotoSettings.visibility = View.VISIBLE
                    switchStartSleep.visibility = View.VISIBLE

                    inputLayoutTimezone.translationX = 1100f
                    inputLayoutPhotoResolution.translationX = 1100f
                    layoutPhotoSettings.translationX = 1100f
                    switchStartSleep.translationX = 1100f

                    inputLayoutTimezone.animate().alpha(1f).translationX(0f).setDuration(500).setStartDelay(200).start()
                    inputLayoutPhotoResolution.animate().alpha(1f).translationX(0f).setDuration(500).setStartDelay(300).start()
                    layoutPhotoSettings.animate().alpha(1f).translationX(0f).setDuration(500).setStartDelay(400).start()
                    switchStartSleep.animate().alpha(1f).translationX(0f).setDuration(500).setStartDelay(500).start()
                }
                5 -> {
                    if (sharedPreferences.getInt("Timezone", 13) != 13) {
                        inputTimezone.setText(sharedPreferences.getInt("Timezone", 13).toString())
                    }
                    if (sharedPreferences.getInt("HeaterInterval", 0) != 0) {
                        inputSensorInterval.setText(sharedPreferences.getInt("HeaterInterval", 0).toString())
                    }
                    switchEnableNotifications.isChecked = sharedPreferences.getBoolean("EnableNotifications", true)

                    buttonStartDelay = 500
                    inputLayoutTimezone.visibility = View.VISIBLE
                    inputLayoutSensorInterval.visibility = View.VISIBLE
                    switchEnableNotifications.visibility = View.VISIBLE

                    inputLayoutTimezone.translationX = 1100f
                    inputLayoutSensorInterval.translationX = 1100f
                    switchEnableNotifications.translationX = 1100f

                    inputLayoutTimezone.animate().alpha(1f).translationX(0f).setDuration(500).setStartDelay(200).start()
                    inputLayoutSensorInterval.animate().alpha(1f).translationX(0f).setDuration(500).setStartDelay(300).start()
                    switchEnableNotifications.animate().alpha(1f).translationX(0f).setDuration(500).setStartDelay(400).start()
                }
            }
            buttonSendSettings.animate().alpha(1f).translationX(0f).setDuration(500).setStartDelay(buttonStartDelay.toLong()).start()

            inputPhotoResolution.setOnItemClickListener { _, _, position, _ ->
                vibrate()
                resolution = position
            }

            buttonFlashOnOff.setOnClickListener {
                vibrate()
                flashState = !flashState
                if (flashState) {
                    buttonFlashOnOff.setImageResource(R.drawable.ic_flash_on)
                } else {
                    buttonFlashOnOff.setImageResource(R.drawable.ic_flash_off)
                }
            }

            buttonVerticalFlip.setOnClickListener {
                vibrate()
                verticalFlip = !verticalFlip
                if (verticalFlip) {
                    buttonVerticalFlip.background = AppCompatResources.getDrawable(requireActivity(), R.drawable.remote_button_background)
                } else {
                    buttonVerticalFlip.background = AppCompatResources.getDrawable(requireActivity(), R.drawable.disabled_remote_button_background)
                }
            }

            buttonHorizontalMirror.setOnClickListener {
                vibrate()
                horizontalMirror = !horizontalMirror
                if (horizontalMirror) {
                    buttonHorizontalMirror.background = AppCompatResources.getDrawable(requireActivity(), R.drawable.remote_button_background)
                } else {
                    buttonHorizontalMirror.background = AppCompatResources.getDrawable(requireActivity(), R.drawable.disabled_remote_button_background)
                }
            }

            switchStartSleep.setOnClickListener {
                vibrate()
            }

            switchEnableNotifications.setOnClickListener {
                vibrate()
            }

            buttonSendSettings.setOnClickListener {
                vibrate()
                when (deviceId) {
                    0 -> {
                        if (inputWiFiSsid.text!!.isNotEmpty() && inputWiFiPassword.text!!.isNotEmpty() && inputSensorInterval.text!!.isNotEmpty()
                            && inputWiFiPassword.text!!.length >= 8 && inputSensorInterval.text!!.toString().toInt() > 0) {
                            hideKeyboard(requireActivity())

                            binding.inputLayoutWiFiSsid.isErrorEnabled = false
                            binding.inputLayoutWiFiPassword.isErrorEnabled = false
                            inputLayoutSensorInterval.isErrorEnabled = false

                            editPreferences.putString("WiFiSsid", binding.inputWiFiSsid.text.toString())
                            editPreferences.putString("WiFiPassword", binding.inputWiFiPassword.text.toString())
                            editPreferences.putInt("WiFiThermometerInterval", inputSensorInterval.text.toString().toInt()).apply()

                            val stringRequest = StringRequest(
                                Request.Method.POST,
                                "http://192.168.4.1/?ssid=${inputWiFiSsid.text!!}&pass=${inputWiFiPassword.text!!}&email=${sharedPreferences.getString("UserEmail", "")}" +
                                        "&user_pass=${sharedPreferences.getString("UserPassword", "")}&interval=${inputSensorInterval.text!!}",
                                {
                                    Toast.makeText(requireActivity(), "Параметры работы отправлены!", Toast.LENGTH_SHORT).show()
                                },
                                {
                                    Toast.makeText(requireActivity(), "Не удалось отправить параметры работы!", Toast.LENGTH_LONG).show()
                                }
                            )
                            Volley.newRequestQueue(requireActivity()).add(stringRequest)
                        } else {
                            checkWiFiInputs()
                            if (inputSensorInterval.text!!.isEmpty()) {
                                inputLayoutSensorInterval.isErrorEnabled = true
                                inputLayoutSensorInterval.error = "Введите интервал датчика температуры"
                            } else {
                                if (inputSensorInterval.text!!.toString().toInt() == 0) {
                                    inputLayoutSensorInterval.isErrorEnabled = true
                                    inputLayoutSensorInterval.error = "Интервал должен быть больше 0"
                                } else {
                                    inputLayoutSensorInterval.isErrorEnabled = false
                                }
                            }
                        }
                    }
                    1 -> {
                        if (inputSensorInterval.text!!.isEmpty()) {
                            if (inputWiFiSsid.text!!.isNotEmpty() && inputWiFiPassword.text!!.isNotEmpty() && inputTimezone.text!!.isNotEmpty() && inputMaxHeatingElements.text!!.isNotEmpty()
                                && inputWiFiPassword.text!!.length >= 8 && inputTimezone.text!!.toString().toInt() in -11..12 && inputMaxHeatingElements.text!!.toString().toInt() in 1..4) {
                                inputLayoutTimezone.isErrorEnabled = false
                                inputLayoutMaxHeatingElements.isErrorEnabled = false

                                editPreferences.putInt("Timezone", inputTimezone.text.toString().toInt())
                                editPreferences.putString("MaxHeatingElements", inputMaxHeatingElements.text.toString())

                                sendSettings("ssid=${inputWiFiSsid.text!!}&pass=${inputWiFiPassword.text!!}&email=${sharedPreferences.getString("UserEmail", "")}" +
                                        "&user_pass=${sharedPreferences.getString("UserPassword", "")}&timezone=${inputTimezone.text!!}&elements=${inputMaxHeatingElements.text!!}",
                                    "${inputWiFiSsid.text!!}#${inputWiFiPassword.text!!}#${sharedPreferences.getString("UserEmail", "")}" +
                                            "#${sharedPreferences.getString("UserPassword", "")}#${inputTimezone.text!!}#${inputMaxHeatingElements.text!!}", "HeatingAndBoiler")
                            } else {
                                checkWiFiInputs()
                                if (inputTimezone.text!!.isEmpty()) {
                                    inputLayoutTimezone.isErrorEnabled = true
                                    inputLayoutTimezone.error = "Введите часовой пояс"
                                } else {
                                    if (inputTimezone.text!!.toString().toInt() !in -11..12) {
                                        inputLayoutTimezone.isErrorEnabled = true
                                        inputLayoutTimezone.error = "Часовой пояс должен быть от -11 до 12"
                                    } else {
                                        inputLayoutTimezone.isErrorEnabled = false
                                    }
                                }
                                if (inputMaxHeatingElements.text!!.isEmpty()) {
                                    inputLayoutMaxHeatingElements.isErrorEnabled = true
                                    inputLayoutMaxHeatingElements.error = "Введите максимальное количество тэнов"
                                } else {
                                    if (inputMaxHeatingElements.text!!.toString().toInt() !in 1..4) {
                                        inputLayoutMaxHeatingElements.isErrorEnabled = true
                                        inputLayoutMaxHeatingElements.error = "Количество тэнов должно быть от 1 до 4"
                                    } else {
                                        inputLayoutMaxHeatingElements.isErrorEnabled = false
                                    }
                                }
                            }
                        } else {
                            if (inputWiFiSsid.text!!.isNotEmpty() && inputWiFiPassword.text!!.isNotEmpty()
                                && inputWiFiPassword.text!!.length >= 8 && inputSensorInterval.text.toString().toInt() > 0) {
                                hideKeyboard(requireActivity())

                                binding.inputLayoutWiFiSsid.isErrorEnabled = false
                                binding.inputLayoutWiFiPassword.isErrorEnabled = false
                                inputLayoutSensorInterval.isErrorEnabled = false

                                editPreferences.putString("WiFiSsid", binding.inputWiFiSsid.text.toString())
                                editPreferences.putString("WiFiPassword", binding.inputWiFiPassword.text.toString())
                                editPreferences.putInt("HeatingTemperatureInterval", inputSensorInterval.text.toString().toInt()).apply()

                                val stringRequest = StringRequest(
                                    Request.Method.POST,
                                    "http://192.168.4.1/?ssid=${inputWiFiSsid.text!!}&pass=${inputWiFiPassword.text!!}&email=${sharedPreferences.getString("UserEmail", "")}" +
                                        "&user_pass=${sharedPreferences.getString("UserPassword", "")}&interval=${inputSensorInterval.text!!}",
                                    {
                                        Toast.makeText(requireActivity(), "Параметры работы отправлены!", Toast.LENGTH_SHORT).show()
                                    },
                                    {
                                        Toast.makeText(requireActivity(), "Не удалось отправить параметры работы!", Toast.LENGTH_LONG).show()
                                    }
                                )
                                Volley.newRequestQueue(requireActivity()).add(stringRequest)
                            } else {
                                checkWiFiInputs()
                                if (inputSensorInterval.text!!.toString().toInt() == 0) {
                                    inputLayoutSensorInterval.isErrorEnabled = true
                                    inputLayoutSensorInterval.error = "Интервал должен быть больше 0"
                                } else {
                                    inputLayoutSensorInterval.isErrorEnabled = false
                                }
                            }
                        }
                    }
                    2 -> {
                        if (inputWiFiSsid.text!!.isNotEmpty() && inputWiFiPassword.text!!.isNotEmpty() && inputTimezone.text!!.isNotEmpty()
                            && inputWiFiPassword.text!!.length >= 8 && inputTimezone.text!!.toString().toInt() in -11..12) {
                            inputLayoutTimezone.isErrorEnabled = false

                            editPreferences.putInt("Timezone", inputTimezone.text.toString().toInt())

                            sendSettings("ssid=${inputWiFiSsid.text!!}&pass=${inputWiFiPassword.text!!}&email=${sharedPreferences.getString("UserEmail", "")}" +
                                    "&user_pass=${sharedPreferences.getString("UserPassword", "")}&timezone=${inputTimezone.text!!}",
                                "${inputWiFiSsid.text!!}#${inputWiFiPassword.text!!}#${sharedPreferences.getString("UserEmail", "")}" +
                                        "#${sharedPreferences.getString("UserPassword", "")}#${inputTimezone.text!!}", "SmartRemotes")
                        } else {
                            checkWiFiInputs()
                            if (inputTimezone.text!!.isEmpty()) {
                                inputLayoutTimezone.isErrorEnabled = true
                                inputLayoutTimezone.error = "Введите часовой пояс"
                            } else {
                                if (inputTimezone.text!!.toString().toInt() !in -11..12) {
                                    inputLayoutTimezone.isErrorEnabled = true
                                    inputLayoutTimezone.error = "Часовой пояс должен быть от -11 до 12"
                                } else {
                                    inputLayoutTimezone.isErrorEnabled = false
                                }
                            }
                        }
                    }
                    3 -> {
                        if (inputWiFiSsid.text!!.isNotEmpty() && inputWiFiPassword.text!!.isNotEmpty() && inputWiFiPassword.text!!.length >= 8
                            && inputTimezone.text!!.isNotEmpty() && inputTimezone.text!!.toString().toInt() in -11..12) {
                            editPreferences.putInt("Timezone", inputTimezone.text.toString().toInt())
                            editPreferences.putString("PhotoResolution", inputPhotoResolution.text.toString())
                            editPreferences.putInt("PhotoResolutionInt", resolution)
                            editPreferences.putBoolean("FlashState", flashState)
                            editPreferences.putBoolean("VerticalFlip", verticalFlip)
                            editPreferences.putBoolean("HorizontalMirror", horizontalMirror)
                            editPreferences.putBoolean("StartSleep", switchStartSleep.isChecked)

                            sendSettings("ssid=${inputWiFiSsid.text!!}&pass=${inputWiFiPassword.text!!}&email=${sharedPreferences.getString("UserEmail", "")}" +
                                    "&user_pass=${sharedPreferences.getString("UserPassword", "")}&timezone=${inputTimezone.text!!}&resolution=$resolution&flash=${booleanToString(flashState)}" +
                                    "&vflip=${booleanToString(verticalFlip)}&hmirror=${booleanToString(horizontalMirror)}&sleep=${booleanToString(switchStartSleep.isChecked)}",
                                "${inputWiFiSsid.text!!}#${inputWiFiPassword.text!!}#${sharedPreferences.getString("UserEmail", "")}" +
                                        "#${sharedPreferences.getString("UserPassword", "")}#${inputTimezone.text!!}#$resolution${booleanToString(flashState)}${booleanToString(verticalFlip)}" +
                                        "${booleanToString(horizontalMirror)}${booleanToString(switchStartSleep.isChecked)}", "SmartDoorbell")
                        } else {
                            checkWiFiInputs()
                            if (inputTimezone.text!!.isEmpty()) {
                                inputLayoutTimezone.isErrorEnabled = true
                                inputLayoutTimezone.error = "Введите часовой пояс"
                            } else {
                                if (inputTimezone.text!!.toString().toInt() !in -11..12) {
                                    inputLayoutTimezone.isErrorEnabled = true
                                    inputLayoutTimezone.error = "Часовой пояс должен быть от -11 до 12"
                                } else {
                                    inputLayoutTimezone.isErrorEnabled = false
                                }
                            }
                        }
                    }
                    5 -> {
                        if (inputWiFiSsid.text!!.isNotEmpty() && inputWiFiPassword.text!!.isNotEmpty() && inputTimezone.text!!.isNotEmpty() && inputSensorInterval.text!!.isNotEmpty()
                            && inputWiFiPassword.text!!.length >= 8 && inputTimezone.text!!.toString().toInt() in -11..12 && inputSensorInterval.text!!.toString().toInt() > 0) {
                            inputLayoutSensorInterval.isErrorEnabled = false

                            editPreferences.putInt("Timezone", inputTimezone.text.toString().toInt())
                            editPreferences.putInt("HeaterInterval", inputSensorInterval.text.toString().toInt())
                            editPreferences.putBoolean("EnableNotifications", switchEnableNotifications.isChecked)

                            sendSettings("ssid=${inputWiFiSsid.text!!}&pass=${inputWiFiPassword.text!!}&email=${sharedPreferences.getString("UserEmail", "")}&user_pass=${sharedPreferences.getString("UserPassword", "")}" +
                                    "&timezone=${inputTimezone.text!!}&interval=${inputSensorInterval.text!!}&notifications=${booleanToString(switchEnableNotifications.isChecked)}",
                                "${inputWiFiSsid.text!!}#${inputWiFiPassword.text!!}#${sharedPreferences.getString("UserEmail", "")}" +
                                        "#${sharedPreferences.getString("UserPassword", "")}#${inputTimezone.text!!}#${inputSensorInterval.text!!}${booleanToString(switchEnableNotifications.isChecked)}", "Heater")
                        } else {
                            checkWiFiInputs()
                            if (inputTimezone.text!!.isEmpty()) {
                                inputLayoutTimezone.isErrorEnabled = true
                                inputLayoutTimezone.error = "Введите часовой пояс"
                            } else {
                                if (inputTimezone.text!!.toString().toInt() !in -11..12) {
                                    inputLayoutTimezone.isErrorEnabled = true
                                    inputLayoutTimezone.error = "Часовой пояс должен быть от -11 до 12"
                                } else {
                                    inputLayoutTimezone.isErrorEnabled = false
                                }
                            }
                            if (inputSensorInterval.text!!.isEmpty()) {
                                inputLayoutSensorInterval.isErrorEnabled = true
                                inputLayoutSensorInterval.error = "Введите интервал датчика температуры"
                            } else {
                                if (inputSensorInterval.text!!.toString().toInt() == 0) {
                                    inputLayoutSensorInterval.isErrorEnabled = true
                                    inputLayoutSensorInterval.error = "Интервал должен быть больше 0"
                                } else {
                                    inputLayoutSensorInterval.isErrorEnabled = false
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()

        val resolutionsArrayAdapter = ArrayAdapter(requireActivity(), R.layout.photo_resolution_menu_item, listOf("UXGA", "SXGA", "XGA", "SVGA", "VGA", "CIF", "QVGA"))
        binding.inputPhotoResolution.setAdapter(resolutionsArrayAdapter)
        binding.inputPhotoResolution.threshold = 1
    }

    private fun sendSettings(wifiSettings: String, firebaseSettings: String, deviceNodeOnFirebase: String) {
        hideKeyboard(requireActivity())
        binding.inputLayoutWiFiSsid.isErrorEnabled = false
        binding.inputLayoutWiFiPassword.isErrorEnabled = false

        editPreferences.putString("WiFiSsid", binding.inputWiFiSsid.text.toString())
        editPreferences.putString("WiFiPassword", binding.inputWiFiPassword.text.toString()).apply()

        val alertDialogBuilder = androidx.appcompat.app.AlertDialog.Builder(requireActivity())
        alertDialogBuilder.setTitle("Отправка настроек")
        alertDialogBuilder.setMessage("Выберите способ отправки настроек устройства.")
        alertDialogBuilder.setPositiveButton("WiFi") { _, _ ->
            vibrate()
            val stringRequest = StringRequest(
                Request.Method.POST,
                "http://192.168.4.1/?${wifiSettings}",
                {
                    Toast.makeText(requireActivity(), "Параметры работы отправлены!", Toast.LENGTH_SHORT).show()
                },
                {
                    Toast.makeText(requireActivity(), "Не удалось отправить параметры работы!", Toast.LENGTH_LONG).show()
                }
            )
            Volley.newRequestQueue(requireActivity()).add(stringRequest)
        }
        alertDialogBuilder.setNegativeButton("Firebase") { _, _ ->
            vibrate()
            if (isNetworkConnected(requireActivity())) {
                realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child(deviceNodeOnFirebase).child("settings")
                    .setValue(firebaseSettings)
                    .addOnCompleteListener { setValueTask ->
                        if (setValueTask.isSuccessful) {
                            Toast.makeText(requireActivity(), "Параметры работы отправлены!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(requireActivity(), "Не удалось отправить параметры работы!", Toast.LENGTH_LONG).show()
                        }
                    }
            } else {
                Toast.makeText(requireActivity(), "Нет подключения\nк Интернету!", Toast.LENGTH_LONG).show()
            }
        }
        alertDialogBuilder.create().show()
    }

    private fun checkWiFiInputs() {
        with(binding) {
            if (inputWiFiSsid.text!!.isEmpty()) {
                inputLayoutWiFiSsid.isErrorEnabled = true
                inputLayoutWiFiSsid.error = "Введите название WiFi сети"
            } else {
                inputLayoutWiFiSsid.isErrorEnabled = false
            }
            if (inputWiFiPassword.text!!.isEmpty()) {
                inputLayoutWiFiPassword.isErrorEnabled = true
                inputLayoutWiFiPassword.error = "Введите пароль WiFi сети"
            } else {
                if (inputWiFiPassword.text!!.length < 8) {
                    inputLayoutWiFiPassword.isErrorEnabled = true
                    inputLayoutWiFiPassword.error = "Пароль должен быть не меньше 8 символов"
                } else {
                    inputLayoutWiFiPassword.isErrorEnabled = false
                }
            }
        }
    }

    private fun booleanToString(boolean: Boolean): String {
        return if (boolean) {
            "1"
        } else {
            "0"
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentDeviceSettingsBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    @Suppress("DEPRECATION")
    private fun vibrate() {
        if (vibrator.hasVibrator()) {
            if (isHapticFeedbackEnabled == "1") {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    binding.buttonSendSettings.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING)
                } else {
                    binding.buttonSendSettings.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING + HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
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