# 앱 실행 진단 — 2026-09-22

## 결과

- 현재 소스의 `:app:assembleDebug` 성공: Gradle 9.3.1, 3분 2초.
- 기존 Windows 스크립트로 API 36 `Medium_Phone`을 headless 모드로 시작했다. ADB `device`, `sys.boot_completed=1` 확인.
- 기본 APK 설치는 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`로 실패했다.
- 기존 설치 앱과 같은 디버그 키로 APK 사본을 서명한 뒤 `adb install -r` 성공. 기존 앱 데이터는 삭제하지 않았다.
- `com.k3i.lumencue/.MainActivity` 시작 결과 `Status: ok`, cold start 약 3.96초.
- 후속 검사에서도 동일 프로세스가 유지되고 MainActivity가 foreground 상태였다. UI 계층에서 공연명, 현재 곡, 입장 완료, 하단 탐색 메뉴를 확인했다.
- 실행 후 crash buffer는 비어 있었다. 전체 기능이나 실제 글래스 출력을 검증한 결과는 아니다.

## 설치 실패 원인

서로 다른 환경의 디버그 키로 동일한 applicationId를 서명했다.

| 대상 | SHA-256 인증서 지문 |
| --- | --- |
| 기존 설치 앱 / 영문 Android user home 키 | `5be34ba1cdff392773a3eb70bc72eb2165467880d58a28764d270b027d686609` |
| 기본 사용자 `.android/debug.keystore` / 이번 Gradle APK | `1578b928baf85038f249309527f41f79d0d9644bcfe1bc304e48b74e5ba0ef85` |

기존 설치 APK의 인증서와 `C:\Users\Public\Documents\ESTsoft\CreatorTemp\android-home\debug.keystore`의 인증서가 일치함을 확인했다. 해당 키로 빌드된 APK 사본을 서명해 업데이트했다. 원본 Gradle APK와 소스/빌드 설정은 변경하지 않았다.

같은 에뮬레이터에 다음 빌드를 업데이트할 때도 서명을 일치시켜야 한다. 기본 키로 생성한 원본 APK를 그대로 설치하면 같은 오류가 재발할 수 있다.

## 과거 종료 기록

`dumpsys activity exit-info`에는 이번 실행 이전의 startup ANR 기록 1건이 있었다 (`failed to complete startup`). 이번 실행의 프로세스와는 다르며, 과거 ANR의 근본 원인은 이 기록만으로 확정할 수 없다. 이번 실행에서는 재현되지 않았다.

## 산출물

- `build/run-diagnostic-build.log`: 빌드 로그
- `build/run-diagnostic-crash.log`: 실행 후 crash buffer (비어 있음)
- `build/run-diagnostic-window.xml`: 실행 화면 UI 계층
- `build/app-debug-emulator.apk`: 기존 에뮬레이터 서명과 일치하는 현재 APK 사본

에뮬레이터는 `emulator-5582`로 백그라운드 실행 상태를 유지했다.
