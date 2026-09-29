[CmdletBinding()]
param(
    [ValidatePattern('^[A-Z]$')]
    [string]$DriveLetter = 'V',

    [string[]]$Tasks = @(':app:testDebugUnitTest', ':app:assembleDebug')
)

$ErrorActionPreference = 'Stop'

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$mappedDrive = "${DriveLetter}:"
$mappedRoot = "${mappedDrive}\"
$defaultJavaHome = 'C:\Program Files\Android\Android Studio\jbr'
$defaultAndroidSdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$publicRoot = if ([string]::IsNullOrWhiteSpace($env:PUBLIC)) { 'C:\Users\Public' } else { $env:PUBLIC }
$asciiGradleHome = Join-Path $publicRoot 'Documents\ESTsoft\CreatorTemp\lumencue-gradle-home'

$javaHome = if ([string]::IsNullOrWhiteSpace($env:JAVA_HOME)) { $defaultJavaHome } else { $env:JAVA_HOME }
$androidSdk = if ([string]::IsNullOrWhiteSpace($env:ANDROID_HOME)) { $defaultAndroidSdk } else { $env:ANDROID_HOME }

if (-not (Test-Path -LiteralPath (Join-Path $javaHome 'bin\java.exe'))) {
    throw "Java를 찾을 수 없습니다: $javaHome"
}
if (-not (Test-Path -LiteralPath $androidSdk)) {
    throw "Android SDK를 찾을 수 없습니다: $androidSdk"
}
if (Test-Path -LiteralPath $mappedRoot) {
    throw "$mappedDrive 드라이브가 이미 사용 중입니다. -DriveLetter 옵션으로 다른 문자를 지정하세요."
}

New-Item -ItemType Directory -Force -Path $asciiGradleHome | Out-Null

$previousJavaHome = $env:JAVA_HOME
$previousAndroidHome = $env:ANDROID_HOME
$previousGradleHome = $env:GRADLE_USER_HOME
$mappingCreated = $false
$exitCode = 1

try {
    & subst.exe $mappedDrive $projectRoot
    if ($LASTEXITCODE -ne 0) {
        throw "$mappedDrive 임시 드라이브 연결에 실패했습니다."
    }
    $mappingCreated = $true

    $env:JAVA_HOME = $javaHome
    $env:ANDROID_HOME = $androidSdk
    $env:GRADLE_USER_HOME = $asciiGradleHome

    Push-Location $mappedRoot
    try {
        & .\gradlew.bat @Tasks '--no-configuration-cache'
        $exitCode = $LASTEXITCODE
    } finally {
        Pop-Location
    }
} finally {
    if ($mappingCreated) {
        & subst.exe $mappedDrive /D | Out-Null
    }

    if ($null -eq $previousJavaHome) { Remove-Item Env:JAVA_HOME -ErrorAction SilentlyContinue }
    else { $env:JAVA_HOME = $previousJavaHome }

    if ($null -eq $previousAndroidHome) { Remove-Item Env:ANDROID_HOME -ErrorAction SilentlyContinue }
    else { $env:ANDROID_HOME = $previousAndroidHome }

    if ($null -eq $previousGradleHome) { Remove-Item Env:GRADLE_USER_HOME -ErrorAction SilentlyContinue }
    else { $env:GRADLE_USER_HOME = $previousGradleHome }
}

exit $exitCode
