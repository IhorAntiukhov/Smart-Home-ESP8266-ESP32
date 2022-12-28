#include <ESP8266WiFi.h>
#include <ESP8266WebServer.h>
#include <Firebase_ESP_Client.h>

#include <addons/TokenHelper.h>
#include <addons/RTDBHelper.h>

#include <FS.h>
#include <LittleFS.h>

#include <OneWire.h>
#include <DallasTemperature.h>

#include "Helper.h"

#define DS18B20_PIN 4                   // пин датчика температуры DS18B20
#define SETTINGS_MODE_BUTTON_PIN 5      // пин кнопки для перехода в режим настройки
#define MAX_GET_TEMPERATURE_ATTEMPTS 3  // максимальное количество попыток получить температуру

Helper helper;  // объект "помощник" с дополнительными методами

ESP8266WebServer server(80);

OneWire oneWire(DS18B20_PIN);
DallasTemperature ds18b20(&oneWire);

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
      short sensorInterval = 0;

      helper.getSettings(sensorInterval);  // получаем настройки из SPIFFS

      ds18b20.begin();
      String userUID;
      helper.connectToWiFi(userUID, firebaseData, auth, config);

      ds18b20.requestTemperatures();
      short temperature = round(ds18b20.getTempCByIndex(0));
      short getTemperatureAttempts = 0;
      Serial.println("Температура: " + String(temperature) + "°C");
      if (temperature < -10) {
        while (temperature < -10 && getTemperatureAttempts < MAX_GET_TEMPERATURE_ATTEMPTS - 1) {
          getTemperatureAttempts++;
          delay(750);
          ds18b20.requestTemperatures();
          temperature = round(ds18b20.getTempCByIndex(0));
          Serial.println("Температура: " + String(temperature) + "°C");
        }
      }

      if (temperature >= -10) {
        if (!Firebase.RTDB.setInt(&firebaseData, (userUID + "/HeatingAndBoiler/temperature").c_str(), temperature))
          Serial.println("Не удалось записать температуру в Firebase :( Причина: " + String(firebaseData.errorReason().c_str()));
      }

      ESP.deepSleep(sensorInterval * 60 * 1000 * 1000);
    }
  } else {
    WiFi.mode(WIFI_AP);
    WiFi.softAP("Термометр для котла", "", 1, false, 1);  // запускаем WiFi сеть для получения настроек (название, пароль, канал, скрытая сеть, максимальное количество подключений)

    server.on("/", handleRoot);
    server.begin();

    Serial.println("Перешли в режим настройки");
  }
}

void loop() {
  server.handleClient();
}