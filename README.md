# WoL

Control remoto de una PC con Windows desde Android: **encenderla con Wake on LAN**, ver si está prendida y **apagarla, reiniciarla, suspenderla o hibernarla**, en casa o desde afuera. Estilo visual monocromo con tipografía de matriz de puntos.

**Para configurarlo en tu red, seguí la [Guía de instalación](GUIA.md).**

## Qué hay en este repositorio

| Carpeta | Qué es | Para qué sirve |
|---|---|---|
| raíz (`app/`, Gradle) | App Android (Kotlin + Jetpack Compose) | Encender la PC y controlarla |
| `agent-windows/` | Agente: servicio de Windows (.NET 10) | Informar el estado y ejecutar las acciones de energía |
| `relay-esp32/` | Firmware para ESP32 (PlatformIO) | Encender la PC cuando estás fuera de casa |
| `tools/` | Scripts de PowerShell | Diagnosticar y probar el Wake on LAN |

Solo la app es imprescindible: con ella y la PC bien configurada ya se puede encender desde la red de casa. El agente, Tailscale y el relé suman funciones (ver la guía).

## Instalar la app

Descargar `wol-vX.Y.Z.apk` desde [Releases](../../releases) e instalarlo (Android 8.0 o superior; hay que permitir la instalación desde orígenes desconocidos). Cada release incluye el `.sha256` para verificar el archivo.

## Cómo funciona

- **Encender en casa:** la app manda el paquete mágico por broadcast a la red WiFi.
- **Encender desde afuera:** el paquete mágico no cruza Internet. La app publica una orden firmada (HMAC-SHA256) en ntfy.sh; el relé ESP32, que está en la red de la casa, la recibe y manda el paquete mágico.
- **Estado y energía:** la app habla con el agente (puerto 47800, autenticado con token) por la red local o por Tailscale. Sin agente, estima el estado probando puertos habituales de Windows.

## Compilar la app

Requiere JDK 17+ y el SDK de Android (API 36).

```bash
./gradlew testDebugUnitTest assembleDebug
```

El APK queda en `app/build/outputs/apk/debug/`. Para un release firmado, definir `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` y `KEY_PASSWORD` y correr `./gradlew assembleRelease`.

## Licencia

Código bajo [MIT](LICENSE). Tipografías Doto, Space Grotesk y Space Mono bajo SIL Open Font License 1.1 ([`licenses/`](licenses/)).
