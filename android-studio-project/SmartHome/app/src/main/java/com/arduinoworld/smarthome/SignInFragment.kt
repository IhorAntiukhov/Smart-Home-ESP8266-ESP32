package com.arduinoworld.smarthome

import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
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
import com.arduinoworld.smarthome.MainActivity.Companion.sharedPreferences
import com.arduinoworld.smarthome.MainActivity.Companion.vibrator
import com.arduinoworld.smarthome.databinding.FragmentSignInBinding
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException

class SignInFragment : Fragment() {
    private lateinit var binding: FragmentSignInBinding

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        with(binding) {
            inputUserEmail.setText(sharedPreferences.getString("UserEmail", ""))
            inputUserPassword.setText(sharedPreferences.getString("UserPassword", ""))

            inputLayoutUserEmail.translationX = 1100f
            inputLayoutUserPassword.translationX = 1100f
            layoutResetPassword.translationX = 1100f
            buttonSignIn.cardView.translationX = 1100f
            buttonSignIn.cardView.alpha = 0f

            inputLayoutUserEmail.animate().alpha(1f).translationX(0f).setDuration(500).setStartDelay(0).start()
            inputLayoutUserPassword.animate().alpha(1f).translationX(0f).setDuration(500).setStartDelay(100).start()
            layoutResetPassword.animate().alpha(1f).translationX(0f).setDuration(500).setStartDelay(200).start()
            buttonSignIn.cardView.animate().alpha(1f).translationX(0f).setDuration(500).setStartDelay(300).start()

            buttonSignIn.cardView.setOnClickListener {
                vibrate()
                if (isNetworkConnected(requireActivity())) {
                    if (inputUserEmail.text!!.isNotEmpty() && inputUserPassword.text!!.isNotEmpty()) {
                        hideKeyboard(requireActivity())
                        buttonSignIn.progressBarButton.visibility = View.VISIBLE
                        buttonSignIn.textButton.text = getString(R.string.button_sign_in_progress)
                        inputLayoutUserEmail.isErrorEnabled = false
                        inputLayoutUserPassword.isErrorEnabled = false
                        firebaseAuth.signInWithEmailAndPassword(inputUserEmail.text.toString(), inputUserPassword.text.toString())
                            .addOnCompleteListener { signInTask ->
                                buttonSignIn.progressBarButton.visibility = View.GONE
                                buttonSignIn.textButton.text = getString(R.string.button_sign_in)
                                if (signInTask.isSuccessful) {
                                    isUserLogged = true
                                    editPreferences.putString("UserEmail", inputUserEmail.text.toString())
                                    editPreferences.putString("UserPassword", inputUserPassword.text.toString())
                                    editPreferences.putBoolean("isUserLogged", true)

                                    Toast.makeText(requireActivity(), "Вошли в пользователя!", Toast.LENGTH_SHORT).show()
                                    editPreferences.putInt("dataToActivity", 2).apply()
                                } else {
                                    try {
                                        throw signInTask.exception!!
                                    } catch (e: FirebaseAuthInvalidCredentialsException) {
                                        inputLayoutUserEmail.isErrorEnabled = false
                                        inputLayoutUserPassword.isErrorEnabled = true
                                        inputLayoutUserPassword.error = "Неверный пароль"
                                    } catch (e: FirebaseAuthInvalidUserException) {
                                        when (e.errorCode) {
                                            "ERROR_USER_NOT_FOUND" -> {
                                                inputLayoutUserPassword.isErrorEnabled = false
                                                inputLayoutUserEmail.isErrorEnabled = true
                                                inputLayoutUserEmail.error = "Пользователь не найден"
                                            }
                                        }
                                    }
                                }
                            }
                    } else {
                        if (inputUserEmail.text!!.isEmpty()) {
                            inputLayoutUserEmail.isErrorEnabled = true
                            inputLayoutUserEmail.error = "Введите email пользователя"
                        } else {
                            inputLayoutUserEmail.isErrorEnabled = false
                        }
                        if (inputUserPassword.text!!.isEmpty()) {
                            inputLayoutUserPassword.isErrorEnabled = true
                            inputLayoutUserPassword.error = "Введите почту пользователя"
                        } else {
                            inputLayoutUserPassword.isErrorEnabled = false
                        }
                    }
                } else {
                    Toast.makeText(requireActivity(), "Нет подключения\nк Интернету!", Toast.LENGTH_LONG).show()
                }
            }

            buttonForgotPassword.setOnClickListener {
                vibrate()
                if (isNetworkConnected(requireActivity())) {
                    if (inputUserEmail.text!!.isNotEmpty() && isValidEmail(inputUserEmail.text.toString())) {
                        hideKeyboard(requireActivity())
                        inputLayoutUserEmail.isErrorEnabled = false
                        firebaseAuth.sendPasswordResetEmail(inputUserEmail.text.toString())
                            .addOnCompleteListener { resetPasswordTask ->
                                if (resetPasswordTask.isSuccessful) {
                                    Toast.makeText(requireActivity(), "Письмо для сброса пароля отправлено на ${inputUserEmail.text.toString()}!", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(requireActivity(), "Не удалось отправить письмо для сброса пароля!", Toast.LENGTH_LONG).show()
                                }
                            }
                    } else {
                        inputLayoutUserEmail.isErrorEnabled = true
                        if (inputUserEmail.text!!.isEmpty()) {
                            inputLayoutUserEmail.error = "Введите email пользователя"
                        } else if (inputUserEmail.text!!.isNotEmpty() && isValidEmail(inputUserEmail.text.toString())) {
                            inputLayoutUserEmail.error = "Неправильная почта"
                        }
                    }
                } else {
                    Toast.makeText(requireActivity(), "Нет подключения\nк Интернету!", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (sharedPreferences.getString("CreatedUserEmail", "") != "") {
            val userEmail = sharedPreferences.getString("CreatedUserEmail", "")
            val userPassword = sharedPreferences.getString("CreatedUserPassword", "")
            editPreferences.putString("CreatedUserEmail", "")
            editPreferences.putString("CreatedUserPassword", "").apply()
            val alertDialogBuilder = androidx.appcompat.app.AlertDialog.Builder(requireActivity())
            alertDialogBuilder.setTitle("Вход в пользователя")
            alertDialogBuilder.setMessage("Войти в пользователя $userEmail?")
            alertDialogBuilder.setPositiveButton("Продолжить") { _, _ ->
                vibrate()
                with(binding) {
                    inputUserEmail.setText(userEmail)
                    inputUserPassword.setText(userPassword)
                    buttonSignIn.progressBarButton.visibility = View.VISIBLE
                    buttonSignIn.textButton.text = getString(R.string.button_sign_in_progress)
                    inputLayoutUserEmail.isErrorEnabled = false
                    inputLayoutUserPassword.isErrorEnabled = false
                    firebaseAuth.signInWithEmailAndPassword(inputUserEmail.text.toString(), inputUserPassword.text.toString())
                        .addOnCompleteListener {
                            buttonSignIn.progressBarButton.visibility = View.GONE
                            buttonSignIn.textButton.text = getString(R.string.button_sign_in)
                            editPreferences.putString("UserEmail", inputUserEmail.text.toString())
                            editPreferences.putString("UserPassword", inputUserPassword.text.toString())
                            editPreferences.putBoolean("isUserLogged", true)
                            isUserLogged = true
                            Toast.makeText(requireActivity(), "Вошли в пользователя!", Toast.LENGTH_SHORT).show()
                            editPreferences.putInt("dataToActivity", 2).apply()
                        }
                }
            }
            alertDialogBuilder.setNegativeButton("Отмена") { _, _ ->
                vibrate()
            }
            alertDialogBuilder.create().show()
        }
    }

    private fun isValidEmail(inputText: CharSequence) : Boolean {
        return Patterns.EMAIL_ADDRESS.matcher(inputText).matches()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentSignInBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    @Suppress("DEPRECATION")
    private fun vibrate() {
        if (vibrator.hasVibrator()) {
            if (isHapticFeedbackEnabled == "1") {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    binding.buttonSignIn.cardView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING)
                } else {
                    binding.buttonSignIn.cardView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING + HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
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