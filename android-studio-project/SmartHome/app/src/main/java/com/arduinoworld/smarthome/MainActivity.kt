package com.arduinoworld.smarthome

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.*
import android.view.HapticFeedbackConstants
import android.view.Menu
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentManager
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import com.arduinoworld.smarthome.databinding.ActivityMainBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase


class MainActivity : AppCompatActivity() {
    companion object {
        lateinit var sharedPreferences: SharedPreferences
        lateinit var editPreferences: SharedPreferences.Editor
        lateinit var vibrator: Vibrator
        lateinit var firebaseAuth: FirebaseAuth
        lateinit var realtimeDatabase: DatabaseReference
        @SuppressLint("StaticFieldLeak")
        lateinit var binding: ActivityMainBinding

        var isHapticFeedbackEnabled = "1"
        var isUserLogged = false

        @Suppress("DEPRECATION")
        fun isNetworkConnected(context: Context) : Boolean {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = connectivityManager.activeNetwork ?: return false
                val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
                return when {
                    activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                    activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
                    else -> false
                }
            } else {
                val networkInfo = connectivityManager.activeNetworkInfo ?: return false
                return networkInfo.isConnected
            }
        }

        fun hideKeyboard(activity: Activity) {
            activity.currentFocus?.let { view ->
                val inputMethodManager = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                inputMethodManager!!.hideSoftInputFromWindow(view.windowToken, 0)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this@MainActivity)
        editPreferences = sharedPreferences.edit()

        firebaseAuth = FirebaseAuth.getInstance()
        realtimeDatabase = FirebaseDatabase.getInstance().reference

        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }

        isHapticFeedbackEnabled = sharedPreferences.getString("isHapticFeedbackEnabled", "1").toString()
        isUserLogged = sharedPreferences.getBoolean("isUserLogged", false)

        val bundle = Bundle()

        with(binding) {
            val fragmentManager: FragmentManager = supportFragmentManager
            var selectedTab = 0

            if (isUserLogged) {
                bundle.putLong("StartDelay", 400)
                val fragment = DevicesFragment()
                fragment.arguments = bundle
                fragmentManager.beginTransaction().replace(R.id.frameLayoutMain, fragment).commit()
            } else {
                selectedTab = 1
                bottomNavigationView.selectedItemId = R.id.buttonUserProfile
                fragmentManager.beginTransaction().replace(R.id.frameLayoutMain, UserProfileFragment()).commit()
            }

            bottomNavigationView.setOnItemSelectedListener { item ->
                when (item.itemId) {
                    R.id.buttonMainPage -> {
                        if (isUserLogged) {
                            if (selectedTab != 0) {
                                vibrate()
                                bundle.putLong("StartDelay", 200)
                                val fragment = DevicesFragment()
                                fragment.arguments = bundle
                                fragmentManager.beginTransaction().replace(R.id.frameLayoutMain, fragment).commit()
                                setTheme(R.style.Theme_SmartHome_NoActionBar)
                                selectedTab = 0
                            }
                        } else {
                            Handler(Looper.getMainLooper()).postDelayed({
                                bottomNavigationView.selectedItemId = R.id.buttonUserProfile
                            }, 50)
                            Toast.makeText(baseContext, "Вы не вошли\nв пользователя!", Toast.LENGTH_LONG).show()
                        }
                    }
                    R.id.buttonUserProfile -> {
                        if (selectedTab != 1) {
                            vibrate()
                            fragmentManager.beginTransaction().replace(R.id.frameLayoutMain, UserProfileFragment()).commit()
                            setTheme(R.style.Theme_SmartHome_NoActionBar)
                            selectedTab = 1
                        }
                    }
                    R.id.buttonSettings -> {
                        if (isUserLogged) {
                            if (selectedTab != 2) {
                                vibrate()
                                fragmentManager.beginTransaction().replace(R.id.frameLayoutMain, SettingsFragment()).commit()
                                setTheme(R.style.SettingsStyle)
                                selectedTab = 2
                            }
                        } else {
                            Handler(Looper.getMainLooper()).postDelayed({
                                bottomNavigationView.selectedItemId = R.id.buttonUserProfile
                            }, 50)
                            Toast.makeText(baseContext, "Вы не вошли\nв пользователя!", Toast.LENGTH_LONG).show()
                        }
                    }
                }
                true
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val actionButtonsInflater = menuInflater
        actionButtonsInflater.inflate(R.menu.activity_main_menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    @Suppress("DEPRECATION")
    private fun vibrate() {
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