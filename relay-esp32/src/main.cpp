// Relé de encendido remoto para la app WoL.
//
// Queda conectado a un tema de ntfy.sh por HTTPS. Cuando llega una orden
// firmada por la app ("wake.<unix-ts>.<hmac>"), manda el paquete mágico a la
// PC por la red local. El tema se deriva de la clave, y la firma HMAC-SHA256
// impide que alguien que descubra el tema pueda encender la PC.

#include <Arduino.h>
#include <WiFi.h>
#include <WiFiClientSecure.h>
#include <WiFiUdp.h>
#include <mbedtls/md.h>
#include <time.h>

#include "certs.h"
#include "secrets.h"

static const char* NTFY_HOST = "ntfy.sh";
static const long MAX_SKEW_S = 90;                  // antigüedad máxima de una orden
static const unsigned long STALL_MS = 120000;       // ntfy manda un keepalive cada ~45 s
static const unsigned long RESTART_MS = 600000;     // sin conexión 10 min: reiniciar la placa
static const int LED_PIN = 2;                       // LED azul de la DevKit: apagado = todo bien; encendido = sin conexion

static WiFiClientSecure client;
static WiFiUDP udp;
static String topic;
static uint8_t targetMac[6];
static time_t lastTs = 0;                           // última orden aceptada (evita repeticiones)
static unsigned long lastByteAt = 0;
static unsigned long lastOkAt = 0;

static String toHex(const uint8_t* data, size_t len) {
    static const char* digits = "0123456789abcdef";
    String out;
    out.reserve(len * 2);
    for (size_t i = 0; i < len; i++) {
        out += digits[data[i] >> 4];
        out += digits[data[i] & 0x0F];
    }
    return out;
}

// SHA-256 si key es nullptr; HMAC-SHA256 si no.
static String digestHex(const char* key, const String& message) {
    uint8_t out[32];
    const mbedtls_md_info_t* info = mbedtls_md_info_from_type(MBEDTLS_MD_SHA256);
    if (key) {
        mbedtls_md_hmac(info, (const uint8_t*)key, strlen(key), (const uint8_t*)message.c_str(), message.length(), out);
    } else {
        mbedtls_md(info, (const uint8_t*)message.c_str(), message.length(), out);
    }
    return toHex(out, sizeof out);
}

static bool constantTimeEquals(const String& a, const String& b) {
    if (a.length() != b.length()) return false;
    uint8_t diff = 0;
    for (size_t i = 0; i < a.length(); i++) diff |= a[i] ^ b[i];
    return diff == 0;
}

static void sendMagicPacket() {
    uint8_t packet[102];
    memset(packet, 0xFF, 6);
    for (int i = 1; i <= 16; i++) memcpy(packet + i * 6, targetMac, 6);
    const IPAddress targets[] = {WiFi.broadcastIP(), IPAddress(255, 255, 255, 255)};
    for (int n = 0; n < 3; n++) {
        for (const IPAddress& target : targets) {
            udp.beginPacket(target, 9);
            udp.write(packet, sizeof packet);
            udp.endPacket();
        }
        delay(100);
    }
}

static void handleLine(String line) {
    line.trim();
    if (!line.startsWith("wake.")) return;          // keepalives y tamaños de chunk HTTP
    int dot = line.lastIndexOf('.');
    String payload = line.substring(0, dot);        // "wake.<ts>"
    String signature = line.substring(dot + 1);
    time_t ts = strtoll(payload.c_str() + 5, nullptr, 10);
    time_t now = time(nullptr);

    if (!constantTimeEquals(signature, digestHex(RELAY_KEY, payload))) {
        Serial.println("Orden rechazada: firma invalida");
    } else if (ts <= lastTs || labs((long)(now - ts)) > MAX_SKEW_S) {
        Serial.println("Orden rechazada: vieja o repetida");
    } else {
        lastTs = ts;
        sendMagicPacket();
        Serial.println("Orden aceptada: paquete magico enviado");
    }
}

static bool connectStream() {
    client.stop();
    if (!client.connect(NTFY_HOST, 443)) return false;
    // since= recupera las ordenes publicadas mientras estaba reconectando.
    client.printf("GET /%s/raw?since=%lds HTTP/1.1\r\nHost: %s\r\nUser-Agent: wol-relay\r\n\r\n",
                  topic.c_str(), MAX_SKEW_S, NTFY_HOST);
    lastByteAt = millis();
    Serial.println("Escuchando ordenes en ntfy.sh");
    return true;
}

void setup() {
    Serial.begin(115200);
    pinMode(LED_PIN, OUTPUT);
    digitalWrite(LED_PIN, HIGH);                    // encendido hasta que quede escuchando

    if (sscanf(TARGET_MAC, "%hhx:%hhx:%hhx:%hhx:%hhx:%hhx", &targetMac[0], &targetMac[1], &targetMac[2],
               &targetMac[3], &targetMac[4], &targetMac[5]) != 6) {
        Serial.println("TARGET_MAC invalida en secrets.h");
    }
    topic = "wol-" + digestHex(nullptr, String("topic:") + RELAY_KEY).substring(0, 32);

    WiFi.mode(WIFI_STA);
    WiFi.setAutoReconnect(true);
    WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
    Serial.printf("\nRele WoL. Conectando a la red %s", WIFI_SSID);
    while (WiFi.status() != WL_CONNECTED) {
        delay(500);
        Serial.print(".");
        if (millis() > 60000) ESP.restart();        // credenciales mal o red fuera de alcance: reintentar
    }
    Serial.printf("\nWiFi OK, IP %s, broadcast %s\n", WiFi.localIP().toString().c_str(),
                  WiFi.broadcastIP().toString().c_str());

    // La hora hace falta para validar el certificado TLS y la antigüedad de las órdenes.
    configTime(0, 0, "pool.ntp.org", "time.google.com");
    Serial.print("Sincronizando hora");
    while (time(nullptr) < 1700000000) {
        delay(500);
        Serial.print(".");
    }
    Serial.println(" OK");

    client.setCACert(NTFY_ROOT_CA);
    lastOkAt = millis();
}

void loop() {
    bool listening = WiFi.status() == WL_CONNECTED && client.connected() && millis() - lastByteAt < STALL_MS;
    digitalWrite(LED_PIN, !listening);

    if (!listening) {
        if (millis() - lastOkAt > RESTART_MS) ESP.restart();
        if (WiFi.status() != WL_CONNECTED || !connectStream()) {
            Serial.println("Sin conexion; reintento en 5 s");
            delay(5000);
            return;
        }
    }
    lastOkAt = millis();

    while (client.available()) {
        handleLine(client.readStringUntil('\n'));
        lastByteAt = millis();
    }
    delay(20);
}
