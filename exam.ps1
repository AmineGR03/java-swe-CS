# Starts the exam environment.
#
#   .\exam.ps1                      real clock (Part A 70 min, Part B 90 min)
#   .\exam.ps1 -Port 9000           serve somewhere else
#   .\exam.ps1 -DemoSeconds 120     short clock, for trying the environment out
#
param(
    [int]$Port = 8711,
    [int]$DemoSeconds = 0
)

$ErrorActionPreference = 'Stop'

if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    Write-Host "java not found on PATH. Install a JDK 17 or newer." -ForegroundColor Red
    exit 2
}

Push-Location $PSScriptRoot
try {
    $jargs = @('exam/ExamServer.java', '--port', $Port)
    if ($DemoSeconds -gt 0) { $jargs += @('--demo-seconds', $DemoSeconds) }

    Write-Host "Starting the exam environment on http://localhost:$Port" -ForegroundColor Cyan
    Write-Host "Open that address in a browser. Ctrl+C here stops the server." -ForegroundColor DarkGray
    & java @jargs
    exit $LASTEXITCODE
}
finally {
    Pop-Location
}
