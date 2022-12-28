class Helper {
public:
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

    ssidName = settings.substring(0, settings.indexOf("#"));
    ssidPass = settings.substring(settings.indexOf("#") + 1, secondHashIndex);
    userEmail = settings.substring(secondHashIndex + 1, thirdHashIndex);
    userPass = settings.substring(thirdHashIndex + 1, fourthHashIndex);
    timezone = (settings.substring(fourthHashIndex + 1, settings.length())).toInt();

    Serial.println("Название WiFi сети: " + String(ssidName));
    Serial.println("Пароль WiFi сети: " + String(ssidPass));
    Serial.println("Почта пользователя: " + String(userEmail));
    Serial.println("Пароль пользователя: " + String(userPass));
    Serial.println("Часовой пояс: " + String(timezone));
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
      // запускаем WiFi сеть для получения настроек (название, пароль, канал, скрытая сеть, максимальное количество подключений)
      WiFi.softAP("Умный ИК Пульт", "", 1, false, 1);

      Serial.println("Перешли в режим настройки");
      return true;
    }
    return false;
  }

  // метод для получения ключей кнопок пульта освещения из SPIFFS
  void getLightRemoteKeys(String *lightRemoteKeys) {
    File lightRemoteFile = LittleFS.open("/LightRemote.txt", "r");
    if (!lightRemoteFile) {
      Serial.println("\nНе удалось открыть файл с ключами пульта освещения :(");
      ESP.restart();
    }

    String lightRemote;
    while (lightRemoteFile.available()) lightRemote = lightRemoteFile.readString();
    lightRemoteFile.close();

    int lastHashIndex = -1;
    for (int i = 0; i <= 9; i++) {
      lightRemoteKeys[i] = lightRemote.substring(lastHashIndex + 1, lightRemote.indexOf("#", lastHashIndex + 1));
      lastHashIndex = lightRemote.indexOf("#", lastHashIndex + 1);
    }
    Serial.println("Пульт освещения прочитан");
  }

  // метод для получения ключей кнопок пульта телевизора из SPIFFS
  void getTvRemoteKeys(uint16_t tvRemoteKeys[13][256], uint16_t &tvRemoteKeyLength) {
    for (int i = 0; i < 13; i++) {
      File tvRemoteFile = LittleFS.open("/TvRemote" + String(i) + ".txt", "r");
      if (!tvRemoteFile) {
        Serial.println("\nНе удалось открыть файл с ключём пульта телевизора :(");
        ESP.restart();
      }

      String tvRemoteKey;
      while (tvRemoteFile.available()) tvRemoteKey = tvRemoteFile.readString();
      tvRemoteFile.close();

      // преобразовываем ключ кнопки пульта из JSON в массив uint16_t
      DynamicJsonDocument doc(1536);
      deserializeJson(doc, tvRemoteKey);
      tvRemoteKeyLength = copyArray(doc, tvRemoteKeys[i]);
    }
    Serial.println("Пульт телевизора прочитан");
  }

  // метод для получения протокола пульта кондиционера из SPIFFS
  void getACRemoteProtocol(IRac &ac) {
    File acRemoteFile = LittleFS.open("/ACRemote.txt", "r");
    if (!acRemoteFile) {
      Serial.println("\nНе удалось открыть файл с протоколом пульта кондиционера :(");
      ESP.restart();
    }

    String acRemoteProtocol;
    while (acRemoteFile.available()) acRemoteProtocol = acRemoteFile.readString();
    acRemoteFile.close();

    Serial.println("Протокол пульта кондиционера: " + String(acRemoteProtocol));

    ac.next.protocol = strToDecodeType(acRemoteProtocol.c_str());
    ac.next.model = 1;
    ac.next.celsius = true;
    ac.next.swingv = stdAc::swingv_t::kOff;
    ac.next.swingh = stdAc::swingh_t::kOff;
    ac.next.beep = false;
    ac.next.econo = false;
    ac.next.filter = false;
    ac.next.turbo = false;
    ac.next.quiet = false;
    ac.next.sleep = -1;
    ac.next.clean = false;
    ac.next.clock = -1;
  }

  // метод для получения параметров кондиционера из SPIFFS
  void getACRemoteSettings(IRac &ac) {
    File acRemoteFile = LittleFS.open("/ACSettings.txt", "r");
    if (!acRemoteFile) {
      Serial.println("\nНе удалось открыть файл с протоколом пульта кондиционера :(");
      ESP.restart();
    }

    String acSettings;
    while (acRemoteFile.available()) acSettings = acRemoteFile.readString();
    acRemoteFile.close();

    Serial.println("Настройки кондиционера: " + String(acSettings));

    ac.next.degrees = acSettings.substring(0, acSettings.indexOf(" ")).toInt();
    Serial.println("Температура: " + String(acSettings.substring(0, acSettings.indexOf(" "))));
    setACMode(acSettings, acSettings.indexOf(" ") + 1, acSettings.indexOf(" ") + 2, ac);
    setACFanSpeed(acSettings, acSettings.indexOf(" ") + 2, acSettings.indexOf(" ") + 3, ac);
    if (acSettings.substring(acSettings.indexOf(" ") + 3, acSettings.indexOf(" ") + 4) == "1") {
      ac.next.turbo = true;
    } else {
      ac.next.turbo = false;
    }
    Serial.println("Турбо режим: " + String(acSettings.substring(acSettings.indexOf(" ") + 3, acSettings.indexOf(" ") + 4)));
    if (acSettings.substring(acSettings.indexOf(" ") + 4, acSettings.indexOf(" ") + 5) == "1") {
      ac.next.light = true;
    } else {
      ac.next.light = false;
    }
    Serial.println("Подсветка: " + String(acSettings.substring(acSettings.indexOf(" ") + 4, acSettings.indexOf(" ") + 5)));
    if (acSettings.substring(acSettings.indexOf(" ") + 5, acSettings.indexOf(" ") + 6) == "1") {
      ac.next.power = true;
      Serial.println("Кондиционер запущен");
    } else {
      ac.next.power = false;
      Serial.println("Кондиционер остановлен");
    }  
  }

  // метод для установки режима кондиционера
  void setACMode(String acRemote, short startIndex, short endIndex, IRac &ac) {
    if (acRemote.substring(startIndex, endIndex) == "0") {
      ac.next.mode = stdAc::opmode_t::kCool;
    } else if (acRemote.substring(startIndex, endIndex) == "1") {
      ac.next.mode = stdAc::opmode_t::kDry;
    } else if (acRemote.substring(startIndex, endIndex) == "2") {
      ac.next.mode = stdAc::opmode_t::kFan;
    } else if (acRemote.substring(startIndex, endIndex) == "3") {
      ac.next.mode = stdAc::opmode_t::kHeat;
    } else if (acRemote.substring(startIndex, endIndex) == "4") {
      ac.next.mode = stdAc::opmode_t::kAuto;
    }
    Serial.println("Режим: " + String(acRemote.substring(startIndex, endIndex)));
  }

  // метод для установки мощности вентилятора
  void setACFanSpeed(String acRemote, short startIndex, short endIndex, IRac &ac) {
    if (acRemote.substring(startIndex, endIndex) == "0") {
      ac.next.fanspeed = stdAc::fanspeed_t::kMin;
    } else if (acRemote.substring(startIndex, endIndex) == "1") {
      ac.next.fanspeed = stdAc::fanspeed_t::kMedium;
    } else if (acRemote.substring(startIndex, endIndex) == "2") {
      ac.next.fanspeed = stdAc::fanspeed_t::kMax;
    } else if (acRemote.substring(startIndex, endIndex) == "3") {
      ac.next.fanspeed = stdAc::fanspeed_t::kAuto;
    }
    Serial.println("Скорость вентилятора: " + String(acRemote.substring(startIndex, endIndex)));
  }

  // метод для подключения к WiFi сети и к Firebase
  void connectToWiFi(FirebaseData &firebaseData, FirebaseData &stream, FirebaseAuth &auth, FirebaseConfig &config,
                     FirebaseData::MultiPathStreamEventCallback streamCallback, FirebaseData::StreamTimeoutCallback streamTimeoutCallback,
                     String &userUID, NTPClient &timeClient, unsigned long &getTimeMillis, byte &hours, byte &minutes, byte maxGetTimeAttempts,
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

      userUID = (auth.token.uid).c_str();
      if (Firebase.RTDB.beginMultiPathStream(&stream, ("/" + userUID + "/SmartRemotes").c_str())) {
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

  // метод для получения ИК сигналов для настройки кнопки пульта освещения
  void receiveLightRemoteKeys(IRrecv &irReceiver, byte lightRemoteSettingsButton, String *lightRemoteKeys, byte *lightRemoteConfiguredButtons) {
    if (irReceiver.decode(&results)) {  // если получен ИК сигнал
      String keyValue = String(results.value, HEX);
      if (results.decode_type == 3 && keyValue != "ffffffffffffffff") {   // если получен ИК сигнал с протоколом NEC и если этот сигнал не является специальным ключём протокола NEC
        lightRemoteConfiguredButtons[lightRemoteSettingsButton - 1] = 1;  // записываем то, что выбранная кнопка настроена
        lightRemoteKeys[lightRemoteSettingsButton - 1] = keyValue;
        Serial.println("Ключ кнопки пульта освещения: " + String(keyValue));
      }
      irReceiver.resume();  // ждём получения следующих ИК сигналов
    }
  }

  // метод для получения ИК сигналов для настройки кнопки пульта телевизора
  void receiveTvRemoteKeys(IRrecv &irReceiver, byte tvRemoteSettingsButton, byte *tvRemoteConfiguredButtons, uint16_t &tvRemoteKeyLength) {
    if (irReceiver.decode(&results)) {                                                            // если получен ИК сигнал
      if (results.decode_type != -1 && results.decode_type != 47 && results.decode_type != 82) {  // фильтруем ложные ИК сигналы
        tvRemoteConfiguredButtons[tvRemoteSettingsButton - 1] = 1;                                // записываем то, что выбранная кнопка настроена
        tvRemoteSettingsKeys[tvRemoteSettingsButton - 1] = resultToRawArray(&results);
        tvRemoteKeyLength = getCorrectedRawLength(&results);
        Serial.print("Ключ кнопки телевизора: ");
        serialPrintUint64(results.value, HEX);
        Serial.print("\n");
      }
      irReceiver.resume();  // ждём получения следующих ИК сигналов
    }
  }

  // метод для получения ИК сигналов для настройки пульта кондиционера
  void receiveACRemoteProtocol(IRrecv &irReceiver, byte &isAcRemoteConfigured) {
    if (irReceiver.decode(&results)) {                                                            // если получен ИК сигнал
      if (results.decode_type != -1 && results.decode_type != 47 && results.decode_type != 82) {  // фильтруем ложные ИК сигналы
        if (hasACState(results.decode_type)) {                                                    // если протокол полученного ИК сигнала поддерживается
          isAcRemoteConfigured = 1;
          String protocol = typeToString(results.decode_type, false);
          Serial.println("Получена команда кондиционера. Протокол: " + String(protocol));
          saveToSPIFFS("/ACRemote.txt", protocol);
        } else {
          isAcRemoteConfigured = 2;
        }
      }
      irReceiver.resume();  // ждём получения следующих ИК сигналов
    }
  }

  // метод для сохранения пульта телевизора в SPIFFS
  void saveTvRemote(byte *tvRemoteConfiguredButtons, uint16_t &tvRemoteKeyLength) {
    // сохраняем ключ каждой кнопки пульта в отдельный файл
    for (int i = 0; i < 13; i++) {
      if (tvRemoteConfiguredButtons[i] == 1) {
        // преобразовываем ключ кнопки пульта из массива uint16_t в JSON
        DynamicJsonDocument doc(1536);
        copyArray(tvRemoteSettingsKeys[i], tvRemoteKeyLength, doc);
        String rawArrayBuffer;
        serializeJson(doc, rawArrayBuffer);
        saveToSPIFFS("/TvRemote" + String(i) + ".txt", rawArrayBuffer);
      } else {
        saveToSPIFFS("/TvRemote" + String(i) + ".txt", " ");
      }
    }
    Serial.println("Пульт телевизора сохранён");
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

  // метод для управления кондиционером по времени
  void controlACInTimeMode(String acOnOffTime, bool &acOnOff, short &timestampId, IRac ac) {
    String nextTimestamp = acOnOffTime.substring(timestampId, timestampId + 4);  // записываем следующее время включения/выключения кондиционера
    if (formattedHours + formattedMinutes == nextTimestamp) {                    // если текущее время равно времени включения/выключения кондиционера
      acOnOff = !acOnOff;
      ac.next.power = acOnOff;
      ac.sendAc();
      if (acOnOff == true) {
        Serial.println("Кондиционер запущен по времени");
      } else {
        Serial.println("Кондиционер остановлен по времени");
      }
      if (timestampId + 4 < acOnOffTime.length()) {
        timestampId = timestampId + 4;
      } else {
        timestampId = 0;
      }
    }
  }

private:
  String ssidName;
  String ssidPass;
  String userEmail;
  String userPass;
  short timezone = 0;

  bool settingsModeButtonFlag;
  unsigned long settingsModeButtonMillis = 0;

  String formattedHours;
  String formattedMinutes;

  uint16_t *tvRemoteSettingsKeys[13];
  decode_results results;  // данные, полученные от ИК приёмника
};
