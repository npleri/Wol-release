<#
.SYNOPSIS
    Revisa la configuracion de Windows relevante para Wake on LAN.

.DESCRIPTION
    Muestra los datos de las placas Ethernet (MAC, IP, broadcast), la
    administracion de energia, las opciones avanzadas de WoL del driver, el
    estado del Inicio rapido y si la placa puede despertar el equipo.
    No modifica nada. Ejecutar como administrador para ver todos los datos.

.EXAMPLE
    Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
    .\diagnostico-wol.ps1
#>

$ErrorActionPreference = "Continue"
$warnings = New-Object System.Collections.Generic.List[string]

function Write-Section([string]$title) {
    Write-Host ""
    Write-Host ("=== {0} ===" -f $title) -ForegroundColor Cyan
}

function Get-BroadcastAddress([string]$ip, [int]$prefix) {
    $ipBytes = [System.Net.IPAddress]::Parse($ip).GetAddressBytes()
    [Array]::Reverse($ipBytes)
    $ipInt = [BitConverter]::ToUInt32($ipBytes, 0)
    $hostBits = [uint32]([math]::Pow(2, 32 - $prefix) - 1)
    $bcBytes = [BitConverter]::GetBytes([uint32]($ipInt -bor $hostBits))
    [Array]::Reverse($bcBytes)
    return ([System.Net.IPAddress]::new($bcBytes)).ToString()
}

$isAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $isAdmin) {
    Write-Host "Aviso: no se esta ejecutando como administrador; algunos datos pueden faltar." -ForegroundColor Yellow
}

# --- Equipo -----------------------------------------------------------------
Write-Section "Equipo"
$board = Get-CimInstance Win32_BaseBoard
$os = Get-CimInstance Win32_OperatingSystem
Write-Host ("Placa madre : {0} {1}" -f $board.Manufacturer, $board.Product)
Write-Host ("Windows     : {0} (build {1})" -f $os.Caption, $os.BuildNumber)

# --- Inicio rapido ----------------------------------------------------------
Write-Section "Inicio rapido (Fast Startup)"
$hiberboot = (Get-ItemProperty -Path 'HKLM:\SYSTEM\CurrentControlSet\Control\Session Manager\Power' -Name HiberbootEnabled -ErrorAction SilentlyContinue).HiberbootEnabled
$hibernate = (Get-ItemProperty -Path 'HKLM:\SYSTEM\CurrentControlSet\Control\Power' -Name HibernateEnabled -ErrorAction SilentlyContinue).HibernateEnabled
Write-Host ("HiberbootEnabled : {0}" -f $hiberboot)
Write-Host ("HibernateEnabled : {0}" -f $hibernate)
if ($hiberboot -eq 1 -and $hibernate -ne 0) {
    Write-Host "-> Inicio rapido ACTIVO. Desactivarlo (ver GUIA.md, paso 1.2)." -ForegroundColor Red
    $warnings.Add("Inicio rapido activo")
}
else {
    Write-Host "-> Inicio rapido desactivado. OK" -ForegroundColor Green
}

# --- Dispositivos que pueden despertar el equipo ----------------------------
$wakeArmed = @(powercfg /devicequery wake_armed 2>$null | Where-Object { $_ -and $_.Trim() })

# --- Placas Ethernet --------------------------------------------------------
$adapters = @(Get-NetAdapter -Physical -ErrorAction SilentlyContinue | Where-Object { $_.MediaType -eq '802.3' })
if ($adapters.Count -eq 0) {
    Write-Section "Placas Ethernet"
    Write-Host "No se encontraron placas Ethernet fisicas." -ForegroundColor Red
    $warnings.Add("Sin placa Ethernet fisica")
}

foreach ($a in $adapters) {
    Write-Section ("Placa: {0}" -f $a.Name)
    Write-Host ("Descripcion : {0}" -f $a.InterfaceDescription)
    Write-Host ("MAC         : {0}" -f ($a.MacAddress -replace '-', ':'))
    Write-Host ("Estado      : {0} ({1})" -f $a.Status, $a.LinkSpeed)
    Write-Host ("Driver      : {0} {1}" -f $a.DriverProvider, $a.DriverVersion)

    $ips = @(Get-NetIPAddress -InterfaceIndex $a.ifIndex -AddressFamily IPv4 -ErrorAction SilentlyContinue)
    foreach ($ip in $ips) {
        $bc = Get-BroadcastAddress $ip.IPAddress $ip.PrefixLength
        Write-Host ("IPv4        : {0}/{1}  (broadcast {2}, origen {3})" -f $ip.IPAddress, $ip.PrefixLength, $bc, $ip.PrefixOrigin)
    }

    Write-Host ""
    Write-Host "Administracion de energia:"
    $pm = Get-NetAdapterPowerManagement -Name $a.Name -ErrorAction SilentlyContinue
    if ($pm) {
        Write-Host ("  WakeOnMagicPacket : {0}" -f $pm.WakeOnMagicPacket)
        Write-Host ("  WakeOnPattern     : {0}" -f $pm.WakeOnPattern)
        if ("$($pm.WakeOnMagicPacket)" -ne "Enabled") {
            $warnings.Add("$($a.Name): WakeOnMagicPacket no esta habilitado")
        }
    }
    else {
        Write-Host "  (no disponible)"
    }

    $armed = $wakeArmed | Where-Object { $_.Trim() -eq $a.InterfaceDescription }
    if ($armed) {
        Write-Host "  Puede despertar el equipo (wake_armed): SI" -ForegroundColor Green
    }
    else {
        Write-Host "  Puede despertar el equipo (wake_armed): NO" -ForegroundColor Red
        $warnings.Add("$($a.Name): no figura en 'powercfg /devicequery wake_armed'")
    }

    Write-Host ""
    Write-Host "Opciones avanzadas del driver relacionadas (WoL / ahorro de energia):"
    $adv = @(Get-NetAdapterAdvancedProperty -Name $a.Name -ErrorAction SilentlyContinue |
        Where-Object { $_.RegistryKeyword -match 'Wake|WOL|PME|EEE|Green|PowerSaving|S5' })
    if ($adv.Count -eq 0) {
        Write-Host "  (ninguna encontrada)"
    }
    foreach ($p in $adv) {
        Write-Host ("  {0,-45} = {1,-12} [{2}]" -f $p.DisplayName, $p.DisplayValue, $p.RegistryKeyword)
    }
}

# --- Resumen ----------------------------------------------------------------
Write-Section "Resumen"
if ($warnings.Count -eq 0) {
    Write-Host "Sin advertencias en Windows. Revisar igualmente la BIOS (WoL activado, ErP desactivado)." -ForegroundColor Green
}
else {
    foreach ($w in $warnings) { Write-Host ("- {0}" -f $w) -ForegroundColor Yellow }
    Write-Host ""
    Write-Host "Ver GUIA.md, paso 1, para corregirlas."
}
Write-Host "Recordatorio: este script no puede ver la BIOS (Wake on LAN / ErP)."
