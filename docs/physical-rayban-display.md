# Ray-Ban Display 실기기 연결

현재 실기기 경로는 DAT 0.9.0의 앱 등록, 기기 선택, Display 세션과 텍스트 전송을 사용합니다.
앱 시작 시 모의 기기는 자동 활성화되지 않습니다.

## 휴대폰 준비

1. PC의 USB 디버깅 요청을 휴대폰에서 허용합니다.
2. Meta AI 앱에서 실제 Meta Ray-Ban Display를 페어링하고 업데이트합니다.
3. 개발 빌드는 Meta AI 앱의 개발자 모드를 켭니다. 이 모드에서는 앱 ID와 클라이언트 토큰 `0`을 사용합니다.
4. Android Studio에서 휴대폰을 선택하고 app의 debug 빌드를 실행합니다.

개발용 패키지는 `com.k3i.lumencue.dev`, 앱 이름은 **LumenCue Dev**입니다.
ZIP에 포함된 기존 앱과 서명이 달라도 기존 앱/데이터를 삭제하지 않고 설치할 수 있습니다.
release 패키지는 기존 `com.k3i.lumencue`를 유지합니다.

## 앱에서 연결

1. 우측 위 설정 → 기술 → **Ray-Ban Display 연결**.
2. **모의 기기 테스트**를 끈 상태에서 **Meta AI 앱 등록**을 누릅니다.
3. 근처 기기 권한을 허용하고 Meta AI 앱에서 등록을 승인합니다.
4. 목록에 실제 안경이 나타나면 **이 안경 연결**을 누릅니다.
5. **안경 화면 연결 완료**를 확인하고 **테스트 문구 전송**을 누릅니다.
6. 안경에 `LumenCue display test`가 표시되는지 확인합니다. 육안 확인 시간을 확보하고 한글 표시 문제를 구분하기 위해 영어 문구를 30초간 표시한 뒤 지웁니다.
7. AR Live → 운영 검증 모드의 HUD 전송 버튼으로 현재 공연 안내를 전송합니다.

전송 성공은 SDK가 성공 응답과 `true`를 반환했을 때만 기록합니다. 안경 착용자가 실제 화면을 보았다는 보장은 아니므로 육안으로도 확인해야 합니다.
전송 실패 시 기존 스마트폰 미리보기 경로를 유지합니다.

## 범위와 오류 처리

- 기존 `HudRenderInstruction` → `ToolkitDisplayDocument`의 텍스트를 최대 2개, 각 160자까지 전송합니다.
- 이번 실기기 구현은 텍스트 HUD입니다. 이미지, 영상, 기존 미리보기 애니메이션의 안경 렌더링은 포함하지 않습니다.
- 세션/디스플레이 준비 완료 전 전송을 차단합니다. 연결 제한 시간은 30초, 전송 제한 시간은 10초입니다.
- 안경 연결 종료, 등록 해제, 모의 기기 전환 시 세션을 정리합니다.
- SDK가 요구하는 펌웨어/안경 DAT 앱 업데이트 버튼을 제공합니다.
- 일반 Ray-Ban Meta처럼 디스플레이가 없는 기기는 연결 버튼을 비활성화합니다.
- 등록 또는 전송 실패 사유는 연결 카드에 표시합니다.

운영용 자격 증명은 소스에 기록하지 말고 로컬 Gradle 속성 `MWDAT_APPLICATION_ID`, `MWDAT_CLIENT_TOKEN`으로 제공하세요.

## 검증

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
adb -s <휴대폰 ID> install -r app\build\outputs\apk\debug\app-debug.apk
adb -s <휴대폰 ID> shell am start -W -n com.k3i.lumencue.dev/com.k3i.lumencue.MainActivity
```

실제 안경 출력은 휴대폰 USB 승인, Meta AI 등록 승인 및 착용자의 화면 확인이 필요합니다.
