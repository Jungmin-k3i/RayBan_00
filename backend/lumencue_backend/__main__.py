from __future__ import annotations

import argparse
import json
from pathlib import Path

from .api import create_server
from .config import Settings
from .database import Database
from .service import ApiError, BackendService


def build_parser() -> argparse.ArgumentParser:
    settings = Settings.from_environment()
    parser = argparse.ArgumentParser(description="LumenCue backend MVP")
    parser.add_argument("--db", type=Path, default=settings.database_path, help="SQLite database path")
    subparsers = parser.add_subparsers(dest="command", required=True)

    subparsers.add_parser("init-db", help="Apply database migrations")

    purge = subparsers.add_parser("purge-data", help="Preview or execute the retention policy")
    purge.add_argument("--execute", action="store_true", help="Delete expired records")

    serve = subparsers.add_parser("serve", help="Start the HTTP API")
    serve.add_argument("--host", default=settings.host)
    serve.add_argument("--port", type=int, default=settings.port)

    package = subparsers.add_parser("import-package", help="Validate and import a package draft")
    package.add_argument("file", type=Path)
    package.add_argument("--version", type=int, default=1)
    package.add_argument("--release-channel", choices=("rehearsal", "production", "hotfix"))
    package.add_argument("--base-version", type=int)
    package.add_argument("--change-summary")
    package.add_argument("--approved-by")
    package.add_argument("--publish", action="store_true")

    ticket = subparsers.add_parser("import-ticket", help="Import a ticket verification result")
    ticket.add_argument("--provider", required=True)
    ticket.add_argument("--ticket-id", required=True)
    ticket.add_argument("--event-id", required=True)
    ticket.add_argument("--checked-in", action="store_true")
    ticket.add_argument("--external-subject")
    return parser


def main() -> int:
    args = build_parser().parse_args()
    settings = Settings.from_environment()
    service = BackendService(Database(args.db), subject_hash_key=settings.subject_hash_key)
    applied = service.initialize()

    try:
        if args.command == "init-db":
            print(json.dumps({"database": str(args.db), "appliedMigrations": applied}, ensure_ascii=False))
            return 0
        if args.command == "purge-data":
            print(json.dumps(service.purge_expired_data(execute=args.execute), ensure_ascii=False))
            return 0
        if args.command == "import-package":
            package = json.loads(args.file.read_text(encoding="utf-8"))
            release_channel = args.release_channel or ("production" if args.publish else "rehearsal")
            result = service.create_package_draft(
                package,
                args.version,
                release_channel=release_channel,
                base_version=args.base_version,
                change_summary=args.change_summary,
            )
            if args.publish:
                if not args.approved_by:
                    raise ApiError(422, "MISSING_APPROVER", "--publish에는 --approved-by가 필요합니다.")
                service.approve_package(package["id"], args.version, args.approved_by)
                result = service.publish_package(package["id"], args.version)
            print(json.dumps(result, ensure_ascii=False))
            return 0
        if args.command == "import-ticket":
            result = service.import_ticket(
                provider=args.provider,
                provider_ticket_id=args.ticket_id,
                event_id=args.event_id,
                checked_in=args.checked_in,
                external_subject=args.external_subject,
            )
            print(json.dumps(result, ensure_ascii=False))
            return 0
        if args.command == "serve":
            server = create_server(
                service=service,
                host=args.host,
                port=args.port,
                admin_key=settings.admin_key,
                allowed_origin=settings.allowed_origin,
                ticket_provider_secrets=settings.ticket_provider_secrets,
            )
            print(f"LumenCue backend listening on http://{args.host}:{server.server_port}")
            if not settings.admin_key:
                print("Admin API disabled: set LUMENCUE_ADMIN_KEY before starting the server.")
            try:
                server.serve_forever()
            except KeyboardInterrupt:
                pass
            finally:
                server.server_close()
            return 0
    except (ApiError, json.JSONDecodeError, OSError) as error:
        if isinstance(error, ApiError):
            payload = {"code": error.code, "message": error.message, "details": error.details}
        else:
            payload = {"code": "COMMAND_FAILED", "message": str(error)}
        print(json.dumps({"error": payload}, ensure_ascii=False))
        return 1
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
