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
      WiFi.softAP("Котёл и Бойлер", "", 1, false, 1);

      Serial.println("Перешли в режим настройки");
      return true;
    }
    return false;
  }

  // метод для получения настроек из SPIFFS
  void getSettings(String &settings) {
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
    maxHeatingElements = (settings.substring(fifthHashIndex + 1, settings.length())).toInt();

    Serial.println("Название WiFi сети: " + String(ssidName));
    Serial.println("Пароль WiFi сети: " + String(ssidPass));
    Serial.println("Почта пользователя: " + String(userEmail));
    Serial.println("Пароль пользователя: " + String(userPass));
    Serial.println("Часовой пояс: " + String(timezone));
    Serial.println("Количество тэнов: " + String(maxHeatingElements));
  }

  // метод для получения настроек режима по времени
  void getTimeModeSettings(String &timeModeSettings, bool &isTemperatureModeStartedInTimeMode, bool &isTemperatureModeStartedNow,
                           byte &heatingElementsInTemperatureMode, String &heatingOnOffTime, String &boilerOnOffTime, bool &isHeatingStartedFromSPIFFS,
                           short heatingRelayPin, short heatingElementsRelayPin, short boilerRelayPin, bool &boilerOnOff) {
    if (LittleFS.exists("/TimeMode.txt")) {
      File timeModeSettingsFile = LittleFS.open("/TimeMode.txt", "r");
      if (!timeModeSettingsFile) {
        Serial.println("\nНе удалось открыть файл с настройками режима по времени :(");
        ESP.restart();
      }

      heatingOnOffTime = "";
      boilerOnOffTime = "";

      while (timeModeSettingsFile.available()) timeModeSettings = timeModeSettingsFile.readString();
      timeModeSettingsFile.close();

      if (timeModeSettings.substring(2, 3) == "1") {
        boilerOnOff = true;
        digitalWrite(boilerRelayPin, true);
        Serial.println("Бойлер запущен по времени");
      }

      if (timeModeSettings.indexOf("H") != -1) {
        if (timeModeSettings.substring(1, 2) == "1") {
          digitalWrite(heatingElementsRelayPin, LOW);
          digitalWrite(heatingRelayPin, HIGH);
          timeModeSettings = "H1";
          isHeatingStartedFromSPIFFS = true;
          Serial.println("Запущен 1 тэн котла по времени");
        } else if (timeModeSettings.substring(1, 2) == "2") {
          digitalWrite(heatingElementsRelayPin, HIGH);
          digitalWrite(heatingRelayPin, HIGH);
          timeModeSettings = "H2";
          isHeatingStartedFromSPIFFS = true;
          Serial.println("Запущено 2 тэна котла по времени");
        } else if (timeModeSettings.substring(1, 2) == "0") {
          timeModeSettings = "H0";
        }
      } else if (timeModeSettings.indexOf("T") != -1) {
        if (timeModeSettings.substring(1, 2) == "1") {
          isTemperatureModeStartedInTimeMode = true;
          isTemperatureModeStartedNow = true;
          heatingElementsInTemperatureMode = 1;
          timeModeSettings = "Т1";
          isHeatingStartedFromSPIFFS = true;
          Serial.println("Запущен режим по температуре на мощности 1 тэн по времени");
        } else if (timeModeSettings.substring(1, 2) == "2") {
          isTemperatureModeStartedInTimeMode = true;
          isTemperatureModeStartedNow = true;
          heatingElementsInTemperatureMode = 2;
          timeModeSettings = "Т2";
          isHeatingStartedFromSPIFFS = true;
          Serial.println("Запущен режим по температуре на мощности 2 тэна по времени");
        } else if (timeModeSettings.substring(1, 2) == "0") {
          timeModeSettings = "Т0";
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

#if defined(ESP8266)
      stream.setBSSLBufferSize(2048, 512);
#endif

      userUID = String(auth.token.uid.c_str());
      if (Firebase.RTDB.beginMultiPathStream(&stream, (userUID + "/HeatingAndBoiler").c_str())) {
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

  // метод для запуска таймера котла
  void startHeatingTimer(unsigned long &heatingTimerMillis, short &heatingTimerTime, short heatingRelayPin, FirebaseData &firebaseData, String userUID) {
    if (millis() - heatingTimerMillis >= heatingTimerTime * 60 * 1000) {
      heatingTimerTime = 0;
      heatingTimerMillis = 0;
      digitalWrite(heatingRelayPin, LOW);
      Serial.println("Время таймера котла истекло");
      if (!Firebase.RTDB.setInt(&firebaseData, (userUID + "/HeatingAndBoiler/heatingTimerTime").c_str(), 0))
        Serial.println("Не удалось записать в Firebase то, что время таймера котла истекло :( Причина: " + String(firebaseData.errorReason().c_str()));
    }
  }

  // метод для запуска таймера бойлера
  void startBoilerTimer(unsigned long &boilerTimerMillis, short &boilerTimerTime, short boilerRelayPin, FirebaseData &firebaseData, String userUID) {
    if (millis() - boilerTimerMillis >= boilerTimerTime * 60 * 1000) {
      boilerTimerTime = 0;
      boilerTimerMillis = 0;
      digitalWrite(boilerRelayPin, LOW);
      Serial.println("Время таймера бойлера истекло");
      if (!Firebase.RTDB.setInt(&firebaseData, (userUID + "/HeatingAndBoiler/boilerTimerTime").c_str(), 0))
        Serial.println("Не удалось записать в Firebase то, что время таймера бойлера истекло :( Причина: " + String(firebaseData.errorReason().c_str()));
    }
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

  // метод для управления котлом в режиме по времени
  void controlHeatingInTimeMode(String heatingOnOffTime, String timeHeatingElements, short &heatingTimestampId,
                                String &timeModeSettings, short heatingRelayPin, short heatingElementsRelayPin, bool boilerOnOff) {
    String nextHeatingTimestamp = heatingOnOffTime.substring(heatingTimestampId, heatingTimestampId + 4);  // записываем следующее время включения/выключения котла
    if (formattedHours + formattedMinutes == nextHeatingTimestamp) {                                       // если текущее время равно времени включения/выключения котла
      controlHeating(heatingOnOffTime, timeHeatingElements, heatingTimestampId, timeModeSettings, heatingRelayPin, heatingElementsRelayPin, boilerOnOff, false);
      if (heatingTimestampId + 4 < heatingOnOffTime.length()) {
        heatingTimestampId = heatingTimestampId + 4;
      } else {
        heatingTimestampId = 0;
      }
    }
  }

  // метод для управления котлом когда текущее время равно времени включения или выключения, или включения котла при запуске режима
  void controlHeating(String heatingOnOffTime, String timeHeatingElements, short heatingTimestampId, String &timeModeSettings,
                      short heatingRelayPin, short heatingElementsRelayPin, bool boilerOnOff, bool decreaseTimestampId) {
    if (!(heatingTimestampId == 0 && timeHeatingElements.length() == 1) || !decreaseTimestampId) {
      if (decreaseTimestampId) heatingTimestampId = heatingTimestampId - 4;
      if (timeHeatingElements.substring(heatingTimestampId / 4, heatingTimestampId / 4 + 1) == "1") {
        digitalWrite(heatingElementsRelayPin, LOW);
        digitalWrite(heatingRelayPin, HIGH);
        timeModeSettings = "H1";
        Serial.println("Запущен 1 тэн котла по времени");
      } else if (timeHeatingElements.substring(heatingTimestampId / 4, heatingTimestampId / 4 + 1) == "2") {
        digitalWrite(heatingElementsRelayPin, HIGH);
        digitalWrite(heatingRelayPin, HIGH);
        timeModeSettings = "H2";
        Serial.println("Запущено 2 тэна котла по времени");
      } else if (timeHeatingElements.substring(heatingTimestampId / 4, heatingTimestampId / 4 + 1) == "0") {
        digitalWrite(heatingRelayPin, LOW);
        timeModeSettings = "H0";
        Serial.println("Котёл остановлен по времени");
      }
      saveToSPIFFS("/TimeMode.txt", timeModeSettings + String(boilerOnOff));
    }
  }

  // метод для запуска/остановки режима управления котлом по температуре, в режиме по времени
  void controlTemperatureModeInTimeMode(String heatingOnOffTime, String timeHeatingElements, short &heatingTimestampId, bool &isTemperatureModeStartedInTimeMode,
                                        bool &isTemperatureModeStartedNow, byte &heatingElementsInTemperatureMode, String &timeModeSettings, bool boilerOnOff,
                                        short heatingRelayPin, short heatingElementsRelayPin) {
    String nextHeatingTimestamp = heatingOnOffTime.substring(heatingTimestampId, heatingTimestampId + 4);  // записываем следующее время включения/выключения котла
    if (formattedHours + formattedMinutes == nextHeatingTimestamp) {                                       // если текущее время равно времени запуска/остановки режима по температуре
      controlTemperatureMode(heatingOnOffTime, timeHeatingElements, heatingTimestampId, isTemperatureModeStartedInTimeMode, isTemperatureModeStartedNow,
                             heatingElementsInTemperatureMode, timeModeSettings, boilerOnOff, heatingRelayPin, false);
      if (heatingTimestampId + 4 < heatingOnOffTime.length()) {
        heatingTimestampId = heatingTimestampId + 4;
      } else {
        heatingTimestampId = 0;
      }
    }
  }

  // метод для управления режимом по температуре когда текущее время равно времени запуска или остановки режима, или запуска режима по температуре при запуске режима по времени
  void controlTemperatureMode(String heatingOnOffTime, String timeHeatingElements, short heatingTimestampId, bool &isTemperatureModeStartedInTimeMode,
                              bool &isTemperatureModeStartedNow, byte &heatingElementsInTemperatureMode, String &timeModeSettings, bool boilerOnOff,
                              short heatingRelayPin, bool decreaseTimestampId) {
    if (!(heatingTimestampId == 0 && timeHeatingElements.length() == 1) || !decreaseTimestampId) {
      if (decreaseTimestampId) heatingTimestampId = heatingTimestampId - 4;
      if (timeHeatingElements.substring(heatingTimestampId / 4, heatingTimestampId / 4 + 1) == "1") {
        isTemperatureModeStartedInTimeMode = true;
        isTemperatureModeStartedNow = true;
        heatingElementsInTemperatureMode = 1;
        timeModeSettings = "T1";
        Serial.println("Запущен режим по температуре на мощности 1 тэн по времени");
      } else if (timeHeatingElements.substring(heatingTimestampId / 4, heatingTimestampId / 4 + 1) == "2") {
        isTemperatureModeStartedInTimeMode = true;
        isTemperatureModeStartedNow = true;
        heatingElementsInTemperatureMode = 2;
        timeModeSettings = "T2";
        Serial.println("Запущен режим по температуре на мощности 2 тэна по времени");
      } else if (timeHeatingElements.substring(heatingTimestampId / 4, heatingTimestampId / 4 + 1) == "0") {
        isTemperatureModeStartedInTimeMode = false;
        heatingElementsInTemperatureMode = 0;
        timeModeSettings = "T0";
        digitalWrite(heatingRelayPin, LOW);
        Serial.println("Режим по температуре остановлен по времени");
      }
      saveToSPIFFS("/TimeMode.txt", timeModeSettings + String(boilerOnOff));
    }
  }

  // метод для управления бойлером в режиме по времени
  void controlBoilerInTimeMode(String boilerOnOffTime, bool &boilerOnOff, short &boilerTimestampId, String &timeModeSettings, short boilerRelayPin) {
    String nextBoilerTimestamp = boilerOnOffTime.substring(boilerTimestampId, boilerTimestampId + 4);  // записываем следующее время включения/выключения бойлера
    if (formattedHours + formattedMinutes == nextBoilerTimestamp) {                                    // если текущее время равно времени включения/выключения бойлера
      boilerOnOff = !boilerOnOff;
      if (boilerOnOff) {
        digitalWrite(boilerRelayPin, HIGH);
        Serial.println("Бойлер запущен по времени");
      } else {
        digitalWrite(boilerRelayPin, LOW);
        Serial.println("Бойлер остановлен по времени");
      }
      saveToSPIFFS("/TimeMode.txt", timeModeSettings + String(boilerOnOff));
      if (boilerTimestampId + 4 < boilerOnOffTime.length()) {
        boilerTimestampId = boilerTimestampId + 4;
      } else {
        boilerTimestampId = 0;
      }
    }
  }

  // метод для включения бойлера при запуске режима по времени, если текущее время между предыдущим временем включения и следующим временем выключения бойлера
  void controlBoiler(String boilerOnOffTime, bool &boilerOnOff, short boilerTimestampId, String &timeModeSettings, short boilerRelayPin) {
    String boilerOnOffAlgo;
    for (int i = 0; i < boilerOnOffTime.length() / 4; i++) {
      boilerOnOff = !boilerOnOff;
      if (boilerOnOff) {
        boilerOnOffAlgo += "1";
      } else {
        boilerOnOffAlgo += "0";
      }
    }
    if (boilerTimestampId < boilerOnOffTime.length()) {
      if (!(boilerTimestampId == 0 && boilerOnOffAlgo == "1")) {
        boilerTimestampId = boilerTimestampId - 4;
        if (boilerOnOffAlgo.substring(boilerTimestampId / 4, boilerTimestampId / 4 + 1) == "1") {
          boilerOnOff = true;
          Serial.println("Бойлер запущен по времени");
        } else {
          boilerOnOff = false;
          Serial.println("Бойлер остановлен по времени");
        }
        digitalWrite(boilerRelayPin, boilerOnOff);
        saveToSPIFFS("/TimeMode.txt", timeModeSettings + String(boilerOnOff));
      }
    } else {
      if (boilerOnOffAlgo.endsWith("1")) {
        boilerOnOff = true;
        digitalWrite(boilerRelayPin, HIGH);
        Serial.println("Бойлер запущен по времени");
        saveToSPIFFS("/TimeMode.txt", timeModeSettings + String(boilerOnOff));
      }
    }
  }

  // метод для управления котлом по температуре
  void controlHeatingInTemperatureMode(byte temperature, byte minTemperature, byte maxTemperature, String temperatureMode, bool &temperatureModePhase,
                                       bool &isTemperatureModeStartedNow, byte heatingElements, short heatingRelayPin, short heatingElementsRelayPin,
                                       FirebaseData &firebaseData, String userUID) {
    // если режим по температуре в 1 фазе и текущая температура меньше минимальной температуры, или если режим по температуре только запустился и текущая температура меньше максимальной
    if ((!temperatureModePhase && temperature < minTemperature) || (isTemperatureModeStartedNow && temperature < maxTemperature)) {
      temperatureModePhase = true;  // переходим в 2 фазу
      if (heatingElements == 1) {
        digitalWrite(heatingElementsRelayPin, LOW);
      } else if (heatingElements == 2) {
        digitalWrite(heatingElementsRelayPin, HIGH);
      }
      digitalWrite(heatingRelayPin, HIGH);
      Serial.println("Котёл запущен по температуре");

      if (!Firebase.RTDB.setString(&firebaseData, (userUID + "/HeatingAndBoiler/temperatureMode").c_str(), temperatureMode + "11"))
        Serial.println("Не удалось записать в Firebase то, что котёл запущен :( Причина: " + String(firebaseData.errorReason().c_str()));
      // если режим по температуре в 2 фазе и текущая температура выше максимальной
    } else if (temperatureModePhase && temperature >= maxTemperature) {
      temperatureModePhase = false;  // переходим в 1 фазу
      digitalWrite(heatingRelayPin, LOW);
      Serial.println("Котёл остановлен по температуре");

      if (!Firebase.RTDB.setString(&firebaseData, (userUID + "/HeatingAndBoiler/temperatureMode").c_str(), temperatureMode + "01"))
        Serial.println("Не удалось записать в Firebase то, что котёл остановлен :( Причина: " + String(firebaseData.errorReason().c_str()));
    }
    isTemperatureModeStartedNow = false;
  }

private:
  String ssidName;
  String ssidPass;
  String userEmail;
  String userPass;
  short timezone = 0;
  byte maxHeatingElements = 0;  // максимальное количество тэнов котла (если у вас 3 и больше тэнов, вам нужно самим прописать логику управления тэнами котла)

  bool settingsModeButtonFlag;
  unsigned long settingsModeButtonMillis = 0;

  String formattedHours;
  String formattedMinutes;
};
