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
import com.arduinoworld.smarthome.MainActivity.Companion.isHapticFeedbackEnabled
import com.arduinoworld.smarthome.MainActivity.Companion.isNetworkConnected
import com.arduinoworld.smarthome.MainActivity.Companion.realtimeDatabase
import com.arduinoworld.smarthome.MainActivity.Companion.sharedPreferences
import com.arduinoworld.smarthome.MainActivity.Companion.vibrator
import com.arduinoworld.smarthome.databinding.FragmentTvRemoteBinding
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener

class TvRemoteFragment : Fragment() {
    private lateinit var binding: FragmentTvRemoteBinding
    private lateinit var handler: Handler
    private var tvState = false
    private var remoteSettingsMode = false
    private var isRemoteButtonSelectedEarlier = false
    private var selectedRemoteButtonId = 0
    private var remoteButtonConfiguredArrayList = arrayListOf(false, false, false, false, false, false, false, false, false, false, false, false, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        with(binding) {
            sharedPreferences.registerOnSharedPreferenceChangeListener(sharedPreferencesChangeListener)

            tvState = sharedPreferences.getBoolean("TvState", false)
            if (tvState) textTvState.text = getString(R.string.on_text)

            realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("SmartRemotes").child("tvRemoteButton")
                .addListenerForSingleValueEvent(object: ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        tvState = snapshot.getValue(String::class.java)!!.last() == '1'
                        if (tvState) {
                            textTvState.text = getString(R.string.on_text)
                        } else {
                            textTvState.text = getString(R.string.off_text)
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {}

                })

            buttonOnOff.setOnClickListener {
                if (!remoteSettingsMode) {
                    if (isNetworkConnected(requireActivity())) {
                        tvState = !tvState
                        if (tvState) {
                            textTvState.text = getString(R.string.on_text)
                        } else {
                            textTvState.text = getString(R.string.off_text)
                        }
                        sendRemoteButton(1, buttonOnOff)
                    } else {
                        Toast.makeText(requireActivity(), "Нет подключения\nк Интернету!", Toast.LENGTH_LONG).show()
                    }
                } else {
                    sendRemoteButton(1, buttonOnOff)
                }
            }
            buttonSource.setOnClickListener { sendRemoteButton(2, buttonSource) }
            buttonUp.setOnClickListener { sendRemoteButton(3, buttonUp) }
            buttonDown.setOnClickListener { sendRemoteButton(4, buttonDown) }
            buttonLeft.setOnClickListener { sendRemoteButton(5, buttonLeft) }
            buttonRight.setOnClickListener { sendRemoteButton(6, buttonRight) }
            textOk.setOnClickListener { sendRemoteButton(7, buttonRight) }
            buttonIncreaseVolume.setOnClickListener { sendRemoteButton(8, buttonIncreaseVolume) }
            buttonDecreaseVolume.setOnClickListener { sendRemoteButton(9, buttonDecreaseVolume) }
            buttonNextChannel.setOnClickListener { sendRemoteButton(10, buttonNextChannel) }
            buttonPreviousChannel.setOnClickListener { sendRemoteButton(11, buttonPreviousChannel) }
            buttonMute.setOnClickListener { sendRemoteButton(12, buttonMute) }
            buttonMenu.setOnClickListener { sendRemoteButton(13, buttonMenu) }

            buttonSaveRemote.setOnClickListener {
                vibrate()
                val stringRequest = StringRequest(
                    Request.Method.POST,
                    "http://192.168.4.1/?save_tv_remote=1",
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

    private fun sendRemoteButton(remoteButton: Int, button: ImageButton) {
        vibrate()
        if (!remoteSettingsMode) {
            if (tvState || remoteButton == 1) {
                if (isNetworkConnected(requireActivity())) {
                    val tvStateString: String = if (tvState) {
                        "1"
                    } else {
                        "0"
                    }

                    realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("SmartRemotes")
                        .child("tvRemoteButton").setValue(remoteButton.toString() + " " + (1000..9999).random().toString() + tvStateString)
                } else {
                    Toast.makeText(requireActivity(), "Нет подключения\nк Интернету!", Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(requireActivity(), "Вы не можете управлять телевизором, пока он выключен!", Toast.LENGTH_LONG).show()
            }
        } else {
            selectedRemoteButtonId = remoteButton
            updateRemoteButtons()

            if (remoteButton in 3..11) {
                if (remoteButton != 7) {
                    button.setColorFilter(Color.parseColor("#473D95"), android.graphics.PorterDuff.Mode.SRC_IN)
                } else {
                    binding.textOk.backgroundTintList = ContextCompat.getColorStateList(requireActivity(), R.color.dark_color)
                }
            } else {
                button.backgroundTintList = ContextCompat.getColorStateList(requireActivity(), R.color.dark_color)
            }
            val stringRequest = StringRequest(
                Request.Method.POST,
                "http://192.168.4.1/?tv_remote=${remoteButton}",
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
                        layoutSelectButton.animate().alpha(1f).setDuration(500).setStartDelay(0).start()
                        buttonSaveRemote.animate().alpha(1f).setDuration(500).setStartDelay(0).start()
                        textTvState.animate().alpha(0f).setDuration(500).setStartDelay(0)
                            .setListener(object: Animator.AnimatorListener {
                                override fun onAnimationStart(animation: Animator) {}

                                override fun onAnimationEnd(animation: Animator) {
                                    if (isAnimationStarted) {
                                        isAnimationStarted = false

                                        textTvState.visibility = View.GONE
                                        imageFirstCircleRemote.visibility = View.INVISIBLE
                                        textVolume.setTextColor(Color.parseColor("#5347AE"))
                                        textChannel.setTextColor(Color.parseColor("#5347AE"))
                                        layoutVolume.setBackgroundResource(0)
                                        layoutChannel.setBackgroundResource(0)
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

                                        textTvState.visibility = View.VISIBLE
                                        imageFirstCircleRemote.visibility = View.VISIBLE

                                        textVolume.setTextColor(Color.parseColor("#FFFFFF"))
                                        textChannel.setTextColor(Color.parseColor("#FFFFFF"))
                                        layoutVolume.background = ContextCompat.getDrawable(requireActivity(), R.drawable.volume_layout_background)
                                        layoutChannel.background = ContextCompat.getDrawable(requireActivity(), R.drawable.volume_layout_background)

                                        textTvState.animate().alpha(1f).setDuration(500).setStartDelay(0).start()

                                        remoteButtonConfiguredArrayList = arrayListOf(true, true, true, true, true, true, true, true, true, true, true, true, true)
                                        updateRemoteButtons()
                                        remoteButtonConfiguredArrayList = arrayListOf(false, false, false, false, false, false, false, false, false, false, false, false, false)

                                        buttonUp.setColorFilter(Color.parseColor("#FFFFFF"), android.graphics.PorterDuff.Mode.SRC_IN)
                                        buttonDown.setColorFilter(Color.parseColor("#FFFFFF"), android.graphics.PorterDuff.Mode.SRC_IN)
                                        buttonLeft.setColorFilter(Color.parseColor("#FFFFFF"), android.graphics.PorterDuff.Mode.SRC_IN)
                                        buttonRight.setColorFilter(Color.parseColor("#FFFFFF"), android.graphics.PorterDuff.Mode.SRC_IN)

                                        buttonIncreaseVolume.setColorFilter(Color.parseColor("#FFFFFF"), android.graphics.PorterDuff.Mode.SRC_IN)
                                        buttonDecreaseVolume.setColorFilter(Color.parseColor("#FFFFFF"), android.graphics.PorterDuff.Mode.SRC_IN)
                                        buttonNextChannel.setColorFilter(Color.parseColor("#FFFFFF"), android.graphics.PorterDuff.Mode.SRC_IN)
                                        buttonPreviousChannel.setColorFilter(Color.parseColor("#FFFFFF"), android.graphics.PorterDuff.Mode.SRC_IN)
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
                buttonSource.backgroundTintList = ContextCompat.getColorStateList(requireActivity(), R.color.primary_color)
            } else {
                buttonSource.backgroundTintList = ContextCompat.getColorStateList(requireActivity(), R.color.secondary_color)
            }
            if (remoteButtonConfiguredArrayList[2]) {
                buttonUp.setColorFilter(Color.parseColor("#5347AE"), android.graphics.PorterDuff.Mode.SRC_IN)
            } else {
                buttonUp.setColorFilter(Color.parseColor("#6A61AD"), android.graphics.PorterDuff.Mode.SRC_IN)
            }
            if (remoteButtonConfiguredArrayList[3]) {
                buttonDown.setColorFilter(Color.parseColor("#5347AE"), android.graphics.PorterDuff.Mode.SRC_IN)
            } else {
                buttonDown.setColorFilter(Color.parseColor("#6A61AD"), android.graphics.PorterDuff.Mode.SRC_IN)
            }
            if (remoteButtonConfiguredArrayList[4]) {
                buttonLeft.setColorFilter(Color.parseColor("#5347AE"), android.graphics.PorterDuff.Mode.SRC_IN)
            } else {
                buttonLeft.setColorFilter(Color.parseColor("#6A61AD"), android.graphics.PorterDuff.Mode.SRC_IN)
            }
            if (remoteButtonConfiguredArrayList[5]) {
                buttonRight.setColorFilter(Color.parseColor("#5347AE"), android.graphics.PorterDuff.Mode.SRC_IN)
            } else {
                buttonRight.setColorFilter(Color.parseColor("#6A61AD"), android.graphics.PorterDuff.Mode.SRC_IN)
            }
            if (remoteButtonConfiguredArrayList[6]) {
                textOk.backgroundTintList = ContextCompat.getColorStateList(requireActivity(), R.color.primary_color)
            } else {
                textOk.backgroundTintList = ContextCompat.getColorStateList(requireActivity(), R.color.secondary_color)
            }
            if (remoteButtonConfiguredArrayList[7]) {
                buttonIncreaseVolume.setColorFilter(Color.parseColor("#5347AE"), android.graphics.PorterDuff.Mode.SRC_IN)
            } else {
                buttonIncreaseVolume.setColorFilter(Color.parseColor("#6A61AD"), android.graphics.PorterDuff.Mode.SRC_IN)
            }
            if (remoteButtonConfiguredArrayList[8]) {
                buttonDecreaseVolume.setColorFilter(Color.parseColor("#5347AE"), android.graphics.PorterDuff.Mode.SRC_IN)
            } else {
                buttonDecreaseVolume.setColorFilter(Color.parseColor("#6A61AD"), android.graphics.PorterDuff.Mode.SRC_IN)
            }
            if (remoteButtonConfiguredArrayList[9]) {
                buttonNextChannel.setColorFilter(Color.parseColor("#5347AE"), android.graphics.PorterDuff.Mode.SRC_IN)
            } else {
                buttonNextChannel.setColorFilter(Color.parseColor("#6A61AD"), android.graphics.PorterDuff.Mode.SRC_IN)
            }
            if (remoteButtonConfiguredArrayList[10]) {
                buttonPreviousChannel.setColorFilter(Color.parseColor("#5347AE"), android.graphics.PorterDuff.Mode.SRC_IN)
            } else {
                buttonPreviousChannel.setColorFilter(Color.parseColor("#6A61AD"), android.graphics.PorterDuff.Mode.SRC_IN)
            }
            if (remoteButtonConfiguredArrayList[11]) {
                buttonMute.backgroundTintList = ContextCompat.getColorStateList(requireActivity(), R.color.primary_color)
            } else {
                buttonMute.backgroundTintList = ContextCompat.getColorStateList(requireActivity(), R.color.secondary_color)
            }
            if (remoteButtonConfiguredArrayList[12]) {
                buttonMenu.backgroundTintList = ContextCompat.getColorStateList(requireActivity(), R.color.primary_color)
            } else {
                buttonMenu.backgroundTintList = ContextCompat.getColorStateList(requireActivity(), R.color.secondary_color)
            }
        }
    }

    private val sendRequestRunnable : Runnable = object : Runnable {
        override fun run() {
            if (remoteSettingsMode) {
                val isButtonConfiguredRequest = StringRequest(
                    Request.Method.POST,
                    "http://192.168.4.1/tv_button_configured",
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

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentTvRemoteBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    @Suppress("DEPRECATION")
    private fun vibrate() {
        if (vibrator.hasVibrator()) {
            if (isHapticFeedbackEnabled == "1") {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    binding.buttonOnOff.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING)
                } else {
                    binding.buttonOnOff.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING + HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
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