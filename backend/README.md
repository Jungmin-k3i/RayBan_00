# LumenCue Backend MVP

Android 앱의 로컬 공연 패키지를 서버 API와 SQLite DB로 옮기기 위한 첫 백엔드 구현입니다. 외부 Python 패키지 없이 Python 3.11 표준 라이브러리만 사용하므로 로컬에서 바로 실행할 수 있습니다.

## 현재 범위

- 공연 패키지 draft 등록, 검증, 명시적 승인, 기준 버전 충돌 검사, 버전별 배포
- `rehearsal`/`production`/`hotfix` release channel과 리허설 변경 요약 관리
- `Warning`/`Critical` 긴급 공지 우선순위 충돌 검사 및 Android HUD 전달
- 배포된 공연 목록과 최신 패키지 조회
- 파트너 티켓/입장 결과 import
- provider별 HMAC-SHA256 서명 체크인 webhook과 event id 기반 재시도 중복 제거
- 입장 확인 티켓에만 12시간 audience session 발급
- audience session 기반 HUD dispatch 감사 로그 저장 및 `clientRecordId` 재시도 중복 제거
- 관리자 API key와 audience Bearer token 분리
- 티켓 사용자 식별자는 원문 대신 SHA-256 값만 선택 저장
- 주최사 운영자가 브라우저에서 배포/공지/체크인/감사 로그를 처리하는 반응형 백오피스
- 관객 본인 데이터 삭제 API와 30일 운영 데이터/365일 삭제 영수증 보존 정책

실제 예매처와의 계약·credential 교환, PostgreSQL, 사용자 실명 계정, 푸시, STT/번역 서버는 아직 포함하지 않습니다.

## 실행

저장소의 `backend` 폴더에서 실행합니다.

```powershell
python -m lumencue_backend init-db
python -m lumencue_backend import-package ..\app\src\main\assets\concert_packages\lumen_live_glass_horizon.json --version 1 --publish --approved-by local-ops
python -m lumencue_backend import-ticket --provider partner-ticket-provider --ticket-id LC-AR-0918-1208 --event-id lumen-2026-seoul --checked-in
$env:LUMENCUE_ADMIN_KEY='로컬에서만-사용할-임의의-긴-키'
python -m lumencue_backend serve
```

기본 주소는 `http://127.0.0.1:8080`, DB는 `backend/var/lumencue.db`입니다. 외부 인터페이스에 바인딩하기 전에 TLS reverse proxy, secret manager, rate limiting을 추가해야 합니다.

서버를 시작한 뒤 `http://127.0.0.1:8080/admin`을 열고 `LUMENCUE_ADMIN_KEY`에 지정한 키를 입력하면 운영 콘솔을 사용할 수 있습니다. 키는 현재 브라우저 탭의 `sessionStorage`에만 보관됩니다.

운영 콘솔에서 지원하는 작업:

- 공연 패키지 JSON 검증 및 새 버전 draft 등록
- 운영자 승인 후 production/hotfix 버전 배포
- Warning/Critical 긴급 공지 발행
- 파트너 티켓 체크인 결과 반영
- 공연별 HUD 전송 감사 로그 확인

## 주요 API

| Method | Path | 인증 | 목적 |
| --- | --- | --- | --- |
| `GET` | `/health` | 없음 | 서버와 DB 상태 |
| `GET` | `/v1/concerts` | 없음 | 배포된 공연 목록 |
| `GET` | `/v1/concerts/{eventId}/package` | 없음 | 최신 배포 패키지 |
| `GET` | `/v1/data-policy` | 없음 | 현재 사용자 데이터 보존·삭제 정책 |
| `POST` | `/v1/partners/tickets/events` | HMAC 헤더 | 예매처 체크인/취소 이벤트 수신 |
| `POST` | `/v1/tickets/verify` | 없음 | 입장 확인 후 audience token 발급 |
| `POST` | `/v1/device-dispatch-logs` | Bearer | HUD 전송 감사 로그 기록 |
| `DELETE` | `/v1/me/data` | Bearer | 현재 관객의 티켓·세션·감사 로그 삭제 |
| `GET` | `/v1/admin/overview` | `X-Admin-Key` | 운영 콘솔용 공연/배포/공지 요약 |
| `POST` | `/v1/admin/retention/purge` | `X-Admin-Key` | 보존 기간 만료 데이터 preview/삭제 |
| `POST` | `/v1/admin/concert-packages` | `X-Admin-Key` | 패키지 draft 등록 |
| `POST` | `/v1/admin/concert-packages/{eventId}/versions/{version}/approve` | `X-Admin-Key` | 검증된 패키지 운영 승인 |
| `POST` | `/v1/admin/concert-packages/{eventId}/versions/{version}/publish` | `X-Admin-Key` | 패키지 배포 |
| `POST` | `/v1/admin/concerts/{eventId}/emergency-notices` | `X-Admin-Key` | 긴급 공지 생성/우선순위 충돌 검사 |
| `POST` | `/v1/admin/tickets` | `X-Admin-Key` | 파트너 티켓 상태 import |
| `GET` | `/v1/admin/device-dispatch-logs` | `X-Admin-Key` | 감사 로그 조회 |

세부 요청·응답 계약은 `openapi.yaml`에 있습니다.

티켓 연동 헤더, 서명 원문, 재시도 규칙은 [`../docs/ticketing-integration.md`](../docs/ticketing-integration.md), 데이터별 수명과 삭제 범위는 [`../docs/data-lifecycle-policy.md`](../docs/data-lifecycle-policy.md)에 고정했습니다.

## 배포 및 충돌 정책

- 첫 production 배포는 `baseVersion` 없이 생성하고, 운영자 승인 후에만 배포할 수 있습니다.
- 이후 버전은 현재 배포 버전을 `baseVersion`으로 지정하고 `changeSummary`에 리허설 변경 내용을 기록해야 합니다.
- draft가 승인된 뒤 다른 버전이 먼저 배포되면 `STALE_BASE_VERSION`으로 차단합니다.
- `rehearsal` 채널은 검수 전용이며 public package로 배포할 수 없습니다.
- 긴급 공지는 최대 24시간 동안 활성화됩니다. `Critical` 공지는 `Warning` 공지로 덮어쓸 수 없고, Android HUD에서는 일반 AR 큐보다 먼저 표시됩니다.

기존 배포본을 기준으로 다음 버전을 올리는 예시는 다음과 같습니다.

```powershell
python -m lumencue_backend import-package .\updated.json --version 2 --release-channel production --base-version 1 --change-summary "최종 리허설 큐 반영" --publish --approved-by ops-lead
```

## Android 앱 연결

Android 앱은 API 주소가 없으면 기존 로컬 asset을 사용합니다. 에뮬레이터에서 로컬 백엔드의 배포 패키지를 읽으려면 프로젝트 루트에서 다음과 같이 debug 빌드합니다.

```powershell
.\gradlew.bat :app:assembleDebug -PLUMENCUE_API_BASE_URL=http://10.0.2.2:8080
```

`10.0.2.2`는 Android 에뮬레이터에서 호스트 PC를 가리킵니다. debug manifest만 로컬 HTTP를 허용합니다. release 환경에는 반드시 HTTPS URL을 사용해야 하며, API 조회나 패키지 검증에 실패하면 앱은 번들된 로컬 공연 패키지로 전환합니다.

HUD 감사 로그 자동 동기화에는 앱 패키지의 `ticketPolicy.provider`, `ticketId`, 공연 ID와 일치하는 체크인 티켓이 백엔드에 import되어 있어야 합니다. 앱은 원문 사용자 식별자를 전송하지 않고 단기 audience token과 전송 기록 UUID만 사용합니다.

## 데이터 보존 작업

삭제 예정 건수 확인 후 실제 정리를 실행합니다.

```powershell
python -m lumencue_backend purge-data
python -m lumencue_backend purge-data --execute
```

## 테스트

```powershell
python -m unittest discover -s tests -v
```

테스트는 임시 SQLite DB와 임의 로컬 포트만 사용하며 저장소의 실제 DB를 변경하지 않습니다.

## 환경 변수

- `LUMENCUE_DB_PATH`: SQLite 파일 경로
- `LUMENCUE_HOST`: 기본 `127.0.0.1`
- `LUMENCUE_PORT`: 기본 `8080`
- `LUMENCUE_ADMIN_KEY`: 설정하지 않으면 모든 관리자 API 비활성화
- `LUMENCUE_ALLOWED_ORIGIN`: 필요한 정확한 프런트엔드 origin만 설정. 기본값은 CORS 비활성화
- `LUMENCUE_TICKET_PROVIDER_SECRETS`: provider 이름을 key로, webhook HMAC secret을 value로 갖는 JSON object
- `LUMENCUE_SUBJECT_HASH_KEY`: partner subject ID를 keyed HMAC으로 가명화할 production secret
