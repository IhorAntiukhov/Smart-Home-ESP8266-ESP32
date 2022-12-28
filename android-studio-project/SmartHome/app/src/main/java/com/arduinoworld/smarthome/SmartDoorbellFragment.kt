package com.arduinoworld.smarthome

import android.animation.Animator
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.provider.MediaStore
import android.view.*
import android.widget.PopupMenu
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.arduinoworld.smarthome.MainActivity.Companion.editPreferences
import com.arduinoworld.smarthome.MainActivity.Companion.firebaseAuth
import com.arduinoworld.smarthome.MainActivity.Companion.isNetworkConnected
import com.arduinoworld.smarthome.MainActivity.Companion.realtimeDatabase
import com.arduinoworld.smarthome.MainActivity.Companion.sharedPreferences
import com.arduinoworld.smarthome.databinding.FragmentSmartDoorbellBinding
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.google.firebase.storage.ktx.storage
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class SmartDoorbellFragment : Fragment() {
    companion object {
        var selectPhotoMode = 0
    }

    private lateinit var binding: FragmentSmartDoorbellBinding
    private lateinit var storage: FirebaseStorage
    private lateinit var photosRecyclerAdapter: PhotosRecyclerAdapter
    private lateinit var gson: Gson
    private lateinit var valueEventListener: ValueEventListener
    private var photosArrayList = ArrayList<Photo>()
    private var recyclerViewPosition = 0

    @RequiresApi(Build.VERSION_CODES.O)
    @SuppressLint("NotifyDataSetChanged", "DiscouragedPrivateApi")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        with(binding) {
            selectPhotoMode = 0
            gson = Gson()
            storage = Firebase.storage

            sharedPreferences.registerOnSharedPreferenceChangeListener(sharedPreferencesChangeListener)

            val notificationManager = requireActivity().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (!sharedPreferences.getBoolean("isNotificationGroupCreated", false)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val notificationGroup = NotificationChannelGroup("SmartHome", "Умный Дом")
                    notificationManager.createNotificationChannelGroup(notificationGroup)
                    editPreferences.putBoolean("isNotificationGroupCreated", true)
                }
            }
            if (!sharedPreferences.getBoolean(firebaseAuth.currentUser!!.uid, false)) {
                FirebaseMessaging.getInstance().subscribeToTopic(firebaseAuth.currentUser!!.uid).addOnCompleteListener { subscribeToTopicTask ->
                    if (subscribeToTopicTask.isSuccessful) {
                        editPreferences.putBoolean(firebaseAuth.currentUser!!.uid, true).apply()

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            val notificationChannel = NotificationChannel("SmartDoorbellNotification", "Уведомление дверного звонка", NotificationManager.IMPORTANCE_HIGH)
                            notificationChannel.description = "Уведомление, которое появляется когда кто-то нажал на кнопку звонка"
                            notificationChannel.enableLights(true)
                            notificationChannel.enableVibration(true)
                            val audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build()
                            notificationChannel.setSound(Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + requireActivity().packageName + "/" + R.raw.notification), audioAttributes)
                            notificationChannel.group = "SmartHome"
                            notificationChannel.lockscreenVisibility = View.VISIBLE
                            notificationManager.createNotificationChannel(notificationChannel)
                        }
                    } else {
                        Toast.makeText(requireActivity(), "Не удалось подписаться на топик уведомлений. Попробуйте выйти и войти в пользователя.", Toast.LENGTH_LONG).show()
                    }
                }
            }

            var photosArrayListIsNotEmpty = false
            if (sharedPreferences.getString("PhotosArrayList", "") != "") {
                photosArrayListIsNotEmpty = true
                photosArrayList = gson.fromJson(
                    sharedPreferences.getString("PhotosArrayList", ""),
                    object : TypeToken<ArrayList<Photo?>?>() {}.type
                )

                layoutNoPhotos.visibility = View.GONE
                recyclerViewPhotos.visibility = View.VISIBLE

                photosRecyclerAdapter = PhotosRecyclerAdapter(photosArrayList)
                photosRecyclerAdapter.setOnItemClickListener(photosRecyclerAdapterClickListener)
                recyclerViewPhotos.apply {
                    adapter = photosRecyclerAdapter
                    layoutManager = LinearLayoutManager(requireActivity())
                }
            }
            photosArrayList.clear()

            storage.reference.child(firebaseAuth.currentUser!!.uid).listAll().addOnCompleteListener {
                if (it.isSuccessful) {
                    val items: List<StorageReference> = it.result.items
                    if (items.isNotEmpty()) {
                        val photoUrlsList = ArrayList<String>()
                        val photoNamesList = ArrayList<String>()
                        val photoNamesForSortingList = ArrayList<String>()
                        val photoDatesList = ArrayList<Date>()

                        val calendar = Calendar.getInstance()
                        val simpleDateFormat = SimpleDateFormat("HH:mm:ss dd.MM.yyyy", Locale.US)
                        items.forEach { item ->
                            item.downloadUrl.addOnCompleteListener { downloadUrlTask ->
                                if (downloadUrlTask.isSuccessful) {
                                    val photoName = item.name.replace(".jpg", "")
                                    photoUrlsList.add(downloadUrlTask.result.toString())
                                    photoNamesList.add(photoName)
                                    var photoNameForSorting = ""
                                    if (!photoName.contains("(2)") && !photoName.contains("(3)")) {
                                        photoNameForSorting = photoName.substring(0, 16).replaceRange(5, 6, ":00 ")
                                    } else if (photoName.contains("(2)")) {
                                        photoNameForSorting = photoName.substring(0, 16).replaceRange(5, 6, ":01 ")
                                    } else if (photoName.contains("(3)")) {
                                        photoNameForSorting = photoName.substring(0, 16).replaceRange(5, 6, ":02 ")
                                    }
                                    photoNamesForSortingList.add(photoNameForSorting)
                                    val date = simpleDateFormat.parse(photoNameForSorting)
                                    if (date != null) photoDatesList.add(date)

                                    if (items.size == photoUrlsList.size) {
                                        photoDatesList.sortDescending()
                                        photoDatesList.forEach { photoDate ->
                                            calendar.time = photoDate
                                            var seconds = calendar.get(Calendar.SECOND).toString()
                                            var hours = calendar.get(Calendar.HOUR_OF_DAY).toString()
                                            var minutes = calendar.get(Calendar.MINUTE).toString()
                                            var days = calendar.get(Calendar.DAY_OF_MONTH).toString()
                                            var month = (calendar.get(Calendar.MONTH) + 1).toString()

                                            seconds = "0$seconds"
                                            if (hours.toInt() < 10) hours = "0$hours"
                                            if (minutes.toInt() < 10) minutes = "0$minutes"
                                            if (days.toInt() < 10) days = "0$days"
                                            if (month.toInt() < 10) month = "0$month"

                                            val index = photoNamesForSortingList.indexOf("$hours:$minutes:$seconds $days.$month.${calendar.get(
                                                Calendar.YEAR)}")
                                            photosArrayList.add(Photo(photoUrlsList[index], photoNamesList[index]))
                                        }

                                        while (photosArrayList.size > sharedPreferences.getString("MaxPhotos", "5")!!.toInt()) {
                                            storage.getReferenceFromUrl(photosArrayList[photosArrayList.size - 1].photoUrl).delete()
                                            photosArrayList.removeAt(photosArrayList.size - 1)
                                        }

                                        editPreferences.putString("PhotosArrayList", gson.toJson(photosArrayList)).apply()
                                        if (!photosArrayListIsNotEmpty) {
                                            photosRecyclerAdapter = PhotosRecyclerAdapter(photosArrayList)
                                            photosRecyclerAdapter.setOnItemClickListener(photosRecyclerAdapterClickListener)
                                            recyclerViewPhotos.apply {
                                                adapter = photosRecyclerAdapter
                                                layoutManager = LinearLayoutManager(requireActivity())
                                            }
                                        } else {
                                            photosRecyclerAdapter.notifyDataSetChanged()
                                        }

                                        layoutNoPhotos.visibility = View.GONE
                                        recyclerViewPhotos.visibility = View.VISIBLE
                                    }
                                }
                            }
                        }
                    } else {
                        recyclerViewPhotos.visibility = View.GONE
                        layoutNoPhotos.visibility = View.VISIBLE
                        editPreferences.putString("PhotosArrayList", "").apply()
                    }
                }
            }

            fabMenu.setOnClickListener {
                if (selectPhotoMode == 0) {
                    vibrate()
                    if (isNetworkConnected(requireActivity())) {
                        val popupMenu = object: PopupMenu(requireActivity(), fabMenu){}
                        popupMenu.setOnMenuItemClickListener { item ->
                            when (item!!.itemId) {
                                R.id.buttonTakePhoto -> {
                                    vibrate()
                                    realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("SmartDoorbell").child("photoUrl")
                                        .setValue("takePhoto").addOnCompleteListener { setValueTask ->
                                            if (setValueTask.isSuccessful) {
                                                Toast.makeText(requireActivity(), "Ждём получение фото ...", Toast.LENGTH_SHORT).show()
                                                valueEventListener = object: ValueEventListener {
                                                    override fun onDataChange(snapshot: DataSnapshot) {
                                                        val photoUrl = snapshot.getValue(String::class.java)!!
                                                        if (photoUrl != "takePhoto" && photoUrl != "error") {
                                                            val photo = storage.getReferenceFromUrl(photoUrl)
                                                            photo.downloadUrl.addOnCompleteListener { downloadUrlTask ->
                                                                if (downloadUrlTask.isSuccessful) {
                                                                    photosArrayList.add(0, Photo(downloadUrlTask.result.toString(), photo.name.replace(".jpg", "")))
                                                                    if (photosArrayList.size > 0) {
                                                                        photosRecyclerAdapter = PhotosRecyclerAdapter(photosArrayList)
                                                                        photosRecyclerAdapter.setOnItemClickListener(photosRecyclerAdapterClickListener)
                                                                        recyclerViewPhotos.apply {
                                                                            adapter = photosRecyclerAdapter
                                                                            layoutManager = LinearLayoutManager(requireActivity())
                                                                        }

                                                                        var isAnimationStarted = true
                                                                        layoutNoPhotos.animate().alpha(0f).setDuration(500).setStartDelay(0)
                                                                            .setListener(object: Animator.AnimatorListener {
                                                                                override fun onAnimationStart(animation: Animator) {}

                                                                                override fun onAnimationEnd(animation: Animator) {
                                                                                    if (isAnimationStarted) {
                                                                                        isAnimationStarted = false

                                                                                        layoutNoPhotos.visibility = View.GONE
                                                                                        recyclerViewPhotos.visibility = View.VISIBLE

                                                                                        recyclerViewPhotos.animate().alpha(1f).setDuration(500).setStartDelay(0).start()
                                                                                    }
                                                                                }

                                                                                override fun onAnimationCancel(animation: Animator) {}
                                                                                override fun onAnimationRepeat(animation: Animator) {}

                                                                            }).start()
                                                                    } else {
                                                                        @Suppress("KotlinConstantConditions")
                                                                        if (photosArrayList.size > sharedPreferences.getString("MaxPhotos", "5")!!.toInt()) {
                                                                            storage.getReferenceFromUrl(photosArrayList[photosArrayList.size - 1].photoUrl).delete()
                                                                                .addOnCompleteListener { deletePhotoTask ->
                                                                                    if (deletePhotoTask.isSuccessful) {
                                                                                        photosArrayList.removeAt(photosArrayList.size - 1)
                                                                                    } else {
                                                                                        Toast.makeText(requireActivity(), "Не удалось удалить фото ${photosArrayList[photosArrayList.size - 1].photoName}!", Toast.LENGTH_LONG).show()
                                                                                    }
                                                                                }
                                                                        }

                                                                        (recyclerViewPhotos.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(0, 0)
                                                                        photosRecyclerAdapter.notifyItemInserted(0)
                                                                    }
                                                                } else {
                                                                    Toast.makeText(requireActivity(), "Не удалось получить ссылку на фото!", Toast.LENGTH_LONG).show()
                                                                }
                                                            }
                                                            realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("SmartDoorbell").child("photoUrl")
                                                                .removeEventListener(valueEventListener)
                                                        } else if (photoUrl == "error") {
                                                            Toast.makeText(requireActivity(), "Плате не удалось загрузить фото в Firebase!", Toast.LENGTH_LONG).show()
                                                        }
                                                    }

                                                    override fun onCancelled(error: DatabaseError) {}

                                                }
                                                realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("SmartDoorbell").child("photoUrl")
                                                    .addValueEventListener(valueEventListener)
                                            } else {
                                                Toast.makeText(requireActivity(), "Не удалось отправить запрос\nна получение фото!", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                }
                                R.id.buttonDownloadPhoto -> {
                                    vibrate()
                                    if (photosArrayList.size > 0) {
                                        selectPhotoMode = 1
                                        hideMenuShowText()
                                    } else {
                                        Toast.makeText(requireActivity(), "Вы не получили ни одного фото!", Toast.LENGTH_LONG).show()
                                    }
                                }
                                R.id.buttonDeletePhoto -> {
                                    vibrate()
                                    if (photosArrayList.size > 0) {
                                        selectPhotoMode = 2
                                        hideMenuShowText()
                                    } else {
                                        Toast.makeText(requireActivity(), "Вы не получили ни одного фото!", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                            true
                        }
                        popupMenu.inflate(R.menu.smart_doorbell_menu)

                        try {
                            val fieldMPopup = PopupMenu::class.java.getDeclaredField("mPopup")
                            fieldMPopup.isAccessible = true
                            val mPopup = fieldMPopup.get(popupMenu)
                            mPopup.javaClass.getDeclaredMethod("setForceShowIcon", Boolean::class.java)
                                .invoke(mPopup, true)
                        } catch(_: Exception) {}
                        finally {
                            popupMenu.show()
                        }
                    } else {
                        Toast.makeText(requireActivity(), "Нет подключения\nк Интернету!", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private val photosRecyclerAdapterClickListener = object: PhotosRecyclerAdapter.OnItemClickListener {
        override fun onItemClick(position: Int) {
            if (selectPhotoMode == 1) {
                vibrate()
                if (isNetworkConnected(requireActivity())) {
                    recyclerViewPosition = position
                    storage.getReferenceFromUrl(photosArrayList[recyclerViewPosition].photoUrl).getBytes(1024 * 1000).addOnSuccessListener { bytes ->
                        val imageBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        val fileOutputStream = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            val myContentResolver = requireActivity().contentResolver
                            val contentValues = ContentValues()
                            contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, (photosArrayList[recyclerViewPosition].photoName).replace(":", "-") + ".jpg")
                            contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                            contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, sharedPreferences.getString("PhotosDirectory", "Pictures/Дверной Звонок").toString())
                            val imageUri = myContentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)!!
                            myContentResolver.openOutputStream(imageUri) as FileOutputStream
                        } else {
                            val imagesDir = File(sharedPreferences.getString("PhotosDirectory", "Pictures/Дверной Звонок").toString())
                            if (!imagesDir.exists()) {
                                imagesDir.mkdir()
                            }
                            FileOutputStream(File(imagesDir, (photosArrayList[recyclerViewPosition].photoName)/*.replace(":", "-")*/ + ".jpg"))
                        }
                        imageBitmap.compress(Bitmap.CompressFormat.JPEG, 100, fileOutputStream)
                        fileOutputStream.flush()
                        fileOutputStream.close()
                        Toast.makeText(requireActivity(), "Фото сохранено!", Toast.LENGTH_LONG).show()
                    }.addOnFailureListener {
                        Toast.makeText(requireActivity(), "Не удалось\nсохранить фото!", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(requireActivity(), "Нет подключения\nк Интернету!", Toast.LENGTH_LONG).show()
                }
            } else if (selectPhotoMode == 2) {
                vibrate()
                if (isNetworkConnected(requireActivity())) {
                    Firebase.storage.getReferenceFromUrl(photosArrayList[position].photoUrl).delete()
                        .addOnCompleteListener { deleteImageTask ->
                            if (deleteImageTask.isSuccessful) {
                                photosArrayList.removeAt(position)
                                photosRecyclerAdapter.notifyItemRemoved(position)
                                Toast.makeText(requireActivity(), "Фото удалено!", Toast.LENGTH_SHORT).show()
                                if (photosArrayList.isEmpty()) {
                                    selectPhotoMode = 0
                                    editPreferences.putString("PhotosArrayList", "").apply()
                                    with(binding) {
                                        recyclerViewPhotos.visibility = View.GONE
                                        textSelectPhoto.visibility = View.GONE
                                        layoutNoPhotos.visibility = View.VISIBLE
                                        fabMenu.visibility = View.VISIBLE

                                        layoutNoPhotos.alpha = 0f
                                        layoutNoPhotos.animate().alpha(1f).setDuration(500).setStartDelay(0).start()
                                    }
                                }
                            } else {
                                Toast.makeText(requireActivity(), "Не удалось\nудалить фото!", Toast.LENGTH_LONG).show()
                            }
                        }
                } else {
                    Toast.makeText(requireActivity(), "Нет подключения\nк Интернету!", Toast.LENGTH_LONG).show()
                }
            }
        }

    }

    private fun hideMenuShowText() {
        with(binding) {
            var isAnimationStarted = true
            fabMenu.animate().alpha(0f).setDuration(500).setStartDelay(0)
                .setListener(object: Animator.AnimatorListener {
                    override fun onAnimationStart(animation: Animator) {}

                    override fun onAnimationEnd(animation: Animator) {
                        if (isAnimationStarted) {
                            isAnimationStarted = false

                            fabMenu.visibility = View.INVISIBLE
                            textSelectPhoto.visibility = View.VISIBLE

                            textSelectPhoto.animate().alpha(1f).setDuration(500).setStartDelay(0).start()
                        }
                    }

                    override fun onAnimationCancel(animation: Animator) {}
                    override fun onAnimationRepeat(animation: Animator) {}

                }).start()
        }
    }

    private val sharedPreferencesChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "CancelPhotoMode") {
                if (sharedPreferences.getBoolean("CancelPhotoMode", false)) {
                    editPreferences.putBoolean("CancelPhotoMode", false).apply()
                    with(binding) {
                        var isAnimationStarted = true
                        textSelectPhoto.animate().alpha(0f).setDuration(500).setStartDelay(0)
                            .setListener(object: Animator.AnimatorListener {
                                override fun onAnimationStart(animation: Animator) {}

                                override fun onAnimationEnd(animation: Animator) {
                                    if (isAnimationStarted) {
                                        isAnimationStarted = false

                                        textSelectPhoto.visibility = View.INVISIBLE
                                        fabMenu.visibility = View.VISIBLE

                                        fabMenu.animate().alpha(1f).setDuration(500).setStartDelay(0).start()
                                    }
                                }

                                override fun onAnimationCancel(animation: Animator) {}
                                override fun onAnimationRepeat(animation: Animator) {}

                            }).start()
                    }
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
        binding = FragmentSmartDoorbellBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    @Suppress("DEPRECATION")
    private fun vibrate() {
        if (MainActivity.vibrator.hasVibrator()) {
            if (MainActivity.isHapticFeedbackEnabled == "1") {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    binding.fabMenu.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING)
                } else {
                    binding.fabMenu.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING + HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
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