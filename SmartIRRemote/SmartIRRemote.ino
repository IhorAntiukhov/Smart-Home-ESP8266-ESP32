#include <ESP8266WiFi.h>
#include <ESP8266WebServer.h>
#include <Firebase_ESP_Client.h>

#include <addons/TokenHelper.h>
#include <addons/RTDBHelper.h>

#include <FS.h>
#include <LittleFS.h>

#include <WiFiUdp.h>
#include <NTPClient.h>

#include <Arduino.h>
#include <IRremoteESP8266.h>
#include <IRrecv.h>
#include <IRutils.h>
#include <IRsend.h>
#include <IRac.h>

#include <ArduinoJson.h>
#include "Helper.h"

#define IR_RECEIVER_PIN D5           // пин ИК приёмника
#define IR_SENDER_PIN D2             // пин для управления ИК светодиодами
#define SETTINGS_MODE_BUTTON_PIN D7  // пин кнопки для перехода в режим настройки
#define MAX_GET_TIME_ATTEMPTS 3      // максимальное количество попыток получить правильное время от NTP сервера
#define RESTART_INTERVAL 1           // интервал перезагрузки платы после неудачного подключения к WiFi сети в минутах

String settings = " ";

byte lightRemoteSettingsButton = 0;  // id кнопки пульта освещения, которую выбрал пользователь в приложении, которую нужно настроить
String lightRemoteKeys[10] = { "", "", "", "", "", "", "", "", "", "" };
byte lightRemoteConfiguredButtons[10] = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };  // массив для хранения того, настроена ли конкретная кнопка пульта освещения

byte tvRemoteSettingsButton = 0;                                                 // id кнопки пульта телевизора, которую выбрал пользователь в приложении, которую нужно настроить
byte tvRemoteConfiguredButtons[13] = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };  // массив для хранения того, настроена ли конкретная кнопка пульта телевизора
uint16_t tvRemoteKeyLength;                                                      // размер ключа кнопки пульта телевизора
uint16_t tvRemoteKeys[13][256];

bool configureACRemoteRequest;
byte isAcRemoteConfigured = 0;  // настроен ли пульт кондиционера (0 = пульт ещё не настроен, 1 = пульт настроен, 2 = протокол пульта не поддерживается)

// ключи кнопок пульта RGB ленты
const String rgbRemoteKeys[24] = { "F7C03F", "F740BF", "F700FF", "F7807F", "F720DF", "F710EF", "F730CF", "F708F7",
                                   "F728D7", "F7A05F", "F7906F", "F7B04F", "F78877", "F7A857", "F7609F", "F750AF",
                                   "F7708F", "F748B7", "F76897", "F7E01F", "F7D02F", "F7F00F", "F7C837", "F7E817" };

unsigned long irReceiverMillis = 0;  // время последнего получения ИК сигнала в millis

String lightRemoteFirebaseButton = " ";
String tvRemoteFirebaseButton = " ";
String rgbRemoteFirebaseButton = " ";

String acRemote = " ";
String acOnOffTime = " ";  // время включения/выключения кондиционера в режиме по времени
bool acOnOff;
short timestampId = 0;  // индекс (в строке со всем временем) следующего времени включения/выключения кондиционера

String userUID;
/* переменная для того, чтобы при запуске слушателя изменения данных, не отправлялась команда на 
 остановку кондиционера, не отправлялись команды пультов и не сохранялись настройки в SPIFFS */
bool streamStartedNow = true;
bool resetSettingsRequest;  // запрос на удаление настроек из Firebase после их отправки пользователем

byte hours = 0;
byte minutes = 0;
bool timeNotReceivedCorrectly;
unsigned long restartMillis = 0;  // время с момента неудачной попытки получить время в millis
unsigned long getTimeMillis = 0;  // время последнего высчитывания текущего времени в millis

Helper helper;  // объект "помощник" с дополнительными методами

IRrecv irReceiver(IR_RECEIVER_PIN, 1024, 30, true);
IRsend irSend(IR_SENDER_PIN);
IRac ac(IR_SENDER_PIN);

ESP8266WebServer server(80);

WiFiUDP ntpUDP;
NTPClient timeClient(ntpUDP, "europe.pool.ntp.org", 3600, 1000);

FirebaseData firebaseData;
FirebaseData stream;  // объект слушателя изменения данных в Firebase

FirebaseAuth auth;
FirebaseConfig config;

// функция слушателя изменения данных в Firebase
void streamCallback(MultiPathStream stream) {
  if (stream.get("acOnOffTime")) {  // если получено время включения/выключения кондиционера в режиме по времени
    acOnOffTime = String(stream.value.c_str());
    if (acOnOffTime != " ") {
      if (!streamStartedNow) {
        ac.next.power = false;
        ac.sendAc();
      }
      short nextTime = 0;
      short previousNextTime = 0;
      // увеличиваем индекс следующего времени включения/выключения кондиционера, пока текущее время меньше времени под этим индексом
      while (hours * 60 + minutes >= acOnOffTime.substring(timestampId, timestampId + 2).toInt() * 60
                                       + acOnOffTime.substring(timestampId + 2, timestampId + 4).toInt()
             && timestampId < acOnOffTime.length() - 1) {
        timestampId = timestampId + 4;
        nextTime = acOnOffTime.substring(timestampId, timestampId + 2).toInt() * 60
                   + acOnOffTime.substring(timestampId + 2, timestampId + 4).toInt();  // записываем следующее время включения/выключения
        acOnOff = !acOnOff;
        if (nextTime < previousNextTime) {  // если текущее время включения/выключения меньше предыдущего
          break;
        }
        previousNextTime = nextTime;  // записываем следующее время включения/выключения для того, чтобы выйти из цикла если время включения/выключения начнёт уменьшаться
      }
      if (timestampId >= acOnOffTime.length() - 1) {
        timestampId = 0;
      }
      Serial.println("Время включения/выключения кондиционера: " + String(acOnOffTime));
      Serial.println("Режим по времени запущен");
    } else {
      timestampId = 0;
      if (!streamStartedNow) {
        ac.next.power = false;
        ac.sendAc();
      }
      Serial.println("Режим по времени для кондиционера остановлен");
    }
  }
  if (stream.get("lightRemoteButton")) {  // если получена команда пульта освещения
    if (String(stream.value.c_str()) != lightRemoteFirebaseButton) {
      lightRemoteFirebaseButton = String(stream.value.c_str());
      if (!streamStartedNow) {
        irSend.sendNEC((uint64_t)strtoull((const char *)lightRemoteKeys[(lightRemoteFirebaseButton.substring(0, lightRemoteFirebaseButton.indexOf(" "))).toInt() - 1].c_str(), 0, 16));
        Serial.println("Кнопка пульта освещения: " + String(lightRemoteFirebaseButton.substring(0, lightRemoteFirebaseButton.indexOf(" "))));
      }
    }
  }
  if (stream.get("tvRemoteButton")) {  // если получена команда пульта телевизора
    if (String(stream.value.c_str()) != tvRemoteFirebaseButton) {
      tvRemoteFirebaseButton = String(stream.value.c_str());
      if (!streamStartedNow) {
        irSend.sendRaw(tvRemoteKeys[(tvRemoteFirebaseButton.substring(0, tvRemoteFirebaseButton.indexOf(" "))).toInt() - 1], tvRemoteKeyLength, 38);
        Serial.println("Кнопка пульта телевизора: " + String(tvRemoteFirebaseButton.substring(0, tvRemoteFirebaseButton.indexOf(" "))));
      }
    }
  }
  if (stream.get("acRemote")) {  // если получена команда пульта кондиционера
    if (String(stream.value.c_str()) != acRemote) {
      acRemote = String(stream.value.c_str());
      if (!streamStartedNow) {
        // записываем параметры работы кондиционера в SPIFFS для того, чтобы они сохранились после перезагрузки платы
        helper.saveToSPIFFS("/ACSettings.txt", acRemote.substring(acRemote.indexOf(" ") + 1, acRemote.length()));
        if (acRemote.substring(0, 11) == "temperature") {  // если получена команда на установку температуры
          ac.next.degrees = (acRemote.substring(11, acRemote.indexOf(" "))).toInt();
          ac.sendAc();
          Serial.println("Температура: " + String(acRemote.substring(11, acRemote.indexOf(" "))) + "°C");
        } else if (acRemote.substring(0, 4) == "mode") {  // если получена команда на установку режима кондиционера
          helper.setACMode(acRemote, 4, 5, ac);
          ac.sendAc();
        } else if (acRemote.substring(0, 8) == "fanSpeed") {  // если получена команда на установку мощности вентилятора
          helper.setACFanSpeed(acRemote, 8, 9, ac);
          ac.sendAc();
        } else if (acRemote.substring(0, 5) == "turbo") {  // если получена команда на запуск/остановку турбо режима
          if (acRemote.substring(5, 6) == "1") {
            ac.next.turbo = true;
          } else {
            ac.next.turbo = false;
          }
          ac.sendAc();
          Serial.println("Турбо режим: " + String(acRemote.substring(5, 6)));
        } else if (acRemote.substring(0, 5) == "light") {  // если получена команда на включение/выключение подсветки на кондиционере
          if (acRemote.substring(5, 6) == "1") {
            ac.next.light = true;
          } else {
            ac.next.light = false;
          }
          ac.sendAc();
          Serial.println("Подсветка: " + String(acRemote.substring(5, 6)));
        } else if (acRemote.substring(0, 2) == "on") {  // если получена команда на включение кондиционера
          ac.next.power = true;
          ac.sendAc();
          Serial.println("Кондиционер запущен");
        } else if (acRemote.substring(0, 3) == "off") {  // если получена команда на выключение кондиционера
          ac.next.power = false;
          ac.sendAc();
          Serial.println("Кондиционер остановлен");
        }
      }
    }
  }
  if (stream.get("rgbRemoteButton")) {  // если получена команда пульта RGB ленты
    if (String(stream.value.c_str()) != rgbRemoteFirebaseButton) {
      rgbRemoteFirebaseButton = String(stream.value.c_str());
      if (!streamStartedNow) {
        irSend.sendNEC((uint64_t)strtoull((const char *)rgbRemoteKeys[(rgbRemoteFirebaseButton.substring(0, rgbRemoteFirebaseButton.indexOf(" "))).toInt() - 1].c_str(), 0, 16));
        Serial.println("Кнопка пульта телевизора: " + String(rgbRemoteFirebaseButton.substring(0, rgbRemoteFirebaseButton.indexOf(" "))));
      }
    }
  }
  if (stream.get("settings")) {  // если получены настройки
    if (String(stream.value.c_str()) != settings && String(stream.value.c_str()) != " " && !streamStartedNow) {
      settings = String(stream.value.c_str());
      Serial.println("Настройки: " + String(settings));
      resetSettingsRequest = true;
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
  if (server.hasArg("light_remote")) {  // если получен id кнопки пульта освещения, которую нужно настроить
    tvRemoteSettingsButton = 0;
    configureACRemoteRequest = false;
    lightRemoteSettingsButton = (server.arg("light_remote")).toInt();
    Serial.println("Кнопка пульта освещения: " + String(lightRemoteSettingsButton));
  }
  if (server.hasArg("save_light_remote")) {  // если получена команда на сохранение пульта освещения
    if (helper.saveToSPIFFS("/LightRemote.txt", lightRemoteKeys[0] + "#" + lightRemoteKeys[1] + "#" + lightRemoteKeys[2] + "#" + lightRemoteKeys[3] + "#" + lightRemoteKeys[4] + "#"
                                                  + lightRemoteKeys[5] + "#" + lightRemoteKeys[6] + "#" + lightRemoteKeys[7] + "#" + lightRemoteKeys[8] + "#" + lightRemoteKeys[9])) {
      Serial.println("Пульт освещения сохранён");
    }
  }

  if (server.hasArg("tv_remote")) {  // если получен id кнопки пульта телевизора, которую нужно настроить
    lightRemoteSettingsButton = 0;
    configureACRemoteRequest = false;
    tvRemoteSettingsButton = (server.arg("tv_remote")).toInt();
    Serial.println("Кнопка пульта телевизора: " + String(tvRemoteSettingsButton));
  }
  if (server.hasArg("save_tv_remote")) helper.saveTvRemote(tvRemoteConfiguredButtons, tvRemoteKeyLength);  // если получена команда на сохранение пульта телевизора

  if (server.hasArg("ssid") && server.hasArg("pass") && server.hasArg("email") && server.hasArg("user_pass") && server.hasArg("timezone")) {  // если получены общие настройки
    delay(500);
    String settings = server.arg("ssid") + "#" + server.arg("pass") + "#" + server.arg("email") + "#" + server.arg("user_pass") + "#" + server.arg("timezone");
    Serial.println("Настройки: " + String(settings));

    if (helper.saveToSPIFFS("/Settings.txt", settings)) ESP.restart();
  }
}

// слушатель получения запросов от пользователя того, настроена ли выбранная кнопка пульта освещения
void handleIsLightRemoteButtonConfigured() {
  server.send(200, "text/plain", String(lightRemoteSettingsButton) + String(lightRemoteConfiguredButtons[lightRemoteSettingsButton - 1]) + "\r\n");
}

// слушатель получения запросов от пользователя того, настроена ли выбранная кнопка пульта телевизора
void handleIsTvRemoteButtonConfigured() {
  server.send(200, "text/plain", String(tvRemoteSettingsButton) + String(tvRemoteConfiguredButtons[tvRemoteSettingsButton - 1]) + "\r\n");
}

// слушатель получения запросов от пользователя того, настроен ли пульт кондиционера
void handleIsAcRemoteConfigured() {
  lightRemoteSettingsButton = 0;
  tvRemoteSettingsButton = 0;
  configureACRemoteRequest = true;
  server.send(200, "text/plain", String(isAcRemoteConfigured) + "\r\n");
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
          irReceiver.enableIRIn();

          server.on("/", handleRoot);
          server.on("/light_button_configured", handleIsLightRemoteButtonConfigured);
          server.on("/tv_button_configured", handleIsTvRemoteButtonConfigured);
          server.on("/ac_remote_configured", handleIsAcRemoteConfigured);
          server.begin();
          break;
        }
        delay(1);
      }
    }

    if (!settingsModeStarted) {
      irSend.begin();
      helper.getSettings(settings);  // получаем настройки из SPIFFS

      if (LittleFS.exists("/LightRemote.txt")) helper.getLightRemoteKeys(lightRemoteKeys);             // получаем ключи кнопок пульта освещения из SPIFFS
      if (LittleFS.exists("/TvRemote0.txt")) helper.getTvRemoteKeys(tvRemoteKeys, tvRemoteKeyLength);  // получаем ключи кнопок пульта телевизора из SPIFFS
      if (LittleFS.exists("/ACRemote.txt")) {
        helper.getACRemoteProtocol(ac);            // получаем протокол пульта кондиционера из SPIFFS
        if (LittleFS.exists("/ACSettings.txt")) {  // если параметры работы кондиционера ранее записывались в SPIFFS
          helper.getACRemoteSettings(ac);
        } else {
          // устанавливаем параметры по умолчанию
          ac.next.degrees = 16;
          ac.next.mode = stdAc::opmode_t::kCool;
          ac.next.fanspeed = stdAc::fanspeed_t::kMin;
          ac.next.turbo = false;
          ac.next.light = false;
        }
      }

      helper.connectToWiFi(firebaseData, stream, auth, config, streamCallback, streamTimeoutCallback, userUID, timeClient,
                           getTimeMillis, hours, minutes, MAX_GET_TIME_ATTEMPTS, timeNotReceivedCorrectly, restartMillis);
    }
  } else {
    irReceiver.enableIRIn();

    WiFi.mode(WIFI_AP);
    // запускаем WiFi сеть для получения настроек (название, пароль, канал, скрытая сеть, максимальное количество подключений)
    WiFi.softAP("Умный ИК Пульт", "", 1, false, 1);

    server.on("/", handleRoot);
    server.on("/light_button_configured", handleIsLightRemoteButtonConfigured);
    server.on("/tv_button_configured", handleIsTvRemoteButtonConfigured);
    server.on("/ac_remote_configured", handleIsAcRemoteConfigured);
    server.begin();

    Serial.println("Перешли в режим настройки");
  }
}

void loop() {
  if (settings == " ") {
    server.handleClient();

    if (lightRemoteSettingsButton != 0) {  // если пользователь выбрал в приложении кнопку пульта освещения, которую нужно настроить
      if (millis() - irReceiverMillis >= 250) {
        irReceiverMillis = millis();

        helper.receiveLightRemoteKeys(irReceiver, lightRemoteSettingsButton, lightRemoteKeys, lightRemoteConfiguredButtons);
      }
    } else if (tvRemoteSettingsButton != 0) {  // если пользователь выбрал в приложении кнопку пульта телевизора, которую нужно настроить
      helper.receiveTvRemoteKeys(irReceiver, tvRemoteSettingsButton, tvRemoteConfiguredButtons, tvRemoteKeyLength);
    } else if (configureACRemoteRequest) {  // если пользователь отправил запрос на настройку пульта кондиционера
      helper.receiveACRemoteProtocol(irReceiver, isAcRemoteConfigured);
    }
  } else {
    if (!timeNotReceivedCorrectly) {  // если время от NTP сервера получено правильно
      if (millis() - getTimeMillis >= 60000) {
        getTimeMillis = millis();

        Firebase.ready();                      // обрабатываем задачи системы авторизации
        helper.getFormatTime(hours, minutes);  // высчитываем время

        if (acOnOffTime != " ") {                                             // если режим по времени для кондиционера запущен
          helper.controlACInTimeMode(acOnOffTime, acOnOff, timestampId, ac);  // управляем кондиционером по времени
        }
      }

      if (resetSettingsRequest) {  // если получен запрос на удаление настроек из Firebase и их сохранение в SPIFFS
        resetSettingsRequest = false;

        if (!Firebase.RTDB.setString(&firebaseData, ("/" + userUID + "/SmartRemotes/settings").c_str(), " "))
          Serial.println("Не удалось удалить настройки из Firebase :( Причина: " + String(firebaseData.errorReason().c_str()));

        if (helper.saveToSPIFFS("/Settings.txt", settings)) ESP.restart();
      }
    } else {
      if (millis() - restartMillis >= RESTART_INTERVAL * 60 * 1000) {
        ESP.restart();
      }
    }
  }
}
