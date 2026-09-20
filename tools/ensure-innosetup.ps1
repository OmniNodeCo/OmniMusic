# Makes sure Inno Setup is reachable before packaging for Windows.
#
# jpackage's `--type exe` (Compose's TargetFormat.Exe) shells out to Inno Setup's ISCC to build the
# installer, and fails if it cannot find it. The GitHub Windows runner images usually ship it; this
# installs it when they do not.
#
# The directory is published through GITHUB_PATH rather than $env:Path, because Gradle runs in a
# later step and inherits the former.

$ErrorActionPreference = 'Stop'

if (Get-Command ISCC -ErrorAction SilentlyContinue) {
    Write-Host "Inno Setup: $((Get-Command ISCC).Source)"
    exit 0
}

Write-Host "ISCC not on PATH, installing Inno Setup"
choco install innosetup -y --no-progress

$installDir = "C:\Program Files (x86)\Inno Setup 6"
if (Test-Path $env:GITHUB_PATH) { Add-Content $env:GITHUB_PATH $installDir }
$env:Path = [Environment]::GetEnvironmentVariable('Path', 'Machine') + ";$env:Path"

if (-not (Get-Command ISCC -ErrorAction SilentlyContinue)) {
    throw "Inno Setup was installed but ISCC is still not on PATH (looked in $installDir)"
}
Write-Host "Inno Setup: $((Get-Command ISCC).Source)"
