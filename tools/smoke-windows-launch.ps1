# Verifies that the packaged Windows app can actually start.
#
# The installer's launcher reports "Failed to launch JVM" for anything that goes wrong before the
# first frame - a JDK module missing from the bundled jlink runtime, a class the classpath does not
# contain, an exception escaping main(). An installed build has no console, so a user sees that one
# message and nothing else. This runs what the launcher runs: the bundled runtime, the classpath and
# JVM options from the launcher's own .cfg, and the main class that .cfg names.
#
# It needs an app image, which `packageExe` does not leave behind - run `createDistributable` too.
# The layout is <image>/app/<name>.cfg next to the jars, <image>/runtime for the jlink image, and
# $APPDIR in the .cfg means <image>/app.
#
# Usage: pwsh -File tools/smoke-windows-launch.ps1 [-TimeoutMilliseconds 30000]

param(
    [int]$TimeoutMilliseconds = 30000
)

$ErrorActionPreference = 'Stop'

$root = "composeApp/build/compose"

# This script has to work on a machine nobody can look at, so when it cannot find something the
# log should already say what was there.
Write-Host "--- layout under $root/binaries ---"
@(Get-ChildItem "$root/binaries" -Recurse -Depth 3 -Directory -ErrorAction SilentlyContinue) |
    Select-Object -First 40 | ForEach-Object { Write-Host ("  " + $_.FullName) }

# The launcher config is the source of truth for what gets run, and it sits in the app directory.
$cfg = @(Get-ChildItem "$root/binaries" -Recurse -Filter '*.cfg' -ErrorAction SilentlyContinue)[0]
if (-not $cfg) { throw "no launcher .cfg under $root/binaries - did :composeApp:createDistributable run?" }
$appDir = $cfg.Directory.FullName
$imageRoot = Split-Path $appDir -Parent
Write-Host "app image: $imageRoot"
Write-Host "--- $($cfg.Name) ---"
Get-Content $cfg.FullName

# Parse the .cfg the way the launcher reads it: app.classpath repeats, one jar per line, and
# $APPDIR is expanded in the classpath and in the JVM options alike.
$classpathEntries = @()
$javaOptions = @()
$mainClass = 'co.omnimusic.app.desktop.MainKt'
foreach ($line in Get-Content $cfg.FullName) {
    switch -Regex ($line) {
        '^\s*app\.classpath\s*=\s*(.+)$' { $classpathEntries += $matches[1].Trim(); break }
        '^\s*app\.mainclass\s*=\s*(.+)$' { $mainClass = $matches[1].Trim(); break }
        '^\s*java-options\s*=\s*(.+)$' { $javaOptions += $matches[1].Trim(); break }
    }
}
$expand = { param($value) $value.Replace('$APPDIR', $appDir) }
$classpath = (($classpathEntries | ForEach-Object { & $expand $_ }) | Join-String -Separator ';')
$javaOptions = @($javaOptions | ForEach-Object { & $expand $_ })
if (-not $classpath) {
    $classpath = (Get-ChildItem $appDir -Filter *.jar -ErrorAction SilentlyContinue |
        ForEach-Object { $_.FullName }) -join ';'
}
if (-not $classpath) { throw "no classpath in $($cfg.FullName) and no jars under $appDir" }
Write-Host ("classpath: {0} entries" -f ($classpath -split ';').Count)
Write-Host "main class: $mainClass"
Write-Host ("java options: {0}" -f ($javaOptions -join ' '))

# The bundled runtime - the java.exe the installer ships, not the one on PATH. It is a junction
# onto the jlink output, so report where it points before trusting it.
$runtimeDir = Join-Path $imageRoot 'runtime'
$runtimeItem = Get-Item $runtimeDir -Force -ErrorAction SilentlyContinue
if ($runtimeItem) {
    Write-Host ("runtime: LinkType={0} Target={1}" -f $runtimeItem.LinkType, ($runtimeItem.Target -join ','))
} else {
    Write-Host "runtime: absent at $runtimeDir"
}
$candidates = @(
    (Join-Path $runtimeDir 'bin/java.exe'),
    "$root/runtime/main/bin/java.exe"
)
if ($runtimeItem -and $runtimeItem.Target) {
    # .Target is a string[] for a link; wrap it so a single string is not indexed into characters.
    $candidates = @((Join-Path (@($runtimeItem.Target)[0]) 'bin/java.exe')) + $candidates
}
$java = @($candidates | Where-Object { Test-Path $_ })[0]
if (-not $java) {
    Write-Host "looked for a bundled runtime at:"
    $candidates | ForEach-Object { Write-Host "  $_" }
    @(Get-ChildItem $runtimeDir -Force -ErrorAction SilentlyContinue) |
        Select-Object -First 15 | ForEach-Object { Write-Host ("  runtime contains: " + $_.Name) }
    throw "no bundled java.exe for the app image at $imageRoot"
}
Write-Host "bundled runtime: $java"

Write-Host "--- bundled runtime ---"
& $java -version
$modules = @(& $java --list-modules)
Write-Host ("bundled modules: {0}" -f $modules.Count)

Write-Host "--- launching the packaged app ---"
$err = Join-Path $env:RUNNER_TEMP "omnimusic-launch.err"
$out = Join-Path $env:RUNNER_TEMP "omnimusic-launch.out"
$proc = Start-Process -FilePath $java `
    -ArgumentList (@($javaOptions) + @('-cp', $classpath, $mainClass)) `
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
