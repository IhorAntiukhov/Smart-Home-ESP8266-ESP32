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
      // запускаем WiFi сеть для получения настроек (название, пароль, канал, скрыть сеть, максимальное количество подключений)
      WiFi.softAP("WiFi Термометр", "", 1, false, 1);

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

    ssidName = settings.substring(0, settings.indexOf("#"));
    ssidPass = settings.substring(settings.indexOf("#") + 1, secondHashIndex);
    userEmail = settings.substring(secondHashIndex + 1, thirdHashIndex);
    userPass = settings.substring(thirdHashIndex + 1, fourthHashIndex);
    sensorInterval = (settings.substring(fourthHashIndex + 1, settings.length())).toInt();

    Serial.println("Название WiFi сети: " + String(ssidName));
    Serial.println("Пароль WiFi сети: " + String(ssidPass));
    Serial.println("Почта пользователя: " + String(userEmail));
    Serial.println("Пароль пользователя: " + String(userPass));
    Serial.println("Интервал датчика: " + String(sensorInterval));
  }

  // метод для подключения к WiFi сети и к Firebase
  void connectToWiFi(FirebaseData &firebaseData, FirebaseAuth &auth, FirebaseConfig &config, short connectToWiFiTimeout, short sensorInterval) {
    WiFi.begin(ssidName, ssidPass);
    Serial.print("Подключаемся к WiFi сети ");
    short connectToWiFiTime = 0;
    while (WiFi.status() != WL_CONNECTED) {
      delay(250);
      connectToWiFiTime++;
      if (connectToWiFiTime >= connectToWiFiTimeout * 4) {
        Serial.println("Не удалось подключиться к WiFi сети :( Переходим в сон ...");
        ESP.deepSleep(sensorInterval * 60 * 1000 * 1000);
      }
      Serial.print(".");
    }
    Serial.print("\nПодключились к WiFi сети! Локальный IP адрес: ");
    Serial.println(WiFi.localIP());

    config.api_key = "AIzaSyADkVab9iTJ4F6GDDCwdEeC7i4N6dqeOy8";

    auth.user.email = userEmail;
    auth.user.password = userPass;

    config.database_url = "https://smarthouseesp-default-rtdb.europe-west1.firebasedatabase.app/";
    config.token_status_callback = tokenStatusCallback;

    Firebase.begin(&config, &auth);
    Firebase.reconnectWiFi(true);
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

  // метод для преобразования значения переменной из одного диапазона в другой
  float mapfloat(float x, float inMin, float inMax, float outMin, float outMax) {
    return (x - inMin) * (outMax - outMin) / (inMax - inMin) + outMin;
  }

private:
  String ssidName;
  String ssidPass;
  String userEmail;
  String userPass;

  bool settingsModeButtonFlag;
  unsigned long settingsModeButtonMillis = 0;
};