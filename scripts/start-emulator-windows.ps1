[CmdletBinding()]
param(
    [string]$SdkPath = 'C:\Users\Public\Documents\ESTsoft\CreatorTemp\AndroidSdk',
    [string]$AndroidUserPath = 'C:\Users\Public\Documents\ESTsoft\CreatorTemp\android-home',
    [ValidatePattern('^[A-Za-z0-9_.-]+$')]
    [string]$AvdName = 'Medium_Phone',
    [ValidateRange(5554, 5682)]
    [int]$Port = 5582,
    [switch]$Headless
)

$ErrorActionPreference = 'Stop'

# Use the existing ASCII SDK junction and API 36 AVD. No global settings,
# AVD files, snapshots or user data are deleted or rewritten by this script.
if ($Port % 2 -ne 0) { throw 'The emulator console port must be even.' }
if ($SdkPath -match '[^\x00-\x7F]' -or $AndroidUserPath -match '[^\x00-\x7F]') {
    throw 'Use ASCII-only SDK and Android user directory paths.'
}
$emulatorExe = Join-Path $SdkPath 'emulator\emulator.exe'
$adbExe = Join-Path $SdkPath 'platform-tools\adb.exe'
$avdRoot = Join-Path $AndroidUserPath 'avd'
foreach ($requiredPath in @($emulatorExe, $adbExe, (Join-Path $avdRoot "$AvdName.ini"))) {
    if (-not (Test-Path -LiteralPath $requiredPath)) {
        throw "Required file not found: $requiredPath"
    }
}
$devices = & $adbExe devices
if ($LASTEXITCODE -ne 0) { throw 'Could not query adb devices.' }
if ($devices -match "^emulator-$Port\s") {
    throw "emulator-$Port is already running. Select it in Android Studio, or close it before restarting."
}

$logDirectory = Join-Path $AndroidUserPath 'emulator-logs'
New-Item -ItemType Directory -Force -Path $logDirectory | Out-Null
$logPrefix = Join-Path $logDirectory ("{0}-{1}" -f $AvdName, (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
$arguments = @('-avd', $AvdName, '-gpu', 'software', '-no-snapshot', '-port', "$Port")
if ($Headless) { $arguments += '-no-window' }

$processEnvironment = @{
    ANDROID_HOME = $SdkPath
    ANDROID_SDK_ROOT = $SdkPath
    ANDROID_USER_HOME = $AndroidUserPath
    ANDROID_AVD_HOME = $avdRoot
}
$previousEnvironment = @{}
try {
    foreach ($name in $processEnvironment.Keys) {
        $previousEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
        [Environment]::SetEnvironmentVariable($name, $processEnvironment[$name], 'Process')
    }
    $windowStyle = if ($Headless) { 'Hidden' } else { 'Normal' }
    $emulatorProcess = Start-Process -FilePath $emulatorExe -ArgumentList $arguments -WindowStyle $windowStyle `
        -RedirectStandardOutput "$logPrefix.log" -RedirectStandardError "$logPrefix.err.log" -PassThru
} finally {
    foreach ($name in $previousEnvironment.Keys) {
        [Environment]::SetEnvironmentVariable($name, $previousEnvironment[$name], 'Process')
    }
}

Write-Host "Started $AvdName (PID $($emulatorProcess.Id)), emulator-$Port."
Write-Host 'Cold boot may take about 5 minutes on this PC. Wait for the Android home screen.'
Write-Host 'Then select the running emulator in Android Studio and run the app.'
Write-Host "Logs: $logPrefix.log"
Write-Host "Boot check: & '$adbExe' -s emulator-$Port shell getprop sys.boot_completed"
