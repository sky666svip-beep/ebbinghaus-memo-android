param(
    [string]$sdkRoot = "C:\Users\AMX\AppData\Local\Android\Sdk"
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $sdkRoot)) {
    New-Item -ItemType Directory -Path $sdkRoot -Force | Out-Null
}

$tempDir = [System.IO.Path]::GetTempPath()

# 1. platforms;android-35
$platformTarget = Join-Path $sdkRoot "platforms\android-35"
if (-not (Test-Path "$platformTarget\android.jar")) {
    Write-Host "Downloading and installing platform-35..."
    $zip = Join-Path $tempDir "platform-35.zip"
    $tempExtract = Join-Path $tempDir "platform-35-temp"
    if (-not (Test-Path $zip)) {
        & curl.exe -L -o $zip "https://dl.google.com/android/repository/platform-35_r01.zip"
    }
    if (Test-Path $tempExtract) { Remove-Item -Recurse -Force $tempExtract }
    New-Item -ItemType Directory -Path $tempExtract -Force | Out-Null
    & tar.exe -xf $zip -C $tempExtract
    $extractedSubdir = Get-ChildItem -Path $tempExtract -Directory | Select-Object -First 1
    if (-not (Test-Path (Split-Path $platformTarget -Parent))) {
        New-Item -ItemType Directory -Path (Split-Path $platformTarget -Parent) -Force | Out-Null
    }
    if (Test-Path $platformTarget) { Remove-Item -Recurse -Force $platformTarget }
    Move-Item -Path $extractedSubdir.FullName -Destination $platformTarget -Force
    if (Test-Path $tempExtract) { Remove-Item -Recurse -Force $tempExtract }
    if (Test-Path $zip) { Remove-Item -Force $zip }
    Write-Host "platform-35 installed successfully."
}

# 2. build-tools;34.0.0
$buildToolsTarget = Join-Path $sdkRoot "build-tools\34.0.0"
if (-not (Test-Path "$buildToolsTarget\aapt2.exe")) {
    Write-Host "Downloading and installing build-tools 34.0.0..."
    $zip = Join-Path $tempDir "build-tools-34.zip"
    $tempExtract = Join-Path $tempDir "build-tools-34-temp"
    if (-not (Test-Path $zip)) {
        & curl.exe -L -o $zip "https://dl.google.com/android/repository/build-tools_r34-windows.zip"
    }
    if (Test-Path $tempExtract) { Remove-Item -Recurse -Force $tempExtract }
    New-Item -ItemType Directory -Path $tempExtract -Force | Out-Null
    & tar.exe -xf $zip -C $tempExtract
    $extractedSubdir = Get-ChildItem -Path $tempExtract -Directory | Select-Object -First 1
    if (-not (Test-Path (Split-Path $buildToolsTarget -Parent))) {
        New-Item -ItemType Directory -Path (Split-Path $buildToolsTarget -Parent) -Force | Out-Null
    }
    if (Test-Path $buildToolsTarget) { Remove-Item -Recurse -Force $buildToolsTarget }
    Move-Item -Path $extractedSubdir.FullName -Destination $buildToolsTarget -Force
    if (Test-Path $tempExtract) { Remove-Item -Recurse -Force $tempExtract }
    if (Test-Path $zip) { Remove-Item -Force $zip }
    Write-Host "build-tools 34.0.0 installed successfully."
}

# 3. platform-tools
$platformToolsTarget = Join-Path $sdkRoot "platform-tools"
if (-not (Test-Path "$platformToolsTarget\adb.exe")) {
    Write-Host "Downloading and installing platform-tools..."
    $zip = Join-Path $tempDir "platform-tools.zip"
    if (Test-Path $zip) {
        if ((Get-Item $zip).Length -lt 1000000) {
            Remove-Item -Force $zip
        }
    }
    if (-not (Test-Path $zip)) {
        & curl.exe -L -o $zip "https://dl.google.com/android/repository/platform-tools-latest-windows.zip"
    }
    if (Test-Path $platformToolsTarget) { Remove-Item -Recurse -Force $platformToolsTarget }
    & tar.exe -xf $zip -C $sdkRoot
    if (Test-Path $zip) { Remove-Item -Force $zip }
    Write-Host "platform-tools installed successfully."
}

# 4. Licenses
$licDir = Join-Path $sdkRoot "licenses"
if (-not (Test-Path $licDir)) {
    New-Item -ItemType Directory -Path $licDir -Force | Out-Null
}
@("24333f8a63b68256972e519686419b657dce302f", "8933bad161af4178b1185d1a37fbf41ea5269c55", "d56f5185645160f14fee78221396302ed071bf4d") | Set-Content -Path (Join-Path $licDir "android-sdk-license") -Encoding Ascii
Write-Host "Android SDK bootstrap completed successfully."
