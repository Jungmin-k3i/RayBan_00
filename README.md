# LumenCue

LumenCue는 실제 콘서트 현장에서 디스플레이가 있는 Meta Ray-Ban Display 계열 스마트글래스를 활용해 관객 경험을 확장하는 Android Compose 앱입니다.

초기 아이디어였던 가상 콘서트보다 현재 방향은 더 현실적인 **콘서트 AR Companion**입니다. 공연을 대체하지 않고, 실제 무대를 보는 관객에게 필요한 순간만 짧은 시각 정보와 참여 큐를 제공합니다.

이 제품의 주인공은 스마트폰 화면이 아니라 **Ray-Ban Display의 글래스 HUD**입니다. 스마트폰 앱은 공연 전 준비, 티켓/권한 확인, 설정, 게시판, 운영 검증을 담당하고, 공연 중 핵심 정보는 `HudState` 기반으로 글래스에 짧고 방해되지 않게 표시하는 구조를 목표로 합니다.

## 제품 방향

이 솔루션은 콘서트 기획사, 공연 주최사, 아티스트 매니지먼트, 공연장 운영사와 사전에 협업하는 B2B/B2B2C 제품입니다.

주최사는 공연 전에 다음 정보를 제공합니다.

- 셋리스트와 곡별 타임라인
- 응원법, 콜앤리스폰스, 떼창 타이밍
- 아티스트 멘트 번역/요약 문구
- 촬영 가능/불가 구간
- 앵콜, 이벤트, 굿즈, 퇴장 동선 안내
- 공연장 좌석/출구/화장실/MD 부스 정보

앱은 이 정보를 공연 중 Ray-Ban Display의 작은 AR HUD에 맞게 변환합니다. AR 원칙은 “무대를 가리지 않는 보조 정보”입니다. 중앙 시야는 비워두고, 하단/측면에 짧은 정보만 표시합니다.

## 핵심 경험

- **정보형 AR**: 현재 곡, 다음 곡 힌트, 가사 한 줄, 실시간 번역 자막, 공연장 안내
- **참여형 AR**: 박수 타이밍, 응원 콜, 떼창 시작, 응원봉 컬러 싱크, 하트/환호 반응
- **불편 해소형 AR**: 해외 아티스트 멘트 번역, 촬영 가능 구간, 퇴장 동선, 이벤트 타이밍처럼 스마트폰을 보기 어려운 순간의 보조 정보
- **참가자 게시판**: 같은 콘서트 관객끼리 팁, 주의사항, 응원법, 현장 분위기를 글과 댓글로 공유

## 현재 구현

현재 저장소는 Android 네이티브 앱과 Python 표준 라이브러리 기반 로컬 백엔드 MVP로 구성됩니다. 실제 티켓 예매처는 아직 연결되어 있지 않습니다. Meta Ray-Ban Display 실기기는 Android 휴대폰을 통해 연결했으며, 텍스트 HUD 전송과 안경 내 문구 표시를 확인했습니다. 검증 범위와 미해결 사항은 아래 실기기 검증 기록에 정리합니다.

구현된 주요 기능:

- 점검, 준비, 홈, AR Live, 게시판 중심의 하단 내비게이션
- 모든 주요 화면의 전역 설정 진입
- 하단 내비게이션 루트 화면에는 뒤로가기를 숨기고, 설정 상세/공연 게시판/게시글 상세 같은 추가 페이지에는 왼쪽 상단 화살표 제공
- 설정의 공연/티켓, 언어, 운영 설정, 기술 설정 상세 화면
- 한국어/영어 언어 선택과 로컬 저장
- 홈 화면의 현재 곡, 곡 진행률, 활성 AR 큐, 현재/다음 콘서트 이벤트와 이벤트별 참여 CTA
- 주최사 사전 제공 `interactionEvents` 기반 콘서트 이벤트 타임라인
- 콜앤리스폰스, 응원법, 응원봉 웨이브, 서프라이즈, 앵콜 게이지, 촬영 정책, 포토 타임, MD 안내, 퇴장 동선, 팬 미션, 셋리스트 힌트, 멘트 번역 이벤트 타입
- 이벤트 CTA 참여 시 일반 반응과 별도로 이벤트별 참여 횟수와 최근 참여 이벤트 기록
- AR Live 화면의 관객 집중 모드와 운영 검증 모드
- Ray-Ban Display HUD 미리보기와 렌즈 장면 프리뷰
- 현재 곡의 글래스 출력 큐 타임라인과 큐별 HUD 미리보기 이동
- `HudState`를 `HudVisualScene`, `HudRenderInstruction`, `ToolkitDisplayDocument`로 변환하는 계층
- 주최사 제공 이미지 없이, 앱이 기본 제공하는 통일된 HUD/AR 객체 키를 장면 데이터로 모델링
- 3D AR 객체 대신 글래스 HUD에 맞춘 레이아웃, 아이콘, 색상 토큰, 애니메이션, 안전 표시 영역을 장면 데이터로 모델링
- 생성형 AI로 제작한 자체 HUD/AR 객체 PNG 4종을 앱 리소스에 포함하고, `HudArObjectKey`에 따라 프리뷰에 렌더링
- 스마트폰 HUD 미리보기와 실제 DAT 텍스트 출력 경로 분리
- Meta AI 앱 등록, 실제 안경 선택, Display 세션 연결, 테스트 문구 및 현재 HUD 전송
- DAT MockDevice 테스트 경로를 별도 렌더러로 모델링하고, 모의 기기 모드에서만 사용
- DAT 0.9.0 MockDevice의 연결·페어링·세션을 검증하고, Display 미지원 시 스마트폰 HUD로 폴백
- AR Live 운영 검증 모드에서 MockDevice 상태, payload 요약, Lens Simulator safe area overlay를 함께 표시
- HUD dispatch/fallback 모델, 최근 전송 기록, 로컬 감사 로그 복원
- 글래스 직접 출력 제한 시 스마트폰 HUD, Android 알림, 오디오/TTS fallback plan
- 글래스 입력 정책과 스마트폰 기반 입력 시뮬레이터
- 티켓 패스, 공연장 미니맵, 안전 모드, AR 타임라인
- 게시판 진입 시 공연을 먼저 선택하고, 티켓/입장 인증된 공연 참가자만 입장 가능한 공연별 게시판
- AR Live의 실시간 번역 HUD 실험 카드
- 원본 음성 저장 없는 개인정보 보수 설계
- 선택 공연, 세션 요약, 이벤트별 참여 횟수 로컬 복원
- SQLite 기반 공연 패키지 검수/버전 배포, 티켓 체크인 검증, audience session, HUD 감사 로그 API
- Gradle 속성으로 백엔드 주소를 설정하면 배포 패키지를 비동기로 조회하고, 실패 시 앱 내 로컬 패키지로 자동 전환
- 체크인된 티켓으로 단기 audience session을 발급받아 HUD dispatch/fallback 기록을 서버 감사 로그에 자동 동기화
- 기준 버전·운영자 승인·release channel을 검사하는 공연 패키지 배포 게이트와 긴급 공지 우선순위 정책
- 활성 긴급 공지를 일반 공연 큐보다 높은 우선순위의 HUD 안전 공지로 표시

홈 화면에서는 운영 패키지 진단, 공연 변경, 내부 준비 카운트 같은 관리 정보를 숨깁니다. 공연 변경은 `설정 > 공연/티켓`에서 티켓 기반 연결 흐름으로 관리합니다.

## 공연/티켓 연결 원칙

여러 콘서트를 단일 앱에서 관리할 때 사용자가 아무 공연이나 고르는 구조는 제품적으로 위험합니다. 실제 제품에서는 다음 데이터를 매칭해 사용자가 접근 가능한 공연만 노출합니다.

- 티켓 예매처 또는 입장 인증 결과
- 주최사 공연 패키지 ID
- 공연 일시
- 좌석/구역
- 승인된 공연 패키지 버전

기본 연결 로직은 다음 순서입니다.

1. 티켓 또는 입장 인증으로 사용자의 공연을 확인합니다.
2. 해당 공연의 주최사 승인 패키지 버전을 찾습니다.
3. 좌석/구역별 AR 큐와 이벤트 타임라인을 적용합니다.
4. 예외 상황에서만 설정 화면에서 수동 복구 흐름을 제공합니다.

현재 로컬 빌드는 이 구조를 샘플 공연 패키지와 티켓 상태로 시뮬레이션합니다.

게시판도 같은 원칙을 따릅니다. 게시판 탭을 누르면 먼저 공연을 선택하고, 티켓/입장 인증이 확인된 공연만 게시판 입장이 가능합니다. 글과 댓글은 선택한 공연 ID에만 저장되며, 다른 공연 참가자가 잘못된 정보를 남기는 상황을 줄이는 것을 기본 정책으로 둡니다.

## HUD 시각화 원칙

주최사에서 이미지나 그래픽 파일을 받는 것을 기본 전제로 두지 않습니다. 콘서트마다 전용 이미지 세트를 새로 제작하는 방식은 운영 비용과 검수 부담이 크기 때문입니다. 기본 제품 방향은 **앱이 자체 제공하는 통일된 HUD/AR 객체를 먼저 고정하고, 주최사가 제공하는 공연 정보는 그 객체에 매핑하는 방식**입니다.

현재 앱이 기본 제공하는 HUD/AR 객체 키:

- `CaptionLine`: 현재 가사, 멘트 번역, 짧은 안내
- `CountdownRing`: 포토 타임, 떼창 시작, 이벤트 시작 전 카운트다운
- `ParticipationWave`: 박수, 떼창, 응원봉 웨이브 같은 관객 참여 큐
- `EnergyGauge`: 현재는 핵심 기능에서 제외한 보조 객체이며, 기본 제품 방향은 번역/자막/안내/참여 큐 중심

주최사가 제공해야 하는 것은 이미지 파일이 아니라 “어느 시점에 어떤 공통 HUD/AR 객체를 어떤 문구, 색상, 지속 시간, 우선순위로 보여줄지”에 대한 타임라인 데이터입니다. 공연 고유 로고나 아티스트 IP 이미지는 기본 기능에서 제외하고, 기본 경험은 자체 객체만으로 동작해야 합니다.

초기 탐색 단계에서 생성형 AI로 만든 객체 이미지를 추가했지만, 이 이미지는 실제 Ray-Ban Display 렌즈에 그대로 출력할 구현 예시가 아닙니다. 현재 Lens Simulator는 배경 사진이나 생성형 이미지를 렌즈 출력처럼 보여주지 않고, 앱이 실제로 생성하는 `HudVisualScene` 값을 Compose Canvas 기반 HUD primitive로 그립니다.

- `app/src/main/res/drawable-nodpi/hud_caption_line.png`
- `app/src/main/res/drawable-nodpi/hud_countdown_ring.png`
- `app/src/main/res/drawable-nodpi/hud_participation_wave.png`
- `app/src/main/res/drawable-nodpi/hud_energy_gauge.png`

위 파일들은 시각 방향 탐색용 보조 자산으로만 보관합니다. 실제 글래스 HUD 구현 기준은 이미지 파일이 아니라 `HudArObjectKey`, `HudVisualScene`, `HudRenderInstruction`, `ToolkitDisplayDocument`입니다. 이후 품질을 높일 때도 주최사 이미지를 받는 방식이 아니라 같은 객체 키를 유지하면서 앱 내부 primitive, 벡터, 또는 DAT가 허용하는 네이티브 HUD 요소로 교체합니다.

## 실시간 번역 HUD 방향

해외 아티스트 내한 공연에서는 관객이 스마트폰을 보지 않고도 멘트의 의미를 이해하는 것이 핵심 가치가 될 수 있습니다. 따라서 AR Live의 우선순위는 현장 분위기를 임의로 수치화하는 기능보다 **실시간 번역 자막**입니다.

제품 방향:

- 주최사 공식 오디오 피드가 있으면 이를 우선 사용합니다.
- 공식 피드가 없을 때만 휴대폰 마이크 기반 실험 모드를 검토합니다.
- 사전에 받은 자막/멘트 피드가 있으면 시간 코드 기반의 저지연 HUD로 처리합니다.
- 렌즈에는 긴 번역문이 아니라 1-2줄 요약 번역만 표시합니다.
- 원문 전체, 긴 문장, 번역 기록은 스마트폰 화면이나 추후 백엔드 기록으로 분리합니다.
- 녹음/저작권/공연장 정책 때문에 원본 음성 저장은 기본 기능에 포함하지 않습니다.
- 현재 앱 상태에는 `LiveTranslationState`가 포함되어 입력 소스, 처리 단계, 원문/번역문, HUD 요약, 예상 지연, 신뢰도, 정책 메모를 함께 계산합니다.
- 번역 엔진은 `LiveTranslationEngine` 인터페이스 뒤에 격리합니다. 현재 구현체는 `OfficialFeedTranslationEngine`, `PreparedSubtitleTranslationEngine`, `PhoneMicExperimentalTranslationEngine`, `DisabledTranslationEngine`이며, 실제 외부 STT/번역 API 연결 전까지 승인된 큐/자막 피드로 HUD 표시를 검증합니다.
- MVP 번역 provider는 Android 스마트폰 앱이 클라우드 STT/번역 서버와 통신하고, 결과를 1-2줄 HUD payload로 변환한 뒤 DAT 경로로 Ray-Ban Display에 전달하는 구조를 기본으로 합니다.
- 클라우드 지연, 네트워크 장애, 승인된 멘트 구간에서는 사전 자막/멘트 피드를 fallback으로 사용합니다.
- Meta AI 앱의 내장 번역 기능은 사용자가 별도로 쓸 수 있는 플랫폼 기능으로 보고, 우리 앱이 직접 호출하는 번역 provider로 의존하지 않습니다.

## 글래스 연동 구조

Meta Wearables DAT와 Meta AI 앱은 다른 경로입니다.

- **Meta Wearables DAT**: Ray-Ban Display 같은 Meta 웨어러블 기기에 접근하기 위한 Device Access Toolkit
- **Meta AI 앱**: Ray-Ban 스마트글래스의 페어링, 설정, 권한, 기기 상태 확인에 영향을 주는 공식 앱 경로

이 프로젝트는 우리 앱을 Meta AI 앱 내부에 넣는 방식이 아닙니다. Android 앱이 제품 UI와 공연 데이터를 담당하고, Meta AI 앱/DAT의 정책과 기기 연결 흐름을 존중하는 구조입니다.

현재 프로젝트에는 Meta Wearables DAT 0.9.0의 `core`, `camera`, `display`, `mockdevice` artifact를 version catalog에 정의하고 일반 Gradle 저장소에서 해석하도록 구성했습니다. 현재 구성은 별도의 GitHub Packages 토큰을 요구하지 않습니다. HUD 내부 모델은 `ToolkitDisplayDocument`로 변환되어 실제 Display renderer 연결 전에도 payload를 검증할 수 있습니다.

글래스 직접 조작은 복잡한 앱 탐색이 아니라 `GlassesInputAction`으로 정의한 짧은 입력에 한정합니다. 공연 선택, 티켓 인증, 번역/자막 소스 설정, 세션 종료는 스마트폰에서만 처리합니다.

### DAT MockDevice 확인 방식

`mwdat-mockdevice`는 Android Studio Device Manager에 별도 스마트글래스 AVD를 추가하는 기능이 아니라, Android 앱 안에서 실제 글래스 없이 DAT 연동 흐름을 테스트하기 위한 SDK 테스트 경로로 봅니다. 따라서 현재 확인 방식은 다음과 같습니다.

1. Android Studio 에뮬레이터에 이 앱을 설치합니다.
2. 앱의 `설정 > 기술 설정`에서 DAT MockDevice 상태를 확인합니다.
3. AR Live에서 생성되는 `ToolkitDisplayDocument` payload와 HUD/AR 객체 매핑을 검증합니다.
4. 하단 `AR Live` 탭에서 HUD 전송 테스트를 실행해 MockDevice 상태, 폴백 경로, payload를 확인합니다.
5. Display 기능을 제공하는 SDK/실기기 세션이 연결되면 같은 payload를 실제 DAT renderer로 전달합니다.

현재 `MetaWearablesDatMockDeviceGateway`는 실제 SDK의 초기화, MockDeviceKit 활성화, 가상 글래스 페어링, 착용 상태 설정, `DeviceSession` 시작과 종료를 수행합니다. 다만 DAT 0.9.0의 `GlassesModel`에는 Ray-Ban Display 모의 모델이 없으므로 `META_GLASSES` 세션은 Display 미지원으로 표시되며, `MetaWearablesMockDeviceHudRenderer`는 payload를 성공 처리하지 않고 스마트폰 HUD 미리보기로 폴백합니다.

DAT MockDevice가 담당하는 범위:

- DAT 패키지 의존성 해석과 앱 빌드 검증
- 실제 글래스 없이 SDK 초기화, 기기 페어링, 연결, 권한, 세션 수명주기 검증
- AR Live에서 생성한 `ToolkitDisplayDocument`와 Display 지원 여부 판정 검증
- Display 미지원 시 스마트폰 HUD 폴백과 최근 전송 감사 로그 검증

DAT MockDevice가 담당하지 않는 범위:

- Ray-Ban Display 렌즈 화면을 그대로 띄우는 시각 뷰어
- 렌즈 내 실제 밝기, 시야각, 광학 왜곡 검증
- Meta AI 앱의 실제 페어링/권한 UI 대체

따라서 렌즈 위치와 어울림은 앱 내부 Lens Simulator에서 확인합니다. AR Live 운영 검증 모드는 MockDevice의 실제 지원 상태, 스마트폰 폴백, 하단/우측/가장자리 safe area overlay를 함께 표시합니다.

### 글래스 화면 미리보기

`AR Live > HUD` 맨 위의 **글래스 화면 미리보기**는 현재 `HudState`에서 만든
`HudRenderInstruction`을 화면에 재현합니다. 실제 글래스 화면을 수신하는 미러링이
아니며, 물리 렌즈 해상도·시야각·광학 특성을 보장하는 뷰어도 아닙니다.

- **현재 HUD**: 공연 상태에서 생성되는 텍스트, 배치, 효과, 에너지 값을 반영합니다.
- **전체 화면**: 같은 내용을 큰 검은 화면에서 확인합니다. 닫기 또는 뒤로 가기로 돌아옵니다.
- **화면 고정 / 실시간 재개**: 미리보기의 내용과 애니메이션을 고정하고 최신 HUD로 복귀합니다.
- **배치 가이드**: 안전 여백과 표시 영역의 검수 가이드를 켜고 끕니다.
- **예시 칩**: 자막, 카운트다운, 웨이브, 에너지 예시를 비교합니다. 실제 공연 상태나
  전송 payload를 변경하지 않으며, HUD 전송 테스트는 항상 실제 현재 HUD를 사용합니다.

가시성을 확인할 수 있도록 효과는 표시 시간 단위로 **반복 미리보기**합니다.
실기기 전송의 수신 확인이나 실제 노출 시간을 의미하지 않습니다.
기존 DAT 상태, 자막 검수, 전송 테스트 및 감사 로그는 미리보기 아래에 유지됩니다.

### Ray-Ban Display 실기기 테스트 구조

Ray-Ban Display는 Android Studio의 일반 실행 대상처럼 APK를 직접 설치하는 기기가 아닙니다. Android Studio에서 실행하는 대상은 Android 스마트폰 또는 에뮬레이터이고, Ray-Ban Display는 Meta AI 앱을 통해 스마트폰과 페어링된 외부 웨어러블 기기로 봅니다.

실기기 실행 및 재연결 흐름:

1. Ray-Ban Display를 Meta AI 앱에서 스마트폰과 페어링하고, 개발 빌드 사용 시 개발자 모드를 켭니다.
2. Android Studio 또는 Gradle로 휴대폰에 **LumenCue Dev** (`com.k3i.lumencue.dev`)를 설치합니다. USB 설치 시 휴대폰에서 PC의 USB 디버깅 요청을 허용해야 합니다.
3. 앱의 **설정(⚙) > 기술 > Ray-Ban Display 연결**에서 **모의 기기 테스트**를 끕니다.
4. 최초 사용 시 **Meta AI 앱 등록**을 누르고 근처 기기 권한 및 Meta AI 등록 요청을 승인합니다. 이미 등록 완료라면 다시 등록할 필요가 없습니다.
5. 실제 안경 목록에서 **이 안경 연결**을 누르고 **안경 화면 연결 완료**를 확인합니다.
6. **테스트 문구 전송**을 누르면 `LumenCue display test`를 30초간 표시하도록 전송합니다.
7. **AR Live > HUD(글래스)**의 하단 **현재 HUD 전송 테스트**로 현재 공연 안내를 전송합니다. SDK 수신 결과와 실제 안경 표시를 각각 확인합니다.

설치 이후에는 PC 없이 휴대폰에서 앱을 다시 열어 연결할 수 있습니다. 상세 준비 및 오류 처리는 [실기기 연결 안내](docs/physical-rayban-display.md)를 참고하세요.

### 실기기 검증 기록 — 2026-09-28

검증 환경은 Android 휴대폰 **Samsung Galaxy S22 (SM-S901N)**, 휴대폰과 페어링된 **Meta Ray-Ban Display**, **LumenCue Dev**입니다. 현재 저장소의 DAT 의존성은 `1.0.0`입니다. 안경 펌웨어 및 Meta AI 앱 버전은 이번 기록에서 수집하지 않았습니다.

| 항목 | 확인 결과 |
| --- | --- |
| 휴대폰 설치·실행 | 개발 APK 설치 성공 및 MainActivity 실행 `Status: ok` 확인 |
| Meta AI 앱 등록·기기 인식 | 등록 완료 상태와 실제 안경의 `CONNECTED · COMPATIBLE` 확인 |
| Display 세션 | **안경 화면 연결 완료** 상태 확인 |
| 테스트 문구 전송 | `LumenCue display test` 전송 후 **SDK 수신 확인** 응답 확인 |
| 실제 렌즈 표시 | 사용자가 안경에서 앱 문구가 보인다고 확인 |
| 현재 공연 HUD 전송 | **현재 HUD 전송 테스트** 실행 후 `DAT Toolkit · Ready` 전송 기록 확인 |

실기기 출력은 `MetaWearablesDisplayGateway`가 기존 `HudRenderInstruction → ToolkitDisplayDocument`에서 텍스트를 추출해 DAT Display로 보내는 방식입니다. 현재 범위는 **최대 2개 텍스트, 각 160자**이며, 스마트폰 미리보기의 이미지·애니메이션·배치 전체를 안경에 그대로 출력하는 것은 아닙니다. SDK 수신 성공은 각각의 문구가 착용자에게 보였다는 증거와 구분합니다. 전송 지연 수치, 장시간 안정성, 공연장 시야·밝기 검증은 아직 완료하지 않았습니다.

### 실기기 캡처·녹화 미해결 사항

동일 기기에서 사용자가 다음 현상을 확인했습니다.

- LumenCue의 안경 연결 중에는 물리 캡처 버튼이 반응하지 않고, **안경 연결 종료** 후에는 다시 촬영됩니다.
- Meta AI의 **Record display**로 녹화를 먼저 시작해도, LumenCue 실행·안경 연결 과정에서 녹화가 중단됩니다. 중단이 발생하는 정확한 SDK 호출 시점은 아직 확인하지 못했습니다.

현재 앱 소스에는 `FLAG_SECURE` 등의 캡처 차단 설정이나 안경 카메라 스트림을 시작하는 호출이 없습니다. 다만 이 확인만으로 원인을 SDK 또는 펌웨어의 확정된 제한으로 판단하지 않습니다. Meta AI·펌웨어 버전과 재현 시점 로그를 수집해 앱 세션 처리 및 플랫폼 동작을 추가 진단해야 합니다. **실제 안경 화면 녹화는 성공 항목에 포함하지 않습니다.** 스마트폰 HUD 미리보기 캡처 역시 실제 렌즈 화면 녹화와 구분합니다.

## 플랫폼 제약

Ray-Ban Display는 Meta가 통제하는 플랫폼입니다. 실제 배포와 기능 범위는 Developer Preview 접근 권한, SDK 라이선스, 앱 정책, Meta AI 앱의 기기 관리 흐름, 지역별 기능 제공 여부에 영향을 받습니다.

Meta 승인 또는 공식 SDK 확인이 필요한 영역:

- 검증된 텍스트 외 Ray-Ban Display 렌즈 HUD 출력 범위 및 운영 배포 권한
- Meta AI 앱의 페어링/설정/권한 흐름과 외부 Android 앱의 호환성
- 글래스 카메라/마이크 스트림 직접 접근
- Neural Band 제스처를 외부 앱 입력으로 사용하는 기능

따라서 글래스 전용 기능은 `HudState`, `HudVisualScene`, `HudRenderInstruction`, `GlassesInputAction` 같은 중립 모델 뒤에 격리합니다. 현재 시각화 방향은 공간 고정 3D AR 객체가 아니라 Ray-Ban Display의 작은 렌즈 HUD에 맞춘 짧은 장면입니다. `HudVisualScene`은 콘서트별 이미지 경로가 아니라 공통 `HudArObjectKey`를 우선 참조합니다. 정책상 직접 출력이 제한되면 스마트폰 HUD, 알림, 오디오/TTS, 게시판 중심의 대체 경로를 유지합니다.

AI Agent 기능은 현재 제품 범위에서 제외합니다. Meta AI 앱은 Ray-Ban 기기 관리와 정책 의존성으로만 추적합니다.

## 백엔드/DB 방향

앱의 기본 모드는 로컬 JSON asset과 `SharedPreferences` 기반입니다. `backend/`에는 외부 Python 패키지 없이 실행되는 custom backend MVP가 있으며, SQLite에 공연 패키지 버전, 티켓 체크인 결과, audience session, HUD dispatch 감사 로그를 저장합니다.

Android 빌드에 `LUMENCUE_API_BASE_URL` Gradle 속성이 설정되면 배포된 공연 패키지를 API에서 조회합니다. 원격 응답이 비어 있거나 검증에 실패하거나 서버에 연결할 수 없으면 기존 로컬 패키지로 전환합니다. 운영 배포 전에는 SQLite를 운영 DB로 교체하고 TLS, secret manager, rate limiting, 실제 파트너 인증을 추가해야 합니다.

실제 제품 배포에는 다음 외부 시스템이 필요합니다.

- 공연사 백오피스
- 공연 패키지 업로드/검수/배포 API
- 원격 DB
- 티켓 예매처 또는 입장 인증 연동
- 사용자 세션/게시판 저장소
- 글래스 HUD dispatch 감사 로그
- 푸시/긴급 공지 서버

DB는 우리 앱이 임의로 만든 독립 원본이 아니라, 공연 주최사/기획사 CMS, 티켓 예매처, 공연장 운영 시스템, 아티스트 매니지먼트의 원본 데이터를 검수, 정규화, 버전 고정, 배포하는 서비스 DB로 설계합니다.

필수 필드 누락, 승인 버전 불일치, 티켓/촬영 정책 누락, 큐 시간 불일치는 production 배포 차단 사유로 봅니다.

## 개인정보 원칙

콘서트장은 주변 관객이 많은 환경이므로 MVP에 다음 기능은 포함하지 않습니다.

- 얼굴 인식
- 관객 자동 촬영
- 상시 녹음
- 원본 음성 저장
- 백그라운드 마이크 수집

휴대폰 마이크 기반 처리는 공식 오디오 피드가 없을 때 검토하는 실험 경로입니다. 사용자가 직접 시작하고 Android 권한을 승인한 경우에만 동작해야 하며, 원본 음성 저장은 기본 기능에 포함하지 않습니다.

앱에는 주최사, 공연장 운영, 법무 검토를 위한 고지문/동의 문안 초안이 포함되어 있습니다. 이 문안은 실제 법률 자문을 대체하지 않으며, 공연장 정책과 지역 법규에 맞춘 검토 후 배포해야 합니다.

## 프로젝트 구조

- `app/src/main/java/com/k3i/lumencue/ConcertExperience.kt`: 공연 상태, 셋리스트, AR 큐, HUD 상태, 실시간 번역 HUD 상태, 공통 HUD/AR 객체, HUD 시각 장면, 이벤트 참여 모델
- `app/src/main/java/com/k3i/lumencue/MainActivity.kt`: 앱 상태, 화면 전환, 설정, Companion, 게시판, 권한 요청, HUD/AR 객체 프리뷰 렌더링
- `app/src/main/java/com/k3i/lumencue/HomeScreen.kt`: 현재 콘서트 동기화 홈, 참여 반응, 콘서트 이벤트
- `app/src/main/java/com/k3i/lumencue/UiCommon.kt`: 공통 화면 프레임과 카드 색상/섹션 타이틀
- `app/src/main/java/com/k3i/lumencue/AudioEnergyEffect.kt`: 실시간 번역 실험을 위한 Android `AudioRecord` 후보 경로
- `app/src/main/java/com/k3i/lumencue/ConcertEventPackageParser.kt`: 주최사 공연 패키지 JSON 파싱과 운영 검증
- `app/src/main/java/com/k3i/lumencue/ConcertEventAssets.kt`: 로컬 공연 패키지 로드 리포트와 예비 데이터 전환
- `app/src/main/java/com/k3i/lumencue/AppSessionStorage.kt`: 선택 공연, 세션 요약, 이벤트 참여 로컬 저장/복원
- `app/src/main/java/com/k3i/lumencue/GlassesHudRenderer.kt`: HUD 렌더러, Toolkit payload, fallback plan
- `app/src/main/java/com/k3i/lumencue/MetaWearablesDisplayGateway.kt`: 실기기 등록, 기기 탐색, Display 세션, 텍스트 전송 및 연결 종료
- `app/src/main/java/com/k3i/lumencue/PhysicalGlassesCard.kt`: 실기기 연결·테스트 문구 전송 UI
- `docs/physical-rayban-display.md`: 실기기 준비 및 연결 절차
- `app/src/main/java/com/k3i/lumencue/GlassesDispatchStorage.kt`: 글래스 HUD dispatch/fallback 기록 로컬 저장/복원
- `app/src/main/java/com/k3i/lumencue/ConcertRepository.kt`: 로컬 asset, 원격 API 후보, fallback 데이터를 감싸는 저장소 계약
- `app/src/main/java/com/k3i/lumencue/RemoteApiConcertRepository.kt`: 배포 공연 목록/패키지 API 클라이언트, 응답 검증, 오류 격리
- `app/src/main/java/com/k3i/lumencue/BackendArchitecture.kt`: 제품 배포에 필요한 백엔드/API/DB 요구사항 모델
- `app/src/main/java/com/k3i/lumencue/PartnerDataMapping.kt`: 파트너 원본 필드와 앱 패키지 필드의 매핑/검증/배포 차단 모델
- `docs/concert-event-package.schema.json`: 주최사 제공 공연 패키지 JSON 스키마 초안
- `docs/ticketing-integration.md`: 서명 체크인 webhook, 재시도, 장애 폴백 계약
- `docs/data-lifecycle-policy.md`: 계정·티켓·세션·게시글·저장 순간의 보존 및 삭제 정책
- `backend/`: SQLite 마이그레이션, 공연 패키지/티켓/감사 로그 서비스, HTTP API, OpenAPI 계약, 단위 테스트

## 빌드

프로젝트 루트에서 실행합니다.

```powershell
.\gradlew.bat :app:assembleDebug
```

로컬 백엔드의 배포 패키지를 Android 에뮬레이터에서 사용할 때는 백엔드를 먼저 실행한 뒤 API 주소를 Gradle 속성으로 전달합니다. 에뮬레이터에서 호스트 PC는 `10.0.2.2`입니다. debug 빌드만 로컬 HTTP를 허용하며 release 빌드는 HTTPS를 사용해야 합니다.

```powershell
Set-Location .\backend
python -m lumencue_backend init-db
python -m lumencue_backend import-package ..\app\src\main\assets\concert_packages\lumen_live_glass_horizon.json --version 1 --publish --approved-by local-ops
python -m lumencue_backend import-ticket --provider partner-ticket-provider --ticket-id LC-AR-0918-1208 --event-id lumen-2026-seoul --checked-in
python -m lumencue_backend serve

Set-Location ..
.\gradlew.bat :app:assembleDebug -PLUMENCUE_API_BASE_URL=http://10.0.2.2:8080
```

에뮬레이터 또는 연결 기기에 설치:

```powershell
.\gradlew.bat :app:installDebug
```

개발 빌드는 **LumenCue Dev** (`com.k3i.lumencue.dev`), release 빌드는 `com.k3i.lumencue`입니다. 개발 빌드의 런처 Activity는 `com.k3i.lumencue.MainActivity`입니다. 아래 `<휴대폰 ID>`는 `adb devices -l`에서 확인한 대상으로 교체합니다.

```powershell
adb -s <휴대폰 ID> shell am start -W -n com.k3i.lumencue.dev/com.k3i.lumencue.MainActivity
```

단위 테스트 실행:

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

Windows 한글 경로에서 Gradle 테스트 워커가 클래스 경로를 읽지 못하는 경우, 검증 스크립트를 사용합니다. 이 스크립트는 임시 영문 드라이브를 연결해 단위 테스트와 디버그 APK 빌드를 실행한 뒤 연결을 자동 해제합니다.

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\verify-windows.ps1
```

검증 스크립트는 기본적으로 Android Studio에 포함된 JBR을 사용합니다. 해당 JBR에서 Gradle JVM 초기화가 실패하는 환경에서는 Temurin JDK 21을 `JAVA_HOME`으로 지정합니다.

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot'
```

## 다음 작업

### Meta Wearables DAT / Ray-Ban Display

- [x] DAT 0.9.0 SDK 의존성 해석 및 `minSdk 29` 기준 빌드 검증
- [x] `mwdat-mockdevice` 실제 SDK 초기화, 페어링, 연결, 세션 수명주기 구현
- [x] MockDevice Display 지원 여부에 따른 HUD 전송/스마트폰 폴백 분기
- [ ] Wearables Developer Center에서 실제 application id/client token을 발급하고 manifest placeholder 교체
- [x] `MetaWearablesDisplayGateway`를 통한 실제 DAT Display 세션 및 텍스트 HUD 전송 구현
- [x] Android 스마트폰 + Meta AI 앱 + Ray-Ban Display 환경에서 연결·전송 및 안경 내 문구 표시 확인 (2026-09-28)
- [ ] 안경 연결 중 물리 캡처 불가 및 화면 녹화 중단 원인 진단: 버전·재현 로그 수집
- [ ] Meta AI 앱의 페어링/설정/권한 흐름과 DAT 연동 충돌 여부 검증

### 실시간 번역 HUD

- [x] AR Live에 실시간 번역 HUD 실험 카드 추가
- [x] 현장 에너지/관객 반응 레벨 중심 UI를 핵심 흐름에서 제거
- [x] 번역 입력 소스 모델 정의: 공식 오디오 피드, 휴대폰 마이크 실험, 사전 자막 피드
- [x] AR Live 카드에 STT -> 번역 -> 1-2줄 HUD 요약 상태 표시
- [x] 실제 STT/번역 엔진 연결 전 인터페이스와 fallback 정책 구현
- [x] 클라우드/온디바이스 STT 및 번역 provider 후보 비교 후 구현체 선택
- [x] 번역 지연 시간, 오역, 욕설/민감 표현, 공연장 녹음 정책 대응 규칙 정의
- [x] Lens Simulator에서 번역 자막 길이, 위치, 표시 시간 검수 기능 강화

### 제품/백엔드

- [x] 외부 의존성 없는 custom backend MVP와 SQLite 스키마 구현
- [x] 주최사용 관리자 웹 구현: 패키지 등록/승인/배포, 긴급 공지, 티켓 체크인, HUD 감사 로그
- [x] 공연 패키지 업로드/검수/버전 배포 API 구현
- [x] Android 원격 공연 패키지 조회와 로컬 fallback 연결
- [x] 공연 패키지 승인 버전, 리허설 변경분, 긴급 공지 충돌 해결 정책 구현
- [x] 티켓 예매처/입장 시스템 연동 방식 확정: HMAC 서명 webhook + 수동 import 폴백
- [x] 사용자 계정, 세션, 게시판 글, 순간 기록 저장/삭제 정책 확정 및 자동 정리/본인 삭제 API 구현
- [x] 앱의 로컬 HUD dispatch/fallback 로그를 서버 감사 로그 API에 자동 동기화하고 재시도 중복 제거

### 검증

- [ ] 실제 Android 기기에서 번역/자막 흐름, 화면 크기, 스크롤, 텍스트 잘림 확인
- [x] 페어링된 Ray-Ban Display 실기기에서 등록 상태, DAT Display 연결 및 텍스트 HUD 출력 확인
- [ ] 텍스트 외 HUD 출력 범위, 전송 지연 및 장시간 연결 안정성 검증
- [ ] 공연장 환경에서 글래스 HUD가 무대를 방해하지 않는지 시야/밝기/표시 시간 검증
