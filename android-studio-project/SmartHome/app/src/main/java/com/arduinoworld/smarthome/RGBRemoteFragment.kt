package com.arduinoworld.smarthome

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.view.HapticFeedbackConstants
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.arduinoworld.smarthome.MainActivity.Companion.editPreferences
import com.arduinoworld.smarthome.MainActivity.Companion.firebaseAuth
import com.arduinoworld.smarthome.MainActivity.Companion.isNetworkConnected
import com.arduinoworld.smarthome.MainActivity.Companion.realtimeDatabase
import com.arduinoworld.smarthome.MainActivity.Companion.sharedPreferences
import com.arduinoworld.smarthome.databinding.FragmentRgbRemoteBinding
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener

class RGBRemoteFragment : Fragment() {
    private lateinit var binding: FragmentRgbRemoteBinding
    private var rgbState = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        with(binding) {
            rgbState = sharedPreferences.getBoolean("RGBState", false)
            if (rgbState) {
                buttonRGBOff.setColorFilter(Color.parseColor("#6A61AD"))
                buttonRGBOn.setColorFilter(Color.parseColor("#5347AE"))
            }

            realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("SmartRemotes").child("rgbRemoteButton")
                .addListenerForSingleValueEvent(object: ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        rgbState = snapshot.getValue(String::class.java)!!.last() == '1'
                        if (rgbState) {
                            buttonRGBOff.setColorFilter(Color.parseColor("#6A61AD"))
                            buttonRGBOn.setColorFilter(Color.parseColor("#5347AE"))
                        } else {
                            buttonRGBOn.setColorFilter(Color.parseColor("#6A61AD"))
                            buttonRGBOff.setColorFilter(Color.parseColor("#5347AE"))
                        }
                        editPreferences.putBoolean("RGBState", rgbState).apply()
                    }

                    override fun onCancelled(error: DatabaseError) {}

                })

            buttonRGBOn.setOnClickListener {
                rgbState = true
                buttonRGBOff.setColorFilter(Color.parseColor("#6A61AD"))
                buttonRGBOn.setColorFilter(Color.parseColor("#5347AE"))
                editPreferences.putBoolean("RGBState", true).apply()
                sendRemoteButton("1")
            }

            buttonRGBOff.setOnClickListener {
                rgbState = false
                buttonRGBOn.setColorFilter(Color.parseColor("#6A61AD"))
                buttonRGBOff.setColorFilter(Color.parseColor("#5347AE"))
                editPreferences.putBoolean("RGBState", false).apply()
                sendRemoteButton("2")
            }
            
            buttonIncreaseBrightness.setOnClickListener { sendRemoteButton("3 ${(1000..9999).random()}") }
            buttonDecreaseBrightness.setOnClickListener { sendRemoteButton("4 ${(1000..9999).random()}") }
            
            buttonRed1.setOnClickListener { sendRemoteButton("5") }
            buttonRed2.setOnClickListener { sendRemoteButton("6") }
            buttonRed3.setOnClickListener { sendRemoteButton("7") }
            buttonRed4.setOnClickListener { sendRemoteButton("8") }
            buttonRed5.setOnClickListener { sendRemoteButton("9") }

            buttonGreen1.setOnClickListener { sendRemoteButton("10") }
            buttonGreen2.setOnClickListener { sendRemoteButton("11") }
            buttonGreen3.setOnClickListener { sendRemoteButton("12") }
            buttonGreen4.setOnClickListener { sendRemoteButton("13") }
            buttonGreen5.setOnClickListener { sendRemoteButton("14") }

            buttonBlue1.setOnClickListener { sendRemoteButton("15") }
            buttonBlue2.setOnClickListener { sendRemoteButton("16") }
            buttonBlue3.setOnClickListener { sendRemoteButton("17") }
            buttonBlue4.setOnClickListener { sendRemoteButton("18") }
            buttonBlue5.setOnClickListener { sendRemoteButton("19") }

            buttonWhite.setOnClickListener { sendRemoteButton("20") }
            buttonFlash.setOnClickListener { sendRemoteButton("21") }
            buttonStrobe.setOnClickListener { sendRemoteButton("22") }
            buttonFade.setOnClickListener { sendRemoteButton("23") }
            buttonSmooth.setOnClickListener { sendRemoteButton("24") }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentRgbRemoteBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    private fun sendRemoteButton(remoteButton: String) {
        vibrate()
        if (rgbState || remoteButton == "1" || remoteButton == "2") {
            if (isNetworkConnected(requireActivity())) {
                val rgbStateString = if (rgbState) {
                    "1"
                } else {
                    "0"
                }

                realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("SmartRemotes")
                    .child("rgbRemoteButton").setValue("$remoteButton $rgbStateString")
            } else {
                Toast.makeText(requireActivity(), "Нет подключения\nк Интернету!", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(requireActivity(), "Вы не можете управлять RGB лентой, пока она выключена!", Toast.LENGTH_LONG).show()
        }
    }

    @Suppress("DEPRECATION")
    private fun vibrate() {
        if (MainActivity.vibrator.hasVibrator()) {
            if (MainActivity.isHapticFeedbackEnabled == "1") {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    binding.buttonRGBOn.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING)
                } else {
                    binding.buttonRGBOn.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING + HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
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