<#
.SYNOPSIS
    Envia un paquete magico de Wake on LAN.

.DESCRIPTION
    Construye el paquete magico (6 x FF + MAC x 16 = 102 bytes) y lo envia por
    UDP broadcast. Sirve para probar WoL desde otra PC de la red sin instalar apps.
    Compatible con Windows PowerShell 5.1 y PowerShell 7+.

.EXAMPLE
    .\enviar-paquete-magico.ps1 -Mac "AA:BB:CC:DD:EE:FF"

.EXAMPLE
    .\enviar-paquete-magico.ps1 -Mac "AA-BB-CC-DD-EE-FF" -Broadcast 192.168.1.255 -Port 7
#>
param(
    [Parameter(Mandatory = $true)]
    [string]$Mac,

    # Broadcast de la subred (ej. 192.168.1.255) o global.
    [string]$Broadcast = "255.255.255.255",

    [ValidateRange(1, 65535)]
    [int]$Port = 9,

    # Cantidad de envios (mas de uno mejora la fiabilidad).
    [ValidateRange(1, 20)]
    [int]$Repeat = 3
)

$ErrorActionPreference = "Stop"

$clean = $Mac -replace '[:\-\.\s]', ''
if ($clean -notmatch '^[0-9A-Fa-f]{12}$') {
    throw "MAC invalida: '$Mac'. Formato esperado: AA:BB:CC:DD:EE:FF"
}

$macBytes = [byte[]](0..5 | ForEach-Object { [Convert]::ToByte($clean.Substring($_ * 2, 2), 16) })

$packet = New-Object System.Collections.Generic.List[byte]
for ($i = 0; $i -lt 6; $i++) { $packet.Add([byte]0xFF) }
for ($i = 0; $i -lt 16; $i++) { $packet.AddRange($macBytes) }
$bytes = $packet.ToArray()

$endpoint = New-Object System.Net.IPEndPoint ([System.Net.IPAddress]::Parse($Broadcast)), $Port
$udp = New-Object System.Net.Sockets.UdpClient
try {
    $udp.EnableBroadcast = $true
    for ($i = 1; $i -le $Repeat; $i++) {
        [void]$udp.Send($bytes, $bytes.Length, $endpoint)
        Start-Sleep -Milliseconds 100
    }
}
finally {
    $udp.Close()
}

Write-Host ("Paquete magico enviado {0} vez/veces a {1}:{2} para la MAC {3} ({4} bytes)." -f $Repeat, $Broadcast, $Port, (($macBytes | ForEach-Object { $_.ToString('X2') }) -join ':'), $bytes.Length)
