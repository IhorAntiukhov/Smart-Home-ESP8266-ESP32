package com.arduinoworld.smarthome

import android.animation.Animator
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.view.HapticFeedbackConstants
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import com.arduinoworld.smarthome.MainActivity.Companion.editPreferences
import com.arduinoworld.smarthome.MainActivity.Companion.firebaseAuth
import com.arduinoworld.smarthome.MainActivity.Companion.isNetworkConnected
import com.arduinoworld.smarthome.MainActivity.Companion.realtimeDatabase
import com.arduinoworld.smarthome.MainActivity.Companion.sharedPreferences
import com.arduinoworld.smarthome.databinding.FragmentSmartRemotesBinding
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError

class SmartRemotesFragment : Fragment() {
    companion object {
        var selectedRemoteId = 0
    }

    private lateinit var binding: FragmentSmartRemotesBinding
    private lateinit var remoteFragmentManager: FragmentManager
    private lateinit var selectedFragment: Fragment
    private var lightState = false
    private var tvState = false
    private var acState = false
    private var rgbState = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        with(binding) {
            remoteFragmentManager = requireActivity().supportFragmentManager
            sharedPreferences.registerOnSharedPreferenceChangeListener(sharedPreferencesChangeListener)

            lightState = sharedPreferences.getBoolean("LightState", false)
            tvState = sharedPreferences.getBoolean("TvState", false)
            acState = sharedPreferences.getBoolean("isACStarted", false)
            rgbState = sharedPreferences.getBoolean("RGBState", false)

            if (lightState) {
                imageLightState.setImageResource(R.drawable.ic_light_on)
                textLightState.text = getString(R.string.on_text)
            }
            if (tvState) {
                imageTvState.setImageResource(R.drawable.ic_tv_on)
                textTvState.text = getString(R.string.on_text)
            }
            if (acState) {
                imageACState.setImageResource(R.drawable.ic_ac_on)
                textACState.text = getString(R.string.on_text)
            }
            if (rgbState) {
                imageRGBState.setImageResource(R.drawable.ic_light_on)
                textRGBState.text = getString(R.string.on_text)
            }

            cardViewLightRemote.setOnClickListener {
                selectedRemoteId = 1
                startFragment(LightRemoteFragment())
            }
            cardViewTvRemote.setOnClickListener {
                selectedRemoteId = 2
                startFragment(TvRemoteFragment())
            }
            cardViewACRemote.setOnClickListener {
                selectedRemoteId = 3
                startFragment(ACRemoteFragment())
            }
            cardViewRGBRemote.setOnClickListener {
                selectedRemoteId = 4
                startFragment(RGBRemoteFragment())
            }

            buttonLightOnOff.setOnClickListener {
                vibrate()
                if (isNetworkConnected(requireActivity())) {
                    lightState = !lightState
                    editPreferences.putBoolean("LightState", lightState).apply()

                    if (lightState) {
                        imageLightState.setImageResource(R.drawable.ic_light_on)
                        textLightState.text = getString(R.string.on_text)

                        realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("SmartRemotes")
                            .child("lightRemoteButton").setValue("1 " + (1000..9999).random().toString() + "1")
                    } else {
                        imageLightState.setImageResource(R.drawable.ic_light_off)
                        textLightState.text = getString(R.string.off_text)

                        realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("SmartRemotes")
                            .child("lightRemoteButton").setValue("1 " + (1000..9999).random().toString() + "0")
                    }
                } else {
                    Toast.makeText(requireActivity(), "Нет подключения\nк Интернету!", Toast.LENGTH_LONG).show()
                }
            }

            buttonTvOnOff.setOnClickListener {
                vibrate()
                if (isNetworkConnected(requireActivity())) {
                    tvState = !tvState
                    editPreferences.putBoolean("TvState", tvState).apply()

                    if (tvState) {
                        imageTvState.setImageResource(R.drawable.ic_tv_on)
                        textTvState.text = getString(R.string.on_text)

                        realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("SmartRemotes")
                            .child("tvRemoteButton").setValue("1 " + (1000..9999).random().toString() + "1")
                    } else {
                        imageTvState.setImageResource(R.drawable.ic_tv_off)
                        textTvState.text = getString(R.string.off_text)

                        realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("SmartRemotes")
                            .child("tvRemoteButton").setValue("1 " + (1000..9999).random().toString() + "0")
                    }
                } else {
                    Toast.makeText(requireActivity(), "Нет подключения\nк Интернету!", Toast.LENGTH_LONG).show()
                }
            }

            buttonACOnOff.setOnClickListener {
                vibrate()
                if (isNetworkConnected(requireActivity())) {
                    if (!sharedPreferences.getBoolean("isACTimeModeStarted", false)) {
                        acState = !acState
                        editPreferences.putBoolean("isACStarted", acState)
                        var acSettings = sharedPreferences.getString("ACSettings", "").toString()
                        acSettings = acSettings.substring(0, acSettings.length - 1)

                        if (acState) {
                            imageACState.setImageResource(R.drawable.ic_ac_on)
                            textACState.text = getString(R.string.on_text)

                            acSettings += "1"
                            realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("SmartRemotes")
                                .child("acRemote").setValue("on $acSettings")
                        } else {
                            imageACState.setImageResource(R.drawable.ic_ac_off)
                            textACState.text = getString(R.string.off_text)

                            acSettings += "0"
                            realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("SmartRemotes")
                                .child("acRemote").setValue("off $acSettings")
                        }
                        editPreferences.putString("ACSettings", acSettings).apply()
                    } else {
                        Toast.makeText(requireActivity(), "Вы не можете включить кондиционер, пока запущен режим по времени!", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(requireActivity(), "Нет подключения\nк Интернету!", Toast.LENGTH_LONG).show()
                }
            }

            buttonRGBOnOff.setOnClickListener {
                vibrate()
                if (isNetworkConnected(requireActivity())) {
                    rgbState = !rgbState
                    editPreferences.putBoolean("RGBState", rgbState).apply()

                    if (rgbState) {
                        imageRGBState.setImageResource(R.drawable.ic_light_on)
                        textRGBState.text = getString(R.string.on_text)

                        realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("SmartRemotes")
                            .child("rgbRemoteButton").setValue("1 1")
                    } else {
                        imageRGBState.setImageResource(R.drawable.ic_light_off)
                        textRGBState.text = getString(R.string.off_text)

                        realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("SmartRemotes")
                            .child("rgbRemoteButton").setValue("2 0")
                    }
                } else {
                    Toast.makeText(requireActivity(), "Нет подключения\nк Интернету!", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun startFragment(fragment: Fragment) {
        vibrate()

        var isAnimationStarted = true
        binding.scrollViewRemotes.animate().alpha(0f).setDuration(500).setStartDelay(0)
            .setListener(object: Animator.AnimatorListener {
                override fun onAnimationStart(animation: Animator) {}

                override fun onAnimationEnd(animation: Animator) {
                    if (isAnimationStarted) {
                        isAnimationStarted = false

                        binding.scrollViewRemotes.visibility = View.GONE
                        binding.frameLayoutRemote.visibility = View.VISIBLE

                        selectedFragment = fragment
                        remoteFragmentManager.beginTransaction().replace(R.id.frameLayoutRemote, fragment).commit()
                        binding.frameLayoutRemote.animate().alpha(1f).setDuration(500).setStartDelay(0).start()
                    }
                }

                override fun onAnimationCancel(animation: Animator) {}
                override fun onAnimationRepeat(animation: Animator) {}

            }).start()
    }

    private val valueEventListener = object: ChildEventListener {
        override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
            updateRemotes(snapshot)
        }

        override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
            updateRemotes(snapshot)
        }

        override fun onChildRemoved(snapshot: DataSnapshot) {}
        override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
        override fun onCancelled(error: DatabaseError) {}

    }

    private fun updateRemotes(snapshot: DataSnapshot) {
        with(binding) {
            when (snapshot.key) {
                "lightRemoteButton" -> {
                    if (snapshot.getValue(String::class.java)!!.last() == '1') {
                        lightState = true
                        imageLightState.setImageResource(R.drawable.ic_light_on)
                        textLightState.text = getString(R.string.on_text)
                    } else {
                        lightState = false
                        imageLightState.setImageResource(R.drawable.ic_light_off)
                        textLightState.text = getString(R.string.off_text)
                    }
                }
                "tvRemoteButton" -> {
                    if (snapshot.getValue(String::class.java)!!.last() == '1') {
                        tvState = true
                        imageTvState.setImageResource(R.drawable.ic_tv_on)
                        textTvState.text = getString(R.string.on_text)
                    } else {
                        tvState = false
                        imageTvState.setImageResource(R.drawable.ic_tv_off)
                        textTvState.text = getString(R.string.off_text)
                    }
                }
                "acRemote" -> {
                    if (snapshot.getValue(String::class.java)!!.last() == '1') {
                        acState = true
                        imageACState.setImageResource(R.drawable.ic_ac_on)
                        textACState.text = getString(R.string.on_text)
                    } else {
                        acState = false
                        imageACState.setImageResource(R.drawable.ic_ac_off)
                        textACState.text = getString(R.string.off_text)
                    }
                }
                "rgbRemoteButton" -> {
                    if (snapshot.getValue(String::class.java)!!.last() == '1') {
                        rgbState = true
                        imageRGBState.setImageResource(R.drawable.ic_light_on)
                        textRGBState.text = getString(R.string.on_text)
                    } else {
                        rgbState = false
                        imageRGBState.setImageResource(R.drawable.ic_light_off)
                        textRGBState.text = getString(R.string.off_text)
                    }
                }
            }
        }
    }

    private val sharedPreferencesChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "CloseRemote") {
                if (sharedPreferences.getBoolean("CloseRemote", false)) {
                    editPreferences.putBoolean("CloseRemote", false).apply()
                    var isAnimationStarted = true
                    binding.frameLayoutRemote.animate().alpha(0f).setDuration(500).setStartDelay(0)
                        .setListener(object: Animator.AnimatorListener {
                            override fun onAnimationStart(animation: Animator) {}

                            override fun onAnimationEnd(animation: Animator) {
                                if (isAnimationStarted) {
                                    isAnimationStarted = false

                                    binding.frameLayoutRemote.visibility = View.GONE
                                    binding.scrollViewRemotes.visibility = View.VISIBLE

                                    val fragmentTransaction = remoteFragmentManager.beginTransaction()
                                    fragmentTransaction.remove(selectedFragment)
                                    fragmentTransaction.commit()
                                    fragmentTransaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_CLOSE)
                                    binding.scrollViewRemotes.animate().alpha(1f).setDuration(500).setStartDelay(0).start()
                                }
                            }

                            override fun onAnimationCancel(animation: Animator) {}
                            override fun onAnimationRepeat(animation: Animator) {}

                        }).start()
                }
            }
        }

    override fun onStart() {
        super.onStart()

        realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("SmartRemotes")
            .addChildEventListener(valueEventListener)
    }

    override fun onStop() {
        super.onStop()

        realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("SmartRemotes")
            .removeEventListener(valueEventListener)
    }

    override fun onDestroy() {
        super.onDestroy()

        sharedPreferences.unregisterOnSharedPreferenceChangeListener(sharedPreferencesChangeListener)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentSmartRemotesBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    @Suppress("DEPRECATION")
    private fun vibrate() {
        if (MainActivity.vibrator.hasVibrator()) {
            if (MainActivity.isHapticFeedbackEnabled == "1") {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    binding.buttonLightOnOff.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING)
                } else {
                    binding.buttonLightOnOff.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING + HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
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