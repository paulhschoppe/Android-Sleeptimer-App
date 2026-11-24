# Einmalige Einrichtung ohne Android Studio (Windows).
# Installiert, was zum Bauen fehlt: JDK 17, Android SDK (platform-tools, Platform, Build-Tools).
# Bereits Vorhandenes wird übersprungen – das Skript kann gefahrlos mehrfach laufen.
#
# Aufruf: setup.cmd (Doppelklick) oder  powershell -ExecutionPolicy Bypass -File setup.ps1 [-SdkRoot <Ordner>]

param(
    [string]$SdkRoot = $(if ($env:ANDROID_HOME) { $env:ANDROID_HOME }
                         elseif ([Environment]::GetEnvironmentVariable('ANDROID_HOME', 'User')) { [Environment]::GetEnvironmentVariable('ANDROID_HOME', 'User') }
                         else { Join-Path $HOME 'Android' })
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

$PlatformVersion = 35          # muss zu compileSdk in app/build.gradle.kts passen
$BuildToolsVersion = '35.0.0'
$JdkVersion = 17

function Write-Step($text) { Write-Host "`n==> $text" -ForegroundColor Cyan }

# Download mit Fortsetzen und Wiederholungen (für wackelige Verbindungen)
function Get-File($url, $target) {
    for ($i = 1; $i -le 10; $i++) {
        & curl.exe -fL --retry 5 --retry-delay 3 -C - -o $target $url
        if ($LASTEXITCODE -eq 0) { return }
        Write-Host "   Download unterbrochen (Versuch $i/10), neuer Versuch ..."
        Start-Sleep -Seconds 3
    }
    throw "Download fehlgeschlagen: $url"
}

function Get-JavaMajor($javaExe) {
    if (-not (Test-Path $javaExe)) { return 0 }
    $out = cmd /c "`"$javaExe`" -version 2>&1"
    if ("$out" -match 'version "(\d+)') { return [int]$Matches[1] }
    return 0
}

New-Item -ItemType Directory -Force $SdkRoot | Out-Null
$downloads = Join-Path $SdkRoot 'downloads'
New-Item -ItemType Directory -Force $downloads | Out-Null

# --- 1. JDK -------------------------------------------------------------------
Write-Step "JDK $JdkVersion"
$javaHome = $null
foreach ($candidate in @($env:JAVA_HOME, [Environment]::GetEnvironmentVariable('JAVA_HOME', 'User'), (Join-Path $SdkRoot "jdk-$JdkVersion"))) {
    if ($candidate -and (Get-JavaMajor (Join-Path $candidate 'bin\java.exe')) -ge $JdkVersion) { $javaHome = $candidate; break }
}
if ($javaHome) {
    Write-Host "   vorhanden: $javaHome"
} else {
    $javaHome = Join-Path $SdkRoot "jdk-$JdkVersion"
    $zip = Join-Path $downloads "jdk-$JdkVersion.zip"
    Get-File "https://api.adoptium.net/v3/binary/latest/$JdkVersion/ga/windows/x64/jdk/hotspot/normal/eclipse?project=jdk" $zip
    $tmp = Join-Path $downloads 'jdk-unpack'
    Remove-Item -Recurse -Force $tmp -ErrorAction SilentlyContinue
    Expand-Archive $zip $tmp
    Move-Item (Get-ChildItem $tmp | Select-Object -First 1).FullName $javaHome
    Remove-Item -Recurse -Force $tmp
    Write-Host "   installiert: $javaHome"
}
$env:JAVA_HOME = $javaHome

# --- 2. Android Command-line Tools ---------------------------------------------
Write-Step 'Android Command-line Tools'
$toolsBin = Join-Path $SdkRoot 'cmdline-tools\latest\bin'
if (Test-Path $toolsBin) {
    Write-Host "   vorhanden: $toolsBin"
} else {
    $repo = (& curl.exe -fsSL --retry 5 https://dl.google.com/android/repository/repository2-3.xml) -join "`n"
    $latest = [regex]::Matches($repo, 'commandlinetools-win-(\d+)_latest\.zip') |
        Sort-Object { [long]$_.Groups[1].Value } | Select-Object -Last 1
    if (-not $latest) { throw 'Konnte die aktuelle Version der Command-line Tools nicht ermitteln.' }
    $zip = Join-Path $downloads $latest.Value
    Get-File "https://dl.google.com/android/repository/$($latest.Value)" $zip
    $tmp = Join-Path $downloads 'cmdline-unpack'
    Remove-Item -Recurse -Force $tmp -ErrorAction SilentlyContinue
    Expand-Archive $zip $tmp
    New-Item -ItemType Directory -Force (Join-Path $SdkRoot 'cmdline-tools') | Out-Null
    Move-Item (Join-Path $tmp 'cmdline-tools') (Join-Path $SdkRoot 'cmdline-tools\latest')
    Remove-Item -Recurse -Force $tmp
    Write-Host "   installiert: $toolsBin"
}

# --- 3. SDK-Pakete -------------------------------------------------------------
Write-Step "SDK-Pakete (platform-tools, android-$PlatformVersion, build-tools $BuildToolsVersion)"
$needed = @{
    'platform-tools'                        = 'platform-tools'
    "platforms/android-$PlatformVersion"    = "platforms\android-$PlatformVersion"
    "build-tools/$BuildToolsVersion"        = "build-tools\$BuildToolsVersion"
}
$android = Join-Path $toolsBin 'android.exe'
for ($i = 1; $i -le 10; $i++) {
    $missing = @($needed.Keys | Where-Object { -not (Test-Path (Join-Path $SdkRoot $needed[$_])) })
    if ($missing.Count -eq 0) { break }
    Write-Host "   installiere: $($missing -join ', ') (Versuch $i/10)"
    if (Test-Path $android) {
        # neues Android-CLI (ersetzt sdkmanager)
        & $android --no-metrics "--sdk=$SdkRoot" sdk install @missing
    } else {
        # ältere Command-line Tools: sdkmanager über cmd, damit die ';' in den Paketnamen erhalten bleiben
        $pkgs = ($missing | ForEach-Object { '"' + ($_ -replace '/', ';') + '"' }) -join ' '
        $sdkmanager = Join-Path $toolsBin 'sdkmanager.bat'
        cmd /c "(for /l %i in (1,1,20) do @echo y) | `"$sdkmanager`" --sdk_root=`"$SdkRoot`" --licenses >nul"
        cmd /c "`"$sdkmanager`" --sdk_root=`"$SdkRoot`" $pkgs"
    }
}
$missing = @($needed.Keys | Where-Object { -not (Test-Path (Join-Path $SdkRoot $needed[$_])) })
if ($missing.Count -gt 0) { throw "Nicht installiert: $($missing -join ', ') – Skript einfach erneut starten." }
Write-Host '   alles vorhanden'

# --- 4. Umgebung ---------------------------------------------------------------
Write-Step 'Umgebungsvariablen und local.properties'
[Environment]::SetEnvironmentVariable('JAVA_HOME', $javaHome, 'User')
[Environment]::SetEnvironmentVariable('ANDROID_HOME', $SdkRoot, 'User')
$userPath = [Environment]::GetEnvironmentVariable('Path', 'User')
$add = @((Join-Path $SdkRoot 'platform-tools'), (Join-Path $javaHome 'bin')) |
    Where-Object { ($userPath -split ';') -notcontains $_ }
if ($add) { [Environment]::SetEnvironmentVariable('Path', ((@($userPath.TrimEnd(';')) + $add) -join ';'), 'User') }

$escaped = $SdkRoot.Replace('\', '\\').Replace(':', '\:')
Set-Content -Path (Join-Path $PSScriptRoot 'local.properties') -Value "sdk.dir=$escaped" -Encoding ascii

Write-Host "   JAVA_HOME    = $javaHome"
Write-Host "   ANDROID_HOME = $SdkRoot"
Write-Host "`nFertig. Neues Terminal öffnen (damit PATH greift), Handy anschließen, dann:" -ForegroundColor Green
Write-Host '   gradlew.bat installDebug' -ForegroundColor Green
