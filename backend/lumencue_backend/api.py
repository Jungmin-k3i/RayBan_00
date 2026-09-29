from __future__ import annotations

import hmac
import hashlib
import json
import re
from datetime import datetime, timezone
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any
from urllib.parse import parse_qs, unquote, urlparse

from .service import ApiError, BackendService


MAX_BODY_BYTES = 1_048_576
PACKAGE_PATH = re.compile(r"^/v1/concerts/([^/]+)/package$")
PUBLISH_PATH = re.compile(r"^/v1/admin/concert-packages/([^/]+)/versions/(\d+)/publish$")
APPROVE_PATH = re.compile(r"^/v1/admin/concert-packages/([^/]+)/versions/(\d+)/approve$")
NOTICE_PATH = re.compile(r"^/v1/admin/concerts/([^/]+)/emergency-notices$")
ADMIN_ASSET_ROOT = Path(__file__).with_name("admin")
ADMIN_ASSETS = {
    "/admin": ("index.html", "text/html; charset=utf-8"),
    "/admin/": ("index.html", "text/html; charset=utf-8"),
    "/admin/app.css": ("app.css", "text/css; charset=utf-8"),
    "/admin/app.js": ("app.js", "text/javascript; charset=utf-8"),
}


class LumenCueHttpServer(ThreadingHTTPServer):
    daemon_threads = True

    def __init__(
        self,
        server_address: tuple[str, int],
        service: BackendService,
        admin_key: str | None,
        allowed_origin: str | None,
        ticket_provider_secrets: dict[str, str] | None,
    ):
        super().__init__(server_address, LumenCueRequestHandler)
        self.service = service
        self.admin_key = admin_key
        self.allowed_origin = allowed_origin
        self.ticket_provider_secrets = ticket_provider_secrets or {}


class LumenCueRequestHandler(BaseHTTPRequestHandler):
    server: LumenCueHttpServer
    protocol_version = "HTTP/1.1"

    def do_GET(self) -> None:
        asset = ADMIN_ASSETS.get(urlparse(self.path).path)
        if asset is not None:
            self._send_admin_asset(*asset)
            return
        self._dispatch(self._handle_get)

    def do_POST(self) -> None:
        self._dispatch(self._handle_post)

    def do_DELETE(self) -> None:
        self._dispatch(self._handle_delete)

    def do_OPTIONS(self) -> None:
        self.send_response(HTTPStatus.NO_CONTENT)
        self._send_common_headers(0)
        self.send_header("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS")
        self.send_header(
            "Access-Control-Allow-Headers",
            "Authorization, Content-Type, X-Admin-Key, X-LumenCue-Provider, "
            "X-LumenCue-Event-Id, X-LumenCue-Timestamp, X-LumenCue-Signature",
        )
        self.end_headers()

    def _dispatch(self, handler: Any) -> None:
        try:
            status, payload = handler()
            self._send_json(status, {"data": payload})
        except ApiError as error:
            body: dict[str, Any] = {
                "error": {"code": error.code, "message": error.message}
            }
            if error.details:
                body["error"]["details"] = error.details
            self._send_json(error.status, body)
        except (json.JSONDecodeError, UnicodeDecodeError):
            self._send_json(
                HTTPStatus.BAD_REQUEST,
                {"error": {"code": "INVALID_JSON", "message": "올바른 UTF-8 JSON body가 필요합니다."}},
            )
        except (BrokenPipeError, ConnectionAbortedError, ConnectionResetError):
            return
        except Exception as error:
            print(f"Unhandled API error: {type(error).__name__}: {error}")
            self._send_json(
                HTTPStatus.INTERNAL_SERVER_ERROR,
                {"error": {"code": "INTERNAL_ERROR", "message": "서버 요청 처리에 실패했습니다."}},
            )

    def _handle_get(self) -> tuple[int, Any]:
        parsed = urlparse(self.path)
        if parsed.path == "/health":
            return HTTPStatus.OK, self.server.service.health()
        if parsed.path == "/v1/data-policy":
            return HTTPStatus.OK, self.server.service.get_data_policy()
        if parsed.path == "/v1/concerts":
            return HTTPStatus.OK, {"items": self.server.service.list_published_concerts()}
        if parsed.path == "/v1/admin/overview":
            self._require_admin()
            return HTTPStatus.OK, self.server.service.get_admin_overview()
        package_match = PACKAGE_PATH.match(parsed.path)
        if package_match:
            return HTTPStatus.OK, self.server.service.get_published_package(unquote(package_match.group(1)))
        if parsed.path == "/v1/admin/device-dispatch-logs":
            self._require_admin()
            query = parse_qs(parsed.query)
            event_id = query.get("eventId", [None])[0]
            try:
                limit = int(query.get("limit", ["100"])[0])
            except ValueError as error:
                raise ApiError(422, "INVALID_LIMIT", "limit은 integer여야 합니다.") from error
            return HTTPStatus.OK, {
                "items": self.server.service.list_dispatch_logs(event_id=event_id, limit=limit)
            }
        raise ApiError(404, "ROUTE_NOT_FOUND", "API 경로를 찾을 수 없습니다.")

    def _handle_post(self) -> tuple[int, Any]:
        parsed = urlparse(self.path)
        if parsed.path == "/v1/partners/tickets/events":
            raw_body = self._read_body()
            provider, provider_event_id = self._require_ticket_provider_signature(raw_body)
            payload = json.loads(raw_body.decode("utf-8"))
            result = self.server.service.record_ticket_provider_event(
                provider=provider,
                provider_event_id=provider_event_id,
                payload=payload,
                payload_sha256=hashlib.sha256(raw_body).hexdigest(),
            )
            return HTTPStatus.OK if result["deduplicated"] else HTTPStatus.CREATED, result
        if parsed.path == "/v1/admin/concert-packages":
            self._require_admin()
            body = self._read_json()
            if not isinstance(body, dict):
                raise ApiError(422, "INVALID_REQUEST", "요청 body는 JSON object여야 합니다.")
            result = self.server.service.create_package_draft(
                body.get("package"),
                body.get("version"),
                release_channel=body.get("releaseChannel", "rehearsal"),
                base_version=body.get("baseVersion"),
                change_summary=body.get("changeSummary"),
            )
            return HTTPStatus.CREATED, result
        approve_match = APPROVE_PATH.match(parsed.path)
        if approve_match:
            self._require_admin()
            body = self._read_object()
            result = self.server.service.approve_package(
                unquote(approve_match.group(1)),
                int(approve_match.group(2)),
                body.get("approvedBy"),
            )
            return HTTPStatus.OK, result
        publish_match = PUBLISH_PATH.match(parsed.path)
        if publish_match:
            self._require_admin()
            result = self.server.service.publish_package(
                unquote(publish_match.group(1)), int(publish_match.group(2))
            )
            return HTTPStatus.OK, result
        notice_match = NOTICE_PATH.match(parsed.path)
        if notice_match:
            self._require_admin()
            body = self._read_object()
            result = self.server.service.create_emergency_notice(
                event_id=unquote(notice_match.group(1)),
                severity=body.get("severity"),
                message_ko=body.get("messageKo"),
                message_en=body.get("messageEn"),
                created_by=body.get("createdBy"),
                expires_at=body.get("expiresAt"),
            )
            return HTTPStatus.CREATED, result
        if parsed.path == "/v1/admin/tickets":
            self._require_admin()
            body = self._read_object()
            result = self.server.service.import_ticket(
                provider=body.get("provider"),
                provider_ticket_id=body.get("ticketId"),
                event_id=body.get("eventId"),
                checked_in=body.get("checkedIn"),
                external_subject=body.get("externalSubject"),
            )
            return HTTPStatus.OK, result
        if parsed.path == "/v1/admin/retention/purge":
            self._require_admin()
            body = self._read_object()
            mode = body.get("mode", "preview")
            if mode not in {"preview", "execute"}:
                raise ApiError(422, "INVALID_PURGE_MODE", "mode는 preview 또는 execute여야 합니다.")
            return HTTPStatus.OK, self.server.service.purge_expired_data(execute=mode == "execute")
        if parsed.path == "/v1/tickets/verify":
            body = self._read_object()
            result = self.server.service.verify_ticket(
                provider=body.get("provider"),
                provider_ticket_id=body.get("ticketId"),
                event_id=body.get("eventId"),
            )
            return HTTPStatus.OK, result
        if parsed.path == "/v1/device-dispatch-logs":
            body = self._read_object()
            result = self.server.service.record_dispatch(self._bearer_token(), body)
            return HTTPStatus.CREATED, result
        raise ApiError(404, "ROUTE_NOT_FOUND", "API 경로를 찾을 수 없습니다.")

    def _handle_delete(self) -> tuple[int, Any]:
        parsed = urlparse(self.path)
        if parsed.path == "/v1/me/data":
            return HTTPStatus.OK, self.server.service.delete_audience_data(self._bearer_token())
        raise ApiError(404, "ROUTE_NOT_FOUND", "API 경로를 찾을 수 없습니다.")

    def _read_object(self) -> dict[str, Any]:
        body = self._read_json()
        if not isinstance(body, dict):
            raise ApiError(422, "INVALID_REQUEST", "요청 body는 JSON object여야 합니다.")
        return body

    def _read_json(self) -> Any:
        return json.loads(self._read_body().decode("utf-8"))

    def _read_body(self) -> bytes:
        content_type = self.headers.get("Content-Type", "").split(";", 1)[0].strip().lower()
        if content_type != "application/json":
            raise ApiError(415, "UNSUPPORTED_MEDIA_TYPE", "Content-Type은 application/json이어야 합니다.")
        try:
            content_length = int(self.headers.get("Content-Length", "0"))
        except ValueError as error:
            raise ApiError(400, "INVALID_CONTENT_LENGTH", "Content-Length가 올바르지 않습니다.") from error
        if content_length <= 0:
            raise ApiError(400, "EMPTY_BODY", "JSON body가 필요합니다.")
        if content_length > MAX_BODY_BYTES:
            raise ApiError(413, "BODY_TOO_LARGE", "요청 body는 1MB 이하여야 합니다.")
        return self.rfile.read(content_length)

    def _require_admin(self) -> None:
        expected = self.server.admin_key
        if not expected:
            raise ApiError(503, "ADMIN_API_DISABLED", "LUMENCUE_ADMIN_KEY가 설정되지 않았습니다.")
        provided = self.headers.get("X-Admin-Key", "")
        if not hmac.compare_digest(provided, expected):
            raise ApiError(401, "INVALID_ADMIN_KEY", "관리자 인증에 실패했습니다.")

    def _require_ticket_provider_signature(self, raw_body: bytes) -> tuple[str, str]:
        if not self.server.ticket_provider_secrets:
            raise ApiError(503, "TICKET_PROVIDER_API_DISABLED", "티켓 provider 연동이 설정되지 않았습니다.")
        provider = self.headers.get("X-LumenCue-Provider", "").strip()
        provider_event_id = self.headers.get("X-LumenCue-Event-Id", "").strip()
        timestamp = self.headers.get("X-LumenCue-Timestamp", "").strip()
        provided_signature = self.headers.get("X-LumenCue-Signature", "").strip()
        secret = self.server.ticket_provider_secrets.get(provider)
        if not provider or not provider_event_id or not timestamp or not provided_signature or not secret:
            raise ApiError(401, "INVALID_PROVIDER_SIGNATURE", "티켓 provider 인증에 실패했습니다.")
        try:
            timestamp_value = int(timestamp)
        except ValueError as error:
            raise ApiError(401, "INVALID_PROVIDER_SIGNATURE", "티켓 provider 인증에 실패했습니다.") from error
        if abs(datetime.now(timezone.utc).timestamp() - timestamp_value) > 300:
            raise ApiError(401, "PROVIDER_REQUEST_EXPIRED", "티켓 provider 요청 시간이 만료되었습니다.")
        signed = timestamp.encode("ascii") + b"." + raw_body
        expected = hmac.new(secret.encode("utf-8"), signed, hashlib.sha256).hexdigest()
        supplied = provided_signature.removeprefix("sha256=")
        if not hmac.compare_digest(supplied, expected):
            raise ApiError(401, "INVALID_PROVIDER_SIGNATURE", "티켓 provider 인증에 실패했습니다.")
        return provider, provider_event_id

    def _bearer_token(self) -> str:
        authorization = self.headers.get("Authorization", "")
        scheme, _, token = authorization.partition(" ")
        if scheme.lower() != "bearer" or not token:
            raise ApiError(401, "MISSING_ACCESS_TOKEN", "Bearer access token이 필요합니다.")
        return token

    def _send_json(self, status: int, payload: Any) -> None:
        encoded = json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
        self.close_connection = True
        self.send_response(status)
        self._send_common_headers(len(encoded))
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.end_headers()
        self.wfile.write(encoded)

    def _send_admin_asset(self, filename: str, content_type: str) -> None:
        try:
            encoded = (ADMIN_ASSET_ROOT / filename).read_bytes()
        except OSError:
            self._send_json(
                HTTPStatus.NOT_FOUND,
                {"error": {"code": "ADMIN_ASSET_NOT_FOUND", "message": "관리자 화면을 찾을 수 없습니다."}},
            )
            return
        self.close_connection = True
        self.send_response(HTTPStatus.OK)
        self._send_common_headers(len(encoded))
        self.send_header("Content-Type", content_type)
        self.send_header(
            "Content-Security-Policy",
            "default-src 'self'; connect-src 'self'; img-src 'self' data:; "
            "style-src 'self'; script-src 'self'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'",
        )
        self.send_header("Referrer-Policy", "no-referrer")
        self.send_header("X-Frame-Options", "DENY")
        self.end_headers()
        self.wfile.write(encoded)

    def _send_common_headers(self, content_length: int) -> None:
        self.send_header("Content-Length", str(content_length))
        self.send_header("Connection", "close")
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        if self.server.allowed_origin:
            self.send_header("Access-Control-Allow-Origin", self.server.allowed_origin)
            self.send_header("Vary", "Origin")

    def log_message(self, format: str, *args: Any) -> None:
        print(f"{self.address_string()} - {format % args}")


def create_server(
    service: BackendService,
    host: str = "127.0.0.1",
    port: int = 8080,
    admin_key: str | None = None,
    allowed_origin: str | None = None,
    ticket_provider_secrets: dict[str, str] | None = None,
) -> LumenCueHttpServer:
    service.initialize()
    return LumenCueHttpServer(
        (host, port),
        service,
        admin_key,
        allowed_origin,
        ticket_provider_secrets,
    )
