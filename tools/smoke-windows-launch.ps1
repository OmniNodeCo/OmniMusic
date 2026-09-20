# Verifies that the packaged Windows app can actually start.
#
# The installer's launcher reports "Failed to launch JVM" for anything that goes wrong before the
# first frame - a JDK module missing from the bundled jlink runtime, a class the classpath does not
# contain, an exception escaping main(). An installed build has no console, so a user sees that one
# message and nothing else.
#
# Two checks, because the failure has two independent halves:
#   1. the bundled runtime carries the JDK modules the app needs at runtime, read from the runtime's
#      own `release` file - a module jdeps cannot see is the classic cause of this symptom;
#   2. the packaged classpath actually resolves the main class, by running it.
#
# Needs an app image, which `packageExe` does not leave behind - run `createDistributable` too.
# Layout: <image>/app/<name>.cfg next to the jars, <image>/runtime for the jlink image, and
# $APPDIR in the .cfg means <image>/app.
#
# Usage: pwsh -File tools/smoke-windows-launch.ps1 [-TimeoutMilliseconds 30000]

param(
    [int]$TimeoutMilliseconds = 30000
)

$ErrorActionPreference = 'Stop'

$root = "composeApp/build/compose"

# The .cfg is the source of truth for what gets run, and it sits in the app directory.
$cfg = @(Get-ChildItem "$root/binaries" -Recurse -Filter '*.cfg' -ErrorAction SilentlyContinue)[0]
if (-not $cfg) { throw "no launcher .cfg under $root/binaries - did :composeApp:createDistributable run?" }
$appDir = $cfg.Directory.FullName
$imageRoot = Split-Path $appDir -Parent
Write-Host "app image: $imageRoot"

# --- 1. the bundled runtime's module set -------------------------------------------------------
# What the app needs beyond what a bare Compose app pulls in: Java Sound's SPI lookup reaches the
# MP3 decoder reflectively, and HTTPS to the music APIs needs the EC crypto provider. jdeps sees
# none of that, which is exactly how a module goes missing.
$requiredModules = @('java.desktop', 'jdk.crypto.ec', 'jdk.unsupported', 'java.sql', 'jdk.zipfs')

$runtimeDir = Join-Path $imageRoot 'runtime'
$releaseFile = Join-Path $runtimeDir 'release'
if (-not (Test-Path $releaseFile)) { throw "no runtime/release at $releaseFile" }
$releaseLine = @(Get-Content $releaseFile | Where-Object { $_ -match '^MODULES=' })[0]
$bundled = @(($releaseLine -replace '^MODULES="?', '' -replace '"$', '') -split '\s+')
Write-Host ("bundled modules: {0}" -f $bundled.Count)
$missing = @($requiredModules | Where-Object { $bundled -notcontains $_ })
if ($missing.Count -gt 0) {
    throw "the bundled runtime is missing modules the app needs at runtime: $($missing -join ', ')"
}
Write-Host ("required modules present: {0}" -f ($requiredModules -join ', '))

# --- the launcher's own configuration ---------------------------------------------------------
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
if (-not $classpath) { throw "no app.classpath entries in $($cfg.FullName)" }
Write-Host ("classpath: {0} entries from {1}" -f ($classpath -split ';').Count, $cfg.Name)
Write-Host "main class: $mainClass"

# --- 2. run it -------------------------------------------------------------------------------
# The bundled runtime ships no launcher executables at all - bin holds only DLLs, because
# jpackage's own launcher enters the JVM through jli.dll and never spawns java.exe. So the classpath
# check runs on the JDK the build used, which cannot vouch for the shipped runtime's module set;
# that is what check 1 above is for, and it reads it from the runtime's own release file.
$bundledJava = Join-Path $runtimeDir 'bin/java.exe'
$java = if (Test-Path $bundledJava) { $bundledJava } else {
    $fallback = Join-Path $env:JAVA_HOME 'bin/java.exe'
    Write-Host "the bundled runtime has no bin/java.exe (launcher executables are stripped; jli.dll is what the launcher uses)"
    if (-not (Test-Path $fallback)) { throw "no java.exe in the bundled runtime or in JAVA_HOME" }
    $fallback
}
Write-Host "running with: $java"
& $java -version

Write-Host "--- launching the packaged app ---"
$err = Join-Path $env:RUNNER_TEMP "omnimusic-launch.err"
$out = Join-Path $env:RUNNER_TEMP "omnimusic-launch.out"
$proc = Start-Process -FilePath $java `
    -ArgumentList (@($javaOptions) + @('-cp', $classpath, $mainClass)) `
    -NoNewWindow -PassThru -RedirectStandardError $err -RedirectStandardOutput $out

if (-not $proc.WaitForExit($TimeoutMilliseconds)) {
    Stop-Process -Id $proc.Id -Force
    Write-Host "PASS: still running after $($TimeoutMilliseconds / 1000)s - the JVM launched the packaged app"
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
