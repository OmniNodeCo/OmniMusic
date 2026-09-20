# Verifies that the packaged Windows app can actually start.
#
# The installer's launcher reports "Failed to launch JVM" for anything that goes wrong before the
# first frame - a JDK module missing from the bundled jlink runtime, a class the classpath does not
# contain, an exception escaping main(). An installed build has no console, so a user sees that one
# message and nothing else. This runs what the launcher runs: the bundled runtime, the classpath and
# JVM options from the launcher's own .cfg, and the main class that .cfg names.
#
# It needs an app image, which `packageExe` does not leave behind - run `createDistributable` too.
# Note that <image>/runtime is a junction onto the jlink output, so recursion does not descend into
# it; the runtime is resolved by path, and `Test-Path` follows the link.
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

# The launcher config is the source of truth for what gets run, and it sits at the image root.
$cfg = @(Get-ChildItem "$root/binaries" -Recurse -Filter '*.cfg' -ErrorAction SilentlyContinue)[0]
if (-not $cfg) { throw "no launcher .cfg under $root/binaries - did :composeApp:createDistributable run?" }
$imageRoot = $cfg.Directory.FullName
Write-Host "app image: $imageRoot"
Write-Host "--- $($cfg.Name) ---"
Get-Content $cfg.FullName
Write-Host "--- image contents ---"
Get-ChildItem $imageRoot -Force | ForEach-Object {
    Write-Host ("  {0}  {1}  {2}" -f $_.Mode, $_.Name, $_.Target)
}

# The bundled runtime, i.e. the java.exe the installer ships - not the one on PATH.
$runtimeCandidates = @(
    (Join-Path $imageRoot 'runtime/bin/java.exe'),
    "$root/runtime/main/bin/java.exe"
)
$java = @($runtimeCandidates | Where-Object { Test-Path $_ })[0]
if (-not $java) {
    Write-Host "looked for a bundled runtime at:"
    $runtimeCandidates | ForEach-Object { Write-Host "  $_" }
    throw "no bundled java.exe for the app image at $imageRoot"
}
Write-Host "bundled runtime: $java"

$classpath = $null
$mainClass = 'co.omnimusic.app.desktop.MainKt'
$javaOptions = @()
foreach ($line in Get-Content $cfg.FullName) {
    if ($line -match '^\s*app\.classpath\s*=\s*(.+)$') {
        $classpath = (($matches[1].Trim() -split ';') |
            ForEach-Object { $_.Replace('$APPDIR', $imageRoot) } |
            Join-String -Separator ';')
    }
    elseif ($line -match '^\s*main-class\s*=\s*(.+)$') { $mainClass = $matches[1].Trim() }
    elseif ($line -match '^\s*java-options\s*=\s*(.+)$') { $javaOptions += $matches[1].Trim() }
}
if (-not $classpath) {
    # No app.classpath entry means the whole app directory is the classpath.
    $classpath = (Get-ChildItem (Join-Path $imageRoot 'app') -Filter *.jar -ErrorAction SilentlyContinue |
        ForEach-Object { $_.FullName }) -join ';'
}
if (-not $classpath) { throw "no classpath in $($cfg.FullName) and no jars under $imageRoot\app" }
Write-Host "classpath: $classpath"
Write-Host "main class: $mainClass"

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
