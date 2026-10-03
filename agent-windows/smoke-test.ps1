<#
.SYNOPSIS
    Prueba rapida de un agente en marcha: autenticacion, /status y respuestas de error.
    No apaga ni suspende nada (solo prueba "cancel" sin apagado pendiente).

    Sin -Token, lo lee del agente instalado (requiere administrador).

.EXAMPLE
    .\agent-windows\smoke-test.ps1
    .\agent-windows\smoke-test.ps1 -Token a1b2-c3d4-...
    .\agent-windows\smoke-test.ps1 -Token ... -Url http://localhost:47801
#>
param(
    [string]$Token,
    [string]$Url = "http://localhost:47800"
)

function Get-Code([string]$Method, [string]$Path, [hashtable]$Headers) {
    $params = @{ Uri = "$Url/api/v1/$Path"; Method = $Method; Headers = $Headers; UseBasicParsing = $true }
    if ($Method -eq "POST") { $params.Body = "{}"; $params.ContentType = "application/json" }
    try { (Invoke-WebRequest @params).StatusCode }
    catch { [int]$_.Exception.Response.StatusCode }
}

if (-not $Token) {
    $Token = & (Join-Path $env:ProgramFiles "WolAgent\Wol.Agent.exe") token
    if ($LASTEXITCODE -ne 0) { throw "No se pudo leer el token: ejecutar como administrador o pasar -Token" }
}
$plain = $Token -replace '[^0-9a-fA-F]', ''
$checks = @(
    @("GET /status sin token", (Get-Code GET status @{}), 401),
    @("GET /status token incorrecto", (Get-Code GET status @{ Authorization = "Bearer 0000" }), 401),
    @("GET /status con token", (Get-Code GET status @{ Authorization = "Bearer $plain" }), 200),
    @("GET /status token con guiones", (Get-Code GET status @{ Authorization = "Bearer $Token" }), 200),
    @("POST accion desconocida", (Get-Code POST power/format-c @{ Authorization = "Bearer $plain" }), 404),
    @("POST cancel sin apagado pendiente", (Get-Code POST power/cancel @{ Authorization = "Bearer $plain" }), 409)
)

$failed = 0
foreach ($c in $checks) {
    $ok = $c[1] -eq $c[2]
    if (-not $ok) { $failed++ }
    Write-Host ("{0} {1,-36} -> {2} (esperado {3})" -f ($(if ($ok) { "OK  " } else { "FALLA" })), $c[0], $c[1], $c[2])
}
(Invoke-WebRequest "$Url/api/v1/status" -Headers @{ Authorization = "Bearer $plain" } -UseBasicParsing).Content
exit $failed
