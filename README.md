## Smart Home on ESP8266 and ESP32

For this project, I used my devices made earlier *(I published repositories with old versions of devices on my github)*, added new features to them and fixed some bugs. I have developed *an Android app* with which you can control each device. **A video with a more detailed description of this project is below.**

[![Video about my project on YouTube]()]()

## WiFi Thermometer

This device measures *the temperature and humidity* outside and sends it to your smartphone. My app displays the current *temperature and humidity*, the temperature and humidity readings at a particular time on *a graph*, and *the battery level* of the device.

![WiFi Thermometer image](https://github.com/IhorAntiukhov/Smart-Home-ESP8266-ESP32/blob/main/images/WiFiThermometer.jpg)

## Heating And Boiler

With this device you can control your *heating and boiler*. My app has *manual control*, *a timer*, *a time control mode*, and heating control depending on *the temperature in your home*.

![Heating And Boiler image](https://github.com/IhorAntiukhov/Smart-Home-ESP8266-ESP32/blob/main/images/HeatingAndBoiler.jpg)

## Smart IR Remote

Using this device, you can control *a lamp*, *a TV*, *an air conditioner*, or *RGB strip controller* from your smartphone. This device has built-in *IR LEDs* to send commands to these devices.

![Smart IR Remote image](https://github.com/IhorAntiukhov/Smart-Home-ESP8266-ESP32/blob/main/images/SmartIRRemote.jpg)

## Smart Doorbell

This is *an ESP32-CAM* device that when someone presses the doorbell button takes a photo, uploads it to *Firebase* and sends a notification on your smartphone. In my app, you can view the latest received photos.

## Meter Reader

This device is also based on *ESP32-CAM* that takes a photo of your *water, gas, or electricity meter*, recognizes the meter reading in the photo and writes it to *Firebase*. My app displays the last meter reading and the change in readings on the graph. I made my device based on [this](https://github.com/jomjol/AI-on-the-edge-device) github repository.

## Smart Heater

Using this device and *a ceramic heater*, you can control the heater *manually*, *by temperature* and in *time mode*.

![Smart Heater image](https://github.com/IhorAntiukhov/Smart-Home-ESP8266-ESP32/blob/main/images/SmartHeater.jpg)

## Firebase

To control my devices over the Internet, I used the [Firebase](https://firebase.google.com/) platform. I've used it to send commands to devices using *Realtime Database*, store photos taken by a smart doorbell in *Firebase Storage*, and send notifications to a smartphone using *Firebase Messaging*.