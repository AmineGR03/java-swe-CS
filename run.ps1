# Grades the assessment.
#
#   .\run.ps1                 grade your code in src/
#   .\run.ps1 task3           grade one section
#   .\run.ps1 -Quiet          failures and totals only
#   .\run.ps1 -Reference      grade the reference solutions instead of src/
#
param(
    [switch]$Reference,
    [switch]$Quiet,
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$Sections
)

$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot

if (-not (Get-Command javac -ErrorAction SilentlyContinue)) {
    Write-Host "javac not found on PATH. Install a JDK 17 or newer." -ForegroundColor Red
    exit 2
}

$sourceDir = if ($Reference) { 'solutions' } else { 'src' }
$outDir = Join-Path $root 'out'

if (Test-Path $outDir) { Remove-Item -Recurse -Force $outDir }
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

Push-Location $root
try {
    $files = Get-ChildItem -Path $sourceDir, 'tests' -Recurse -Filter *.java |
        Resolve-Path -Relative

    Write-Host "Compiling $($files.Count) files from $sourceDir/ ..." -ForegroundColor DarkGray
    & javac -d out -encoding UTF-8 -nowarn @files
    if ($LASTEXITCODE -ne 0) {
        Write-Host ""
        Write-Host "Compilation failed - fix the errors above, then run again." -ForegroundColor Red
        exit 1
    }

    $runnerArgs = @()
    if ($Sections) { $runnerArgs += $Sections }
    if ($Quiet) { $runnerArgs += '--quiet' }

    & java -cp out harness.Runner @runnerArgs
    exit $LASTEXITCODE
}
finally {
    Pop-Location
}
