package com.arduinoworld.smarthome

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.arduinoworld.smarthome.MainActivity.Companion.binding
import com.arduinoworld.smarthome.MainActivity.Companion.editPreferences
import com.arduinoworld.smarthome.MainActivity.Companion.isHapticFeedbackEnabled
import com.arduinoworld.smarthome.MainActivity.Companion.sharedPreferences
import com.arduinoworld.smarthome.MainActivity.Companion.vibrator
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        val wifiThermometerGraphInterval = preferenceManager.findPreference<Preference>("WiFiThermometerGraphInterval")!!

        val maxHeatingElements = preferenceManager.findPreference<Preference>("MaxHeatingElements")!!
        val heatingDecreaseInMinTemperature = preferenceManager.findPreference<Preference>("HeatingDecreaseInMinTemperature")!!
        val heatingIncreaseInMaxTemperature = preferenceManager.findPreference<Preference>("HeatingIncreaseInMaxTemperature")!!
        
        val minTemperature = preferenceManager.findPreference<Preference>("MinTemperature")!!
        val maxTemperature = preferenceManager.findPreference<Preference>("MaxTemperature")!!
        
        val maxPhotos = preferenceManager.findPreference<Preference>("MaxPhotos")!!
        
        val meterDigitsAfterPoint = preferenceManager.findPreference<Preference>("MeterDigitsAfterPoint")!!
        val maxStorageTimeOfMeterReadings = preferenceManager.findPreference<Preference>("MaxStorageTimeOfMeterReadings")!!

        val heaterDecreaseInMinTemperature = preferenceManager.findPreference<Preference>("HeaterDecreaseInMinTemperature")!!
        val heaterIncreaseInMaxTemperature = preferenceManager.findPreference<Preference>("HeaterIncreaseInMaxTemperature")!!

        val isHapticFeedbackEnabled = preferenceManager.findPreference<Preference>("isHapticFeedbackEnabled")!!

        preferenceManager.findPreference<Preference>("buttonClearWiFiThermometerGraph")!!.setOnPreferenceClickListener {
            vibrate()
            val alertDialogBuilder = androidx.appcompat.app.AlertDialog.Builder(requireActivity())
            alertDialogBuilder.setTitle("Очистка данных графика")
            alertDialogBuilder.setMessage("Вы точно хотите удалить все показания датчика на графике?")
            alertDialogBuilder.setPositiveButton("Подтвердить") { _, _ ->
                vibrate()
                editPreferences.remove("TemperatureEntriesArrayList")
                editPreferences.remove("HumidityEntriesArrayList")
                editPreferences.remove("WiFiThermometerTimestampsArrayList").commit()
                Toast.makeText(requireActivity(), "Данные графика очищены!", Toast.LENGTH_SHORT).show()
            }
            alertDialogBuilder.setNegativeButton("Отмена") { _, _ ->
                vibrate()
            }
            alertDialogBuilder.create().show()
            true
        }

        wifiThermometerGraphInterval.setOnPreferenceClickListener {
            vibrate()
            true
        }
        wifiThermometerGraphInterval.setOnPreferenceChangeListener { _, _ ->
            vibrate()
            true
        }

        preferenceManager.findPreference<Preference>("ShowBatteryLevelInNotification")!!.setOnPreferenceClickListener {
            vibrate()
            true
        }

        maxHeatingElements.setOnPreferenceClickListener {
            vibrate()
            true
        }
        maxHeatingElements.setOnPreferenceChangeListener { _, _ ->
            vibrate()
            true
        }

        heatingDecreaseInMinTemperature.setOnPreferenceClickListener {
            vibrate()
            true
        }
        heatingDecreaseInMinTemperature.setOnPreferenceChangeListener { _, _ ->
            vibrate()
            true
        }

        heatingIncreaseInMaxTemperature.setOnPreferenceClickListener {
            vibrate()
            true
        }
        heatingIncreaseInMaxTemperature.setOnPreferenceChangeListener { _, _ ->
            vibrate()
            true
        }

        preferenceManager.findPreference<Preference>("isOverCurrentProtectionEnabled")!!.setOnPreferenceClickListener {
            vibrate()
            true
        }

        minTemperature.setOnPreferenceClickListener {
            vibrate()
            true
        }
        minTemperature.setOnPreferenceChangeListener { _, _ ->
            vibrate()
            true
        }

        maxTemperature.setOnPreferenceClickListener {
            vibrate()
            true
        }
        maxTemperature.setOnPreferenceChangeListener { _, _ ->
            vibrate()
            true
        }

        maxPhotos.setOnPreferenceClickListener {
            vibrate()
            true
        }
        maxPhotos.setOnPreferenceChangeListener { _, _ ->
            vibrate()
            true
        }

        preferenceManager.findPreference<Preference>("buttonSelectPhotosDirectory")!!.setOnPreferenceClickListener {
            vibrate()
            val directoryChooser = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            directoryChooser.addCategory(Intent.CATEGORY_DEFAULT)
            getResult.launch(Intent.createChooser(directoryChooser, "Выберите папку"))
            true
        }

        preferenceManager.findPreference<Preference>("buttonDeleteAllPhotos")!!.setOnPreferenceClickListener {
            vibrate()
            if (sharedPreferences.getString("PhotosArrayList", "") != "") {
                val gson = Gson()
                if (sharedPreferences.getString("PhotosArrayList", "") != "") {
                    val photosArrayList = gson.fromJson<ArrayList<Photo>>(
                        sharedPreferences.getString("PhotosArrayList", ""),
                        object : TypeToken<ArrayList<Photo?>?>() {}.type
                    )
                    photosArrayList.forEach { photo ->
                        Firebase.storage.getReferenceFromUrl(photo.photoUrl).delete()
                            .addOnFailureListener {
                                Toast.makeText(requireActivity(), "Не удалось удалить фото ${photo.photoName}!", Toast.LENGTH_LONG).show()
                            }
                    }
                }
                editPreferences.remove("PhotosArrayList").commit()
            } else {
                Toast.makeText(requireActivity(), "Вы не получили ни одной фотографии!", Toast.LENGTH_LONG).show()
            }
            true
        }

        meterDigitsAfterPoint.setOnPreferenceClickListener {
            vibrate()
            true
        }
        meterDigitsAfterPoint.setOnPreferenceChangeListener { _, _ ->
            vibrate()
            true
        }

        maxStorageTimeOfMeterReadings.setOnPreferenceClickListener {
            vibrate()
            true
        }
        maxStorageTimeOfMeterReadings.setOnPreferenceChangeListener { _, _ ->
            vibrate()
            true
        }

        preferenceManager.findPreference<Preference>("buttonClearMeterReaderGraph")!!.setOnPreferenceClickListener {
            vibrate()
            val alertDialogBuilder = androidx.appcompat.app.AlertDialog.Builder(requireActivity())
            alertDialogBuilder.setTitle("Очистка данных графика")
            alertDialogBuilder.setMessage("Вы точно хотите удалить все показания счётчика на графике?")
            alertDialogBuilder.setPositiveButton("Подтвердить") { _, _ ->
                vibrate()
                editPreferences.remove("MeterReadingsArrayList")
                editPreferences.remove("MeterReadingsTimeArrayList").commit()
                Toast.makeText(requireActivity(), "Данные графика очищены!", Toast.LENGTH_SHORT).show()
            }
            alertDialogBuilder.setNegativeButton("Отмена") { _, _ ->
                vibrate()
            }
            alertDialogBuilder.create().show()
            true
        }

        heaterDecreaseInMinTemperature.setOnPreferenceClickListener {
            vibrate()
            true
        }
        heaterDecreaseInMinTemperature.setOnPreferenceChangeListener { _, _ ->
            vibrate()
            true
        }

        heaterIncreaseInMaxTemperature.setOnPreferenceClickListener {
            vibrate()
            true
        }
        heaterIncreaseInMaxTemperature.setOnPreferenceChangeListener { _, _ ->
            vibrate()
            true
        }

        preferenceManager.findPreference<Preference>("isHeaterNotificationsEnabled")!!.setOnPreferenceClickListener {
            vibrate()
            true
        }

        isHapticFeedbackEnabled.setOnPreferenceClickListener {
            vibrate()
            true
        }
        isHapticFeedbackEnabled.setOnPreferenceChangeListener { _, newValue ->
            vibrate()
            MainActivity.isHapticFeedbackEnabled = newValue as String
            true
        }

        preferenceManager.findPreference<Preference>("buttonDefaultSettings")!!.setOnPreferenceClickListener {
            vibrate()
            val alertDialogBuilder = androidx.appcompat.app.AlertDialog.Builder(requireActivity())
            alertDialogBuilder.setTitle("Настройки по умолчанию")
            alertDialogBuilder.setMessage("Установить настройки по умолчанию?")
            alertDialogBuilder.setPositiveButton("Подтвердить") { _, _ ->
                vibrate()
                MainActivity.isHapticFeedbackEnabled = "1"
                editPreferences.putBoolean("ShowBatteryLevelInNotification", true)
                editPreferences.putString("MaxHeatingElements", "2")
                editPreferences.putString("HeatingDecreaseInMinTemperature", "2")
                editPreferences.putString("heatingIncreaseInMaxTemperature", "1")
                editPreferences.putBoolean("isOverCurrentProtectionEnabled", true)
                editPreferences.putString("MinTemperature", "16")
                editPreferences.putString("MaxTemperature", "30")
                editPreferences.putString("MaxPhotos", "5")
                editPreferences.putString("PhotosDirectory", "Pictures/Дверной Звонок")
                editPreferences.putString("MeterDigitsAfterPoint", "2")
                editPreferences.putString("MaxPhotos", "5")
                editPreferences.putString("MaxStorageTimeOfMeterReadings", "7")
                editPreferences.putString("HeaterDecreaseInMinTemperature", "2")
                editPreferences.putString("HeaterIncreaseInMaxTemperature", "1")
                editPreferences.putString("HeaterIncreaseInMaxTemperature", "1")
                editPreferences.putBoolean("isHeaterNotificationsEnabled", true)
                editPreferences.putString("isHapticFeedbackEnabled", "1").apply()
                Toast.makeText(requireActivity(), "Установлены настройки по умолчанию!", Toast.LENGTH_SHORT).show()
            }
            alertDialogBuilder.setNegativeButton("Отмена") { _, _ ->
                vibrate()
            }
            alertDialogBuilder.create().show()
            true
        }
    }

    private val getResult =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            if (it.resultCode == Activity.RESULT_OK) {
                val directory = it.data!!.data!!.path.toString().substring(14, it.data!!.data!!.path.toString().length) + "/Умный Звонок"
                editPreferences.putString("PhotosDirectory", directory).apply()
            }
        }

    @Suppress("DEPRECATION")
    fun vibrate() {
        if (vibrator.hasVibrator()) {
            if (isHapticFeedbackEnabled == "1") {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    binding.frameLayoutMain.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING)
                } else {
                    binding.frameLayoutMain.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING + HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
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