#Requires -RunAsAdministrator
<#
.SYNOPSIS
    Instala el agente WoL como servicio de Windows (o lo desinstala con -Uninstall).

.DESCRIPTION
    Instala el agente en %ProgramFiles%\WolAgent, crea el servicio "WolAgent"
    (LocalSystem, inicio automatico, se reinicia si falla) y una regla de
    firewall para el puerto 47800 limitada a la red local y a Tailscale
    (100.64.0.0/10). Al final muestra el token para cargarlo en la app.
    Volver a ejecutarlo actualiza el agente.

    Si al lado del script hay una carpeta bin\ con Wol.Agent.exe (el zip de
    Releases), instala ese ejecutable y no hace falta tener .NET. Si no, lo
    compila desde el codigo, y para eso requiere el SDK de .NET 10.

.EXAMPLE
    .\agent-windows\install.ps1
    .\agent-windows\install.ps1 -Uninstall
#>
param([switch]$Uninstall)

$ErrorActionPreference = "Stop"
$name = "WolAgent"
$port = 47800
$dir = Join-Path $env:ProgramFiles "WolAgent"
$exe = Join-Path $dir "Wol.Agent.exe"

if (Get-Service $name -ErrorAction SilentlyContinue) {
    Stop-Service $name
    sc.exe delete $name | Out-Null
    Start-Sleep -Seconds 1
}
Get-NetFirewallRule -DisplayName "WoL Agent" -ErrorAction SilentlyContinue | Remove-NetFirewallRule

if ($Uninstall) {
    Remove-Item $dir -Recurse -Force -ErrorAction SilentlyContinue
    Write-Host "Agente desinstalado. El token queda en $env:ProgramData\WolAgent (borrarlo para generar uno nuevo)."
    return
}

$prebuilt = Join-Path $PSScriptRoot "bin"
if (Test-Path (Join-Path $prebuilt "Wol.Agent.exe")) {
    New-Item -ItemType Directory -Force $dir | Out-Null
    Copy-Item (Join-Path $prebuilt "*") $dir -Recurse -Force
    Get-ChildItem $dir -Recurse | Unblock-File      # quita la marca de "descargado de Internet"
}
else {
    dotnet publish (Join-Path $PSScriptRoot "Wol.Agent") -c Release -o $dir
    if ($LASTEXITCODE -ne 0) { throw "Fallo la compilacion del agente" }
}

New-Service -Name $name -BinaryPathName "`"$exe`"" -DisplayName "WoL Agent" `
    -Description "Agente para controlar la PC desde la app WoL (puerto $port)" -StartupType Automatic | Out-Null
sc.exe failure $name reset= 86400 actions= restart/5000/restart/5000/restart/60000 | Out-Null
New-NetFirewallRule -DisplayName "WoL Agent" -Direction Inbound -Action Allow -Protocol TCP -LocalPort $port `
    -RemoteAddress LocalSubnet, 100.64.0.0/10 -Program $exe | Out-Null
Start-Service $name

Write-Host ""
Write-Host "Agente instalado y en marcha en el puerto $port." -ForegroundColor Green
Write-Host "Token para la app (Config -> 06 TOKEN DEL AGENTE):"
& $exe token
