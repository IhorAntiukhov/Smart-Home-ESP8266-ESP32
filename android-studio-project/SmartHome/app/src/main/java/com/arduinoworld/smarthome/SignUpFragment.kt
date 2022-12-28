package com.arduinoworld.smarthome

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.util.Patterns
import android.view.HapticFeedbackConstants
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import com.arduinoworld.smarthome.MainActivity.Companion.editPreferences
import com.arduinoworld.smarthome.MainActivity.Companion.firebaseAuth
import com.arduinoworld.smarthome.MainActivity.Companion.isHapticFeedbackEnabled
import com.arduinoworld.smarthome.MainActivity.Companion.isNetworkConnected
import com.arduinoworld.smarthome.MainActivity.Companion.vibrator
import com.arduinoworld.smarthome.databinding.FragmentSignUpBinding

class SignUpFragment : Fragment() {
    private lateinit var binding: FragmentSignUpBinding

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        with(binding) {
            buttonSignUp.textButton.text = getString(R.string.button_sign_up)

            buttonSignUp.cardView.setOnClickListener {
                vibrate()
                if (isNetworkConnected(requireActivity())) {
                    if (inputUserEmail.text!!.isNotEmpty() && inputUserPassword.text!!.isNotEmpty() && inputConfirmPassword.text!!.isNotEmpty() &&
                            isValidEmail(inputUserEmail.text.toString()) && inputUserPassword.text.toString().length >= 6 &&
                        inputConfirmPassword.text.toString() == inputUserPassword.text.toString()) {
                        hideKeyboard()
                        buttonSignUp.progressBarButton.visibility = View.VISIBLE
                        buttonSignUp.textButton.text = getString(R.string.button_sign_up_progress)
                        inputLayoutUserEmail.isErrorEnabled = false
                        inputLayoutUserPassword.isErrorEnabled = false
                        inputLayoutConfirmPassword.isErrorEnabled = false
                        firebaseAuth.createUserWithEmailAndPassword(inputUserEmail.text!!.toString(), inputUserPassword.text!!.toString())
                            .addOnCompleteListener { signUpTask ->
                                buttonSignUp.progressBarButton.visibility = View.GONE
                                buttonSignUp.textButton.text = getString(R.string.button_sign_up)
                                if (signUpTask.isSuccessful) {
                                    editPreferences.putString("CreatedUserEmail", inputUserEmail.text!!.toString())
                                    editPreferences.putString("CreatedUserPassword", inputUserPassword.text!!.toString())
                                    Toast.makeText(requireActivity(), "Пользователь зарегистрирован!", Toast.LENGTH_SHORT).show()
                                    editPreferences.putInt("dataToActivity", 1).apply()
                                } else {
                                    inputLayoutUserEmail.isErrorEnabled = true
                                    inputLayoutUserEmail.error = "Эта почта уже существует"
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
                                inputLayoutUserEmail.isErrorEnabled = false
                            }
                        }
                        if (inputUserPassword.text!!.isEmpty()) {
                            inputLayoutUserPassword.isErrorEnabled = true
                            inputLayoutUserPassword.error = "Введите пароль пользователя"
                        } else {
                            if (inputUserPassword.text!!.length < 6) {
                                inputLayoutUserPassword.isErrorEnabled = true
                                inputLayoutUserPassword.error = "Пароль должен быть не меньше 6 символов"
                            } else {
                                inputLayoutUserPassword.isErrorEnabled = false
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

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentSignUpBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    private fun isValidEmail(inputText: CharSequence) : Boolean {
        return Patterns.EMAIL_ADDRESS.matcher(inputText).matches()
    }

    private fun hideKeyboard() {
        requireActivity().currentFocus?.let { view ->
            val inputMethodManager = requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            inputMethodManager!!.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }

    @Suppress("DEPRECATION")
    private fun vibrate() {
        if (vibrator.hasVibrator()) {
            if (isHapticFeedbackEnabled == "1") {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    binding.buttonSignUp.cardView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING)
                } else {
                    binding.buttonSignUp.cardView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING + HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
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