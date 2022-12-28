#include <ESP8266WiFi.h>
#include <ESP8266WebServer.h>
#include <Firebase_ESP_Client.h>

#include <addons/TokenHelper.h>
#include <addons/RTDBHelper.h>

#include <FS.h>
#include <LittleFS.h>

#include <WiFiUdp.h>
#include <NTPClient.h>

#include <OneWire.h>
#include <DallasTemperature.h>

#include "Helper.h"

#define RELAY_PIN D6                    // пин реле для управления обогревателем
#define DS18B20_PIN D2                  // пин датчика температуры
#define SETTINGS_MODE_BUTTON_PIN D1     // пин кнопки для перехода в режим настройки
#define MAX_GET_TEMPERATURE_ATTEMPTS 5  // максимальное количество попыток получить температуру
#define MAX_GET_TIME_ATTEMPTS 3         // максимальное количество попыток получить правильное время от NTP сервера
#define RESTART_INTERVAL 1              // интервал перезагрузки платы после неудачного подключения к WiFi сети в минутах

String settings = " ";
short sensorInterval = 0;

bool isHeaterStarted;

String heaterOnOffTime = " ";  // время включения/выключения обогревателя в режиме по времени
short timestampId = 0;         // индекс (в строке со всем временем) следующего времени включения/выключения обогревателя
bool heaterOnOff;

String temperatureMode = " ";  // настройки режима по температуре
byte minTemperature = 0;
byte maxTemperature = 0;
byte temperature = 0;
bool temperatureModePhase = false;  // фаза работы режима по времени (включен ли обогреватель в этом режиме)
bool isTemperatureModeStartedNow;

bool isTemperatureModeStartedInTimeMode;
String timeModeSettings;  // запущен ли режим по температуре, или обогреватель, в режиме по времени

String userUID;
/* переменная-флажок для того, чтобы при записи в Firebase того, что обогреватель 
   запущен/остановлен по температуре, слушатель изменения данных не реагировал */
bool temperatureRangeNotChanged;
/* переменная для того, чтобы при запуске слушателя изменения данных, не выключался 
  обогреватель при запуске режима по времени и не сохранялись настройки в SPIFFS */
bool streamStartedNow = true;

byte hours = 0;
byte minutes = 0;
bool timeNotReceivedCorrectly;
unsigned long restartMillis = 0;  // время с момента неудачной попытки получить время в millis
unsigned long getTimeMillis = 0;  // время последнего высчитывания текущего времени в millis
unsigned long getTemperatureMillis = 0; // время последнего получения температуры в millis

Helper helper;  // объект "помощник" с дополнительными методами

ESP8266WebServer server(80);

OneWire oneWire(DS18B20_PIN);
DallasTemperature ds18b20(&oneWire);

WiFiUDP ntpUDP;
NTPClient timeClient(ntpUDP, "europe.pool.ntp.org", 3600, 1000);

FirebaseData firebaseData;
FirebaseData stream;  // объект слушателя изменения данных в Firebase

FirebaseAuth auth;
FirebaseConfig config;

// функция слушателя изменения данных в Firebase
void streamCallback(MultiPathStream stream) {
  if (stream.get("heaterStarted")) {  // если получена команда на включение/выключения обогревателя
    if (String(stream.value.c_str()) == "true") {
      if (!isHeaterStarted) {
        isHeaterStarted = true;
        digitalWrite(RELAY_PIN, HIGH);
        Serial.println("Обогреватель запущен");
      }
    } else {
      if (isHeaterStarted) {
        isHeaterStarted = false;
        digitalWrite(RELAY_PIN, LOW);
        Serial.println("Обогреватель остановлен");
      }
    }
  }
  if (stream.get("heaterOnOffTime")) {  // если получено время включения/выключения обогревателя в режиме по времени
    if (String(stream.value.c_str()) != heaterOnOffTime) {
      heaterOnOffTime = String(stream.value.c_str());
      if (heaterOnOffTime != " ") {
        if (!streamStartedNow) digitalWrite(RELAY_PIN, LOW);
        if (temperatureMode != " ") getTemperatureMillis = 0;
        short nextTime = 0;
        short previousNextTime = 0;
        // увеличиваем индекс следующего времени включения/выключения обогревателя, пока текущее время меньше времени под этим индексом
        while (hours * 60 + minutes >= heaterOnOffTime.substring(timestampId, timestampId + 2).toInt() * 60
                                         + heaterOnOffTime.substring(timestampId + 2, timestampId + 4).toInt()
               && timestampId < heaterOnOffTime.length() - 1) {
          timestampId = timestampId + 4;
          nextTime = heaterOnOffTime.substring(timestampId, timestampId + 2).toInt() * 60
                     + heaterOnOffTime.substring(timestampId + 2, timestampId + 4).toInt();  // записываем следующее время включения/выключения
          if (nextTime < previousNextTime) {                                                 // если текущее время включения/выключения меньше предыдущего
            break;
          }
          previousNextTime = nextTime;  // записываем следующее время включения/выключения для того, чтобы выйти из цикла если время включения/выключения начнёт уменьшаться
        }
        if (timestampId >= heaterOnOffTime.length() - 1) {
          timestampId = 0;
        }
        Serial.println("Время включения/выключения обогревателя: " + String(heaterOnOffTime));
        Serial.println("Режим по времени запущен");
      } else {
        timestampId = 0;
        timeModeSettings = "H0";
        isTemperatureModeStartedInTimeMode = false;
        digitalWrite(RELAY_PIN, LOW);
        helper.saveToSPIFFS("/TimeMode.txt", "H0");
        Serial.println("Режим по времени остановлен");
      }
    }
  }
  if (stream.get("temperatureMode")) {  // если получены настройки режима по температуре
    if (!temperatureRangeNotChanged) {
      if (String(stream.value.c_str()) != temperatureMode) {
        temperatureMode = String(stream.value.c_str());
        if (String(stream.value.c_str()) != " ") {
          isTemperatureModeStartedNow = true;
          minTemperature = (String(stream.value.c_str()).substring(0, String(stream.value.c_str()).indexOf(" "))).toInt();
          maxTemperature = (String(stream.value.c_str()).substring(String(stream.value.c_str()).indexOf(" ") + 1, String(stream.value.c_str()).indexOf(" ", String(stream.value.c_str()).indexOf(" ") + 1))).toInt();
          digitalWrite(RELAY_PIN, LOW);
          Serial.println("Режим по температуре запущен\nМинимальная температура: " + String(minTemperature) + "°C\nМаксимальная температура: " + String(maxTemperature) + "°C");
        } else {
          minTemperature = 0;
          maxTemperature = 0;
          temperatureModePhase = false;
          digitalWrite(RELAY_PIN, LOW);
          Serial.println("Режим по температуре остановлен");
        }
      }
    } else {
      temperatureRangeNotChanged = false;
    }
  }
  if (stream.get("settings")) {  // если получены настройки
    if (String(stream.value.c_str()) != settings && String(stream.value.c_str()) != " " && !streamStartedNow) {
      settings = String(stream.value.c_str());
      Serial.println("Настройки: " + String(settings));
      if (!Firebase.RTDB.setString(&firebaseData, (userUID + "/Heater/settings").c_str(), " "))
        Serial.println("Не удалось удалить настройки из Firebase :( Причина: " + String(firebaseData.errorReason().c_str()));
      if (helper.saveToSPIFFS("/Settings.txt", settings)) ESP.restart();
    }
  }

  streamStartedNow = false;
}

void streamTimeoutCallback(bool timeout) {
  if (timeout)
    Serial.println("Время ожидания слушателя истекло, перезапускаем слушатель ...");

  if (!stream.httpConnected())
    Serial.printf("Возникла ошибка слушателя :( Код ошибки: %d, причина: %s\n", stream.httpCode(), stream.errorReason().c_str());
}

// слушатель получения настроек через WiFi
void handleRoot() {
  server.send(200, "text/plain", "Ok\r\n");
  delay(500);
  if (server.hasArg("ssid") && server.hasArg("pass") && server.hasArg("email")
      && server.hasArg("user_pass") && server.hasArg("timezone") && server.hasArg("interval") && server.hasArg("notifications")) {
    String settings = server.arg("ssid") + "#" + server.arg("pass") + "#" + server.arg("email")
                      + "#" + server.arg("user_pass") + "#" + server.arg("timezone") + "#" + server.arg("interval") + server.arg("notifications");
    Serial.println("Настройки: " + String(settings));

    if (helper.saveToSPIFFS("/Settings.txt", settings)) ESP.restart();
  }
}

void setup() {
  delay(2000);
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
      pinMode(RELAY_PIN, OUTPUT);
      digitalWrite(RELAY_PIN, LOW);
      ds18b20.begin();

      helper.getSettings(settings, sensorInterval);  // получаем настройки из SPIFFS
      // получаем настройки режима по времени из SPIFFS
      helper.getTimeModeSettings(timeModeSettings, isTemperatureModeStartedInTimeMode, isTemperatureModeStartedNow, heaterOnOffTime, heaterOnOff, RELAY_PIN);
      helper.connectToWiFi(userUID, firebaseData, stream, auth, config, streamCallback, streamTimeoutCallback, timeClient,
                           getTimeMillis, hours, minutes, MAX_GET_TIME_ATTEMPTS, timeNotReceivedCorrectly, restartMillis);
    }
  } else {
    WiFi.mode(WIFI_AP);
    // запускаем WiFi сеть для получения настроек (название, пароль, канал, скрытая сеть, максимальное количество подключений)
    WiFi.softAP("Обогреватель", "", 1, false, 1);

    server.on("/", handleRoot);
    server.begin();

    Serial.println("Перешли в режим настройки");
  }
}

void loop() {
  if (settings == " ") {
    server.handleClient();
  } else {
    if (!timeNotReceivedCorrectly) {  // если время от NTP сервера получено правильно
      if (millis() - getTimeMillis >= 60000) {
        getTimeMillis = millis();

        Firebase.ready();                      // обрабатываем задачи системы авторизации
        helper.getFormatTime(hours, minutes);  // высчитываем время

        if (heaterOnOffTime != " ") {    // если режим по времени для обогревателя запущен
          if (temperatureMode == " ") {  // если одновременно с режимом по времени не запущен режим по температуре
            // управляем обогревателем по времени
            helper.controlHeaterInTimeMode(heaterOnOffTime, timeModeSettings, timestampId, heaterOnOff, RELAY_PIN);
          } else {
            // запускаем/останавливаем режим управления обогревателем по температуре по времени
            helper.controlTemperatureModeInTimeMode(heaterOnOffTime, timestampId, isTemperatureModeStartedInTimeMode,
                                                    isTemperatureModeStartedNow, timeModeSettings, heaterOnOff, RELAY_PIN);
          }
        }
      }

      if (temperatureMode != " ") {  // если запущен режим по температуре
        if (millis() - getTemperatureMillis >= sensorInterval * 60 * 1000 || getTemperatureMillis == 0) {
          getTemperatureMillis = millis();

          short currentTemperature = helper.getTemperatureC(ds18b20, MAX_GET_TEMPERATURE_ATTEMPTS);
          if (currentTemperature >= -10) temperature = currentTemperature;

          if (isTemperatureModeStartedInTimeMode && heaterOnOffTime != " ") {  // если режим управления обогревателем по температуре запущен по времени
            helper.controlHeaterInTemperatureMode(temperature, minTemperature, maxTemperature, temperatureMode, temperatureModePhase,
                                                  isTemperatureModeStartedNow, RELAY_PIN, firebaseData, userUID, temperatureRangeNotChanged);
          } else if (heaterOnOffTime == " ") {  // если одновременно с режимом по температуре не запущен режим по времени
            helper.controlHeaterInTemperatureMode(temperature, minTemperature, maxTemperature, temperatureMode, temperatureModePhase,
                                                  isTemperatureModeStartedNow, RELAY_PIN, firebaseData, userUID, temperatureRangeNotChanged);
          }
          if (!Firebase.RTDB.setInt(&firebaseData, (userUID + "/Heater/temperature").c_str(), temperature))
            Serial.println("Не удалось записать температуру в Firebase :( Причина: " + String(firebaseData.errorReason().c_str()));
        }
      }
    } else {
      if (millis() - restartMillis >= RESTART_INTERVAL * 60 * 1000) {
        ESP.restart();
      }
    }
  }
}
