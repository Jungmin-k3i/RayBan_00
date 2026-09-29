from __future__ import annotations

import http.client
import hashlib
import hmac
import json
import tempfile
import threading
import unittest
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any

from lumencue_backend import BackendService, Database, create_server
from lumencue_backend.service import ApiError
from lumencue_backend.validation import validate_concert_package


REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
SAMPLE_PACKAGE = (
    REPOSITORY_ROOT
    / "app"
    / "src"
    / "main"
    / "assets"
    / "concert_packages"
    / "lumen_live_glass_horizon.json"
)


class BackendServiceTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.database_path = Path(self.temporary_directory.name) / "test.db"
        self.service = BackendService(Database(self.database_path))
        self.service.initialize()
        self.package = json.loads(SAMPLE_PACKAGE.read_text(encoding="utf-8"))

    def tearDown(self) -> None:
        self.temporary_directory.cleanup()

    def test_package_validation_rejects_missing_required_data(self) -> None:
        issues = validate_concert_package({"id": "event"})

        self.assertTrue(any(issue.path == "title" for issue in issues))
        self.assertTrue(any(issue.path == "tracks" for issue in issues))
        self.assertTrue(any(issue.path == "ticketPolicy.requiresCheckIn" for issue in issues))

    def test_package_draft_is_hidden_until_published(self) -> None:
        draft = self.service.create_package_draft(
            self.package, version=1, release_channel="production"
        )

        self.assertEqual("draft", draft["status"])
        self.assertEqual([], self.service.list_published_concerts())

        with self.assertRaises(ApiError) as error:
            self.service.publish_package(self.package["id"], version=1)
        self.assertEqual("PACKAGE_NOT_APPROVED", error.exception.code)

        self.service.approve_package(self.package["id"], version=1, approved_by="ops-lead")
        published = self.service.publish_package(self.package["id"], version=1)
        package = self.service.get_published_package(self.package["id"])

        self.assertEqual("published", published["status"])
        self.assertEqual(1, len(self.service.list_published_concerts()))
        self.assertEqual(self.package, package["package"])
        self.assertEqual(64, len(package["checksumSha256"]))

    def test_new_package_version_supersedes_previous_publication(self) -> None:
        self.service.create_package_draft(self.package, version=1, release_channel="production")
        self.service.approve_package(self.package["id"], version=1, approved_by="ops-lead")
        self.service.publish_package(self.package["id"], version=1)
        updated = dict(self.package)
        updated["subtitle"] = "Version two"
        self.service.create_package_draft(
            updated,
            version=2,
            release_channel="production",
            base_version=1,
            change_summary="리허설 큐 반영",
        )
        self.service.approve_package(self.package["id"], version=2, approved_by="ops-lead")
        self.service.publish_package(self.package["id"], version=2)

        package = self.service.get_published_package(self.package["id"])

        self.assertEqual(2, package["version"])
        self.assertEqual("Version two", package["package"]["subtitle"])

    def test_stale_approved_version_cannot_replace_newer_publication(self) -> None:
        self._publish_sample()
        version_two = dict(self.package)
        version_two["subtitle"] = "Version two"
        self.service.create_package_draft(
            version_two,
            version=2,
            release_channel="production",
            base_version=1,
            change_summary="리허설 A 변경",
        )
        self.service.approve_package(self.package["id"], version=2, approved_by="approver-a")
        version_three = dict(self.package)
        version_three["subtitle"] = "Version three"
        self.service.create_package_draft(
            version_three,
            version=3,
            release_channel="hotfix",
            base_version=1,
            change_summary="긴급 큐 수정",
        )
        self.service.approve_package(self.package["id"], version=3, approved_by="approver-b")
        self.service.publish_package(self.package["id"], version=3)

        with self.assertRaises(ApiError) as error:
            self.service.publish_package(self.package["id"], version=2)

        self.assertEqual("STALE_BASE_VERSION", error.exception.code)

    def test_critical_notice_cannot_be_replaced_by_warning(self) -> None:
        self._publish_sample()
        expiry = (datetime.now(timezone.utc) + timedelta(hours=1)).isoformat()
        self.service.create_emergency_notice(
            self.package["id"], "Warning", "입장 지연", "Entry delayed", "venue-ops", expiry
        )
        critical = self.service.create_emergency_notice(
            self.package["id"], "Critical", "대피 안내", "Evacuate", "safety-lead", expiry
        )

        with self.assertRaises(ApiError) as error:
            self.service.create_emergency_notice(
                self.package["id"], "Warning", "일반 안내", "Notice", "venue-ops", expiry
            )

        package = self.service.get_published_package(self.package["id"])
        self.assertEqual("NOTICE_PRIORITY_CONFLICT", error.exception.code)
        self.assertEqual(critical["id"], package["emergencyNotice"]["id"])
        self.assertEqual("Critical", package["emergencyNotice"]["severity"])

    def test_ticket_requires_check_in_before_session_is_issued(self) -> None:
        self._publish_sample()
        self.service.import_ticket(
            provider="partner-ticket-provider",
            provider_ticket_id="ticket-1",
            event_id=self.package["id"],
            checked_in=False,
        )

        with self.assertRaises(ApiError) as error:
            self.service.verify_ticket(
                provider="partner-ticket-provider",
                provider_ticket_id="ticket-1",
                event_id=self.package["id"],
            )

        self.assertEqual(403, error.exception.status)
        self.assertEqual("TICKET_NOT_AUTHORIZED", error.exception.code)

    def test_provider_event_is_idempotent_and_can_revoke_ticket(self) -> None:
        self._publish_sample()
        base_time = datetime.now(timezone.utc)
        checked_in = {
            "type": "ticket.checked_in",
            "eventId": self.package["id"],
            "ticketId": "provider-ticket-1",
            "subjectId": "provider-user-1",
            "occurredAt": base_time.isoformat(),
        }
        canonical = json.dumps(checked_in, ensure_ascii=False, separators=(",", ":")).encode()

        first = self.service.record_ticket_provider_event(
            "partner-ticket-provider", "provider-event-1", checked_in, hashlib.sha256(canonical).hexdigest()
        )
        duplicate = self.service.record_ticket_provider_event(
            "partner-ticket-provider", "provider-event-1", checked_in, hashlib.sha256(canonical).hexdigest()
        )

        self.assertFalse(first["deduplicated"])
        self.assertTrue(duplicate["deduplicated"])
        verification = self.service.verify_ticket(
            "partner-ticket-provider", "provider-ticket-1", self.package["id"]
        )
        self.assertTrue(verification["authorized"])

        revoked = dict(checked_in)
        revoked["type"] = "ticket.revoked"
        revoked["occurredAt"] = (base_time + timedelta(minutes=1)).isoformat()
        revoked_raw = json.dumps(revoked, ensure_ascii=False, separators=(",", ":")).encode()
        self.service.record_ticket_provider_event(
            "partner-ticket-provider",
            "provider-event-2",
            revoked,
            hashlib.sha256(revoked_raw).hexdigest(),
        )
        with self.assertRaises(ApiError) as error:
            self.service.verify_ticket(
                "partner-ticket-provider", "provider-ticket-1", self.package["id"]
            )
        self.assertEqual("TICKET_NOT_AUTHORIZED", error.exception.code)
        with self.assertRaises(ApiError):
            self.service.authorize_session(verification["accessToken"])

        late_check_in = dict(checked_in)
        late_check_in["occurredAt"] = (base_time - timedelta(minutes=1)).isoformat()
        late_raw = json.dumps(late_check_in, ensure_ascii=False, separators=(",", ":")).encode()
        late = self.service.record_ticket_provider_event(
            "partner-ticket-provider",
            "provider-event-3",
            late_check_in,
            hashlib.sha256(late_raw).hexdigest(),
        )
        self.assertTrue(late["ticket"]["staleIgnored"])
        with self.assertRaises(ApiError):
            self.service.verify_ticket(
                "partner-ticket-provider", "provider-ticket-1", self.package["id"]
            )

    def test_audience_can_delete_subject_scoped_data(self) -> None:
        self._publish_sample()
        for ticket_id in ("ticket-delete-1", "ticket-delete-2"):
            self.service.import_ticket(
                provider="partner-ticket-provider",
                provider_ticket_id=ticket_id,
                event_id=self.package["id"],
                checked_in=True,
                external_subject="same-user",
            )
        verification = self.service.verify_ticket(
            "partner-ticket-provider", "ticket-delete-1", self.package["id"]
        )

        deletion = self.service.delete_audience_data(verification["accessToken"])

        self.assertEqual("subject", deletion["scope"])
        self.assertEqual(2, deletion["ticketsDeleted"])
        with self.assertRaises(ApiError):
            self.service.authorize_session(verification["accessToken"])
        with self.assertRaises(ApiError):
            self.service.verify_ticket(
                "partner-ticket-provider", "ticket-delete-2", self.package["id"]
            )

    def test_retention_purge_has_preview_and_execute_modes(self) -> None:
        self._publish_sample()
        self.service.import_ticket(
            provider="partner-ticket-provider",
            provider_ticket_id="old-ticket",
            event_id=self.package["id"],
            checked_in=True,
        )
        verification = self.service.verify_ticket(
            "partner-ticket-provider", "old-ticket", self.package["id"]
        )
        old_time = "2025-01-01T00:00:00Z"
        with self.service.database.connection() as connection, connection:
            connection.execute("UPDATE tickets SET updated_at = ?", (old_time,))
            connection.execute("UPDATE audience_sessions SET expires_at = ?", (old_time,))

        preview = self.service.purge_expired_data(
            execute=False, as_of=datetime(2026, 9, 18, tzinfo=timezone.utc)
        )
        executed = self.service.purge_expired_data(
            execute=True, as_of=datetime(2026, 9, 18, tzinfo=timezone.utc)
        )

        self.assertEqual("preview", preview["mode"])
        self.assertEqual(1, preview["items"]["sessions"])
        self.assertEqual(1, preview["items"]["tickets"])
        self.assertEqual("execute", executed["mode"])
        with self.assertRaises(ApiError):
            self.service.authorize_session(verification["accessToken"])

    def test_admin_overview_summarizes_release_pipeline(self) -> None:
        self._publish_sample()
        updated = dict(self.package)
        updated["subtitle"] = "Awaiting approval"
        self.service.create_package_draft(
            updated,
            version=2,
            release_channel="production",
            base_version=1,
            change_summary="최종 리허설 반영",
        )

        overview = self.service.get_admin_overview()

        self.assertEqual(1, overview["metrics"]["eventCount"])
        self.assertEqual(1, overview["metrics"]["publishedEvents"])
        self.assertEqual(1, overview["metrics"]["awaitingApproval"])
        self.assertEqual([2, 1], [item["version"] for item in overview["events"][0]["versions"]])
        self.assertEqual("draft", overview["events"][0]["versions"][0]["status"])
        self.assertEqual("published", overview["events"][0]["versions"][1]["status"])

    def test_verified_ticket_can_write_dispatch_audit_log(self) -> None:
        self._publish_sample()
        self.service.import_ticket(
            provider="partner-ticket-provider",
            provider_ticket_id="ticket-1",
            event_id=self.package["id"],
            checked_in=True,
            external_subject="partner-user-123",
        )
        verification = self.service.verify_ticket(
            provider="partner-ticket-provider",
            provider_ticket_id="ticket-1",
            event_id=self.package["id"],
        )

        result = self.service.record_dispatch(
            verification["accessToken"],
            {
                "route": "FallbackPreview",
                "accepted": True,
                "rendererName": "SmartphonePreviewHudRenderer",
                "availability": "Ready",
                "documentId": "scene-1",
                "priority": "High",
                "clientRecordId": "event-1:7:scene-1",
                "metadata": {"reason": "mock-display-unsupported"},
            },
        )
        refreshed_verification = self.service.verify_ticket(
            provider="partner-ticket-provider",
            provider_ticket_id="ticket-1",
            event_id=self.package["id"],
        )
        duplicate = self.service.record_dispatch(
            refreshed_verification["accessToken"],
            {
                "route": "FallbackPreview",
                "accepted": True,
                "rendererName": "SmartphonePreviewHudRenderer",
                "availability": "Ready",
                "documentId": "scene-1",
                "priority": "High",
                "clientRecordId": "event-1:7:scene-1",
                "metadata": {"reason": "retry"},
            },
        )
        logs = self.service.list_dispatch_logs(event_id=self.package["id"])

        self.assertEqual(self.package["id"], result["eventId"])
        self.assertFalse(result["deduplicated"])
        self.assertTrue(duplicate["deduplicated"])
        self.assertEqual(result["id"], duplicate["id"])
        self.assertEqual(1, len(logs))
        self.assertEqual("FallbackPreview", logs[0]["route"])
        self.assertEqual({"reason": "mock-display-unsupported"}, logs[0]["metadata"])

    def _publish_sample(self) -> None:
        self.service.create_package_draft(self.package, version=1, release_channel="production")
        self.service.approve_package(self.package["id"], version=1, approved_by="test-operator")
        self.service.publish_package(self.package["id"], version=1)


class BackendApiTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary_directory = tempfile.TemporaryDirectory()
        database = Database(Path(self.temporary_directory.name) / "api.db")
        self.service = BackendService(database)
        self.server = create_server(
            self.service,
            host="127.0.0.1",
            port=0,
            admin_key="test-admin-key",
            ticket_provider_secrets={"partner-ticket-provider": "provider-test-secret"},
        )
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.package = json.loads(SAMPLE_PACKAGE.read_text(encoding="utf-8"))

    def tearDown(self) -> None:
        self.server.shutdown()
        self.server.server_close()
        self.thread.join(timeout=2)
        self.temporary_directory.cleanup()

    def test_end_to_end_api_flow(self) -> None:
        status, headers, body = self._request_raw("GET", "/admin")
        self.assertEqual(200, status)
        self.assertIn("text/html", headers["content-type"])
        self.assertIn("frame-ancestors 'none'", headers["content-security-policy"])
        self.assertIn("LumenCue 운영 콘솔", body.decode("utf-8"))

        status, health = self._request("GET", "/health")
        self.assertEqual(200, status)
        self.assertEqual("ok", health["data"]["status"])

        status, policy = self._request("GET", "/v1/data-policy")
        self.assertEqual(200, status)
        self.assertEqual("ticket-scoped-pseudonymous", policy["data"]["accountMode"])

        status, unauthorized = self._request(
            "POST",
            "/v1/admin/concert-packages",
            {"version": 1, "releaseChannel": "production", "package": self.package},
        )
        self.assertEqual(401, status)
        self.assertEqual("INVALID_ADMIN_KEY", unauthorized["error"]["code"])

        admin_headers = {"X-Admin-Key": "test-admin-key"}
        status, unauthorized = self._request("GET", "/v1/admin/overview")
        self.assertEqual(401, status)
        self.assertEqual("INVALID_ADMIN_KEY", unauthorized["error"]["code"])

        status, _ = self._request(
            "POST",
            "/v1/admin/concert-packages",
            {"version": 1, "releaseChannel": "production", "package": self.package},
            admin_headers,
        )
        self.assertEqual(201, status)

        event_id = self.package["id"]
        status, _ = self._request(
            "POST",
            f"/v1/admin/concert-packages/{event_id}/versions/1/approve",
            {"approvedBy": "api-test-operator"},
            admin_headers,
        )
        self.assertEqual(200, status)

        status, _ = self._request(
            "POST",
            f"/v1/admin/concert-packages/{event_id}/versions/1/publish",
            {},
            admin_headers,
        )
        self.assertEqual(200, status)

        status, concerts = self._request("GET", "/v1/concerts")
        self.assertEqual(200, status)
        self.assertEqual(event_id, concerts["data"]["items"][0]["id"])

        status, package = self._request("GET", f"/v1/concerts/{event_id}/package")
        self.assertEqual(200, status)
        self.assertEqual(1, package["data"]["version"])

        provider_payload = {
            "type": "ticket.checked_in",
            "eventId": event_id,
            "ticketId": "ticket-provider-api-1",
            "subjectId": "provider-subject-api-1",
            "occurredAt": datetime.now(timezone.utc).isoformat(),
        }
        provider_headers = self._provider_headers(provider_payload, "provider-api-event-1")
        status, provider_event = self._request(
            "POST", "/v1/partners/tickets/events", provider_payload, provider_headers
        )
        self.assertEqual(201, status)
        self.assertFalse(provider_event["data"]["deduplicated"])
        status, provider_event = self._request(
            "POST", "/v1/partners/tickets/events", provider_payload, provider_headers
        )
        self.assertEqual(200, status)
        self.assertTrue(provider_event["data"]["deduplicated"])

        status, _ = self._request(
            "POST",
            "/v1/admin/tickets",
            {
                "provider": "partner-ticket-provider",
                "ticketId": "ticket-api-1",
                "eventId": event_id,
                "checkedIn": True,
            },
            admin_headers,
        )
        self.assertEqual(200, status)

        status, verification = self._request(
            "POST",
            "/v1/tickets/verify",
            {
                "provider": "partner-ticket-provider",
                "ticketId": "ticket-api-1",
                "eventId": event_id,
            },
        )
        self.assertEqual(200, status)
        access_token = verification["data"]["accessToken"]

        status, _ = self._request(
            "POST",
            "/v1/device-dispatch-logs",
            {
                "eventId": event_id,
                "route": "FallbackPreview",
                "accepted": True,
                "rendererName": "SmartphonePreviewHudRenderer",
                "availability": "Ready",
                "documentId": "document-api-1",
                "priority": "Normal",
            },
            {"Authorization": f"Bearer {access_token}"},
        )
        self.assertEqual(201, status)

        status, logs = self._request(
            "GET",
            f"/v1/admin/device-dispatch-logs?eventId={event_id}",
            headers=admin_headers,
        )
        self.assertEqual(200, status)
        self.assertEqual(1, len(logs["data"]["items"]))

        status, overview = self._request(
            "GET", "/v1/admin/overview", headers=admin_headers
        )
        self.assertEqual(200, status)
        self.assertEqual(1, overview["data"]["metrics"]["publishedEvents"])
        self.assertEqual(1, overview["data"]["metrics"]["dispatchCount"])

        status, retention = self._request(
            "POST", "/v1/admin/retention/purge", {"mode": "preview"}, admin_headers
        )
        self.assertEqual(200, status)
        self.assertEqual("preview", retention["data"]["mode"])

        status, deletion = self._request(
            "DELETE",
            "/v1/me/data",
            headers={"Authorization": f"Bearer {access_token}"},
        )
        self.assertEqual(200, status)
        self.assertEqual(1, deletion["data"]["ticketsDeleted"])
        status, invalidated = self._request(
            "POST",
            "/v1/device-dispatch-logs",
            {
                "route": "FallbackPreview",
                "accepted": True,
                "rendererName": "SmartphonePreviewHudRenderer",
                "availability": "Ready",
                "documentId": "deleted-session",
                "priority": "Normal",
            },
            {"Authorization": f"Bearer {access_token}"},
        )
        self.assertEqual(401, status)
        self.assertEqual("INVALID_ACCESS_TOKEN", invalidated["error"]["code"])

    def _request(
        self,
        method: str,
        path: str,
        body: dict[str, Any] | None = None,
        headers: dict[str, str] | None = None,
    ) -> tuple[int, dict[str, Any]]:
        connection = http.client.HTTPConnection("127.0.0.1", self.server.server_port, timeout=5)
        request_headers = dict(headers or {})
        encoded: bytes | None = None
        if body is not None:
            encoded = json.dumps(body, ensure_ascii=False).encode("utf-8")
            request_headers["Content-Type"] = "application/json"
        connection.request(method, path, body=encoded, headers=request_headers)
        response = connection.getresponse()
        payload = json.loads(response.read().decode("utf-8"))
        connection.close()
        return response.status, payload

    def _provider_headers(self, body: dict[str, Any], event_id: str) -> dict[str, str]:
        timestamp = str(int(datetime.now(timezone.utc).timestamp()))
        encoded = json.dumps(body, ensure_ascii=False).encode("utf-8")
        signature = hmac.new(
            b"provider-test-secret",
            timestamp.encode("ascii") + b"." + encoded,
            hashlib.sha256,
        ).hexdigest()
        return {
            "X-LumenCue-Provider": "partner-ticket-provider",
            "X-LumenCue-Event-Id": event_id,
            "X-LumenCue-Timestamp": timestamp,
            "X-LumenCue-Signature": f"sha256={signature}",
        }

    def _request_raw(
        self,
        method: str,
        path: str,
    ) -> tuple[int, dict[str, str], bytes]:
        connection = http.client.HTTPConnection("127.0.0.1", self.server.server_port, timeout=5)
        connection.request(method, path)
        response = connection.getresponse()
        headers = {key.lower(): value for key, value in response.getheaders()}
        body = response.read()
        connection.close()
        return response.status, headers, body


if __name__ == "__main__":
    unittest.main()
