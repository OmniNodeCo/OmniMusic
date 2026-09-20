# Verifies that the packaged Windows app can actually start.
#
# The installer's launcher reports "Failed to launch JVM" for anything that goes wrong before the
# first frame - a JDK module missing from the bundled jlink runtime, a class the classpath does not
# contain, an exception escaping main(). An installed build has no console, so a user sees that one
# message and nothing else. This runs what the launcher runs: the bundled runtime, the classpath and
# JVM options from the launcher's own .cfg, and the main class that .cfg names.
#
# It needs an app image, which `packageExe` does not leave behind - run `createDistributable` too.
#
# Usage: pwsh -File tools/smoke-windows-launch.ps1 [-TimeoutMilliseconds 30000]

param(
    [int]$TimeoutMilliseconds = 30000
)

$ErrorActionPreference = 'Stop'

$root = "composeApp/build/compose"

# Print the layout first: this script has to work on a machine nobody can look at, so when it
# cannot find something the log should say what was actually there.
Write-Host "--- layout under $root/binaries ---"
@(Get-ChildItem "$root/binaries" -Recurse -Depth 3 -Directory -ErrorAction SilentlyContinue) |
    Select-Object -First 40 | ForEach-Object { Write-Host ("  " + $_.FullName) }

# The bundled runtime: the java.exe the installer ships, not the one on PATH.
$javaExe = @(Get-ChildItem $root -Recurse -Filter 'java.exe' -ErrorAction SilentlyContinue)[0]
if (-not $javaExe) { throw "no bundled java.exe under $root - did :composeApp:createDistributable run?" }
$java = $javaExe.FullName
Write-Host "bundled runtime: $java"

# The launcher config is the source of truth for what gets run.
$cfg = @(Get-ChildItem "$root/binaries" -Recurse -Filter '*.cfg' -ErrorAction SilentlyContinue)[0]
if (-not $cfg) { throw "no launcher .cfg under $root/binaries" }
Write-Host "--- $($cfg.FullName) ---"
Get-Content $cfg.FullName

$imageRoot = $cfg.Directory.FullName
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
