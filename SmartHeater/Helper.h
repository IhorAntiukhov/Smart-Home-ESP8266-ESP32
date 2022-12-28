#include "LittleFS.h"
class Helper {
public:
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
      // запускаем WiFi сеть для получения настроек (название, пароль, канал, скрытая сеть, максимальное количество подключений)
      WiFi.softAP("Обогреватель", "", 1, false, 1);

      Serial.println("Перешли в режим настройки");
      return true;
    }
    return false;
  }

  // метод для получения настроек из SPIFFS
  void getSettings(String &settings, short &sensorInterval) {
    File settingsFile = LittleFS.open("/Settings.txt", "r");
    if (!settingsFile) {
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
    sensorInterval = (settings.substring(fifthHashIndex + 1, settings.length() - 1)).toInt();
    if (settings.substring(settings.length() - 1, settings.length()) == "1") notificationsEnabled = true;

    Serial.println("Название WiFi сети: " + String(ssidName));
    Serial.println("Пароль WiFi сети: " + String(ssidPass));
    Serial.println("Почта пользователя: " + String(userEmail));
    Serial.println("Пароль пользователя: " + String(userPass));
    Serial.println("Часовой пояс: " + String(timezone));
    Serial.println("Интервал датчика: " + String(sensorInterval));
    Serial.println("Включены ли уведомления: " + String(notificationsEnabled));
  }

  // метод для получения настроек режима по времени
  void getTimeModeSettings(String &timeModeSettings, bool &isTemperatureModeStartedInTimeMode, bool &isTemperatureModeStartedNow,
                           String &heaterOnOffTime, bool &heaterOnOff, short relayPin) {
    if (LittleFS.exists("/TimeMode.txt")) {
      File timeModeSettingsFile = LittleFS.open("/TimeMode.txt", "r");
      if (!timeModeSettingsFile) {
        Serial.println("\nНе удалось открыть файл с настройками режима по времени :(");
        ESP.restart();
      }

      heaterOnOffTime = "";

      while (timeModeSettingsFile.available()) timeModeSettings = timeModeSettingsFile.readString();
      timeModeSettingsFile.close();

      Serial.println("Настройки режима по времени: " + String(timeModeSettings));

      if (timeModeSettings.indexOf("H") != -1) {
        if (timeModeSettings.substring(1, 2) == "1") {
          heaterOnOff = true;
          digitalWrite(relayPin, HIGH);
          Serial.println("Обогреватель запущен по времени");
        }
      } else if (timeModeSettings.indexOf("T") != -1) {
        if (timeModeSettings.substring(1, 2) == "1") {
          heaterOnOff = true;
          isTemperatureModeStartedInTimeMode = true;
          isTemperatureModeStartedNow = true;
          Serial.println("Режим по температуре запущен по времени");
        }
      }
    }
  }

  // метод для подключения к WiFi сети и к Firebase
  void connectToWiFi(String &userUID, FirebaseData &firebaseData, FirebaseData &stream, FirebaseAuth &auth, FirebaseConfig &config,
                     FirebaseData::MultiPathStreamEventCallback streamCallback, FirebaseData::StreamTimeoutCallback streamTimeoutCallback,
                     NTPClient &timeClient, unsigned long &getTimeMillis, byte &hours, byte &minutes, byte maxGetTimeAttempts,
                     bool &timeNotReceivedCorrectly, unsigned long &restartMillis) {
    WiFi.begin(ssidName, ssidPass);
    Serial.print("Подключаемся к WiFi сети ");
    while (WiFi.status() != WL_CONNECTED) {
      Serial.print(".");
      delay(250);
    }
    Serial.print("\nПодключились к WiFi сети! Локальный IP адрес: ");
    Serial.println(WiFi.localIP());

    byte allGetTimeAttempts = 0;  // количество перезагрузок из-за того, что не удалось получить правильное время от NTP сервера
    if (LittleFS.exists("/GetTimeAttempts.txt")) {
      File getTimeAttemptsFile = LittleFS.open("/GetTimeAttempts.txt", "r");
      if (!getTimeAttemptsFile) {
        Serial.println("\nНе удалось открыть файл с количеством попыток получить правильное время :(");
      }

      while (getTimeAttemptsFile.available()) allGetTimeAttempts = (getTimeAttemptsFile.readString()).toInt();
      getTimeAttemptsFile.close();

      Serial.println("Количество попыток получить правильное время: " + String(allGetTimeAttempts));
    }

    if (allGetTimeAttempts < maxGetTimeAttempts) {
      byte getTimeAttempts = 0;
      timeClient.setTimeOffset(timezone * 3600);
      timeClient.begin();
      do {
        timeClient.forceUpdate();
        String formattedTime = timeClient.getFormattedTime();
        getTimeMillis = millis();

        String hoursStr = formattedTime.substring(0, 2);
        String minutesStr = formattedTime.substring(3, 5);

        if (hoursStr.startsWith("0")) {
          hoursStr.remove(0, 1);
        }
        if (minutesStr.startsWith("0")) {
          minutesStr.remove(0, 1);
        }

        hours = hoursStr.toInt();
        minutes = minutesStr.toInt();

        formattedHours = String(hours);
        formattedMinutes = String(minutes);

        if (formattedHours.toInt() < 10) {
          formattedHours = "0" + formattedHours;
        }
        if (formattedMinutes.toInt() < 10) {
          formattedMinutes = "0" + formattedMinutes;
        }

        Serial.println("Текущее время: " + String(formattedHours) + ":" + String(formattedMinutes));

        getTimeAttempts++;
      } while (hours == timezone && minutes == 0 && getTimeAttempts < 10);  // получаем время от NTP сервера, пока оно не будет получено правильно

      if (getTimeAttempts == 10) {
        Serial.println("Не удалось получить правильное время от NTP сервера :( Перезагружаем плату ...");
        saveToSPIFFS("/GetTimeAttempts.txt", String(allGetTimeAttempts + 1));
        ESP.restart();
      }

      if (LittleFS.exists("/GetTimeAttempts.txt")) {
        if (!LittleFS.remove("/GetTimeAttempts.txt")) Serial.println("Не удалось удалить файл с количеством попыток получить правильное время :(");
      }

      timeClient.end();

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

#if defined(ESP8266)
      stream.setBSSLBufferSize(2048, 512);
#endif

      userUID = String(auth.token.uid.c_str());
      if (Firebase.RTDB.beginMultiPathStream(&stream, (userUID + "/Heater").c_str())) {
        Firebase.RTDB.setMultiPathStreamCallback(&stream, streamCallback, streamTimeoutCallback);
      } else {
        Serial.printf("Не удалось запустить слушатель, %s\n\n", stream.errorReason().c_str());
        ESP.restart();
      }
    } else {
      timeNotReceivedCorrectly = true;
      restartMillis = millis();
      if (!LittleFS.remove("/GetTimeAttempts.txt")) Serial.println("Не удалось удалить файл с количеством попыток получить правильное время :(");
    }
  }

  // метод для записи файла в SPIFFS
  bool saveToSPIFFS(String fileName, String fileContent) {
    File file = LittleFS.open(fileName, "w");
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

  // метод для высчитывания текущего времени
  void getFormatTime(byte &hours, byte &minutes) {
    minutes++;
    if (minutes == 60) {
      hours++;
      minutes = 0;
    }
    if (hours == 24) {
      hours = 0;
      minutes = 0;
    }

    formattedHours = String(hours);
    formattedMinutes = String(minutes);

    if (formattedHours.toInt() < 10) {
      formattedHours = "0" + formattedHours;
    }
    if (formattedMinutes.toInt() < 10) {
      formattedMinutes = "0" + formattedMinutes;
    }

    Serial.println("Текущее время: " + String(formattedHours) + ":" + String(formattedMinutes));
  }

  // метод для управления обогревателем в режиме по времени
  void controlHeaterInTimeMode(String heaterOnOffTime, String timeModeSettings, short &timestampId, bool &heaterOnOff, short relayPin) {
    String nextTimestamp = heaterOnOffTime.substring(timestampId, timestampId + 4);  // записываем следующее время включения/выключения обогревателя
    if (formattedHours + formattedMinutes == nextTimestamp) {                        // если текущее время равно времени включения/выключения обогревателя
      heaterOnOff = !heaterOnOff;
      if (heaterOnOff) {
        timeModeSettings = "H1";
        digitalWrite(relayPin, HIGH);
        Serial.println("Обогреватель запущен по времени");
      } else {
        timeModeSettings = "H0";
        digitalWrite(relayPin, LOW);
        Serial.println("Обогреватель остановлен по времени");
      }
      saveToSPIFFS("/TimeMode.txt", timeModeSettings);
      if (timestampId + 4 < heaterOnOffTime.length()) {
        timestampId = timestampId + 4;
      } else {
        timestampId = 0;
      }
    }
  }

  // метод для запуска/остановки режима управления обогревателем по температуре, в режиме по времени
  void controlTemperatureModeInTimeMode(String heaterOnOffTime, short &timestampId, bool &isTemperatureModeStartedInTimeMode,
                                        bool &isTemperatureModeStartedNow, String &timeModeSettings, bool &heaterOnOff, short relayPin) {
    String nextTimestamp = heaterOnOffTime.substring(timestampId, timestampId + 4);  // записываем следующее время включения/выключения обогревателя
    if (formattedHours + formattedMinutes == nextTimestamp) {                        // если текущее время равно времени запуска/остановки режима по температуре
      heaterOnOff = !heaterOnOff;
      if (heaterOnOff) {
        isTemperatureModeStartedInTimeMode = true;
        isTemperatureModeStartedNow = true;
        timeModeSettings = "T1";
        Serial.println("Режим по температуре запущен по времени");
      } else {
        isTemperatureModeStartedInTimeMode = false;
        timeModeSettings = "T0";
        digitalWrite(relayPin, LOW);
        Serial.println("Режим по температуре остановлен по времени");
      }
      saveToSPIFFS("/TimeMode.txt", timeModeSettings);
      if (timestampId + 4 < heaterOnOffTime.length()) {
        timestampId = timestampId + 4;
      } else {
        timestampId = 0;
      }
    }
  }

  // метод для управления обогревателем по температуре
  void controlHeaterInTemperatureMode(byte temperature, byte minTemperature, byte maxTemperature, String temperatureMode, bool &temperatureModePhase,
                                      bool &isTemperatureModeStartedNow, short relayPin, FirebaseData &firebaseData, String userUID, bool &temperatureRangeNotChanged) {
    // если режим по температуре в 1 фазе и текущая температура меньше минимальной температуры, или если режим по температуре только запустился и текущая температура меньше максимальной
    if ((!temperatureModePhase && temperature < minTemperature) || (isTemperatureModeStartedNow && temperature < maxTemperature)) {
      temperatureModePhase = true;  // переходим в 2 фазу
      digitalWrite(relayPin, HIGH);
      temperatureRangeNotChanged = true;
      Serial.println("Обогреватель запущен по температуре");
      if (!Firebase.RTDB.setString(&firebaseData, (userUID + "/Heater/temperatureMode").c_str(), temperatureMode.substring(0, temperatureMode.length() - 1) + "1"))
        Serial.println("Не удалось записать в Firebase то, что обогреватель запущен :( Причина: " + String(firebaseData.errorReason().c_str()));        
      sendMessage("1", userUID, firebaseData); 
      // если режим по температуре в 2 фазе и текущая температура выше максимальной
    } else if (temperatureModePhase && temperature >= maxTemperature) {
      temperatureModePhase = false;  // переходим в 1 фазу
      digitalWrite(relayPin, LOW);
      temperatureRangeNotChanged = true;
      Serial.println("Обогреватель остановлен по температуре");
      if (!Firebase.RTDB.setString(&firebaseData, (userUID + "/Heater/temperatureMode").c_str(), temperatureMode.substring(0, temperatureMode.length() - 1) + "0"))
        Serial.println("Не удалось записать в Firebase то, что обогреватель остановлен :( Причина: " + String(firebaseData.errorReason().c_str()));
      sendMessage("0", userUID, firebaseData);      
    }
    isTemperatureModeStartedNow = false;
  }

  // метод для отправки сообщения через Firebase Messaging
  void sendMessage(String heaterStarted, String userUID, FirebaseData &firebaseData) {
    if (notificationsEnabled) {
      FCM_Legacy_HTTP_Message message;

      message.targets.to = "/topics/" + userUID;

      FirebaseJson payload;
      payload.add("heaterStarted", heaterStarted);  // добавляем к данным сообщения то, запущен ли обогреватель
      message.payloads.data = payload.raw();

      // отправляем сообщение через Firebase Messaging
      if (Firebase.FCM.send(&firebaseData, &message))
        Serial.printf("Сообщение отправлено, %s\n", Firebase.FCM.payload(&firebaseData).c_str());
      else
        Serial.println("Не удалось отправить сообщение, ошибка: " + String(firebaseData.errorReason()));
    }
  }

  // метод для получения температуры
  short getTemperatureC(DallasTemperature &ds18b20, byte maxGetTemperatureAttempts) {
    ds18b20.requestTemperatures();
    short temperature = round(ds18b20.getTempCByIndex(0));
    short getTemperatureAttempts = 0;
    Serial.println("Температура: " + String(temperature) + "°C");
    if (temperature < -10) {
      while (temperature < -10 && getTemperatureAttempts < maxGetTemperatureAttempts - 1) {
        getTemperatureAttempts++;
        delay(750);
        ds18b20.requestTemperatures();
        temperature = round(ds18b20.getTempCByIndex(0));
        Serial.println("Температура: " + String(temperature) + "°C");
      }
    }
    return temperature;
  }

private:
  String ssidName;
  String ssidPass;
  String userEmail;
  String userPass;
  short timezone = 0;
  bool notificationsEnabled; // отправлять ли сообщение при включении/выключении обогревателя в режиме по температуре

  bool settingsModeButtonFlag;
  unsigned long settingsModeButtonMillis = 0;

  String formattedHours;
  String formattedMinutes;
};
