// Plantilla. El archivo real es include/secrets.h (no se versiona);
// lo genera crear-secretos.ps1 y solo hay que completar el WiFi.
#pragma once

#define WIFI_SSID "nombre-de-la-red"       // red de 2.4 GHz (el ESP32 no usa 5 GHz)
#define WIFI_PASSWORD "contraseña-del-wifi"
#define RELAY_KEY "00000000000000000000000000000000"  // 32 hex; la misma clave se carga en la app
#define TARGET_MAC "00:11:22:AA:BB:CC"     // MAC Ethernet de la PC a encender
