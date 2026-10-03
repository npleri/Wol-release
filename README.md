# WoL

App Android para **encender una PC con Wake on LAN** y ver si está prendida. Estilo visual monocromo con tipografía de matriz de puntos.

## Instalar

Descargar `wol-vX.Y.Z.apk` desde [Releases](../../releases) e instalarlo (Android 8.0 o superior; hay que permitir la instalación desde orígenes desconocidos). Cada release incluye el `.sha256` para verificar el archivo.

## Uso

1. En la PC: activar Wake on LAN en la BIOS y en la placa de red, y desactivar el Inicio rápido de Windows.
2. En la app: cargar el nombre, la **MAC** de la placa Ethernet, la **IP** de la PC y el puerto (9 por defecto).
3. Con el celular en la **misma red WiFi** que la PC, tocar **ENCENDER**.

El estado (ON / OFF) se obtiene probando si la PC responde en puertos habituales de Windows (445, 135, 139, 3389). Si el firewall los bloquea, la PC puede figurar como OFF aunque esté encendida.

**Limitación:** el paquete mágico no cruza Internet. Para encender desde afuera hace falta un equipo siempre encendido en la red de la casa (router con WoL, ESP32, Raspberry Pi).

## Compilar

Requiere JDK 17+ y el SDK de Android (API 36).

```bash
./gradlew testDebugUnitTest assembleDebug
```

El APK queda en `app/build/outputs/apk/debug/`. Para un release firmado, definir `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` y `KEY_PASSWORD` y correr `./gradlew assembleRelease`.

## Licencia

Código bajo [MIT](LICENSE). Tipografías Doto, Space Grotesk y Space Mono bajo SIL Open Font License 1.1 ([`licenses/`](licenses/)).
