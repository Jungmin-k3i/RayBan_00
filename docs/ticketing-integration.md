# 티켓·입장 인증 연동 계약

## 확정 방식

LumenCue의 1차 연동 방식은 **예매처/입장 시스템이 보내는 서명된 서버 간 웹훅**입니다. 현장 체크인 상태가 바뀔 때 partner가 LumenCue로 이벤트를 보내며, Android 앱은 LumenCue의 `/v1/tickets/verify`만 호출합니다.

관리자 콘솔과 `import-ticket` 명령은 리허설, 장애 복구, 연동 전환 기간을 위한 수동 폴백으로 유지합니다. 앱이 예매처 API를 직접 호출하거나 예매처 credential을 보관하지 않습니다.

## 보안 계약

- 환경 변수 `LUMENCUE_TICKET_PROVIDER_SECRETS`에 provider별 secret을 JSON object로 설정합니다.
- `X-LumenCue-Timestamp`는 Unix seconds이며 서버 시각과 5분 이상 차이 나면 거절합니다.
- 서명 원문은 `{timestamp}.{raw UTF-8 request body}`입니다.
- `X-LumenCue-Signature`는 `sha256=<HMAC-SHA256 hex digest>` 형식입니다.
- `X-LumenCue-Event-Id`는 provider 내부에서 안정적이고 유일해야 합니다.
- 같은 event id와 같은 payload 재시도는 성공으로 중복 제거하고, 다른 payload 재사용은 `409`로 차단합니다.
- 서로 다른 이벤트가 순서 없이 도착해도 `occurredAt`이 최신인 상태만 티켓에 반영합니다.

필수 헤더:

```text
X-LumenCue-Provider: partner-ticket-provider
X-LumenCue-Event-Id: checkin-event-123
X-LumenCue-Timestamp: 1789693200
X-LumenCue-Signature: sha256=<hex digest>
Content-Type: application/json
```

체크인 이벤트:

```json
{
  "type": "ticket.checked_in",
  "eventId": "lumen-2026-seoul",
  "ticketId": "LC-AR-0918-1208",
  "subjectId": "partner-owned-pseudonymous-id",
  "occurredAt": "2026-09-18T09:00:00+09:00"
}
```

입장 권한 취소는 동일한 필드에 `type: ticket.revoked`를 사용합니다. `subjectId`는 선택 사항이며, 제공된 경우 LumenCue는 production에서 `LUMENCUE_SUBJECT_HASH_KEY`를 이용한 keyed HMAC만 저장합니다.

## 재시도와 장애 처리

- provider는 연결 실패와 `5xx`에 지수 backoff로 재시도합니다.
- `2xx`는 처리 완료 또는 안전한 중복 제거를 의미합니다.
- `4xx`는 서명, 계약, 이벤트 내용 수정이 필요한 영구 오류입니다.
- provider 장애 중에는 관리자 수동 import를 사용하고, 복구 후 동일 상태 이벤트를 보내 최종 상태를 일치시킵니다.
- 원본 티켓, 결제 정보, 좌석 소유권의 source of truth는 계속 예매처에 있습니다.

## 출시 전 교체 지점

실제 예매처가 webhook을 제공하지 않으면 이 계약의 입력부만 polling adapter 또는 일회성 입장 토큰 검증 adapter로 교체합니다. 내부 `tickets` 모델과 Android `/v1/tickets/verify` 계약은 유지합니다.
