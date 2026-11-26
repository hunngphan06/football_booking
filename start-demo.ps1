param(
    [string]$MavenRepository = '',
    [ValidateRange(1024, 65535)][int]$Port = 8085,
    [switch]$SaveGroqKey
)

$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
$previousPort = $env:BOOKING_DEMO_PORT
$previousGroqKey = $env:GROQ_API_KEY
$groqKeyFile = Join-Path $env:LOCALAPPDATA 'EventBooking\groq-key.xml'
try {
    if (!$SaveGroqKey -and [string]::IsNullOrWhiteSpace($env:GROQ_API_KEY) -and
            (Test-Path -LiteralPath $groqKeyFile -PathType Leaf)) {
        try {
            $savedKey = Import-Clixml -LiteralPath $groqKeyFile
            $env:GROQ_API_KEY = $savedKey.GetNetworkCredential().Password.Trim()
        } catch {
            Write-Warning 'Could not read the saved Groq key. Use -SaveGroqKey to replace it.'
        }
    }
    if ($SaveGroqKey -or [string]::IsNullOrWhiteSpace($env:GROQ_API_KEY)) {
        $secret = Read-Host 'Groq API key (input is hidden)' -AsSecureString
        $credential = [pscredential]::new('groq', $secret)
        $env:GROQ_API_KEY = $credential.GetNetworkCredential().Password.Trim()
    }
    if ([string]::IsNullOrWhiteSpace($env:GROQ_API_KEY)) { throw 'Enter a Groq API key.' }
    if ($SaveGroqKey) {
        New-Item -ItemType Directory -Path (Split-Path -Parent $groqKeyFile) -Force | Out-Null
        $credential | Export-Clixml -LiteralPath $groqKeyFile
        Write-Host 'Groq API key saved for this Windows account.'
        return
    }
    $env:BOOKING_DEMO_PORT = $Port
    Write-Host 'AI provider: Groq'
    $arguments = @('-Pdemo', 'spring-boot:run', '-Dspring-boot.run.profiles=demo')
    if ($MavenRepository) { $arguments += "-Dmaven.repo.local=$MavenRepository" }
    & .\mvnw.cmd @arguments
    if ($LASTEXITCODE -ne 0) { throw 'Spring Boot demo failed. Read the error above.' }
} finally {
    $env:BOOKING_DEMO_PORT = $previousPort
    $env:GROQ_API_KEY = $previousGroqKey
}
