from __future__ import annotations

import hashlib
import hmac
import json
import secrets
import sqlite3
import uuid
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from typing import Any, Mapping

from .database import Database
from .validation import validate_concert_package


class ApiError(Exception):
    def __init__(
        self,
        status: int,
        code: str,
        message: str,
        details: list[dict[str, str]] | None = None,
    ):
        super().__init__(message)
        self.status = status
        self.code = code
        self.message = message
        self.details = details or []


@dataclass(frozen=True)
class AudienceSession:
    id: str
    event_id: str
    ticket_id: int
    expires_at: str


class BackendService:
    def __init__(self, database: Database, subject_hash_key: str | None = None):
        self.database = database
        self.subject_hash_key = subject_hash_key

    def initialize(self) -> list[str]:
        return self.database.migrate()

    def health(self) -> dict[str, Any]:
        with self.database.connection() as connection:
            migration_count = connection.execute("SELECT COUNT(*) FROM schema_migrations").fetchone()[0]
            published_count = connection.execute(
                "SELECT COUNT(*) FROM concert_packages WHERE status = 'published'"
            ).fetchone()[0]
        return {
            "status": "ok",
            "database": "ready",
            "schemaMigrations": migration_count,
            "publishedConcerts": published_count,
        }

    def create_package_draft(
        self,
        package: Any,
        version: int,
        release_channel: str = "rehearsal",
        base_version: int | None = None,
        change_summary: str | None = None,
    ) -> dict[str, Any]:
        if not isinstance(version, int) or isinstance(version, bool) or version < 1:
            raise ApiError(422, "INVALID_VERSION", "version은 1 이상의 integer여야 합니다.")
        if release_channel not in {"rehearsal", "production", "hotfix"}:
            raise ApiError(422, "INVALID_RELEASE_CHANNEL", "지원하지 않는 releaseChannel입니다.")
        if base_version is not None and (
            not isinstance(base_version, int) or isinstance(base_version, bool) or base_version < 1
        ):
            raise ApiError(422, "INVALID_BASE_VERSION", "baseVersion은 1 이상의 integer여야 합니다.")
        normalized_summary = change_summary.strip() if isinstance(change_summary, str) else ""
        issues = validate_concert_package(package)
        if issues:
            raise ApiError(
                422,
                "PACKAGE_VALIDATION_FAILED",
                "공연 패키지 검증에 실패했습니다.",
                [issue.to_dict() for issue in issues],
            )

        canonical = _canonical_json(package)
        checksum = hashlib.sha256(canonical.encode("utf-8")).hexdigest()
        now = _utc_now()
        event_id = package["id"].strip()
        partner = package["partnerBrief"]
        with self.database.connection() as connection:
            published = connection.execute(
                "SELECT version FROM concert_packages WHERE event_id = ? AND status = 'published'",
                (event_id,),
            ).fetchone()
            latest = connection.execute(
                "SELECT MAX(version) FROM concert_packages WHERE event_id = ?",
                (event_id,),
            ).fetchone()[0]
            if latest is not None and version <= latest:
                raise ApiError(
                    409,
                    "VERSION_NOT_MONOTONIC",
                    f"새 버전은 현재 최대 버전 {latest}보다 커야 합니다.",
                )
            if published is None and base_version is not None:
                raise ApiError(409, "UNEXPECTED_BASE_VERSION", "첫 배포에는 baseVersion을 사용할 수 없습니다.")
            if published is not None and base_version is None:
                raise ApiError(409, "MISSING_BASE_VERSION", "변경 버전에는 현재 배포 버전이 필요합니다.")
            if published is not None and base_version != published["version"]:
                raise ApiError(
                    409,
                    "STALE_BASE_VERSION",
                    f"baseVersion {base_version}이 현재 배포 버전 {published['version']}과 다릅니다.",
                )
            if published is not None and not normalized_summary:
                raise ApiError(422, "MISSING_CHANGE_SUMMARY", "변경 버전에는 changeSummary가 필요합니다.")
            try:
                with connection:
                    connection.execute(
                        """
                        INSERT INTO concert_events(
                            id, title, promoter, venue, show_date, status, created_at, updated_at
                        ) VALUES (?, ?, ?, ?, ?, 'draft', ?, ?)
                        ON CONFLICT(id) DO UPDATE SET
                            title = excluded.title,
                            promoter = excluded.promoter,
                            venue = excluded.venue,
                            show_date = excluded.show_date,
                            updated_at = excluded.updated_at
                        """,
                        (
                            event_id,
                            package["title"].strip(),
                            partner["promoter"].strip(),
                            partner["venue"].strip(),
                            partner["showDate"].strip(),
                            now,
                            now,
                        ),
                    )
                    connection.execute(
                        """
                        INSERT INTO concert_packages(
                            event_id, version, status, payload_json, checksum_sha256,
                            validation_errors_json, created_at, release_channel,
                            base_version, change_summary
                        ) VALUES (?, ?, 'draft', ?, ?, '[]', ?, ?, ?, ?)
                        """,
                        (
                            event_id,
                            version,
                            canonical,
                            checksum,
                            now,
                            release_channel,
                            base_version,
                            normalized_summary,
                        ),
                    )
            except sqlite3.IntegrityError as error:
                if "UNIQUE" in str(error) or "PRIMARY KEY" in str(error):
                    raise ApiError(
                        409,
                        "PACKAGE_VERSION_EXISTS",
                        f"{event_id} version {version}이 이미 존재합니다.",
                    ) from error
                raise
        return {
            "eventId": event_id,
            "version": version,
            "status": "draft",
            "checksumSha256": checksum,
            "releaseChannel": release_channel,
            "baseVersion": base_version,
            "changeSummary": normalized_summary,
        }

    def approve_package(self, event_id: str, version: int, approved_by: str) -> dict[str, Any]:
        approved_by = _required_text(approved_by, "approvedBy")
        with self.database.connection() as connection:
            row = connection.execute(
                """
                SELECT payload_json, status, release_channel, base_version
                FROM concert_packages WHERE event_id = ? AND version = ?
                """,
                (event_id, version),
            ).fetchone()
            if row is None:
                raise ApiError(404, "PACKAGE_NOT_FOUND", "공연 패키지 버전을 찾을 수 없습니다.")
            if row["status"] != "draft":
                raise ApiError(409, "PACKAGE_NOT_DRAFT", "draft 상태의 패키지만 승인할 수 있습니다.")
            issues = validate_concert_package(json.loads(row["payload_json"]))
            if issues:
                raise ApiError(
                    422,
                    "PACKAGE_VALIDATION_FAILED",
                    "공연 패키지 검증에 실패했습니다.",
                    [issue.to_dict() for issue in issues],
                )
            approved_at = _utc_now()
            with connection:
                connection.execute(
                    """
                    UPDATE concert_packages SET approved_by = ?, approved_at = ?
                    WHERE event_id = ? AND version = ?
                    """,
                    (approved_by, approved_at, event_id, version),
                )
        return {
            "eventId": event_id,
            "version": version,
            "status": "approved",
            "releaseChannel": row["release_channel"],
            "baseVersion": row["base_version"],
            "approvedBy": approved_by,
            "approvedAt": approved_at,
        }

    def publish_package(self, event_id: str, version: int) -> dict[str, Any]:
        with self.database.connection() as connection:
            row = connection.execute(
                """
                SELECT payload_json, release_channel, base_version, approved_by, approved_at
                FROM concert_packages WHERE event_id = ? AND version = ?
                """,
                (event_id, version),
            ).fetchone()
            if row is None:
                raise ApiError(404, "PACKAGE_NOT_FOUND", "공연 패키지 버전을 찾을 수 없습니다.")
            if row["release_channel"] == "rehearsal":
                raise ApiError(409, "REHEARSAL_NOT_PUBLISHABLE", "리허설 버전은 production으로 배포할 수 없습니다.")
            if row["approved_at"] is None or row["approved_by"] is None:
                raise ApiError(409, "PACKAGE_NOT_APPROVED", "승인된 패키지만 배포할 수 있습니다.")
            current = connection.execute(
                """
                SELECT version FROM concert_packages
                WHERE event_id = ? AND status = 'published' AND version <> ?
                """,
                (event_id, version),
            ).fetchone()
            current_version = current["version"] if current is not None else None
            if row["base_version"] != current_version:
                raise ApiError(
                    409,
                    "STALE_BASE_VERSION",
                    f"승인본의 baseVersion {row['base_version']}이 현재 배포 버전 {current_version}과 다릅니다.",
                )
            issues = validate_concert_package(json.loads(row["payload_json"]))
            if issues:
                raise ApiError(
                    422,
                    "PACKAGE_VALIDATION_FAILED",
                    "공연 패키지 검증에 실패했습니다.",
                    [issue.to_dict() for issue in issues],
                )
            now = _utc_now()
            with connection:
                connection.execute(
                    """
                    UPDATE concert_packages
                    SET status = 'superseded'
                    WHERE event_id = ? AND status = 'published' AND version <> ?
                    """,
                    (event_id, version),
                )
                connection.execute(
                    """
                    UPDATE concert_packages
                    SET status = 'published', published_at = ?
                    WHERE event_id = ? AND version = ?
                    """,
                    (now, event_id, version),
                )
                connection.execute(
                    "UPDATE concert_events SET status = 'published', updated_at = ? WHERE id = ?",
                    (now, event_id),
                )
        return {
            "eventId": event_id,
            "version": version,
            "status": "published",
            "releaseChannel": row["release_channel"],
            "baseVersion": row["base_version"],
            "approvedBy": row["approved_by"],
            "approvedAt": row["approved_at"],
            "publishedAt": now,
        }

    def list_published_concerts(self) -> list[dict[str, Any]]:
        with self.database.connection() as connection:
            rows = connection.execute(
                """
                SELECT e.id, e.title, e.promoter, e.venue, e.show_date,
                       p.version, p.checksum_sha256, p.published_at
                FROM concert_events e
                JOIN concert_packages p ON p.event_id = e.id AND p.status = 'published'
                WHERE e.status = 'published'
                ORDER BY e.show_date, e.id
                """
            ).fetchall()
        return [
            {
                "id": row["id"],
                "title": row["title"],
                "promoter": row["promoter"],
                "venue": row["venue"],
                "showDate": row["show_date"],
                "packageVersion": row["version"],
                "checksumSha256": row["checksum_sha256"],
                "publishedAt": row["published_at"],
            }
            for row in rows
        ]

    def get_admin_overview(self) -> dict[str, Any]:
        """Return the small, non-secret operational view used by the backoffice."""
        now = _utc_now()
        with self.database.connection() as connection, connection:
            connection.execute(
                """
                UPDATE emergency_notices SET status = 'expired'
                WHERE status = 'active' AND expires_at <= ?
                """,
                (now,),
            )
            event_rows = connection.execute(
                """
                SELECT e.id, e.title, e.promoter, e.venue, e.show_date, e.status,
                       COUNT(DISTINCT CASE WHEN t.checked_in = 1 THEN t.id END) AS checked_in_tickets,
                       COUNT(DISTINCT d.id) AS dispatch_count
                FROM concert_events e
                LEFT JOIN tickets t ON t.event_id = e.id
                LEFT JOIN device_dispatch_logs d ON d.event_id = e.id
                GROUP BY e.id
                ORDER BY e.show_date, e.id
                """
            ).fetchall()
            package_rows = connection.execute(
                """
                SELECT event_id, version, status, checksum_sha256, created_at,
                       published_at, release_channel, base_version, change_summary,
                       approved_by, approved_at
                FROM concert_packages
                ORDER BY event_id, version DESC
                """
            ).fetchall()
            notice_rows = connection.execute(
                """
                SELECT id, event_id, severity, message_ko, message_en,
                       created_by, created_at, expires_at
                FROM emergency_notices
                WHERE status = 'active' AND expires_at > ?
                ORDER BY created_at DESC
                """,
                (now,),
            ).fetchall()
            totals = connection.execute(
                """
                SELECT
                    (SELECT COUNT(*) FROM concert_packages) AS package_count,
                    (SELECT COUNT(*) FROM concert_packages
                     WHERE status = 'draft' AND approved_at IS NULL) AS awaiting_approval,
                    (SELECT COUNT(*) FROM emergency_notices
                     WHERE status = 'active' AND expires_at > ?) AS active_notices,
                    (SELECT COUNT(*) FROM device_dispatch_logs) AS dispatch_count
                """,
                (now,),
            ).fetchone()

        versions_by_event: dict[str, list[dict[str, Any]]] = {}
        for row in package_rows:
            effective_status = (
                "approved"
                if row["status"] == "draft" and row["approved_at"] is not None
                else row["status"]
            )
            versions_by_event.setdefault(row["event_id"], []).append(
                {
                    "version": row["version"],
                    "status": effective_status,
                    "releaseChannel": row["release_channel"],
                    "baseVersion": row["base_version"],
                    "changeSummary": row["change_summary"],
                    "checksumSha256": row["checksum_sha256"],
                    "createdAt": row["created_at"],
                    "approvedBy": row["approved_by"],
                    "approvedAt": row["approved_at"],
                    "publishedAt": row["published_at"],
                }
            )
        notices_by_event = {
            row["event_id"]: {
                "id": row["id"],
                "eventId": row["event_id"],
                "severity": row["severity"],
                "messageKo": row["message_ko"],
                "messageEn": row["message_en"],
                "createdBy": row["created_by"],
                "createdAt": row["created_at"],
                "expiresAt": row["expires_at"],
            }
            for row in notice_rows
        }
        events = [
            {
                "id": row["id"],
                "title": row["title"],
                "promoter": row["promoter"],
                "venue": row["venue"],
                "showDate": row["show_date"],
                "status": row["status"],
                "checkedInTickets": row["checked_in_tickets"],
                "dispatchCount": row["dispatch_count"],
                "activeNotice": notices_by_event.get(row["id"]),
                "versions": versions_by_event.get(row["id"], []),
            }
            for row in event_rows
        ]
        return {
            "generatedAt": now,
            "metrics": {
                "eventCount": len(events),
                "publishedEvents": sum(event["status"] == "published" for event in events),
                "packageCount": totals["package_count"],
                "awaitingApproval": totals["awaiting_approval"],
                "activeNotices": totals["active_notices"],
                "dispatchCount": totals["dispatch_count"],
            },
            "events": events,
        }

    def get_published_package(self, event_id: str) -> dict[str, Any]:
        with self.database.connection() as connection:
            row = connection.execute(
                """
                SELECT version, payload_json, checksum_sha256, published_at
                FROM concert_packages
                WHERE event_id = ? AND status = 'published'
                """,
                (event_id,),
            ).fetchone()
        if row is None:
            raise ApiError(404, "PUBLISHED_PACKAGE_NOT_FOUND", "배포된 공연 패키지를 찾을 수 없습니다.")
        result = {
            "eventId": event_id,
            "version": row["version"],
            "checksumSha256": row["checksum_sha256"],
            "publishedAt": row["published_at"],
            "package": json.loads(row["payload_json"]),
        }
        result["emergencyNotice"] = self.get_active_emergency_notice(event_id)
        return result

    def create_emergency_notice(
        self,
        event_id: str,
        severity: str,
        message_ko: str,
        message_en: str,
        created_by: str,
        expires_at: str,
    ) -> dict[str, Any]:
        if severity not in {"Warning", "Critical"}:
            raise ApiError(422, "INVALID_NOTICE_SEVERITY", "severity는 Warning 또는 Critical이어야 합니다.")
        message_ko = _required_text(message_ko, "messageKo")
        message_en = _required_text(message_en, "messageEn")
        created_by = _required_text(created_by, "createdBy")
        if any(len(message) > 80 or len(message.splitlines()) > 2 for message in (message_ko, message_en)):
            raise ApiError(
                422,
                "NOTICE_TOO_LONG",
                "긴급 공지는 언어별 80자, 최대 2줄이어야 합니다.",
            )
        try:
            expiry = _parse_time(_required_text(expires_at, "expiresAt"))
        except ValueError as error:
            raise ApiError(422, "INVALID_NOTICE_EXPIRY", "expiresAt은 ISO-8601 시각이어야 합니다.") from error
        if expiry.tzinfo is None:
            raise ApiError(422, "INVALID_NOTICE_EXPIRY", "expiresAt에는 timezone이 필요합니다.")
        now_value = datetime.now(timezone.utc)
        if expiry <= now_value or expiry > now_value + timedelta(hours=24):
            raise ApiError(422, "INVALID_NOTICE_EXPIRY", "긴급 공지는 현재부터 24시간 이내에 만료되어야 합니다.")
        now = _format_time(now_value)
        with self.database.connection() as connection:
            event = connection.execute(
                "SELECT id FROM concert_events WHERE id = ? AND status = 'published'",
                (event_id,),
            ).fetchone()
            if event is None:
                raise ApiError(404, "PUBLISHED_EVENT_NOT_FOUND", "배포된 공연을 찾을 수 없습니다.")
            active = connection.execute(
                """
                SELECT id, severity FROM emergency_notices
                WHERE event_id = ? AND status = 'active' AND expires_at > ?
                ORDER BY CASE severity WHEN 'Critical' THEN 2 ELSE 1 END DESC, created_at DESC
                LIMIT 1
                """,
                (event_id, now),
            ).fetchone()
            if active is not None and active["severity"] == "Critical" and severity == "Warning":
                raise ApiError(
                    409,
                    "NOTICE_PRIORITY_CONFLICT",
                    "Critical 공지를 Warning 공지로 교체할 수 없습니다.",
                )
            notice_id = str(uuid.uuid4())
            with connection:
                connection.execute(
                    """
                    UPDATE emergency_notices
                    SET status = 'superseded', superseded_at = ?
                    WHERE event_id = ? AND status = 'active'
                    """,
                    (now, event_id),
                )
                connection.execute(
                    """
                    INSERT INTO emergency_notices(
                        id, event_id, severity, message_ko, message_en,
                        created_by, created_at, expires_at, status
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'active')
                    """,
                    (
                        notice_id,
                        event_id,
                        severity,
                        message_ko,
                        message_en,
                        created_by,
                        now,
                        _format_time(expiry),
                    ),
                )
        return self.get_active_emergency_notice(event_id) or {}

    def get_active_emergency_notice(self, event_id: str) -> dict[str, Any] | None:
        now = _utc_now()
        with self.database.connection() as connection, connection:
            connection.execute(
                """
                UPDATE emergency_notices SET status = 'expired'
                WHERE event_id = ? AND status = 'active' AND expires_at <= ?
                """,
                (event_id, now),
            )
            row = connection.execute(
                """
                SELECT id, event_id, severity, message_ko, message_en,
                       created_by, created_at, expires_at
                FROM emergency_notices
                WHERE event_id = ? AND status = 'active' AND expires_at > ?
                ORDER BY CASE severity WHEN 'Critical' THEN 2 ELSE 1 END DESC, created_at DESC
                LIMIT 1
                """,
                (event_id, now),
            ).fetchone()
        if row is None:
            return None
        return {
            "id": row["id"],
            "eventId": row["event_id"],
            "severity": row["severity"],
            "messageKo": row["message_ko"],
            "messageEn": row["message_en"],
            "createdBy": row["created_by"],
            "createdAt": row["created_at"],
            "expiresAt": row["expires_at"],
        }

    def import_ticket(
        self,
        provider: str,
        provider_ticket_id: str,
        event_id: str,
        checked_in: bool,
        external_subject: str | None = None,
        provider_occurred_at: str | None = None,
    ) -> dict[str, Any]:
        provider = _required_text(provider, "provider")
        provider_ticket_id = _required_text(provider_ticket_id, "ticketId")
        event_id = _required_text(event_id, "eventId")
        if not isinstance(checked_in, bool):
            raise ApiError(422, "INVALID_TICKET", "checkedIn은 boolean이어야 합니다.")
        subject_hash = self._subject_hash(external_subject) if external_subject else None
        now = _utc_now()
        provider_time = provider_occurred_at or now
        try:
            parsed_provider_time = _parse_time(provider_time)
        except ValueError as error:
            raise ApiError(422, "INVALID_TICKET_EVENT_TIME", "provider 시각은 ISO-8601이어야 합니다.") from error
        if parsed_provider_time.tzinfo is None:
            raise ApiError(422, "INVALID_TICKET_EVENT_TIME", "provider 시각에는 timezone이 필요합니다.")
        provider_time = _format_time(parsed_provider_time)
        revoked_sessions = 0
        stale_ignored = False
        with self.database.connection() as connection:
            event = connection.execute("SELECT id FROM concert_events WHERE id = ?", (event_id,)).fetchone()
            if event is None:
                raise ApiError(404, "EVENT_NOT_FOUND", "티켓을 연결할 공연을 찾을 수 없습니다.")
            with connection:
                connection.execute(
                    """
                    INSERT INTO tickets(
                        provider, provider_ticket_id, event_id, checked_in,
                        external_subject_hash, created_at, updated_at, provider_updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(provider, provider_ticket_id, event_id) DO UPDATE SET
                        checked_in = excluded.checked_in,
                        external_subject_hash = COALESCE(
                            excluded.external_subject_hash,
                            tickets.external_subject_hash
                        ),
                        updated_at = excluded.updated_at,
                        provider_updated_at = excluded.provider_updated_at
                    WHERE excluded.provider_updated_at >= COALESCE(tickets.provider_updated_at, '')
                    """,
                    (
                        provider,
                        provider_ticket_id,
                        event_id,
                        int(checked_in),
                        subject_hash,
                        now,
                        now,
                        provider_time,
                    ),
                )
                ticket_row = connection.execute(
                    """
                    SELECT id, checked_in, provider_updated_at FROM tickets
                    WHERE provider = ? AND provider_ticket_id = ? AND event_id = ?
                    """,
                    (provider, provider_ticket_id, event_id),
                ).fetchone()
                stale_ignored = ticket_row["provider_updated_at"] != provider_time
                if not bool(ticket_row["checked_in"]):
                    revoked_sessions = connection.execute(
                        """
                        UPDATE audience_sessions SET revoked_at = ?
                        WHERE ticket_id = ? AND revoked_at IS NULL
                        """,
                        (now, ticket_row["id"]),
                    ).rowcount
        return {
            "provider": provider,
            "ticketId": provider_ticket_id,
            "eventId": event_id,
            "checkedIn": bool(ticket_row["checked_in"]),
            "sessionsRevoked": revoked_sessions,
            "staleIgnored": stale_ignored,
        }

    def record_ticket_provider_event(
        self,
        provider: str,
        provider_event_id: str,
        payload: Any,
        payload_sha256: str,
    ) -> dict[str, Any]:
        provider = _required_text(provider, "provider")
        provider_event_id = _required_text(provider_event_id, "providerEventId")
        if len(provider_event_id) > 200:
            raise ApiError(422, "INVALID_PROVIDER_EVENT_ID", "provider event id는 200자 이하여야 합니다.")
        if not isinstance(payload, dict):
            raise ApiError(422, "INVALID_TICKET_EVENT", "티켓 이벤트는 JSON object여야 합니다.")
        event_type = payload.get("type")
        if event_type not in {"ticket.checked_in", "ticket.revoked"}:
            raise ApiError(422, "INVALID_TICKET_EVENT", "지원하지 않는 티켓 이벤트 type입니다.")
        occurred_at = _required_text(payload.get("occurredAt"), "occurredAt")
        try:
            occurred_time = _parse_time(occurred_at)
        except ValueError as error:
            raise ApiError(422, "INVALID_TICKET_EVENT_TIME", "occurredAt은 ISO-8601 시각이어야 합니다.") from error
        if occurred_time.tzinfo is None:
            raise ApiError(422, "INVALID_TICKET_EVENT_TIME", "occurredAt에는 timezone이 필요합니다.")

        with self.database.connection() as connection:
            existing = connection.execute(
                """
                SELECT event_type, payload_sha256, occurred_at, received_at
                FROM ticket_provider_events
                WHERE provider = ? AND provider_event_id = ?
                """,
                (provider, provider_event_id),
            ).fetchone()
        if existing is not None:
            if existing["payload_sha256"] != payload_sha256:
                raise ApiError(
                    409,
                    "PROVIDER_EVENT_ID_CONFLICT",
                    "같은 provider event id에 다른 payload를 사용할 수 없습니다.",
                )
            return {
                "provider": provider,
                "providerEventId": provider_event_id,
                "type": existing["event_type"],
                "occurredAt": existing["occurred_at"],
                "receivedAt": existing["received_at"],
                "deduplicated": True,
            }

        ticket = self.import_ticket(
            provider=provider,
            provider_ticket_id=payload.get("ticketId"),
            event_id=payload.get("eventId"),
            checked_in=event_type == "ticket.checked_in",
            external_subject=payload.get("subjectId"),
            provider_occurred_at=_format_time(occurred_time),
        )
        received_at = _utc_now()
        try:
            with self.database.connection() as connection, connection:
                connection.execute(
                    """
                    INSERT INTO ticket_provider_events(
                        provider, provider_event_id, event_type, payload_sha256,
                        occurred_at, received_at
                    ) VALUES (?, ?, ?, ?, ?, ?)
                    """,
                    (
                        provider,
                        provider_event_id,
                        event_type,
                        payload_sha256,
                        _format_time(occurred_time),
                        received_at,
                    ),
                )
        except sqlite3.IntegrityError as error:
            raise ApiError(409, "PROVIDER_EVENT_RACE", "티켓 이벤트가 동시에 처리되었습니다. 재시도하세요.") from error
        return {
            "provider": provider,
            "providerEventId": provider_event_id,
            "type": event_type,
            "occurredAt": _format_time(occurred_time),
            "receivedAt": received_at,
            "deduplicated": False,
            "ticket": ticket,
        }

    def verify_ticket(self, provider: str, provider_ticket_id: str, event_id: str) -> dict[str, Any]:
        provider = _required_text(provider, "provider")
        provider_ticket_id = _required_text(provider_ticket_id, "ticketId")
        event_id = _required_text(event_id, "eventId")
        with self.database.connection() as connection:
            row = connection.execute(
                """
                SELECT t.id
                FROM tickets t
                JOIN concert_events e ON e.id = t.event_id AND e.status = 'published'
                WHERE t.provider = ? AND t.provider_ticket_id = ?
                  AND t.event_id = ? AND t.checked_in = 1
                """,
                (provider, provider_ticket_id, event_id),
            ).fetchone()
            if row is None:
                raise ApiError(
                    403,
                    "TICKET_NOT_AUTHORIZED",
                    "입장이 확인된 티켓과 공연 정보를 찾을 수 없습니다.",
                )
            now = datetime.now(timezone.utc)
            expires = now + timedelta(hours=12)
            token = secrets.token_urlsafe(32)
            session_id = str(uuid.uuid4())
            with connection:
                connection.execute(
                    "UPDATE tickets SET verified_at = ?, updated_at = ? WHERE id = ?",
                    (_format_time(now), _format_time(now), row["id"]),
                )
                connection.execute(
                    """
                    INSERT INTO audience_sessions(
                        id, event_id, ticket_id, access_token_hash, created_at, expires_at
                    ) VALUES (?, ?, ?, ?, ?, ?)
                    """,
                    (
                        session_id,
                        event_id,
                        row["id"],
                        _token_hash(token),
                        _format_time(now),
                        _format_time(expires),
                    ),
                )
        return {
            "authorized": True,
            "eventId": event_id,
            "sessionId": session_id,
            "accessToken": token,
            "expiresAt": _format_time(expires),
        }

    def authorize_session(self, access_token: str) -> AudienceSession:
        if not access_token:
            raise ApiError(401, "MISSING_ACCESS_TOKEN", "Bearer access token이 필요합니다.")
        with self.database.connection() as connection:
            row = connection.execute(
                """
                SELECT id, event_id, ticket_id, expires_at
                FROM audience_sessions
                WHERE access_token_hash = ? AND revoked_at IS NULL
                """,
                (_token_hash(access_token),),
            ).fetchone()
        if row is None or _parse_time(row["expires_at"]) <= datetime.now(timezone.utc):
            raise ApiError(401, "INVALID_ACCESS_TOKEN", "세션이 없거나 만료되었습니다.")
        return AudienceSession(
            id=row["id"],
            event_id=row["event_id"],
            ticket_id=row["ticket_id"],
            expires_at=row["expires_at"],
        )

    def delete_audience_data(self, access_token: str) -> dict[str, Any]:
        session = self.authorize_session(access_token)
        now = _utc_now()
        receipt_id = str(uuid.uuid4())
        with self.database.connection() as connection, connection:
            ticket = connection.execute(
                "SELECT external_subject_hash FROM tickets WHERE id = ?",
                (session.ticket_id,),
            ).fetchone()
            if ticket is None:
                raise ApiError(404, "TICKET_NOT_FOUND", "삭제할 관객 데이터를 찾을 수 없습니다.")
            subject_hash = ticket["external_subject_hash"]
            if subject_hash:
                ticket_rows = connection.execute(
                    "SELECT id FROM tickets WHERE external_subject_hash = ?",
                    (subject_hash,),
                ).fetchall()
                subject_scope = "subject"
            else:
                ticket_rows = connection.execute(
                    "SELECT id FROM tickets WHERE id = ?",
                    (session.ticket_id,),
                ).fetchall()
                subject_scope = "ticket"
            ticket_ids = [row["id"] for row in ticket_rows]
            placeholders = ",".join("?" for _ in ticket_ids)
            session_count = connection.execute(
                f"SELECT COUNT(*) FROM audience_sessions WHERE ticket_id IN ({placeholders})",
                ticket_ids,
            ).fetchone()[0]
            dispatch_count = connection.execute(
                f"""
                SELECT COUNT(*) FROM device_dispatch_logs
                WHERE session_id IN (
                    SELECT id FROM audience_sessions WHERE ticket_id IN ({placeholders})
                )
                """,
                ticket_ids,
            ).fetchone()[0]
            connection.execute(
                """
                INSERT INTO data_deletion_receipts(
                    id, requested_at, completed_at, subject_scope,
                    tickets_deleted, sessions_deleted, dispatch_logs_deleted
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                (
                    receipt_id,
                    now,
                    now,
                    subject_scope,
                    len(ticket_ids),
                    session_count,
                    dispatch_count,
                ),
            )
            connection.execute(
                f"DELETE FROM tickets WHERE id IN ({placeholders})",
                ticket_ids,
            )
        return {
            "receiptId": receipt_id,
            "completedAt": now,
            "scope": subject_scope,
            "ticketsDeleted": len(ticket_ids),
            "sessionsDeleted": session_count,
            "dispatchLogsDeleted": dispatch_count,
        }

    def get_data_policy(self) -> dict[str, Any]:
        return {
            "version": "2026-09-18",
            "accountMode": "ticket-scoped-pseudonymous",
            "rules": [
                {
                    "data": "account",
                    "storage": "MVP에서는 이메일, 비밀번호, 실명 계정을 수집하지 않음",
                    "retention": "not-collected",
                    "deletion": "해당 없음",
                },
                {
                    "data": "ticket",
                    "storage": "provider ticket id, 입장 상태, 선택적 keyed subject hash",
                    "retention": "마지막 상태 갱신 후 30일",
                    "deletion": "관객 삭제 요청 시 즉시 삭제",
                },
                {
                    "data": "audience-session-and-dispatch-log",
                    "storage": "access token hash와 HUD 전송 최소 감사 정보",
                    "retention": "세션 만료 후 30일",
                    "deletion": "관객 삭제 요청 시 즉시 연쇄 삭제",
                },
                {
                    "data": "board-post",
                    "storage": "현재 앱 메모리의 로컬 데모 데이터만 사용, 서버 미전송",
                    "retention": "앱 프로세스 수명",
                    "deletion": "앱 종료 또는 사용자가 로컬 데이터 초기화",
                },
                {
                    "data": "saved-moment",
                    "storage": "현재 공연 세션의 로컬 상태만 사용, 오디오·생체정보 미수집",
                    "retention": "현재 공연 세션 수명",
                    "deletion": "세션 초기화 시 삭제",
                },
                {
                    "data": "deletion-receipt",
                    "storage": "개인 식별자 없는 삭제 건수와 완료 시각",
                    "retention": "365일",
                    "deletion": "보존 기간 종료 후 자동 삭제",
                },
            ],
        }

    def purge_expired_data(
        self,
        execute: bool = False,
        as_of: datetime | None = None,
    ) -> dict[str, Any]:
        now = as_of or datetime.now(timezone.utc)
        if now.tzinfo is None:
            raise ValueError("as_of must include timezone")
        operational_cutoff = _format_time(now - timedelta(days=30))
        receipt_cutoff = _format_time(now - timedelta(days=365))
        with self.database.connection() as connection:
            counts = {
                "providerEvents": connection.execute(
                    "SELECT COUNT(*) FROM ticket_provider_events WHERE received_at < ?",
                    (operational_cutoff,),
                ).fetchone()[0],
                "sessions": connection.execute(
                    "SELECT COUNT(*) FROM audience_sessions WHERE expires_at < ?",
                    (operational_cutoff,),
                ).fetchone()[0],
                "dispatchLogs": connection.execute(
                    """
                    SELECT COUNT(*) FROM device_dispatch_logs
                    WHERE session_id IN (
                        SELECT id FROM audience_sessions WHERE expires_at < ?
                    )
                    """,
                    (operational_cutoff,),
                ).fetchone()[0],
                "tickets": connection.execute(
                    """
                    SELECT COUNT(*) FROM tickets t
                    WHERE t.updated_at < ?
                      AND NOT EXISTS (
                          SELECT 1 FROM audience_sessions s
                          WHERE s.ticket_id = t.id AND s.expires_at >= ?
                      )
                    """,
                    (operational_cutoff, operational_cutoff),
                ).fetchone()[0],
                "deletionReceipts": connection.execute(
                    "SELECT COUNT(*) FROM data_deletion_receipts WHERE completed_at < ?",
                    (receipt_cutoff,),
                ).fetchone()[0],
            }
            if execute:
                with connection:
                    connection.execute(
                        "DELETE FROM ticket_provider_events WHERE received_at < ?",
                        (operational_cutoff,),
                    )
                    connection.execute(
                        "DELETE FROM audience_sessions WHERE expires_at < ?",
                        (operational_cutoff,),
                    )
                    connection.execute(
                        """
                        DELETE FROM tickets
                        WHERE updated_at < ?
                          AND NOT EXISTS (
                              SELECT 1 FROM audience_sessions s WHERE s.ticket_id = tickets.id
                          )
                        """,
                        (operational_cutoff,),
                    )
                    connection.execute(
                        "DELETE FROM data_deletion_receipts WHERE completed_at < ?",
                        (receipt_cutoff,),
                    )
        return {
            "mode": "execute" if execute else "preview",
            "asOf": _format_time(now),
            "operationalCutoff": operational_cutoff,
            "deletionReceiptCutoff": receipt_cutoff,
            "items": counts,
        }

    def _subject_hash(self, external_subject: str) -> str:
        subject = _required_text(external_subject, "externalSubject")
        if self.subject_hash_key:
            digest = hmac.new(
                self.subject_hash_key.encode("utf-8"),
                subject.encode("utf-8"),
                hashlib.sha256,
            ).hexdigest()
            return f"hmac-sha256:{digest}"
        return f"sha256:{hashlib.sha256(subject.encode('utf-8')).hexdigest()}"

    def record_dispatch(self, access_token: str, payload: Any) -> dict[str, Any]:
        session = self.authorize_session(access_token)
        if not isinstance(payload, dict):
            raise ApiError(422, "INVALID_DISPATCH_LOG", "요청 body는 JSON object여야 합니다.")
        event_id = payload.get("eventId", session.event_id)
        if event_id != session.event_id:
            raise ApiError(403, "SESSION_EVENT_MISMATCH", "세션과 다른 공연의 로그를 기록할 수 없습니다.")

        route = payload.get("route")
        availability = payload.get("availability")
        priority = payload.get("priority")
        if route not in {"PrimaryToolkit", "MockDevice", "FallbackPreview"}:
            raise ApiError(422, "INVALID_DISPATCH_LOG", "지원하지 않는 route입니다.")
        if availability not in {"Ready", "WaitingForOfficialSdk", "Unsupported"}:
            raise ApiError(422, "INVALID_DISPATCH_LOG", "지원하지 않는 availability입니다.")
        if priority not in {"Low", "Normal", "High"}:
            raise ApiError(422, "INVALID_DISPATCH_LOG", "지원하지 않는 priority입니다.")
        accepted = payload.get("accepted")
        if not isinstance(accepted, bool):
            raise ApiError(422, "INVALID_DISPATCH_LOG", "accepted는 boolean이어야 합니다.")
        renderer_name = _required_text(payload.get("rendererName"), "rendererName")
        document_id = _required_text(payload.get("documentId"), "documentId")
        client_record_id = payload.get("clientRecordId")
        if client_record_id is not None:
            client_record_id = _required_text(client_record_id, "clientRecordId")
            if len(client_record_id) > 300:
                raise ApiError(
                    422,
                    "INVALID_DISPATCH_LOG",
                    "clientRecordId는 300자 이하여야 합니다.",
                )
        metadata = payload.get("metadata", {})
        if not isinstance(metadata, dict):
            raise ApiError(422, "INVALID_DISPATCH_LOG", "metadata는 object여야 합니다.")
        metadata_json = _canonical_json(metadata)
        if len(metadata_json.encode("utf-8")) > 16_384:
            raise ApiError(413, "METADATA_TOO_LARGE", "metadata는 16KB 이하여야 합니다.")

        log_id = str(uuid.uuid4())
        occurred_at = _utc_now()
        with self.database.connection() as connection, connection:
            if client_record_id is not None:
                existing = connection.execute(
                    """
                    SELECT id, event_id, occurred_at
                    FROM device_dispatch_logs
                    WHERE event_id = ? AND client_record_id = ?
                    """,
                    (session.event_id, client_record_id),
                ).fetchone()
                if existing is not None:
                    return {
                        "id": existing["id"],
                        "eventId": existing["event_id"],
                        "occurredAt": existing["occurred_at"],
                        "deduplicated": True,
                    }
            connection.execute(
                """
                INSERT INTO device_dispatch_logs(
                    id, event_id, session_id, occurred_at, route, accepted,
                    renderer_name, availability, document_id, priority, error_code,
                    metadata_json, client_record_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                (
                    log_id,
                    session.event_id,
                    session.id,
                    occurred_at,
                    route,
                    int(accepted),
                    renderer_name,
                    availability,
                    document_id,
                    priority,
                    payload.get("errorCode"),
                    metadata_json,
                    client_record_id,
                ),
            )
        return {
            "id": log_id,
            "eventId": session.event_id,
            "occurredAt": occurred_at,
            "deduplicated": False,
        }

    def list_dispatch_logs(self, event_id: str | None = None, limit: int = 100) -> list[dict[str, Any]]:
        limit = max(1, min(limit, 200))
        sql = """
            SELECT id, event_id, session_id, occurred_at, route, accepted,
                   renderer_name, availability, document_id, priority, error_code,
                   metadata_json, client_record_id
            FROM device_dispatch_logs
        """
        parameters: list[Any] = []
        if event_id:
            sql += " WHERE event_id = ?"
            parameters.append(event_id)
        sql += " ORDER BY occurred_at DESC LIMIT ?"
        parameters.append(limit)
        with self.database.connection() as connection:
            rows = connection.execute(sql, parameters).fetchall()
        return [
            {
                "id": row["id"],
                "eventId": row["event_id"],
                "sessionId": row["session_id"],
                "occurredAt": row["occurred_at"],
                "route": row["route"],
                "accepted": bool(row["accepted"]),
                "rendererName": row["renderer_name"],
                "availability": row["availability"],
                "documentId": row["document_id"],
                "priority": row["priority"],
                "errorCode": row["error_code"],
                "clientRecordId": row["client_record_id"],
                "metadata": json.loads(row["metadata_json"]),
            }
            for row in rows
        ]


def _canonical_json(value: Any) -> str:
    try:
        return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    except (TypeError, ValueError) as error:
        raise ApiError(422, "INVALID_JSON_VALUE", "JSON으로 저장할 수 없는 값이 포함되어 있습니다.") from error


def _required_text(value: Any, field: str) -> str:
    if not isinstance(value, str) or not value.strip():
        raise ApiError(422, "MISSING_REQUIRED_FIELD", f"{field}에는 비어 있지 않은 string이 필요합니다.")
    return value.strip()


def _token_hash(token: str) -> str:
    return hashlib.sha256(token.encode("utf-8")).hexdigest()


def _utc_now() -> str:
    return _format_time(datetime.now(timezone.utc))


def _format_time(value: datetime) -> str:
    return value.astimezone(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z")


def _parse_time(value: str) -> datetime:
    return datetime.fromisoformat(value.replace("Z", "+00:00"))
