# 사용자 데이터 보존·삭제 정책

정책 버전: 2026-09-18

## 기본 원칙

- MVP에서는 LumenCue 이메일/비밀번호/실명 계정을 만들지 않습니다.
- 티켓 범위의 가명 세션만 발급하고 access token 원문은 DB에 저장하지 않습니다.
- 앱은 예매·결제 원본, 음성 녹음, 생체정보를 서버로 전송하지 않습니다.
- 게시판과 저장 순간은 현재 로컬 데모 기능이며 서버 저장 대상이 아닙니다.
- production에서는 `LUMENCUE_SUBJECT_HASH_KEY`를 설정해 partner subject를 keyed HMAC으로 가명화합니다.

## 데이터별 수명

| 데이터 | 저장 내용 | 자동 보존 | 사용자 삭제 |
| --- | --- | --- | --- |
| 계정 | MVP에서는 수집하지 않음 | 해당 없음 | 해당 없음 |
| 티켓 | provider, ticket id, 입장 상태, 선택적 subject hash | 마지막 갱신 후 30일 | 즉시 삭제 |
| 관객 세션 | access token hash, 공연·티켓 연결, 만료 시각 | 사용 가능 12시간, 만료 후 30일에 삭제 | 즉시 삭제 |
| HUD 감사 로그 | renderer, route, 오류 코드, 최소 metadata | 연결 세션 만료 후 30일 | 세션과 함께 삭제 |
| provider 이벤트 | event id, type, payload hash, 처리 시각 | 수신 후 30일 | 티켓 원문이 없으므로 자동 만료 |
| 게시판 글·댓글 | 현재 앱 메모리의 데모 데이터 | 앱 프로세스 수명 | 앱 종료/로컬 초기화 |
| 저장 순간 | 현재 공연 세션의 곡·큐 참조 | 공연 세션 수명 | 세션 초기화 |
| 삭제 영수증 | 개인 식별자 없는 처리 건수와 완료 시각 | 365일 | 기간 종료 후 자동 삭제 |

## 삭제 요청 동작

관객은 유효한 audience token으로 `DELETE /v1/me/data`를 호출합니다.

- partner subject hash가 있으면 같은 subject에 연결된 모든 티켓, 세션, HUD 로그를 삭제합니다.
- subject hash가 없으면 현재 티켓 범위만 삭제합니다.
- 현재 access token도 즉시 무효화됩니다.
- 응답에는 개인 식별자를 포함하지 않는 삭제 영수증 ID와 삭제 건수만 반환합니다.

## 자동 정리

`python -m lumencue_backend purge-data`는 삭제 예정 건수를 미리 보여줍니다. 실제 삭제는 운영자가 결과를 확인한 뒤 `--execute`를 붙여 실행합니다. API에서는 `/v1/admin/retention/purge`에 `{"mode":"preview"}` 또는 `{"mode":"execute"}`를 보냅니다.

운영 환경에서는 하루 한 번 preview 지표를 감시한 뒤 execute 작업을 실행합니다. 법적 보존 명령이 필요한 시장에서는 별도 legal hold 저장소를 설계하기 전까지 해당 시장의 사용자 데이터를 수집하지 않습니다.
