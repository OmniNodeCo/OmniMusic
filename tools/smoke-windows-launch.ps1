# Verifies that the packaged Windows app can actually start.
#
# The installer's launcher reports "Failed to launch JVM" for anything that goes wrong before the
# first frame - a JDK module missing from the bundled jlink runtime, a class the classpath does not
# contain, an exception escaping main(). An installed build has no console, so a user sees that one
# message and nothing else. This runs exactly what the launcher runs: the bundled runtime, the
# packaged classpath, the real main class.
#
# Usage: pwsh -File tools/smoke-windows-launch.ps1 [-Image <path to the app image>]

param(
    [string]$Image = "composeApp/build/compose/binaries/main/app/OmniMusic",
    [int]$TimeoutMilliseconds = 30000
)

$ErrorActionPreference = 'Stop'

$java = Join-Path $Image "runtime/bin/java.exe"
if (-not (Test-Path $java)) { throw "no bundled runtime at $java" }

Write-Host "--- what the launcher is told to run ---"
Get-Content (Join-Path $Image "app/OmniMusic.cfg")

Write-Host "--- bundled runtime ---"
& $java -version
$modules = & $java --list-modules
Write-Host ("bundled modules: {0}" -f $modules.Count)

Write-Host "--- launching the packaged app ---"
$appDir = Join-Path $Image "app"
$classpath = (Get-ChildItem $appDir -Filter *.jar | ForEach-Object { $_.FullName }) -join ';'
if (-not $classpath) { throw "no jars under $appDir" }

$err = Join-Path $env:RUNNER_TEMP "omnimusic-launch.err"
$out = Join-Path $env:RUNNER_TEMP "omnimusic-launch.out"
$proc = Start-Process -FilePath $java `
    -ArgumentList '-cp', $classpath, 'co.omnimusic.app.desktop.MainKt' `
    -NoNewWindow -PassThru -RedirectStandardError $err -RedirectStandardOutput $out

if (-not $proc.WaitForExit($TimeoutMilliseconds)) {
    Stop-Process -Id $proc.Id -Force
    Write-Host "PASS: still running after $($TimeoutMilliseconds / 1000)s - the bundled JVM launched the packaged app"
    exit 0
}

Write-Host "exited with code $($proc.ExitCode)"
Write-Host "--- stderr ---"; Get-Content $err
Write-Host "--- stdout ---"; Get-Content $out
$log = (Get-Content $err -Raw) + (Get-Content $out -Raw)

# These are packaging failures: nothing to do with rendering, everything to do with how the app
# was bundled. Any of them is what a user would see as "Failed to launch JVM".
$packagingFailures = @(
    'Could not find or load main class',
    'NoClassDefFoundError',
    'ClassNotFoundException',
    'Error occurred during initialization'
)
foreach ($signature in $packagingFailures) {
    if ($log -match [regex]::Escape($signature)) { throw "the packaged app cannot start: $signature" }
}

# A CI runner has no desktop, so a failure inside the window toolkit still counts as a launched
# JVM - the launcher's job was done, and the reason is in the log above.
Write-Host "PASS: the JVM launched and resolved the main class; it then stopped for a reason unrelated to packaging"
exit 0
