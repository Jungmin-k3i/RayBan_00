# RayBan_0.0

RayBan_0.0은 실제 콘서트 현장에서 디스플레이가 있는 Meta Ray-Ban Display 계열 스마트글래스를 활용해 관객 경험을 확장하는 Android Compose 앱입니다.

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

현재 저장소는 Android 네이티브 앱입니다. 웹앱이 아니며, 아직 실제 백엔드 서버/원격 DB/티켓 예매처/Meta 실기기 연동은 연결되어 있지 않습니다.

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
- 스마트폰 HUD 미리보기 렌더러와 DAT 대기 렌더러 분리
- DAT MockDevice 테스트 경로를 별도 렌더러로 모델링하고, AR Live 전송 테스트에서 MockDevice 경로를 우선 사용
- AR Live 운영 검증 모드에서 MockDevice accepted 상태, payload 요약, Lens Simulator safe area overlay를 함께 표시
- HUD dispatch/fallback 모델, 최근 전송 기록, 로컬 감사 로그 복원
- 글래스 직접 출력 제한 시 스마트폰 HUD, Android 알림, 오디오/TTS fallback plan
- 글래스 입력 정책과 스마트폰 기반 입력 시뮬레이터
- 티켓 패스, 공연장 미니맵, 안전 모드, AR 타임라인
- 게시판 진입 시 공연을 먼저 선택하고, 티켓/입장 인증된 공연 참가자만 입장 가능한 공연별 게시판
- AR Live의 실시간 번역 HUD 실험 카드
- 원본 음성 저장 없는 개인정보 보수 설계
- 선택 공연, 세션 요약, 이벤트별 참여 횟수 로컬 복원

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

현재 프로젝트에는 `facebook/meta-wearables-dat-android` 공식 저장소 기준의 Gradle repository와 version catalog artifact 정의를 반영했습니다. GitHub Packages 접근에는 `GITHUB_TOKEN` 환경변수 또는 `local.properties`의 `github_token` 값이 필요합니다. 토큰이 없을 때도 앱 빌드와 테스트는 유지되고, 내부 모델은 `ToolkitDisplayDocument`로 변환되어 실제 SDK 연결 전까지 payload를 검증합니다.

글래스 직접 조작은 복잡한 앱 탐색이 아니라 `GlassesInputAction`으로 정의한 짧은 입력에 한정합니다. 공연 선택, 티켓 인증, 번역/자막 소스 설정, 세션 종료는 스마트폰에서만 처리합니다.

### DAT MockDevice 확인 방식

`mwdat-mockdevice`는 Android Studio Device Manager에 별도 스마트글래스 AVD를 추가하는 기능이 아니라, Android 앱 안에서 실제 글래스 없이 DAT 연동 흐름을 테스트하기 위한 SDK 테스트 경로로 봅니다. 따라서 현재 확인 방식은 다음과 같습니다.

1. Android Studio 에뮬레이터에 이 앱을 설치합니다.
2. 앱의 `설정 > 기술 설정`에서 DAT MockDevice 상태를 확인합니다.
3. AR Live에서 생성되는 `ToolkitDisplayDocument` payload와 HUD/AR 객체 매핑을 검증합니다.
4. 하단 `AR Live` 탭에서 HUD 전송 테스트를 실행해 MockDevice 경로의 accepted 상태와 payload를 확인합니다.
5. 이후 공식 DAT SDK 문서의 MockDeviceKit 세션 API에 맞춰 실제 SDK 호출 범위를 확장합니다.

현재 로컬 환경에는 GitHub Packages 토큰이 설정되어 DAT artifact 다운로드와 컴파일이 가능한 상태입니다. 임시 검증 단계에서는 AR Live의 `MetaWearablesMockDeviceHudRenderer`가 `ToolkitDisplayDocument` payload를 accepted 처리합니다.

DAT MockDevice가 담당하는 범위:

- DAT 패키지 의존성 해석과 앱 빌드 검증
- 실제 글래스 없이 기기 연결/권한/세션 흐름을 붙일 준비
- AR Live에서 생성한 `ToolkitDisplayDocument` payload의 전송 경로 검증
- 최근 전송 기록과 감사 로그에 MockDevice route 저장

DAT MockDevice가 담당하지 않는 범위:

- Ray-Ban Display 렌즈 화면을 그대로 띄우는 시각 뷰어
- 렌즈 내 실제 밝기, 시야각, 광학 왜곡 검증
- Meta AI 앱의 실제 페어링/권한 UI 대체

따라서 렌즈 위치와 어울림은 앱 내부 Lens Simulator에서 확인합니다. AR Live 운영 검증 모드는 MockDevice accepted 상태와 함께 하단/우측/가장자리 safe area overlay를 표시합니다.

### Ray-Ban Display 실기기 테스트 구조

Ray-Ban Display는 Android Studio의 일반 실행 대상처럼 APK를 직접 설치하는 기기가 아닙니다. Android Studio에서 실행하는 대상은 Android 스마트폰 또는 에뮬레이터이고, Ray-Ban Display는 Meta AI 앱을 통해 스마트폰과 페어링된 외부 웨어러블 기기로 봅니다.

실기기 테스트의 기본 흐름:

1. Ray-Ban Display를 Meta AI 앱에서 스마트폰과 정상 페어링합니다.
2. Android Studio 또는 Gradle로 이 앱을 Android 스마트폰에 설치합니다.
3. Wearables Developer Center의 application id와 DAT 접근 권한을 확인합니다.
4. `AndroidManifest.xml`에 `com.meta.wearable.mwdat.APPLICATION_ID` 메타데이터를 반영합니다.
5. `MetaWearablesToolkitHudRenderer`를 실제 DAT 세션/디바이스 호출로 교체합니다.
6. 앱의 AR Live에서 `HudRenderInstruction`과 `ToolkitDisplayDocument`를 생성해 Ray-Ban Display로 전송합니다.
7. 글래스에서 실제 표시, 거부, 권한 실패, Meta AI 앱 설정 충돌, 지연 시간을 확인합니다.

따라서 현재 단계에서 가능한 테스트는 Android 앱 실행, DAT artifact 빌드 검증, MockDevice route 검증, Lens Simulator 위치 검수입니다. 실제 렌즈 HUD 출력은 DAT 실기기 renderer 구현과 Meta 쪽 권한/앱 등록 확인 이후에 검증합니다.

## 플랫폼 제약

Ray-Ban Display는 Meta가 통제하는 플랫폼입니다. 실제 배포와 기능 범위는 Developer Preview 접근 권한, SDK 라이선스, 앱 정책, Meta AI 앱의 기기 관리 흐름, 지역별 기능 제공 여부에 영향을 받습니다.

Meta 승인 또는 공식 SDK 확인이 필요한 영역:

- Ray-Ban Display 렌즈 AR HUD 직접 출력
- Meta AI 앱의 페어링/설정/권한 흐름과 외부 Android 앱의 호환성
- 글래스 카메라/마이크 스트림 직접 접근
- Neural Band 제스처를 외부 앱 입력으로 사용하는 기능

따라서 글래스 전용 기능은 `HudState`, `HudVisualScene`, `HudRenderInstruction`, `GlassesInputAction` 같은 중립 모델 뒤에 격리합니다. 현재 시각화 방향은 공간 고정 3D AR 객체가 아니라 Ray-Ban Display의 작은 렌즈 HUD에 맞춘 짧은 장면입니다. `HudVisualScene`은 콘서트별 이미지 경로가 아니라 공통 `HudArObjectKey`를 우선 참조합니다. 정책상 직접 출력이 제한되면 스마트폰 HUD, 알림, 오디오/TTS, 게시판 중심의 대체 경로를 유지합니다.

AI Agent 기능은 현재 제품 범위에서 제외합니다. Meta AI 앱은 Ray-Ban 기기 관리와 정책 의존성으로만 추적합니다.

## 백엔드/DB 방향

현재 앱은 로컬 JSON asset과 `SharedPreferences` 기반입니다.

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

- `app/src/main/java/com/k3i/rayban_00/ConcertExperience.kt`: 공연 상태, 셋리스트, AR 큐, HUD 상태, 실시간 번역 HUD 상태, 공통 HUD/AR 객체, HUD 시각 장면, 이벤트 참여 모델
- `app/src/main/java/com/k3i/rayban_00/MainActivity.kt`: 앱 상태, 화면 전환, 설정, Companion, 게시판, 권한 요청, HUD/AR 객체 프리뷰 렌더링
- `app/src/main/java/com/k3i/rayban_00/HomeScreen.kt`: 현재 콘서트 동기화 홈, 참여 반응, 콘서트 이벤트
- `app/src/main/java/com/k3i/rayban_00/UiCommon.kt`: 공통 화면 프레임과 카드 색상/섹션 타이틀
- `app/src/main/java/com/k3i/rayban_00/AudioEnergyEffect.kt`: 실시간 번역 실험을 위한 Android `AudioRecord` 후보 경로
- `app/src/main/java/com/k3i/rayban_00/ConcertEventPackageParser.kt`: 주최사 공연 패키지 JSON 파싱과 운영 검증
- `app/src/main/java/com/k3i/rayban_00/ConcertEventAssets.kt`: 로컬 공연 패키지 로드 리포트와 예비 데이터 전환
- `app/src/main/java/com/k3i/rayban_00/AppSessionStorage.kt`: 선택 공연, 세션 요약, 이벤트 참여 로컬 저장/복원
- `app/src/main/java/com/k3i/rayban_00/GlassesHudRenderer.kt`: HUD 렌더러, Toolkit payload, fallback plan
- `app/src/main/java/com/k3i/rayban_00/GlassesDispatchStorage.kt`: 글래스 HUD dispatch/fallback 기록 로컬 저장/복원
- `app/src/main/java/com/k3i/rayban_00/ConcertRepository.kt`: 로컬 asset, 원격 API 후보, fallback 데이터를 감싸는 저장소 계약
- `app/src/main/java/com/k3i/rayban_00/BackendArchitecture.kt`: 제품 배포에 필요한 백엔드/API/DB 요구사항 모델
- `app/src/main/java/com/k3i/rayban_00/PartnerDataMapping.kt`: 파트너 원본 필드와 앱 패키지 필드의 매핑/검증/배포 차단 모델
- `docs/concert-event-package.schema.json`: 주최사 제공 공연 패키지 JSON 스키마 초안

## 빌드

프로젝트 루트에서 실행합니다.

```powershell
.\gradlew.bat :app:assembleDebug
```

에뮬레이터 또는 연결 기기에 설치:

```powershell
.\gradlew.bat :app:installDebug
```

현재 앱 ID는 `com.k3i.rayban_00`이며, 런처 Activity는 `com.k3i.rayban_00/.MainActivity`입니다.

```powershell
C:\Users\ADMIN\AppData\Local\Android\Sdk\platform-tools\adb.exe shell am start -W -n com.k3i.rayban_00/.MainActivity
```

단위 테스트 실행:

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

현재 환경에서 `JAVA_HOME`이 Android Studio JBR을 가리키면 Gradle JVM 초기화가 실패할 수 있습니다. 이 경우 Temurin JDK 21을 지정합니다.

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot'
```

## 다음 작업

### Meta Wearables DAT / Ray-Ban Display

- [x] GitHub Packages 접근용 `GITHUB_TOKEN` 또는 `local.properties`의 `github_token` 설정
- [x] DAT SDK 의존성 다운로드 및 `minSdk 29` 기준 빌드 검증
- [x] AR Live 전송 테스트를 임시 MockDevice accepted 경로와 연결
- [ ] `mwdat-mockdevice` 실제 SDK 클래스 호출 확장
- [ ] Wearables Developer Center에서 application id 발급 후 manifest에 `com.meta.wearable.mwdat.APPLICATION_ID` 추가
- [ ] `MetaWearablesToolkitHudRenderer`를 실제 DAT Android renderer로 구현
- [ ] Android 스마트폰 + Meta AI 앱 + Ray-Ban Display 페어링 상태에서 실기기 전송 테스트 실행
- [ ] Meta AI 앱의 페어링/설정/권한 흐름과 DAT 연동 충돌 여부 검증

### 실시간 번역 HUD

- [x] AR Live에 실시간 번역 HUD 실험 카드 추가
- [x] 현장 에너지/관객 반응 레벨 중심 UI를 핵심 흐름에서 제거
- [x] 번역 입력 소스 모델 정의: 공식 오디오 피드, 휴대폰 마이크 실험, 사전 자막 피드
- [x] AR Live 카드에 STT -> 번역 -> 1-2줄 HUD 요약 상태 표시
- [x] 실제 STT/번역 엔진 연결 전 인터페이스와 fallback 정책 구현
- [x] 클라우드/온디바이스 STT 및 번역 provider 후보 비교 후 구현체 선택
- [ ] 번역 지연 시간, 오역, 욕설/민감 표현, 공연장 녹음 정책 대응 규칙 정의
- [ ] Lens Simulator에서 번역 자막 길이, 위치, 표시 시간 검수 기능 강화

### 제품/백엔드

- [ ] API 서버 후보 결정: Firebase, Supabase, custom backend, 회사 내부 서버 중 선택
- [ ] 주최사용 관리자 웹 또는 백오피스 앱 설계
- [ ] 공연 패키지 업로드/검수/배포 API 구현
- [ ] 공연 패키지 승인 버전, 리허설 변경분, 긴급 공지 충돌 해결 정책 구현
- [ ] 티켓 예매처 또는 입장 인증 시스템 연동 방식 확정
- [ ] 사용자 계정, 세션, 게시판 글, 순간 기록 저장/삭제 정책 확정
- [ ] 로컬 글래스 HUD dispatch/fallback 로그를 서버 운영 감사 로그로 동기화

### 검증

- [ ] 실제 Android 기기에서 번역/자막 흐름, 화면 크기, 스크롤, 텍스트 잘림 확인
- [ ] Ray-Ban Display 실기기에서 Meta AI 앱 페어링, DAT 접근 가능성, HUD 출력 가능 범위, 지연 시간 확인
- [ ] 공연장 환경에서 글래스 HUD가 무대를 방해하지 않는지 시야/밝기/표시 시간 검증
# RayBan_0.0
