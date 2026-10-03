# Guía de instalación

Cómo dejar todo funcionando en tu propia red. Los valores de ejemplo (`192.168.1.50`, `AA:BB:CC:DD:EE:FF`, `100.x.y.z`) hay que reemplazarlos por los tuyos.

## Qué necesitás según lo que quieras hacer

| Querés… | Necesitás | Pasos |
|---|---|---|
| Encender la PC estando en casa | PC configurada + app | 1 y 2 |
| Ver el estado real y apagar, reiniciar, suspender o hibernar | + agente de Windows | 3 |
| Lo anterior desde afuera de casa | + Tailscale | 4 |
| Encender la PC desde afuera de casa | + relé ESP32 | 5 |

Cada paso se apoya en los anteriores, pero podés quedarte en el que te alcance.

**Requisitos generales:** PC con Windows 10/11 conectada **por cable Ethernet** al router, y un celular con Android 8.0 o superior. Para los scripts de diagnóstico (paso 1) y el relé (paso 5), descargá este repositorio (botón **Code → Download ZIP**, o `git clone`).

---

## 1. Preparar la PC para Wake on LAN

### 1.1 BIOS / UEFI
Entrá a la BIOS (normalmente `Supr` o `F2` al encender) y buscá estas opciones; los nombres cambian según el fabricante:

| Opción | Valor | Otros nombres |
|---|---|---|
| Wake on LAN | **Activado** | "Power On By PCI-E", "Resume by PCI-E Device", "PME Event Wake Up" |
| ErP / EuP | **Desactivado** | "ErP Ready", "EuP 2013", "Deep Sleep" |

Con ErP activado la placa de red se queda sin corriente al apagar y no puede recibir la orden.

### 1.2 Windows
En PowerShell **como administrador**:

```powershell
# Inicio rápido: con él activo, "Apagar" no apaga del todo y el WoL suele fallar
Set-ItemProperty -Path 'HKLM:\SYSTEM\CurrentControlSet\Control\Session Manager\Power' -Name HiberbootEnabled -Value 0

# Que la PC solo se despierte con el paquete mágico, no con cualquier tráfico de red
Set-NetAdapterAdvancedProperty -Name "Ethernet" -RegistryKeyword "*WakeOnPattern" -RegistryValue 0
```

Si tu adaptador no se llama `Ethernet`, mirá el nombre con `Get-NetAdapter -Physical`.

En el **Administrador de dispositivos → Adaptadores de red → (tu placa Ethernet) → Opciones avanzadas**, dejá en *Enabled*: **Wake on Magic Packet** y, si existe, **Shutdown Wake-On-Lan** (Realtek) o **Wake on Magic Packet from power off state** (Intel).

### 1.3 Verificar y anotar los datos
```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
.\tools\diagnostico-wol.ps1
```

El script no cambia nada: muestra la **MAC** y la **IP** de la placa Ethernet (anotalas) y avisa si quedó algo mal configurado.

### 1.4 Reservar la IP en el router
En la página de administración del router, asociá la MAC de la PC a su IP (suele llamarse "DHCP estático" o "reserva de IP"). Así la IP no cambia.

---

## 2. Instalar la app y encender en casa

1. Descargá `wol-vX.Y.Z.apk` desde [Releases](../../releases) e instalalo (hay que permitir la instalación desde orígenes desconocidos).
2. Al abrirla, completá:
   - **01 · NOMBRE:** el que quieras.
   - **02 · MAC:** la de la placa Ethernet de la PC.
   - **03 · IP EN CASA:** la IP de la PC en tu red (ej. `192.168.1.50`).
   - **05 · PUERTO WOL:** `9`.
3. Apagá la PC. Las luces del puerto de red deben seguir encendidas.
4. Con el celular en el **WiFi de tu casa** (no en la red de invitados), tocá **ENCENDER**.

Para probar el WoL sin la app, desde otra PC de la red: `.\tools\enviar-paquete-magico.ps1 -Mac "AA:BB:CC:DD:EE:FF"`.

---

## 3. Agente de Windows: estado y botones de energía

El agente es un servicio de Windows que escucha en el puerto **47800** y acepta solo pedidos con un token. Su regla de firewall admite únicamente tu red local y Tailscale.

1. Descargá `wol-agent-vX.Y.Z.zip` desde [Releases](../../releases) y descomprimilo. Trae el agente ya compilado: no hace falta instalar .NET.
2. Abrí PowerShell **como administrador** en la carpeta descomprimida:
   ```powershell
   Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
   .\install.ps1
   ```
   Lo instala como servicio con inicio automático, crea la regla de firewall y al final muestra el **token**.
3. En la app: **CONFIG → 06 · TOKEN DEL AGENTE**, pegá el token y guardá.

<details>
<summary>Alternativa: compilarlo desde el código</summary>

Instalá el SDK de .NET 10 (`winget install Microsoft.DotNet.SDK.10`), abrí una terminal **nueva** como administrador en la carpeta del repositorio y corré `.\agent-windows\install.ps1`. En ese caso los scripts de la tabla de abajo están en `agent-windows\`.
</details>

Con la PC encendida la app muestra `AGENTE OK` y los botones **APAGAR / REINICIAR / SUSPENDER / HIBERNAR**. Cada botón se confirma con un segundo toque.

Comandos útiles (como administrador):

| Para | Comando |
|---|---|
| Volver a ver el token | `& "$env:ProgramFiles\WolAgent\Wol.Agent.exe" token` |
| Probar el agente (no apaga nada) | `.\smoke-test.ps1` |
| Actualizar el agente | bajar el zip nuevo y volver a correr `.\install.ps1` |
| Desinstalarlo | `.\install.ps1 -Uninstall` |

---

## 4. Tailscale: controlar la PC desde afuera

Tailscale crea una red privada y cifrada entre tus dispositivos, sin abrir puertos en el router. Es gratis para uso personal.

1. Instalá Tailscale en la **PC** y en el **celular**, e iniciá sesión con **la misma cuenta** en los dos.
2. En la PC, como administrador, activá el modo desatendido para que conecte aunque nadie haya iniciado sesión en Windows:
   ```powershell
   tailscale set --unattended
   ```
3. Averiguá la IP de Tailscale de la PC:
   ```powershell
   tailscale ip -4
   ```
4. En la app: **CONFIG → 04 · IP DE TAILSCALE**, cargá esa IP (`100.x.y.z`).

La app consulta las dos direcciones a la vez y usa la que responda: en casa funciona sin Tailscale; afuera necesita Tailscale **activo en el celular**.

---

## 5. Relé ESP32: encender la PC desde afuera

El paquete mágico no viaja por Internet. El relé es una placa ESP32 que queda siempre encendida en tu casa: recibe la orden de la app a través de [ntfy.sh](https://ntfy.sh) y manda el paquete mágico dentro de tu red. Solo obedece órdenes firmadas con una clave que comparten la app y la placa.

**Hace falta:** una placa ESP32 DevKit (ESP32-WROOM-32), un cable USB de datos, un cargador USB y Python 3 instalado.

1. Instalá PlatformIO:
   ```powershell
   python -m pip install --user platformio
   ```
2. **En la PC que querés encender**, creá el archivo de configuración. Genera una clave al azar y detecta la MAC de esa PC:
   ```powershell
   cd relay-esp32
   .\crear-secretos.ps1
   ```
3. Abrí `include\secrets.h` y completá `WIFI_SSID` y `WIFI_PASSWORD`. Tiene que ser una red de **2.4 GHz**: el ESP32 no usa 5 GHz. Si corriste el script en otra PC, corregí también `TARGET_MAC`.
4. Conectá la placa por USB y grabala:
   ```powershell
   python -m platformio run -t upload
   ```
   Si se queda en `Connecting....`, mantené apretado el botón **BOOT** de la placa hasta que empiece a grabar. Si no aparece ningún puerto, falta el driver del chip USB (CP210x o CH340).
5. Mirá el arranque (`Ctrl+C` para salir):
   ```powershell
   python -m platformio device monitor -b 115200
   ```
   Tiene que mostrar `WiFi OK`, `Sincronizando hora... OK` y `Escuchando ordenes en ntfy.sh`.
6. En la app: **CONFIG → 07 · CLAVE DEL RELÉ**, cargá el valor de `RELAY_KEY` que está en `include\secrets.h`.
7. Pasá la placa a un cargador USB, en un lugar con buena señal WiFi. **No la alimentes desde la PC que querés encender:** muchas placas madre cortan los USB al apagarse.

**LED azul apagado** = el relé está conectado y escuchando. **Encendido** = está arrancando o no logra conectarse (WiFi o Internet). La luz roja de la placa solo indica que tiene corriente y no se puede apagar por programa. Con la PC apagada y el celular en datos móviles, tocá **ENCENDER**: debería decir "Orden enviada al relé" y la PC arrancar en unos segundos.

`include\secrets.h` tiene la contraseña de tu WiFi y la clave del relé: no lo compartas ni lo subas a ningún repositorio (ya está excluido de git).

Para otra placa (ESP32-C3, S3…), cambiá `board` en `platformio.ini`.

---

## Campos de la configuración de la app

| Campo | Qué va | Obligatorio |
|---|---|---|
| 01 · NOMBRE | Nombre para mostrar | No |
| 02 · MAC | MAC de la placa Ethernet de la PC | Sí |
| 03 · IP EN CASA | IP de la PC en tu red local | Sí |
| 04 · IP DE TAILSCALE | IP `100.x.y.z` de la PC (paso 4) | No |
| 05 · PUERTO WOL | `9` (o `7`) | Sí |
| 06 · TOKEN DEL AGENTE | Token que muestra el agente (paso 3) | No |
| 07 · CLAVE DEL RELÉ | `RELAY_KEY` del relé (paso 5) | No |

El token y la clave se guardan cifrados en el Android Keystore.

---

## Problemas frecuentes

| Síntoma | Causa probable |
|---|---|
| Despierta de suspensión pero no enciende desde apagado | Inicio rápido activo, o falta "Shutdown Wake-On-Lan" en la placa de red |
| Las luces del puerto de red se apagan con la PC apagada | ErP activado o Wake on LAN desactivado en la BIOS |
| La PC se enciende o despierta sola | "Wake on pattern match" activado (paso 1.2) |
| ENCENDER no hace nada desde el celular, pero sí desde otra PC | El celular está en la red de invitados o el router aísla los clientes WiFi |
| Dejó de encender después de un corte de luz | Muchas placas pierden el estado de WoL: encendela a mano una vez |
| La app muestra ON pero dice "EL AGENTE NO RESPONDE" | El servicio `WolAgent` no está corriendo, o el firewall bloquea el puerto 47800 |
| "TOKEN INVÁLIDO" | El token de la app no coincide: volvé a mirarlo con `Wol.Agent.exe token` |
| Desde afuera figura OFF aunque la PC esté encendida | Tailscale apagado en el celular, o sin modo desatendido en la PC (paso 4) |
| El relé no enciende la PC | LED azul encendido (sin WiFi o sin Internet), clave distinta en la app, o `TARGET_MAC` equivocada |
| La hora `ACT.` de la app no avanza | La app dejó de consultar: cerrala y abrila, y reportalo |

---

## Seguridad: qué conviene saber

- **No se abre ningún puerto en el router.** El agente solo acepta conexiones de la red local y de Tailscale; el relé solo hace conexiones salientes.
- **El agente no ejecuta comandos arbitrarios:** solo las acciones de una lista fija (apagar, reiniciar, suspender, hibernar, cancelar).
- **Entre la app y el agente se usa HTTP sin cifrar.** Dentro de tu red local el token viaja en claro; por Tailscale va cifrado. Usalo en redes de confianza.
- **El relé depende de ntfy.sh,** un servicio gratuito de terceros. Si no está disponible no se puede encender desde afuera (en casa sigue funcionando). Quien descubriera el canal podría ver o bloquear órdenes, pero no encender la PC: le falta la clave. Las órdenes vencen a los 90 segundos y no se pueden repetir.
