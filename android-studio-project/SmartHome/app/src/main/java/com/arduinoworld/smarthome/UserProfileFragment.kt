package com.arduinoworld.smarthome

import android.animation.Animator
import android.content.SharedPreferences
import android.os.*
import android.util.Patterns
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
import com.arduinoworld.smarthome.MainActivity.Companion.isUserLogged
import com.arduinoworld.smarthome.MainActivity.Companion.realtimeDatabase
import com.arduinoworld.smarthome.MainActivity.Companion.sharedPreferences
import com.arduinoworld.smarthome.MainActivity.Companion.vibrator
import com.arduinoworld.smarthome.databinding.FragmentUserProfileBinding
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class UserProfileFragment : Fragment() {
    private lateinit var binding: FragmentUserProfileBinding

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        with(binding) {
            val fragmentList = listOf(SignInFragment(), SignUpFragment())
            val adapter = ViewPagerAdapter(requireActivity(), fragmentList)
            viewPager.adapter = adapter
            TabLayoutMediator(tabLayout, viewPager) { tab, position ->
                if (position == 0) {
                    tab.text = getString(R.string.tab_item_sign_in)
                } else {
                    tab.text = getString(R.string.tab_item_sign_up)
                }
            }.attach()

            sharedPreferences.registerOnSharedPreferenceChangeListener(sharedPreferencesChangeListener)

            if (isUserLogged) {
                textUserEmail.text = sharedPreferences.getString("UserEmail", "")
                textUserPassword.text = sharedPreferences.getString("UserPassword", "")
                hideSignInShowProfile()
            }

            buttonLogout.cardView.setOnClickListener {
                vibrate()
                if (isNetworkConnected(requireActivity())) {
                    firebaseAuth.signOut()
                    editPreferences.putBoolean("isUserLogged", false).apply()
                    isUserLogged = false
                    hideProfileShownSignIn()
                } else {
                    Toast.makeText(requireActivity(), "Нет подключения\nк Интернету!", Toast.LENGTH_LONG).show()
                }
            }

            buttonDeleteUser.cardView.setOnClickListener {
                vibrate()
                if (isNetworkConnected(requireActivity())) {
                    val alertDialogBuilder = androidx.appcompat.app.AlertDialog.Builder(requireActivity())
                    alertDialogBuilder.setTitle("Удаление пользователя")
                    alertDialogBuilder.setMessage("Вы точно хотите удалить пользователя? Вместе с ним также удалятся все ваши устройства.")
                    alertDialogBuilder.setPositiveButton("Подтвердить") { _, _ ->
                        vibrate()
                        buttonDeleteUser.imageButton.visibility = View.GONE
                        buttonDeleteUser.textButton.visibility = View.GONE
                        buttonDeleteUser.progressBarButton.visibility = View.VISIBLE

                        if (sharedPreferences.getString("DevicesArrayList", "") != "") {
                            val gson = Gson()
                            val addedDevicesArrayList: ArrayList<Device> = gson.fromJson(
                                sharedPreferences.getString("DevicesArrayList", ""),
                                object : TypeToken<ArrayList<Device?>?>() {}.type
                            )

                            with(realtimeDatabase.child(firebaseAuth.currentUser!!.uid)) {
                                addedDevicesArrayList.forEach { device ->
                                    when (device.deviceName) {
                                        getString(R.string.text_wifi_thermometer) -> {
                                            editPreferences.remove("TemperatureEntriesArrayList")
                                            editPreferences.remove("HumidityEntriesArrayList")
                                            editPreferences.remove("WiFiThermometerTimestampsArrayList").commit()

                                            child("WiFiThermometer").child("temperatureHumidity").removeValue()
                                            child("WiFiThermometer").child("batteryLevel").removeValue()
                                        }
                                        getString(R.string.text_heating_and_boiler) -> {
                                            val nodesList = listOf("heatingStarted", "heatingElements", "boilerStarted",
                                                "heatingTimerTime", "boilerTimerTime", "heatingOnOffTime", "timeHeatingElements", "boilerOnOffTime",
                                                "temperatureMode", "temperature", "settings")
                                            nodesList.forEach { node ->
                                                child("HeatingAndBoiler").child(node).removeValue()
                                            }
                                        }
                                        getString(R.string.text_smart_ir_remote) -> {
                                            val nodesList = listOf("lightRemoteButton", "tvRemoteButton",
                                                "rgbRemoteButton", "acRemote", "acOnOffTime", "settings")
                                            nodesList.forEach { node ->
                                                child("SmartIRRemote").child(node).removeValue()
                                            }
                                        }
                                        getString(R.string.text_smart_doorbell) -> {
                                            child("SmartDoorbell").child("photoUrl").removeValue()
                                            child("SmartDoorbell").child("settings").removeValue()
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
                                        }
                                        getString(R.string.text_meter_reader) -> {
                                            child("MeterReader").addChildEventListener(object: ChildEventListener {
                                                override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                                                    child("MeterReader").child(snapshot.key!!).removeValue()
                                                }

                                                override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
                                                override fun onChildRemoved(snapshot: DataSnapshot) {}
                                                override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
                                                override fun onCancelled(error: DatabaseError) {}

                                            })
                                        }
                                        getString(R.string.text_smart_heater) -> {
                                            val nodesList = listOf("heaterStarted", "heaterOnOffTime", "temperatureRange", "temperature", "settings")
                                            nodesList.forEach { node ->
                                                child("Heater").child(node).removeValue()
                                            }
                                        }
                                    }
                                }
                            }
                            editPreferences.putString("DevicesArrayList", "").apply()
                            Handler(Looper.getMainLooper()).postDelayed({
                                deleteUser()
                            }, 300)
                        } else {
                            deleteUser()
                        }
                    }
                    alertDialogBuilder.setNegativeButton("Отмена") { _, _ ->
                        vibrate()
                    }
                    alertDialogBuilder.create().show()
                } else {
                    Toast.makeText(requireActivity(), "Нет подключения\nк Интернету!", Toast.LENGTH_LONG).show()
                }
            }

            buttonChangeEmail.setOnClickListener {
                vibrate()
                if (isNetworkConnected(requireActivity())) {
                    var isAnimationStarted = true
                    cardViewUserEmail.animate().alpha(0f).translationX(-1100f).setDuration(500).setStartDelay(0).start()
                    cardViewUserPassword.animate().alpha(0f).translationX(-1100f).setDuration(500).setStartDelay(100).start()
                    layoutUserSettings.animate().alpha(0f).translationX(-1100f).setDuration(500).setStartDelay(200)
                        .setListener(object: Animator.AnimatorListener {
                            override fun onAnimationStart(animation: Animator) {}

                            override fun onAnimationEnd(animation: Animator) {
                                if (isAnimationStarted) {
                                    isAnimationStarted = false

                                    cardViewUserEmail.visibility = View.GONE
                                    cardViewUserPassword.visibility = View.GONE
                                    layoutUserSettings.visibility = View.GONE

                                    inputLayoutUserEmail.visibility = View.VISIBLE
                                    buttonUpdateUser.visibility = View.VISIBLE

                                    inputLayoutUserEmail.translationX = 1100f
                                    buttonUpdateUser.translationX = 1100f

                                    inputLayoutUserEmail.animate().alpha(1f).translationX(0f).setDuration(500).setStartDelay(0).start()
                                    buttonUpdateUser.animate().alpha(1f).translationX(0f).setDuration(500).setStartDelay(100).start()

                                    buttonUpdateUser.setOnClickListener {
                                        vibrate()
                                        if (isNetworkConnected(requireActivity())) {
                                            if (inputUserEmail.text!!.isNotEmpty() && isValidEmail(inputUserEmail.text.toString()) && inputUserEmail.text.toString() != sharedPreferences.getString("UserEmail", "")) {
                                                hideKeyboard(requireActivity())
                                                firebaseAuth.currentUser!!.reauthenticate(EmailAuthProvider.getCredential(
                                                    sharedPreferences.getString("UserEmail", "").toString(),
                                                    sharedPreferences.getString("UserPassword", "").toString()))
                                                    .addOnCompleteListener { reauthTask ->
                                                        if (reauthTask.isSuccessful) {
                                                            firebaseAuth.currentUser!!.updateEmail(inputUserEmail.text.toString())
                                                            editPreferences.putString("UserEmail", inputUserEmail.text.toString()).apply()
                                                            textUserEmail.text = inputUserEmail.text.toString()
                                                            Toast.makeText(requireActivity(), "Почта обновлена!", Toast.LENGTH_SHORT).show()

                                                            var isUpdateUserAnimationStarted = true
                                                            inputLayoutUserEmail.animate().alpha(0f).translationX(-1100f).setDuration(500).setStartDelay(0).start()
                                                            buttonUpdateUser.animate().alpha(0f).translationX(-1100f).setDuration(500).setStartDelay(100)
                                                                .setListener(object: Animator.AnimatorListener {
                                                                    override fun onAnimationStart(animation: Animator) {}

                                                                    override fun onAnimationEnd(animation: Animator) {
                                                                        if (isUpdateUserAnimationStarted) {
                                                                            isUpdateUserAnimationStarted = false

                                                                            inputLayoutUserEmail.visibility = View.GONE
                                                                            buttonUpdateUser.visibility = View.GONE

                                                                            cardViewUserEmail.visibility = View.VISIBLE
                                                                            cardViewUserPassword.visibility = View.VISIBLE
                                                                            layoutUserSettings.visibility = View.VISIBLE

                                                                            cardViewUserEmail.translationX = 1100f
                                                                            cardViewUserPassword.translationX = 1100f
                                                                            layoutUserSettings.translationX = 1100f

                                                                            cardViewUserEmail.animate().alpha(1f).translationX(0f).setDuration(500).setStartDelay(0).start()
                                                                            cardViewUserPassword.animate().alpha(1f).translationX(0f).setDuration(500).setStartDelay(100).start()
                                                                            layoutUserSettings.animate().alpha(1f).translationX(0f).setDuration(500).setStartDelay(200).start()
                                                                        }
                                                                    }

                                                                    override fun onAnimationCancel(animation: Animator) {}
                                                                    override fun onAnimationRepeat(animation: Animator) {}

                                                                }).start()
                                                        } else {
                                                            editPreferences.putBoolean("isUserLogged", false).apply()
                                                            isUserLogged = false
                                                            Toast.makeText(requireActivity(), "Не удалось переавторизироваться!", Toast.LENGTH_LONG).show()
                                                        }
                                                    }
                                            } else {
                                                if (inputUserEmail.text!!.isEmpty()) {
                                                    inputLayoutUserEmail.isErrorEnabled = true
                                                    inputLayoutUserEmail.error = "Введите почту пользователя"
                                                } else {
                                                    if (!isValidEmail(inputUserEmail.text.toString())) {
                                                        inputLayoutUserEmail.isErrorEnabled = true
                                                        inputLayoutUserEmail.error = "Неправильная почта"
                                                    } else {
                                                        if (inputUserEmail.text.toString() == sharedPreferences.getString("UserEmail", "")) {
                                                            inputLayoutUserEmail.isErrorEnabled = true
                                                            inputLayoutUserEmail.error = "Введите новую почту"
                                                        }
                                                    }
                                                }
                                            }
                                        } else {
                                            Toast.makeText(requireActivity(), "Нет подключения\nк Интернету!", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            }

                            override fun onAnimationCancel(animation: Animator) {}
                            override fun onAnimationRepeat(animation: Animator) {}

                        }).start()
                } else {
                    Toast.makeText(requireActivity(), "Нет подключения\nк Интернету!", Toast.LENGTH_LONG).show()
                }
            }

            buttonChangePassword.setOnClickListener {
                vibrate()
                if (isNetworkConnected(requireActivity())) {
                    var isAnimationStarted = true
                    cardViewUserEmail.animate().alpha(0f).translationX(-1100f).setDuration(500).setStartDelay(0).start()
                    cardViewUserPassword.animate().alpha(0f).translationX(-1100f).setDuration(500).setStartDelay(100).start()
                    layoutUserSettings.animate().alpha(0f).translationX(-1100f).setDuration(500).setStartDelay(200)
                        .setListener(object: Animator.AnimatorListener {
                            override fun onAnimationStart(animation: Animator) {}

                            override fun onAnimationEnd(animation: Animator) {
                                if (isAnimationStarted) {
                                    isAnimationStarted = false

                                    cardViewUserEmail.visibility = View.GONE
                                    cardViewUserPassword.visibility = View.GONE
                                    layoutUserSettings.visibility = View.GONE

                                    inputLayoutUserPassword.visibility = View.VISIBLE
                                    inputLayoutConfirmPassword.visibility = View.VISIBLE
                                    buttonUpdateUser.visibility = View.VISIBLE

                                    inputLayoutUserPassword.translationX = 1100f
                                    inputLayoutConfirmPassword.translationX = 1100f
                                    buttonUpdateUser.translationX = 1100f

                                    inputLayoutUserPassword.animate().alpha(1f).translationX(0f).setDuration(500).setStartDelay(0).start()
                                    inputLayoutConfirmPassword.animate().alpha(1f).translationX(0f).setDuration(500).setStartDelay(100).start()
                                    buttonUpdateUser.animate().alpha(1f).translationX(0f).setDuration(500).setStartDelay(200).start()

                                    buttonUpdateUser.setOnClickListener {
                                        vibrate()
                                        if (isNetworkConnected(requireActivity())) {
                                            if (inputUserPassword.text!!.isNotEmpty() && inputConfirmPassword.text!!.isNotEmpty() &&
                                                inputUserPassword.text.toString().length >= 6 && inputUserPassword.text.toString() != sharedPreferences.getString("UserPassword", "")
                                                && inputConfirmPassword.text.toString() == inputUserPassword.text.toString()) {
                                                hideKeyboard(requireActivity())
                                                firebaseAuth.currentUser!!.reauthenticate(EmailAuthProvider.getCredential(
                                                    sharedPreferences.getString("UserEmail", "").toString(),
                                                    sharedPreferences.getString("UserPassword", "").toString()))
                                                    .addOnCompleteListener { reauthTask ->
                                                        if (reauthTask.isSuccessful) {
                                                            firebaseAuth.currentUser!!.updatePassword(inputUserPassword.text.toString())
                                                            editPreferences.putString("UserPassword", inputUserPassword.text.toString()).apply()
                                                            textUserPassword.text = inputUserPassword.text.toString()
                                                            Toast.makeText(requireActivity(), "Пароль обновлён!", Toast.LENGTH_SHORT).show()

                                                            var isUpdateUserAnimationStarted = true
                                                            inputLayoutUserPassword.animate().alpha(0f).translationX(-1100f).setDuration(500).setStartDelay(0).start()
                                                            inputLayoutConfirmPassword.animate().alpha(0f).translationX(-1100f).setDuration(500).setStartDelay(0).start()
                                                            buttonUpdateUser.animate().alpha(0f).translationX(-1100f).setDuration(500).setStartDelay(100)
                                                                .setListener(object: Animator.AnimatorListener {
                                                                    override fun onAnimationStart(animation: Animator) {}

                                                                    override fun onAnimationEnd(animation: Animator) {
                                                                        if (isUpdateUserAnimationStarted) {
                                                                            isUpdateUserAnimationStarted = false

                                                                            inputLayoutUserPassword.visibility = View.GONE
                                                                            inputLayoutConfirmPassword.visibility = View.GONE
                                                                            buttonUpdateUser.visibility = View.GONE

                                                                            cardViewUserEmail.visibility = View.VISIBLE
                                                                            cardViewUserPassword.visibility = View.VISIBLE
                                                                            layoutUserSettings.visibility = View.VISIBLE

                                                                            cardViewUserEmail.translationX = 1100f
                                                                            cardViewUserPassword.translationX = 1100f
                                                                            layoutUserSettings.translationX = 1100f

                                                                            cardViewUserEmail.animate().alpha(1f).translationX(0f).setDuration(500).setStartDelay(0).start()
                                                                            cardViewUserPassword.animate().alpha(1f).translationX(0f).setDuration(500).setStartDelay(100).start()
                                                                            layoutUserSettings.animate().alpha(1f).translationX(0f).setDuration(500).setStartDelay(200).start()
                                                                        }
                                                                    }

                                                                    override fun onAnimationCancel(animation: Animator) {}
                                                                    override fun onAnimationRepeat(animation: Animator) {}

                                                                }).start()
                                                        } else {
                                                            editPreferences.putBoolean("isUserLogged", false).apply()
                                                            isUserLogged = false
                                                            Toast.makeText(requireActivity(), "Не удалось переавторизироваться!", Toast.LENGTH_LONG).show()
                                                        }
                                                    }
                                            } else {
                                                if (inputUserPassword.text!!.isEmpty()) {
                                                    inputLayoutUserPassword.isErrorEnabled = true
                                                    inputLayoutUserPassword.error = "Введите пароль пользователя"
                                                } else {
                                                    if (inputUserPassword.text!!.length < 6) {
                                                        inputLayoutUserPassword.isErrorEnabled = true
                                                        inputLayoutUserPassword.error = "Пароль должен быть не меньше 6 символов"
                                                    } else {
                                                        if (inputUserPassword.text.toString() == sharedPreferences.getString("UserPassword", "")) {
                                                            inputLayoutUserPassword.isErrorEnabled = true
                                                            inputLayoutUserPassword.error = "Введите новый пароль"
                                                        } else {
                                                            inputLayoutUserPassword.isErrorEnabled = false
                                                        }
                                                    }
                                                }
                                                if (inputConfirmPassword.text!!.isEmpty()) {
                                                    inputLayoutConfirmPassword.isErrorEnabled = true
                                                    inputLayoutConfirmPassword.error = "Подтвердите пароль пользователя"
                                                } else {
                                                    if (inputConfirmPassword.text!!.toString() != inputUserPassword.text!!.toString()) {
                                                        inputLayoutConfirmPassword.isErrorEnabled = true
                                                        inputLayoutConfirmPassword.error = "Пароль не совпадает"
                                                    } else {
                                                        inputLayoutConfirmPassword.isErrorEnabled = false
                                                    }
                                                }
                                            }
                                        } else {
                                            Toast.makeText(requireActivity(), "Нет подключения\nк Интернету!", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            }

                            override fun onAnimationCancel(animation: Animator) {}
                            override fun onAnimationRepeat(animation: Animator) {}

                        }).start()
                } else {
                    Toast.makeText(requireActivity(), "Нет подключения\nк Интернету!", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun hideSignInShowProfile() {
        with(binding) {
            cardViewLogo.visibility = View.GONE
            imageSignInUpPattern.visibility = View.GONE
            layoutSignInUp.visibility = View.GONE
            layoutUserProfile.visibility = View.VISIBLE

            buttonDeleteUser.textButton.text = getString(R.string.button_delete_user)
            buttonDeleteUser.imageButton.setImageResource(R.drawable.ic_delete)

            cardViewUserEmail.translationX = 1100f
            cardViewUserPassword.translationX = 1100f
            layoutUserSettings.translationX = 1100f

            layoutUserProfilePattern.animate().alpha(1f).translationY(0f).setDuration(500).setStartDelay(0).start()
            cardViewUserEmail.animate().alpha(1f).translationX(0f).setDuration(500).setStartDelay(100).start()
            cardViewUserPassword.animate().alpha(1f).translationX(0f).setDuration(500).setStartDelay(200).start()
            layoutUserSettings.animate().alpha(1f).translationX(0f).setDuration(500).setStartDelay(300).start()
        }
    }

    private fun hideProfileShownSignIn() {
        with(binding) {
            var isAnimationStarted = true
            layoutUserProfilePattern.animate().alpha(0f).translationY(-700f).setDuration(500).setStartDelay(0).start()
            cardViewUserEmail.animate().alpha(0f).translationX(-1100f).setDuration(500).setStartDelay(100).start()
            cardViewUserPassword.animate().alpha(0f).translationX(-1100f).setDuration(500).setStartDelay(200).start()
            layoutUserSettings.animate().alpha(0f).translationX(-1100f).setDuration(500).setStartDelay(300)
                .setListener(object: Animator.AnimatorListener {
                    override fun onAnimationStart(animation: Animator) {}

                    override fun onAnimationEnd(animation: Animator) {
                        if (isAnimationStarted) {
                            isAnimationStarted = false

                            layoutUserProfile.visibility = View.GONE
                            cardViewLogo.visibility = View.VISIBLE
                            imageSignInUpPattern.visibility = View.VISIBLE
                            layoutSignInUp.visibility = View.VISIBLE

                            cardViewLogo.alpha = 0f
                            imageSignInUpPattern.alpha = 0f
                            layoutSignInUp.alpha = 0f

                            cardViewLogo.animate().alpha(1f).setDuration(500).setStartDelay(0).start()
                            imageSignInUpPattern.animate().alpha(1f).setDuration(500).setStartDelay(0).start()
                            layoutSignInUp.animate().alpha(1f).setDuration(500).setStartDelay(0).start()
                        }
                    }

                    override fun onAnimationCancel(animation: Animator) {}
                    override fun onAnimationRepeat(animation: Animator) {}

                }).start()
        }
    }

    private fun deleteUser() {
        firebaseAuth.currentUser!!.reauthenticate(
            EmailAuthProvider.getCredential(
            sharedPreferences.getString("UserEmail", "").toString(),
            sharedPreferences.getString("UserPassword", "").toString()))
            .addOnCompleteListener { reauthTask ->
                with(binding) {
                    if (reauthTask.isSuccessful) {
                        firebaseAuth.currentUser!!.delete().addOnCompleteListener { deleteUserTask ->
                            if (deleteUserTask.isSuccessful) {
                                editPreferences.putString("UserEmail", "")
                                editPreferences.putString("UserPassword", "")
                                Toast.makeText(requireActivity(), "Пользователь удалён!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(requireActivity(), "Не удалось удалить пользователя!", Toast.LENGTH_LONG).show()
                            }
                            buttonDeleteUser.imageButton.visibility = View.VISIBLE
                            buttonDeleteUser.textButton.visibility = View.VISIBLE
                            buttonDeleteUser.progressBarButton.visibility = View.GONE
                            hideProfileShownSignIn()
                            editPreferences.putBoolean("isUserLogged", false).apply()
                            isUserLogged = false
                        }
                    } else {
                        buttonDeleteUser.imageButton.visibility = View.VISIBLE
                        buttonDeleteUser.textButton.visibility = View.VISIBLE
                        buttonDeleteUser.progressBarButton.visibility = View.GONE
                        editPreferences.putBoolean("isUserLogged", false).apply()
                        isUserLogged = false
                        Toast.makeText(requireActivity(), "Не удалось переавторизироваться!", Toast.LENGTH_LONG).show()
                    }
                }
            }
    }

    private val sharedPreferencesChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "dataToActivity") {
                with(binding) {
                    if (sharedPreferences.getInt(key, 0) == 1) {
                        tabLayout.getTabAt(0)!!.select()
                    } else if (sharedPreferences.getInt(key, 0) == 2) {
                        textUserEmail.text = sharedPreferences.getString("UserEmail", "")
                        textUserPassword.text = sharedPreferences.getString("UserPassword", "")

                        var isAnimationStarted = true
                        cardViewLogo.animate().alpha(0f).setDuration(500).setStartDelay(0).start()
                        imageSignInUpPattern.animate().alpha(0f).setDuration(500).setStartDelay(0).start()
                        layoutSignInUp.animate().alpha(0f).setDuration(500).setStartDelay(0).
                        setListener(object: Animator.AnimatorListener {
                            override fun onAnimationStart(animation: Animator) {}

                            override fun onAnimationEnd(animation: Animator) {
                                if (isAnimationStarted) {
                                    isAnimationStarted = false
                                    hideSignInShowProfile()
                                }
                            }

                            override fun onAnimationCancel(animation: Animator) {}
                            override fun onAnimationRepeat(animation: Animator) {}

                        }).start()
                    }
                    editPreferences.putInt(key, 0).apply()
                }
            }
        }

    override fun onDestroy() {
        super.onDestroy()

        sharedPreferences.unregisterOnSharedPreferenceChangeListener(sharedPreferencesChangeListener)
    }

    private fun isValidEmail(inputText: CharSequence) : Boolean {
        return Patterns.EMAIL_ADDRESS.matcher(inputText).matches()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentUserProfileBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    @Suppress("DEPRECATION")
    private fun vibrate() {
        if (vibrator.hasVibrator()) {
            if (isHapticFeedbackEnabled == "1") {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    binding.buttonUpdateUser.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING)
                } else {
                    binding.buttonUpdateUser.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING + HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
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