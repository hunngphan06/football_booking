param(
    [string]$MavenRepository = '',
    [ValidateRange(1024, 65535)][int]$Port = 8085,
    [ValidateRange(1024, 65535)][int]$AiPort = 18089
)

$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
if ($Port -eq $AiPort) { throw 'The demo and AI service must use different ports.' }
$previousPort = $env:BOOKING_DEMO_PORT
$previousAiPort = $env:BOOKING_DEMO_AI_PORT
$env:BOOKING_DEMO_PORT = $Port
$env:BOOKING_DEMO_AI_PORT = $AiPort
$node = (Get-Command node -ErrorAction Stop).Source
$script = Join-Path $PSScriptRoot 'tools/demo-ai.cjs'
$provider = $null
try {
    $provider = Start-Process -FilePath $node -ArgumentList ('"' + $script + '" ' + $AiPort) -WindowStyle Hidden -PassThru
    $ready = $false
    for ($attempt = 0; $attempt -lt 20; $attempt++) {
        if ($provider.HasExited) { throw "Demo AI could not start. Check port $AiPort." }
        try {
            $response = Invoke-WebRequest "http://127.0.0.1:$AiPort/health" -TimeoutSec 1
            if ($response.StatusCode -eq 200) { $ready = $true; break }
        } catch { Start-Sleep -Milliseconds 150 }
    }
    if (!$ready) { throw 'Demo AI did not become ready' }
    $arguments = @('-Pdemo', 'spring-boot:run', '-Dspring-boot.run.profiles=demo')
    if ($MavenRepository) { $arguments += "-Dmaven.repo.local=$MavenRepository" }
    & .\mvnw.cmd @arguments
    if ($LASTEXITCODE -ne 0) { throw 'Spring Boot demo failed. Read the error above.' }
} finally {
    if ($provider -and !$provider.HasExited) { Stop-Process -Id $provider.Id }
    $env:BOOKING_DEMO_PORT = $previousPort
    $env:BOOKING_DEMO_AI_PORT = $previousAiPort
}
