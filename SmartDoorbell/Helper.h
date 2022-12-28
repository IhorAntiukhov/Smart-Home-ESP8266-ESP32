class Helper {
public:
  // метод для записи файла в SPIFFS
  bool saveToSPIFFS(String fileName, String fileContent) {
    File file = SPIFFS.open(fileName, FILE_WRITE);
    if (file) {
      if (file.print(fileContent)) {
        file.close();
        return true;
      } else {
        Serial.println("Не удалось записать в SPIFFS :(");
      }
    } else {
      Serial.println("Не удалось открыть файл из SPIFFS :(");
    }
    return false;
  }

  // метод для получения настроек из SPIFFS
  void getSettings(String &settings, bool &startSleep) {
    File settingsFile = SPIFFS.open("/Settings.txt");
    if (!settingsFile || settingsFile.isDirectory()) {
      Serial.println("\nНе удалось открыть файл с настройками :(");
      ESP.restart();
    }

    while (settingsFile.available()) settings = settingsFile.readString();
    settingsFile.close();

    Serial.println("\nНастройки: " + String(settings));
    // записываем индексы разделительных символов между отдельными параметрами
    int secondHashIndex = settings.indexOf("#", settings.indexOf("#") + 1);
    int thirdHashIndex = settings.indexOf("#", secondHashIndex + 1);
    int fourthHashIndex = settings.indexOf("#", thirdHashIndex + 1);
    int fifthHashIndex = settings.indexOf("#", fourthHashIndex + 1);

    ssidName = settings.substring(0, settings.indexOf("#"));
    ssidPass = settings.substring(settings.indexOf("#") + 1, secondHashIndex);
    userEmail = settings.substring(secondHashIndex + 1, thirdHashIndex);
    userPass = settings.substring(thirdHashIndex + 1, fourthHashIndex);
    timezone = (settings.substring(fourthHashIndex + 1, fifthHashIndex)).toInt();

    resolution = (settings.substring(fifthHashIndex + 1, fifthHashIndex + 2)).toInt();
    if (settings.substring(fifthHashIndex + 2, fifthHashIndex + 3) == "1") flashOnOff = true;
    if (settings.substring(fifthHashIndex + 3, fifthHashIndex + 4) == "1") verticalFlip = true;
    if (settings.substring(fifthHashIndex + 4, fifthHashIndex + 5) == "1") horizontalMirror = true;
    if (settings.substring(fifthHashIndex + 5, fifthHashIndex + 6) == "0") startSleep = false;

    Serial.println("Название WiFi сети: " + String(ssidName));
    Serial.println("Пароль WiFi сети: " + String(ssidPass));
    Serial.println("Почта пользователя: " + String(userEmail));
    Serial.println("Пароль пользователя: " + String(userPass));
    Serial.println("Часовой пояс: " + String(timezone));

    Serial.println("Разрешение: " + String(resolution));
    Serial.println("Включать вспышку: " + String(flashOnOff));
    Serial.println("Переворачивать фото: " + String(verticalFlip));
    Serial.println("Отзеркаливать фото: " + String(horizontalMirror));
    Serial.println("Переходить в сон: " + String(startSleep));
  }

  // метод для проверки состояния кнопки для перехода в режим настройки
  bool checkSettingsModeButton(short SETTINGS_MODE_BUTTON_PIN) {
    bool settingsModeButtonState = !digitalRead(SETTINGS_MODE_BUTTON_PIN);
    if (settingsModeButtonState && !settingsModeButtonFlag && millis() - settingsModeButtonMillis >= 100) {
      settingsModeButtonMillis = millis();
      settingsModeButtonFlag = true;
      Serial.println("Кнопка перехода в режим настройки нажата");
    }

    if (!settingsModeButtonState && settingsModeButtonFlag && millis() - settingsModeButtonMillis >= 100) {
      settingsModeButtonMillis = millis();
      settingsModeButtonFlag = false;
      Serial.println("Кнопка перехода в режим настройки отпущена");
      return false;
    }

    if (settingsModeButtonState && settingsModeButtonFlag && millis() - settingsModeButtonMillis >= 3000) {
      WiFi.mode(WIFI_AP);
      // запускаем WiFi сеть для получения настроек (название, пароль, канал, скрыть сеть, максимальное количество подключений)
      WiFi.softAP("Дверной Звонок", "", 1, false, 1);

      Serial.println("Перешли в режим настройки");
      return true;
    }
    return false;
  }

  // метод для запуска камеры на ESP32-CAM
  void initCamera() {
    camera_config_t config;
    config.ledc_channel = LEDC_CHANNEL_0;
    config.ledc_timer = LEDC_TIMER_0;
    config.pin_d0 = 5;
    config.pin_d1 = 18;
    config.pin_d2 = 19;
    config.pin_d3 = 21;
    config.pin_d4 = 36;
    config.pin_d5 = 39;
    config.pin_d6 = 34;
    config.pin_d7 = 35;
    config.pin_xclk = 0;
    config.pin_pclk = 22;
    config.pin_vsync = 25;
    config.pin_href = 23;
    config.pin_sscb_sda = 26;
    config.pin_sscb_scl = 27;
    config.pin_pwdn = 32;
    config.pin_reset = -1;
    config.xclk_freq_hz = 20000000;
    config.pixel_format = PIXFORMAT_JPEG;

    config.frame_size = FRAMESIZE_SVGA;
    config.jpeg_quality = 12;
    config.fb_count = 1;

    esp_err_t err = esp_camera_init(&config);
    if (err != ESP_OK) {
      Serial.printf("Не удалось запустить камеру, ошибка: 0x%x\n", err);
      ESP.restart();
    }

    sensor_t *camera = esp_camera_sensor_get();  // объект для изменения настроек изображения

    switch (resolution) {
      case 0:
        camera->set_framesize(camera, FRAMESIZE_UXGA);
        break;
      case 1:
        camera->set_framesize(camera, FRAMESIZE_SXGA);
        break;
      case 2:
        camera->set_framesize(camera, FRAMESIZE_XGA);
        break;
      case 3:
        camera->set_framesize(camera, FRAMESIZE_SVGA);
        break;
      case 4:
        camera->set_framesize(camera, FRAMESIZE_VGA);
        break;
      case 5:
        camera->set_framesize(camera, FRAMESIZE_CIF);
        break;
      case 6:
        camera->set_framesize(camera, FRAMESIZE_QVGA);
        break;
    }

    if (verticalFlip) camera->set_vflip(camera, 1);        // устанавливаем то, будет ли фото переворачиваться на 180°
    if (horizontalMirror) camera->set_hmirror(camera, 1);  // устанавливаем то, будет ли фото отзеркаливаться
  }

  // метод для подключения к WiFi сети и к Firebase
  void connectToWiFi(FirebaseData &firebaseData, FirebaseData &stream, FirebaseAuth &auth, FirebaseConfig &config, String &userUID,
                     NTPClient &timeClient, bool startSleep, short connectToWiFiTimeout, byte maxGetTimeAttempts, short doorbellButtonPin) {
    WiFi.begin((const char *)ssidName.c_str(), (const char *)ssidPass.c_str());
    Serial.print("Подключаемся к WiFi сети ");
    short connectToWiFiTime = 0;
    while (WiFi.status() != WL_CONNECTED) {
      delay(250);
      connectToWiFiTime++;
      if (connectToWiFiTime >= connectToWiFiTimeout * 4) {
        Serial.println("Не удалось подключиться к WiFi сети :( Переходим в сон ...");
        startDeepSleep(doorbellButtonPin);
      }
      Serial.print(".");
    }
    Serial.print("\nПодключились к WiFi сети! Локальный IP адрес: ");
    Serial.println(WiFi.localIP());

    byte allGetTimeAttempts = 0;  // количество перезагрузок из-за того, что не удалось получить правильное время от NTP сервера
    if (startSleep) {
      if (SPIFFS.exists("/GetTimeAttempts.txt")) {
        File getTimeAttemptsFile = SPIFFS.open("/GetTimeAttempts.txt");
        if (!getTimeAttemptsFile) {
          Serial.println("\nНе удалось открыть файл с количеством попыток получить правильное время :(");
        }

        while (getTimeAttemptsFile.available()) allGetTimeAttempts = (getTimeAttemptsFile.readString()).toInt();
        getTimeAttemptsFile.close();

        Serial.println("Количество попыток получить правильное время: " + String(allGetTimeAttempts));
      }
    }

    if (allGetTimeAttempts < maxGetTimeAttempts) {
      timeClient.setTimeOffset(timezone * 3600);
      timeClient.begin();
      if (startSleep) {
        byte getTimeAttempts = 0;
        do {
          timeClient.update();
          String formattedTime = String(timeClient.getFormattedTime()).substring(0, 5);
          String formattedDate = timeClient.getFormattedDate();

          String years = formattedDate.substring(0, 4);
          String month = formattedDate.substring(5, 7);
          String days = formattedDate.substring(8, 10);

          timeDate = String(formattedTime) + " " + String(days) + "." + String(month) + "." + String(years);
          getTimeAttempts++;

          Serial.println("Время и дата: " + String(timeDate));
        } while (timeDate.endsWith("1970") && getTimeAttempts < 10);  // получаем время от NTP сервера, пока оно не будет получено правильно

        if (getTimeAttempts == 10) {
          Serial.println("Не удалось получить правильное время от NTP сервера :( Перезагружаем плату ...");
          saveToSPIFFS("/GetTimeAttempts.txt", String(allGetTimeAttempts + 1));
          ESP.restart();
        }

        if (SPIFFS.exists("/GetTimeAttempts.txt")) {
          if (!SPIFFS.remove("/GetTimeAttempts.txt")) Serial.println("Не удалось удалить файл с количеством попыток получить правильное время :(");
        }

        timeClient.end();
      }

      config.api_key = "AIzaSyADkVab9iTJ4F6GDDCwdEeC7i4N6dqeOy8";

      auth.user.email = userEmail;
      auth.user.password = userPass;

      config.database_url = "https://smarthouseesp-default-rtdb.europe-west1.firebasedatabase.app/";
      config.token_status_callback = tokenStatusCallback;

      Firebase.begin(&config, &auth);
      Firebase.reconnectWiFi(true);

      Firebase.FCM.setServerKey(String("AAAAntfJVDU:APA91bHf4C29ImVLI2TXU4CuvWjlBUYbslNiXjPr93l5aVYjtQ16p6KDqLDeAbjO_V")
                                + String("wL-_4QzPuF95n5rZoiqrLNPKJatoJXA6ZvWds4YSqDhxC6sh29E20RdFg9SDEW-xMIa5rXBRWK"));
      config.fcs.upload_buffer_size = 512;

      userUID = (auth.token.uid).c_str();

      if (!startSleep) {
        // если ранее был получен запрос на отправку фото, но из-за того, что не удалось получить правильное время плата перезагрузилась
        if (SPIFFS.exists("/TakePhotoAfterRestart.txt")) {
          if (!SPIFFS.remove("/TakePhotoAfterRestart.txt")) Serial.println("Не удалось удалить файл с тем, нужно ли загрузить фото после перезагрузки :(");

          getFormatTimeDate(timeClient);
          takePhoto();
          sendPhotoAndMessage(firebaseData, auth, userUID, true);
        }

        if (!Firebase.RTDB.beginStream(&stream, ("/" + userUID + "/SmartDoorbell").c_str())) {
          Serial.printf("Не удалось запустить слушатель, %s\n", stream.errorReason().c_str());
          ESP.restart();
        }
      }
    } else {
      if (!SPIFFS.remove("/GetTimeAttempts.txt")) Serial.println("Не удалось удалить файл с количеством попыток получить правильное время :(");
      startDeepSleep(doorbellButtonPin);
    }
  }

  // метод для получения фото и сохранения в SPIFFS
  void takePhoto() {
    camera_fb_t *fb = NULL;
    int photoFileSize = 0;
    do {
      if (flashOnOff) {
        digitalWrite(4, HIGH);  // включаем вспышку
        delay(50);
      }
      fb = esp_camera_fb_get();
      digitalWrite(4, LOW);
      if (!fb) {
        Serial.println("Не удалось получить фото :(");
        return;
      }

      File photoFile = SPIFFS.open("/Photo.jpg", FILE_WRITE);
      if (photoFile) {
        photoFile.write(fb->buf, fb->len);
        photoFileSize = photoFile.size();
        Serial.println("Фото записано в SPIFFS. Размер: " + String(photoFileSize) + " байт");
      } else {
        Serial.println("Не удалось открыть файл для фото из SPIFFS :(");
      }
      esp_camera_fb_return(fb);
      photoFile.close();
    } while (photoFileSize < 100);
  }

  // метод для загрузки фото в Firebase Storage и отправки сообщения через Firebase Messaging
  void sendPhotoAndMessage(FirebaseData &firebaseData, FirebaseAuth &auth, String &userUID, bool sendMessage) {
    String previousPhotoName;
    if (SPIFFS.exists("/PreviousPhotoName.txt")) {
      File previousPhotoNameFile = SPIFFS.open("/PreviousPhotoName.txt");
      if (!previousPhotoNameFile) {
        Serial.println("\nНе удалось открыть файл с названием предыдущего фото :(");
      }

      while (previousPhotoNameFile.available()) previousPhotoName = previousPhotoNameFile.readString();
      previousPhotoNameFile.close();

      if (!SPIFFS.remove("/PreviousPhotoName.txt")) Serial.println("Не удалось удалить файл с названием предыдущего фото :(");

      Serial.println("Название предыдущего фото: " + String(previousPhotoName));
    }
    // если названия текущего и предыдущего фото совпадают, добавляем в конец названия текущего фото (2) или (3)
    String photoName = timeDate;
    if (photoName == previousPhotoName.substring(1, 17)) {
      if (previousPhotoName.indexOf("(2)") == -1) {
        photoName = photoName + " (2)";
      } else {
        photoName = photoName + " (3)";
      }
    }
    photoName = "/" + photoName + ".jpg";
    Serial.println("Название текущего фото: " + String(photoName));

    String photoUrl;  // ссылка на скачивание загруженного фото
    // загружаем фото в Firebase Storage
    if (Firebase.Storage.upload(&firebaseData, "smarthouseesp.appspot.com", "/Photo.jpg", mem_storage_type_flash,
                                ("/" + userUID + photoName).c_str(), "image/jpg")) {
      photoUrl = firebaseData.downloadURL().c_str();
      Serial.printf("Фото загружено в Firebase, ссылка на скачивание: %s\n", firebaseData.downloadURL().c_str());
      saveToSPIFFS("/PreviousPhotoName.txt", photoName);

      if (!sendMessage) {
        if (!Firebase.RTDB.setString(&firebaseData, ("/" + userUID + "/SmartDoorbell/photoUrl").c_str(), firebaseData.downloadURL().c_str()))
          Serial.println("Не удалось записать ссылку на скачивание в Firebase :( Причина: " + String(firebaseData.errorReason().c_str()));
      }
    } else {
      Serial.println("Не удалось загрузить фото в Firebase, ошибка: " + String(firebaseData.errorReason()));

      if (!sendMessage) {
        if (!Firebase.RTDB.setString(&firebaseData, ("/" + userUID + "/SmartDoorbell/photoUrl").c_str(), "error"))
          Serial.println("Не удалось записать в Firebase то, что не удалось загрузить фото в Firebase :( Причина: " + String(firebaseData.errorReason().c_str()));
      }
    }

    if (sendMessage) {
      FCM_Legacy_HTTP_Message message;

      message.targets.to = "/topics/" + userUID;

      FirebaseJson payload;
      payload.add("photoUrl", photoUrl);  // добавляем к данным сообщения ссылку на скачивание фото для того, чтобы это фото отобразилось в уведомлении
      message.payloads.data = payload.raw();

      // отправляем сообщение через Firebase Messaging
      if (Firebase.FCM.send(&firebaseData, &message))
        Serial.printf("Сообщение отправлено, %s\n", Firebase.FCM.payload(&firebaseData).c_str());
      else
        Serial.println("Не удалось отправить сообщение, ошибка: " + String(firebaseData.errorReason()));
    }
  }

  // метод для перехода в глубокий сон
  void startDeepSleep(short doorbellButtonPin) {
    digitalWrite(4, LOW);
    rtc_gpio_hold_en(GPIO_NUM_4);                                    // делаем так, чтобы во время сна вспышка была выключена
    esp_sleep_enable_ext0_wakeup((gpio_num_t)doorbellButtonPin, 0);  // устанавливаем пин для выхода из сна
    esp_deep_sleep_start();
  }

  // метод для получения времени и даты от NTP сервера
  void getFormatTimeDate(NTPClient &timeClient) {
    byte getTimeAttempts = 0;
    do {
      timeClient.forceUpdate();
      String formattedTime = String(timeClient.getFormattedTime()).substring(0, 5);
      String formattedDate = timeClient.getFormattedDate();

      String years = formattedDate.substring(0, 4);
      String month = formattedDate.substring(5, 7);
      String days = formattedDate.substring(8, 10);

      timeDate = String(formattedTime) + " " + String(days) + "." + String(month) + "." + String(years);

      Serial.println("Время и дата: " + String(timeDate));

      getTimeAttempts++;
    } while (timeDate.endsWith("1970") && getTimeAttempts < 10);  // получаем время от NTP сервера, пока оно не будет получено правильно

    if (getTimeAttempts == 10) {
      Serial.println("Не удалось получить правильное время от NTP сервера :( Перезагружаем плату ...");
      saveToSPIFFS("/TakePhotoAfterRestart.txt", "1");
      ESP.restart();
    }
  }

private:
  String ssidName;
  String ssidPass;
  String userEmail;
  String userPass;
  short timezone = 0;

  byte resolution = 3;
  bool flashOnOff;
  bool verticalFlip;
  bool horizontalMirror;

  bool settingsModeButtonFlag;
  unsigned long settingsModeButtonMillis = 0;

  String timeDate;
};
