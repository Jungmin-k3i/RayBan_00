# Windows 에뮬레이터 실행

이 PC에서 `The emulator process for AVD Medium_Phone has terminated` 오류를
피해 부팅과 기존 디버그 APK 실행을 확인한 조합은 다음과 같습니다.

- Android Emulator 36.6.11, WHPX 가속
- API 36 Google APIs x86_64 가상 기기
- 영문 경로의 SDK와 AVD
- 소프트웨어 그래픽, Quick Boot 스냅샷 사용 안 함

프로젝트 루트(`gradlew.bat`이 있는 폴더)의 PowerShell에서 실행합니다.

```powershell
.\scripts\start-emulator-windows.ps1
```

Android 홈 화면이 나타날 때까지 기다린 뒤 Android Studio 실행 대상에서
실행 중인 `Medium_Phone` / `emulator-5582`를 선택하고 앱을 Run 합니다.
첫 진단 부팅에는 약 5분이 걸렸습니다. Device Manager에서 원래의 API 37.0
기기를 다시 시작하는 것과는 다른 실행 경로입니다.

스크립트는 이 PC에 이미 존재하는 다음 경로를 사용합니다.

- SDK: `C:\Users\Public\Documents\ESTsoft\CreatorTemp\AndroidSdk`
  (기존 SDK를 가리키는 junction)
- AVD: `C:\Users\Public\Documents\ESTsoft\CreatorTemp\android-home\avd\Medium_Phone.avd`
- 로그: `C:\Users\Public\Documents\ESTsoft\CreatorTemp\android-home\emulator-logs`

SDK나 AVD를 새로 설치하지 않으므로 다른 PC에서는 `-SdkPath`,
`-AndroidUserPath`, `-AvdName`으로 준비된 영문 경로를 지정해야 합니다.
환경변수는 실행하는 프로세스에만 적용하고 복원합니다. 기존 기기를 삭제하거나
Wipe Data를 수행하지 않습니다. 종료는 에뮬레이터 창을 닫으면 됩니다.

## 확인 범위

2026-09-21 진단에서 API 37.0 16KB 이미지의 기존 실행은
`0xC0000005`로 종료됐습니다. CPU 가속 점검은 정상이었고,
소프트웨어 그래픽만 적용한 기존 경로 실행도 부팅에 실패했습니다.
위 조합에서는 ADB `device` 상태와 `sys.boot_completed=1`,
2026-09-18 빌드 APK의 설치 및 `com.k3i.lumencue/.MainActivity` 실행을
확인했습니다. 경로와 OS 이미지 등을 함께 바꿨으므로 한 가지를 단독 원인으로
확정한 것은 아닙니다. 최신 소스와 실제 글래스 연동까지 검증한 결과도 아닙니다.
