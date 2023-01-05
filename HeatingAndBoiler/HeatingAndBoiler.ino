#include <ESP8266WiFi.h>
#include <ESP8266WebServer.h>
#include <Firebase_ESP_Client.h>

#include <addons/TokenHelper.h>
#include <addons/RTDBHelper.h>

#include <FS.h>
#include <LittleFS.h>

#include <WiFiUdp.h>
#include <NTPClient.h>

#include "Helper.h"

#define HEATING_RELAY_PIN 15           // пин реле для включения/выключения котла
#define HEATING_ELEMENTS_RELAY_PIN 14  // пин реле для установки тэна котла
#define BOILER_RELAY_PIN 12            // пин реле для включения/выключения бойлера
#define SETTINGS_MODE_BUTTON_PIN 4     // пин кнопки для перехода в режим настройки
#define MAX_GET_TIME_ATTEMPTS 3        // максимальное количество попыток получить правильное время от NTP сервера
#define RESTART_INTERVAL 1             // интервал перезагрузки платы после неудачного подключения к WiFi сети в минутах

String settings = " ";

byte heatingElements = 1;
bool isHeatingStarted;

short heatingTimerTime = 0;
unsigned long heatingTimerMillis = 0;  // время запуска таймера котла в millis

String heatingOnOffTime = " ";     // время включения/выключения котла в режиме по времени
String timeHeatingElements = " ";  // алгоритм включения тэнов котла в режиме по времени
short heatingTimestampId = 0;      // индекс (в строке со всем временем) следующего времени включения/выключения котла
/* переменная для того, чтобы если котёл запустился по времени, плата перезагрузилась и получила
 последнее состояние котла из SPIFFS, при запуске слушателя изменения данных не изменялось количество тэнов */
bool isHeatingStartedFromSPIFFS;

String temperatureMode = " ";  // настройки режима по температуре
byte minTemperature = 0;
byte maxTemperature = 0;
byte temperature = 0;               // температура, полученная от термометра, для управления котлом в режиме по времени
bool temperatureModePhase = false;  // фаза работы режима по времени (включен ли котёл в этом режиме)
bool isTemperatureModeStartedNow;

byte heatingElementsInTemperatureMode = 1;
bool isTemperatureModeStartedInTimeMode;
String timeModeSettings;  // запущен ли, и на какой мощности, режим по температуре, или котёл, в режиме по времени

bool isBoilerStarted;

short boilerTimerTime = 0;
unsigned long boilerTimerMillis = 0;  // время запуска таймера бойлера в millis

String boilerOnOffTime = " ";  // время включения/выключения бойлера в режиме по времени
bool boilerOnOff;
short boilerTimestampId = 0;  // индекс (в строке со всем временем) следующего времени включения/выключения бойлера

String userUID;
/* переменная для того, чтобы при запуске слушателя изменения данных, не запускался таймер котла,
 или бойлера, не выключался котёл при запуске режима по времени и не сохранялись настройки в SPIFFS */
bool streamStartedNow = true;

byte sendResponseRequest; // запрос на отправку ответа через Firebase пользователю, который отправил команду
String response; // ответ, который нужно записать в Firebase
String responseNode; // узел в Firebase, в который нужно записать ответ

byte hours = 0;
byte minutes = 0;
bool timeNotReceivedCorrectly;
unsigned long restartMillis = 0;  // время с момента неудачной попытки получить время в millis
unsigned long getTimeMillis = 0;  // время последнего высчитывания текущего времени в millis

Helper helper;  // объект "помощник" с дополнительными методами

ESP8266WebServer server(80);

WiFiUDP ntpUDP;
NTPClient timeClient(ntpUDP, "europe.pool.ntp.org", 3600, 1000);

FirebaseData firebaseData;
FirebaseData stream;  // объект слушателя изменения данных в Firebase

FirebaseAuth auth;
FirebaseConfig config;

// функция слушателя изменения данных в Firebase
void streamCallback(MultiPathStream stream) {
  if (stream.get("heatingElements")) {  // если получено количество тэнов котла
    if (String(stream.value.c_str()).toInt() != heatingElements) {
      if (!isHeatingStartedFromSPIFFS) {
        heatingElements = String(stream.value.c_str()).toInt();
        if (heatingElements == 1) {
          digitalWrite(HEATING_ELEMENTS_RELAY_PIN, LOW);
          Serial.println("Установлен 1 тэн");
        } else if (heatingElements == 2) {
          digitalWrite(HEATING_ELEMENTS_RELAY_PIN, HIGH);
          Serial.println("Установлено 2 тэна");
        }
      }
      isHeatingStartedFromSPIFFS = false;
    }
  }
  if (stream.get("heatingStarted")) {  // если получена команда на включение/выключения котла
    if (String(stream.value.c_str()) == "true") {
      if (!isHeatingStarted) {
        isHeatingStarted = true;
        digitalWrite(HEATING_RELAY_PIN, HIGH);
        Serial.println("Котёл запущен");
        sendResponseRequest = 1;
      }
    } else {
      if (isHeatingStarted) {
        isHeatingStarted = false;
        digitalWrite(HEATING_RELAY_PIN, LOW);
        Serial.println("Котёл остановлен");
        sendResponseRequest = 1;
      }
    }
  }
  if (stream.get("heatingTimerTime")) {  // если получено время работы таймера котла
    if (String(stream.value.c_str()).toInt() != heatingTimerTime) {
      if (String(stream.value.c_str()).toInt() != 0) {
        if (!streamStartedNow) {
          heatingTimerTime = String(stream.value.c_str()).toInt();
          heatingTimerMillis = millis();
          digitalWrite(HEATING_RELAY_PIN, HIGH);
          Serial.println("Запущен таймер котла. Время работы таймера: " + String(heatingTimerTime) + " минут");
          sendResponseRequest = 1;
        }
      } else {
        heatingTimerTime = 0;
        heatingTimerMillis = 0;
        digitalWrite(HEATING_RELAY_PIN, LOW);
        Serial.println("Таймер котла остановлен");
        sendResponseRequest = 1;
      }
    }
  }
  if (stream.get("timeHeatingElements")) {  // если получен алгоритм включения тэнов котла в режиме по времени
    if (String(stream.value.c_str()) != timeHeatingElements) {
      timeHeatingElements = String(stream.value.c_str());
      Serial.println("Включение тэнов в режиме по времени: " + String(timeHeatingElements));
    }
  }
  if (stream.get("heatingOnOffTime")) {  // если получено время включения/выключения котла в режиме по времени
    if (String(stream.value.c_str()).substring(0, String(stream.value.c_str()).length() - 1) != heatingOnOffTime) {
      heatingOnOffTime = String(stream.value.c_str()).substring(0, String(stream.value.c_str()).length() - 1);
      if (heatingOnOffTime != " ") {
        if (!streamStartedNow) digitalWrite(HEATING_RELAY_PIN, LOW);
        short nextTime = 0;
        short previousNextTime = 0;
        // увеличиваем индекс следующего времени включения/выключения котла, пока текущее время меньше времени под этим индексом
        while (hours * 60 + minutes >= heatingOnOffTime.substring(heatingTimestampId, heatingTimestampId + 2).toInt() * 60
                                         + heatingOnOffTime.substring(heatingTimestampId + 2, heatingTimestampId + 4).toInt()
               && heatingTimestampId < heatingOnOffTime.length() - 1) {
          heatingTimestampId = heatingTimestampId + 4;
          nextTime = heatingOnOffTime.substring(heatingTimestampId, heatingTimestampId + 2).toInt() * 60
                     + heatingOnOffTime.substring(heatingTimestampId + 2, heatingTimestampId + 4).toInt();  // записываем следующее время включения/выключения
          if (nextTime < previousNextTime) {                                                                // если текущее время включения/выключения меньше предыдущего
            break;
          }
          previousNextTime = nextTime;  // записываем следующее время включения/выключения для того, чтобы выйти из цикла если время включения/выключения начнёт уменьшаться
        }
        if (temperatureMode != " ") {   
          // если текущее время в диапазоне между временем запуска и временем остановки режима по температуре, запускаем режим       
          helper.controlTemperatureMode(heatingOnOffTime, timeHeatingElements, heatingTimestampId, isTemperatureModeStartedInTimeMode, isTemperatureModeStartedNow,
                                        heatingElementsInTemperatureMode, timeModeSettings, boilerOnOff, HEATING_RELAY_PIN, true);
        } else {
          // если текущее время в диапазоне между временем включения и временем выключения котла, запускаем котёл
          helper.controlHeating(heatingOnOffTime, timeHeatingElements, heatingTimestampId, timeModeSettings, HEATING_RELAY_PIN, HEATING_ELEMENTS_RELAY_PIN, boilerOnOff, true);
        }
        if (heatingTimestampId >= heatingOnOffTime.length() - 1) {
          heatingTimestampId = 0;
        }
        isHeatingStartedFromSPIFFS = false;
        Serial.println("Время включения/выключения котла: " + String(heatingOnOffTime));
      } else {
        heatingTimestampId = 0;
        timeModeSettings = "H0";
        isTemperatureModeStartedInTimeMode = false;
        digitalWrite(HEATING_RELAY_PIN, LOW);
        helper.saveToSPIFFS("/TimeMode.txt", "H0" + String(boilerOnOff));
        Serial.println("Режим по времени для котла остановлен");
      }

      sendResponseRequest = 2;
      response = heatingOnOffTime + "1";
      responseNode = "heatingOnOffTime";
    }
  }
  if (stream.get("temperatureMode")) {  // если получены настройки режима по температуре
    if (String(stream.value.c_str()).substring(0, String(stream.value.c_str()).length() - 2) != temperatureMode) {
      temperatureMode = String(stream.value.c_str()).substring(0, String(stream.value.c_str()).length() - 2);
      if (temperatureMode != " ") {
        isTemperatureModeStartedNow = true;
        minTemperature = (temperatureMode.substring(0, temperatureMode.indexOf(" "))).toInt();
        maxTemperature = (temperatureMode.substring(temperatureMode.indexOf(" ") + 1, temperatureMode.indexOf(" ", temperatureMode.indexOf(" ") + 1))).toInt();
        Serial.println("Минимальная температура: " + String(minTemperature) + "°C Максимальная температура: " + String(maxTemperature) + "°C");

        digitalWrite(HEATING_RELAY_PIN, LOW);
        if (temperatureMode.charAt(temperatureMode.length() - 1) == '1') {
          digitalWrite(HEATING_ELEMENTS_RELAY_PIN, LOW);
          Serial.println("Установлен 1 тэн");
        } else {
          digitalWrite(HEATING_ELEMENTS_RELAY_PIN, HIGH);
          Serial.println("Установлено 2 тэна");
        }

        if (heatingOnOffTime != " ") {
          helper.controlTemperatureMode(heatingOnOffTime, timeHeatingElements, heatingTimestampId, isTemperatureModeStartedInTimeMode, isTemperatureModeStartedNow,
                                        heatingElementsInTemperatureMode, timeModeSettings, boilerOnOff, HEATING_RELAY_PIN, true);
        }
      } else {
        minTemperature = 0;
        maxTemperature = 0;
        temperatureModePhase = false;
        digitalWrite(HEATING_RELAY_PIN, LOW);
        Serial.println("Режим по температуре остановлен");
      }

      sendResponseRequest = 2;
      response = temperatureMode + "01";
      responseNode = "temperatureMode";
    }
  }
  if (stream.get("temperature")) {  // если получена температура от термометра
    if (String(stream.value.c_str()).toInt() != temperature) {
      temperature = String(stream.value.c_str()).toInt();
      Serial.println("Температура: " + String(temperature) + "°C");
    }
  }
  if (stream.get("boilerStarted")) {  // если получена команда на запуск/остановку бойлера
    if (String(stream.value.c_str()) == "true") {
      if (!isBoilerStarted) {
        isBoilerStarted = true;
        digitalWrite(BOILER_RELAY_PIN, HIGH);
        Serial.println("Бойлер запущен");
        sendResponseRequest = 1;
      }
    } else {
      if (isBoilerStarted) {
        isBoilerStarted = false;
        digitalWrite(BOILER_RELAY_PIN, LOW);
        Serial.println("Бойлер остановлен");
        sendResponseRequest = 1;
      }
    }
  }
  if (stream.get("boilerTimerTime")) {  // если получено время работы таймера бойлера
    if (String(stream.value.c_str()).toInt() != boilerTimerTime) {
      boilerTimerTime = String(stream.value.c_str()).toInt();
      if (boilerTimerTime != 0) {
        if (!streamStartedNow) {
          boilerTimerMillis = millis();
          digitalWrite(BOILER_RELAY_PIN, HIGH);
          Serial.println("Запущен таймер бойлера. Время работы таймера: " + String(boilerTimerTime) + " минут");
          sendResponseRequest = 1;
        }
      } else {
        boilerTimerMillis = 0;
        digitalWrite(BOILER_RELAY_PIN, LOW);
        Serial.println("Таймер бойлера остановлен");
        sendResponseRequest = 1;
      }
    }
  }
  if (stream.get("boilerOnOffTime")) {  // если получено время включения/выключения бойлера в режиме по времени
    if (String(stream.value.c_str()).substring(0, String(stream.value.c_str()).length() - 1) != boilerOnOffTime) {
      boilerOnOffTime = String(stream.value.c_str()).substring(0, String(stream.value.c_str()).length() - 1);
      if (boilerOnOffTime != " ") {
        short nextTime = 0;
        short previousLastTime = 0;
        // увеличиваем индекс следующего времени включения/выключения бойлера, пока текущее время меньше времени под этим индексом
        while (hours * 60 + minutes >= boilerOnOffTime.substring(boilerTimestampId, boilerTimestampId + 2).toInt() * 60
                                         + boilerOnOffTime.substring(boilerTimestampId + 2, boilerTimestampId + 4).toInt()
               && boilerTimestampId < boilerOnOffTime.length() - 1) {
          boilerTimestampId = boilerTimestampId + 4;
          nextTime = boilerOnOffTime.substring(boilerTimestampId, boilerTimestampId + 2).toInt() * 60
                     + boilerOnOffTime.substring(boilerTimestampId + 2, boilerTimestampId + 4).toInt();  // записываем следующее время включения/выключения
          if (nextTime < previousLastTime) {
            break;
          }
          previousLastTime = nextTime;  // записываем следующее время включения/выключения для того, чтобы выйти из цикла если время включения/выключения начнёт уменьшаться
        }
        // если текущее время в диапазоне между временем включения и временем выключения бойлера, запускаем бойлера
        helper.controlBoiler(boilerOnOffTime, boilerOnOff, boilerTimestampId, timeModeSettings, BOILER_RELAY_PIN);
        if (boilerTimestampId >= boilerOnOffTime.length() - 1) {
          boilerTimestampId = 0;
        }
        Serial.println("Время включения/выключения бойлера: " + String(boilerOnOffTime));
      } else {
        boilerOnOff = false;
        boilerTimestampId = 0;
        digitalWrite(BOILER_RELAY_PIN, LOW);
        helper.saveToSPIFFS("/TimeMode.txt", timeModeSettings + "0");
        Serial.println("Режим по времени для бойлера остановлен");
      }

      sendResponseRequest = 2;
      response = boilerOnOffTime + "1";
      responseNode = "boilerOnOffTime";
    }
  }
  if (stream.get("settings")) {  // если получены настройки
    if (String(stream.value.c_str()) != settings && String(stream.value.c_str()) != " " && !streamStartedNow) {
      settings = String(stream.value.c_str());
      Serial.println("Настройки: " + String(settings));
      if (!Firebase.RTDB.setString(&firebaseData, (userUID + "/HeatingAndBoiler/settings").c_str(), " "))
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
      && server.hasArg("user_pass") && server.hasArg("timezone") && server.hasArg("elements")) {
    String settings = server.arg("ssid") + "#" + server.arg("pass") + "#" + server.arg("email")
                      + "#" + server.arg("user_pass") + "#" + server.arg("timezone") + "#" + server.arg("elements");
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
      pinMode(HEATING_RELAY_PIN, OUTPUT);
      pinMode(HEATING_ELEMENTS_RELAY_PIN, OUTPUT);
      pinMode(BOILER_RELAY_PIN, OUTPUT);

      digitalWrite(HEATING_RELAY_PIN, LOW);
      digitalWrite(HEATING_ELEMENTS_RELAY_PIN, LOW);
      digitalWrite(BOILER_RELAY_PIN, LOW);

      helper.getSettings(settings);  // получаем настройки из SPIFFS
      // получаем настройки режима по времени из SPIFFS
      helper.getTimeModeSettings(timeModeSettings, isTemperatureModeStartedInTimeMode, isTemperatureModeStartedNow, heatingElementsInTemperatureMode, heatingOnOffTime,
                                 boilerOnOffTime, isHeatingStartedFromSPIFFS, HEATING_RELAY_PIN, HEATING_ELEMENTS_RELAY_PIN, BOILER_RELAY_PIN, boilerOnOff);
      helper.connectToWiFi(userUID, firebaseData, stream, auth, config, streamCallback, streamTimeoutCallback, timeClient,
                           getTimeMillis, hours, minutes, MAX_GET_TIME_ATTEMPTS, timeNotReceivedCorrectly, restartMillis);
    }
  } else {
    WiFi.mode(WIFI_AP);
    // запускаем WiFi сеть для получения настроек (название, пароль, канал, скрытая сеть, максимальное количество подключений)
    WiFi.softAP("Котёл и Бойлер", "", 1, false, 1);

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
      if (sendResponseRequest == 1) {
        if (!Firebase.RTDB.setBool(&firebaseData, (userUID + "/HeatingAndBoiler/response").c_str(), true))
          Serial.println("Не удалось записать ответ в Firebase :( Причина: " + String(firebaseData.errorReason().c_str()));
      } else if (sendResponseRequest == 2) {
        if (!Firebase.RTDB.setString(&firebaseData, (userUID + "/HeatingAndBoiler/" + responseNode).c_str(), response))
          Serial.println("Не удалось записать ответ в Firebase :( Причина: " + String(firebaseData.errorReason().c_str()));
      }
      sendResponseRequest = 0;

      if (heatingTimerTime != 0) {
        helper.startHeatingTimer(heatingTimerMillis, heatingTimerTime, HEATING_RELAY_PIN, firebaseData, userUID);
      }
      if (boilerTimerTime != 0) {
        helper.startBoilerTimer(boilerTimerMillis, boilerTimerTime, BOILER_RELAY_PIN, firebaseData, userUID);
      }

      if (millis() - getTimeMillis >= 60000) {
        getTimeMillis = millis();

        Firebase.ready();                      // обрабатываем задачи системы авторизации
        helper.getFormatTime(hours, minutes);  // высчитываем время

        if (heatingOnOffTime != " ") {   // если режим по времени для котла запущен
          if (temperatureMode == " ") {  // если одновременно с режимом по времени не запущен режим по температуре
            // управляем котлом по времени
            helper.controlHeatingInTimeMode(heatingOnOffTime, timeHeatingElements, heatingTimestampId, timeModeSettings, HEATING_RELAY_PIN, HEATING_ELEMENTS_RELAY_PIN, boilerOnOff);
          } else {
            // запускаем/останавливаем режим управления котлом по температуре по времени
            helper.controlTemperatureModeInTimeMode(heatingOnOffTime, timeHeatingElements, heatingTimestampId, isTemperatureModeStartedInTimeMode,
                                                    isTemperatureModeStartedNow, heatingElementsInTemperatureMode, timeModeSettings, boilerOnOff,
                                                    HEATING_RELAY_PIN, HEATING_ELEMENTS_RELAY_PIN);
          }
        }
        if (boilerOnOffTime != " ") {  // если режим по времени для бойлера запущен
          // управляем бойлером по времени
          helper.controlBoilerInTimeMode(boilerOnOffTime, boilerOnOff, boilerTimestampId, timeModeSettings, BOILER_RELAY_PIN);
        }
      }
      if (temperatureMode != " ") {                                           // если запущен режим по температуре
        if (isTemperatureModeStartedInTimeMode && heatingOnOffTime != " ") {  // если режим управления котлом по температуре запущен по времени
          helper.controlHeatingInTemperatureMode(temperature, minTemperature, maxTemperature, temperatureMode, temperatureModePhase, isTemperatureModeStartedNow,
                                                 heatingElementsInTemperatureMode, HEATING_RELAY_PIN, HEATING_ELEMENTS_RELAY_PIN, firebaseData, userUID);
        } else if (heatingOnOffTime == " ") {  // если одновременно с режимом по температуре не запущен режим по времени
          helper.controlHeatingInTemperatureMode(temperature, minTemperature, maxTemperature, temperatureMode, temperatureModePhase, isTemperatureModeStartedNow,
                                                 0, HEATING_RELAY_PIN, HEATING_ELEMENTS_RELAY_PIN, firebaseData, userUID);
        }
      }
    } else {
      if (millis() - restartMillis >= RESTART_INTERVAL * 60 * 1000) {
        ESP.restart();
      }
    }
  }
}
