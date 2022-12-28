#include <ESP8266WiFi.h>
#include <ESP8266WebServer.h>
#include <Firebase_ESP_Client.h>

#include <addons/TokenHelper.h>
#include <addons/RTDBHelper.h>

#include <FS.h>
#include <LittleFS.h>

#include <DHT.h>

#include "Helper.h"

#define DHT_SENSOR_PIN 14                 // пин датчика температуры и влажности
#define SETTINGS_MODE_BUTTON_PIN 12       // пин кнопки для перехода в режим настройки
#define BATTERY_VOLTAGE_CALIBRATION 0.36  // калиброка рассчитываемого напряжения на аккумуляторе
#define MAX_GET_DHT_READINGS_ATTEMPTS 5   // максимальное количество попыток получить температуру и влажность
#define CONNECT_TO_WIFI_TIMEOUT 30        // таймаут подключения к WiFi сети в секундах

DHT dht(DHT_SENSOR_PIN, DHT21);

Helper helper;  // объект "помощник" с дополнительными методами

ESP8266WebServer server(80);

FirebaseData firebaseData;
FirebaseAuth auth;
FirebaseConfig config;

// слушатель получения настроек через WiFi
void handleRoot() {
  server.send(200, "text/plain", "Ok\r\n");
  delay(500);
  if (server.hasArg("ssid") && server.hasArg("pass") && server.hasArg("email")
      && server.hasArg("user_pass") && server.hasArg("interval")) {
    String settings = server.arg("ssid") + "#" + server.arg("pass") + "#" + server.arg("email")
                      + "#" + server.arg("user_pass") + "#" + server.arg("interval");
    Serial.println("Настройки: " + String(settings));

    if (helper.saveToSPIFFS("/Settings.txt", settings)) ESP.restart();
  }
}

void setup() {
  Serial.begin(115200);

  if (!LittleFS.begin()) {
    Serial.println("\nНе удалось монтировать SPIFFS :(");
    ESP.restart();
  }

  if (LittleFS.exists("/Settings.txt")) {
    bool settingsModeStarted = false;
    pinMode(SETTINGS_MODE_BUTTON_PIN, INPUT);
    if (!digitalRead(SETTINGS_MODE_BUTTON_PIN)) {
      for (int i = 0; i <= 3010; i++) {
        if (helper.checkSettingsModeButton(SETTINGS_MODE_BUTTON_PIN)) {  // если кнопка перехода в режим настройки удерживалась 3 секунды
          settingsModeStarted = true;
          server.on("/", handleRoot);
          server.begin();
          break;
        }
        delay(1);
      }
    }

    if (!settingsModeStarted) {
      dht.begin();

      String settings;
      short sensorInterval = 0;
      helper.getSettings(settings, sensorInterval);  // получаем настройки из SPIFFS
      helper.connectToWiFi(firebaseData, auth, config, CONNECT_TO_WIFI_TIMEOUT, sensorInterval);

      short temperature = 0;
      short humidity = 0;
      byte getDHTReadingsAttempts = 0;
      // получаем температуру и влажность
      do {
        temperature = dht.readTemperature();
        humidity = dht.readHumidity();

        Serial.println("Температура: " + String(temperature) + "°C");
        Serial.println("Влажность: " + String(humidity) + "%");

        getDHTReadingsAttempts++;
      } while ((humidity < 10 || humidity > 100) && getDHTReadingsAttempts < MAX_GET_DHT_READINGS_ATTEMPTS);

      if (!(humidity < 10 || humidity > 100)) {
        if (!Firebase.RTDB.setString(&firebaseData, (String(auth.token.uid.c_str()) + "/WiFiThermometer/temperatureHumidity").c_str(), String(temperature) + " " + String(humidity)))
          Serial.println("Не удалось записать температуру и влажность в Firebase :( Причина: " + String(firebaseData.errorReason().c_str()));
      }

      float batteryVoltage = helper.mapfloat((float)analogRead(A0), 0.0, 1023.0, 0, 4.16) + BATTERY_VOLTAGE_CALIBRATION;  // получаем напряжение на аккумуляторе
      /* высчитываем примерный уровень заряда аккумулятора (дело в том, что при разряде, напряжение 
        изменяется не линейно. Поэтому вычисленный уровень заряда может не совпадать с реальным) */
      short batteryLevel = round(helper.mapfloat(batteryVoltage, 2.82, 4.16, 0.0, 100.0));

      if (batteryLevel > 100) {
        batteryLevel = 100;
      } else if (batteryLevel < 0) {
        batteryLevel = 0;
      }

      Serial.println("Напряжение: " + String(batteryVoltage) + "V Уровень заряда: " + String(batteryLevel) + "%");

      if (!Firebase.RTDB.setInt(&firebaseData, (String(auth.token.uid.c_str()) + "/WiFiThermometer/batteryLevel").c_str(), batteryLevel))
        Serial.println("Не удалось записать уровень заряда в Firebase :( Причина: " + String(firebaseData.errorReason().c_str()));

      ESP.deepSleep(sensorInterval * 60 * 1000 * 1000);
    }
  } else {
    WiFi.mode(WIFI_AP);
    // запускаем WiFi сеть для получения настроек (название, пароль, канал, скрыть сеть, максимальное количество подключений)
    WiFi.softAP("WiFi Термометр", "", 1, false, 1);

    server.on("/", handleRoot);
    server.begin();

    Serial.println("Перешли в режим настройки");
  }
}

void loop() {
  server.handleClient();
}
