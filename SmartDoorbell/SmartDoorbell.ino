#include <WiFi.h>
#include <WiFiClient.h>
#include <WebServer.h>
#include <Firebase_ESP_Client.h>

#include <addons/TokenHelper.h>
#include <addons/RTDBHelper.h>

#include "FS.h"
#include "SPIFFS.h"

#include <WiFiUdp.h>
#include <NTPClient.h>

#include "Arduino.h"
#include "esp_camera.h"
#include "driver/rtc_io.h"

#include "soc/soc.h"
#include "soc/rtc_cntl_reg.h"

#include "Helper.h"

#define DOORBELL_BUTTON_PIN 12       // пин кнопки звонка
#define SETTINGS_MODE_BUTTON_PIN 12  // пин кнопки для перехода в режим настройки
#define MAX_GET_TIME_ATTEMPTS 3      // максимальное количество попыток получить правильное время от NTP сервера
#define CONNECT_TO_WIFI_TIMEOUT 30   // таймаут подключения к WiFi сети в секундах

String settings = " ";
bool startSleep = true;  // переходить ли в сон после отправки фото по нажатию на кнопку звонка

String streamResult;
String userUID;
/* переменная для того, чтобы при запуске слушателя изменения данных не сохранялись настройки в SPIFFS */
bool streamStartedNow = true;

bool doorbellButtonFlag;
unsigned doorbellButtonMillis = 0;

Helper helper;  // объект "помощник" с дополнительными методами

WebServer server(80);

WiFiUDP ntpUDP;
NTPClient timeClient(ntpUDP, "europe.pool.ntp.org", 3600, 1000);

FirebaseData firebaseData;
FirebaseData stream;  // объект слушателя изменения данных в Firebase

FirebaseAuth auth;
FirebaseConfig config;

// слушатель получения настроек через WiFi
void handleRoot() {
  server.send(200, "text/plain", "Ok\r\n");
  delay(500);
  if (server.hasArg("ssid") && server.hasArg("pass") && server.hasArg("email") && server.hasArg("user_pass") && server.hasArg("timezone") 
      && server.hasArg("resolution") && server.hasArg("flash") && server.hasArg("vflip") && server.hasArg("hmirror") && server.hasArg("sleep")) {
    String settings = server.arg("ssid") + "#" + server.arg("pass") + "#" + server.arg("email") + "#" + server.arg("user_pass") + "#" + server.arg("timezone")
                      + "#" + server.arg("resolution") + server.arg("flash") + server.arg("vflip") + server.arg("hmirror") + server.arg("sleep");
    Serial.println("Настройки: " + String(settings));

    if (helper.saveToSPIFFS("/Settings.txt", settings)) ESP.restart();
  }
}

void setup() {
  WRITE_PERI_REG(RTC_CNTL_BROWN_OUT_REG, 0);  // выключаем детектор отключения питания (это нужно для того, чтобы плата не перезагружалась из-за плохого контакта)
  Serial.begin(115200);

  if (!SPIFFS.begin(true)) {
    Serial.println("\nНе удалось монтировать SPIFFS :(");
    ESP.restart();
  }

  if (SPIFFS.exists("/Settings.txt")) {
    bool settingsModeStarted = false;
    pinMode(SETTINGS_MODE_BUTTON_PIN, INPUT_PULLUP);
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
      helper.getSettings(settings, startSleep);  // получаем настройки из SPIFFS

      pinMode(4, OUTPUT);
      helper.initCamera();
      if (startSleep) helper.takePhoto();

      helper.connectToWiFi(firebaseData, stream, auth, config, userUID, timeClient, startSleep,
                           CONNECT_TO_WIFI_TIMEOUT, MAX_GET_TIME_ATTEMPTS, DOORBELL_BUTTON_PIN);
      if (startSleep) {
        helper.sendPhotoAndMessage(firebaseData, auth, userUID, true);
        helper.startDeepSleep(DOORBELL_BUTTON_PIN);
      } else {
        pinMode(DOORBELL_BUTTON_PIN, INPUT_PULLUP);
      }
    }
  } else {
    WiFi.mode(WIFI_AP);
    // запускаем WiFi сеть для получения настроек (название, пароль, канал, скрытая сеть, максимальное количество подключений)
    WiFi.softAP("Дверной Звонок", "", 1, false, 1);

    server.on("/", handleRoot);
    server.begin();

    Serial.println("Перешли в режим настройки");
  }
}

void loop() {
  if (settings == " ") {
    server.handleClient();
  } else {
    bool sendPhotoRequest = false;  // запрос на отправку фото
    bool sendMessage = false;       // отправлять ли сообщение после отправки фото

    bool buttonState = !digitalRead(DOORBELL_BUTTON_PIN);
    if (buttonState && !doorbellButtonFlag && millis() - doorbellButtonMillis > 100) {
      doorbellButtonFlag = true;
      sendPhotoRequest = true;
      sendMessage = true;
      doorbellButtonMillis = millis();
      Serial.println("Кнопка звонка нажата");
    }
    if (!buttonState && doorbellButtonFlag && millis() - doorbellButtonMillis > 100) {
      doorbellButtonFlag = false;
      doorbellButtonMillis = millis();
    }

    if (!Firebase.ready())  // обрабатываем задачи системы авторизации
      return;

    if (!Firebase.RTDB.readStream(&stream)) Serial.printf("Не удалось прочитать поток, %s\n\n", stream.errorReason().c_str());

    if (stream.streamTimeout()) {
      Serial.println("Время ожидания слушателя истекло, перезапускаем слушатель ...");

      if (!stream.httpConnected())
        Serial.printf("Возникла ошибка слушателя :( Код ошибки: %d, причина: %s\n", stream.httpCode(), stream.errorReason().c_str());
    }

    if (stream.streamAvailable()) {
      if (String(stream.to<String>()) != streamResult) {
        streamResult = String(stream.to<String>());

        if (streamResult.indexOf("takePhoto") != -1) {  // если получен запрос на отправку фото
          sendPhotoRequest = true;
          Serial.println("Получен запрос на отправку фото");
        }
        // если получены настройки
        if (streamResult.indexOf("#") != -1 && streamResult != settings && streamResult != " " && !streamStartedNow) {
          settings = streamResult;
          Serial.println("Настройки: " + String(settings));
          Firebase.RTDB.endStream(&stream);  // останавливаем слушатель изменения данных для того, чтобы удалить настройки из Firebase

          if (!Firebase.RTDB.setString(&firebaseData, ("/" + userUID + "/SmartDoorbell/settings").c_str(), " "))
            Serial.println("Не удалось удалить настройки из Firebase :( Причина: " + String(firebaseData.errorReason().c_str()));

          if (helper.saveToSPIFFS("/Settings.txt", settings)) ESP.restart();
        }
      }

      streamStartedNow = false;
    }

    if (sendPhotoRequest) {
      sendPhotoRequest = false;
      streamStartedNow = true;
      Firebase.RTDB.endStream(&stream);  // останавливаем слушатель изменения данных для того, чтобы отправить фото и сообщение

      helper.getFormatTimeDate(timeClient);
      helper.takePhoto();
      helper.sendPhotoAndMessage(firebaseData, auth, userUID, sendMessage);

      if (!Firebase.RTDB.beginStream(&stream, ("/" + userUID + "/SmartDoorbell").c_str())) {
        Serial.printf("Не удалось запустить слушатель, %s\n", stream.errorReason().c_str());
        ESP.restart();
      }
    }
  }
}
