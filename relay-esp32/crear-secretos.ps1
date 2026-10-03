<#
.SYNOPSIS
    Crea include/secrets.h para el rele: clave aleatoria y MAC de esta PC.
    Despues hay que completar a mano WIFI_SSID y WIFI_PASSWORD.

.DESCRIPTION
    El archivo no se versiona. No pisa uno existente (para rotar la clave,
    borrarlo antes; despues hay que volver a grabar la placa y recargar la
    clave en la app). No muestra la clave en pantalla.
#>
$path = Join-Path $PSScriptRoot "include\secrets.h"
if (Test-Path $path) { throw "Ya existe $path; borrarlo para generar una clave nueva." }

$bytes = New-Object byte[] 16
[Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
$key = -join ($bytes | ForEach-Object { $_.ToString("x2") })
$mac = (Get-NetAdapter -Physical | Where-Object { $_.MediaType -eq '802.3' -and $_.Status -eq 'Up' } |
    Select-Object -First 1).MacAddress -replace '-', ':'

@"
// Generado por crear-secretos.ps1. NO subir a ningun repo.
#pragma once

#define WIFI_SSID "COMPLETAR"        // red de 2.4 GHz (el ESP32 no usa 5 GHz)
#define WIFI_PASSWORD "COMPLETAR"
#define RELAY_KEY "$key"  // cargar esta misma clave en la app (Config -> 07)
#define TARGET_MAC "$mac"       // MAC Ethernet de la PC a encender
"@ | Set-Content -Path $path -Encoding ascii

Write-Host "Creado $path (MAC $mac). Completar WIFI_SSID y WIFI_PASSWORD."
