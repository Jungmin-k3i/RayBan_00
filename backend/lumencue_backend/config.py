from __future__ import annotations

import json
import os
from dataclasses import dataclass, field
from pathlib import Path


BACKEND_ROOT = Path(__file__).resolve().parents[1]


@dataclass(frozen=True)
class Settings:
    database_path: Path
    host: str = "127.0.0.1"
    port: int = 8080
    admin_key: str | None = None
    allowed_origin: str | None = None
    subject_hash_key: str | None = None
    ticket_provider_secrets: dict[str, str] = field(default_factory=dict)

    @classmethod
    def from_environment(cls) -> "Settings":
        database_path = Path(
            os.environ.get(
                "LUMENCUE_DB_PATH",
                str(BACKEND_ROOT / "var" / "lumencue.db"),
            )
        ).expanduser()
        return cls(
            database_path=database_path,
            host=os.environ.get("LUMENCUE_HOST", "127.0.0.1"),
            port=int(os.environ.get("LUMENCUE_PORT", "8080")),
            admin_key=os.environ.get("LUMENCUE_ADMIN_KEY") or None,
            allowed_origin=os.environ.get("LUMENCUE_ALLOWED_ORIGIN") or None,
            subject_hash_key=os.environ.get("LUMENCUE_SUBJECT_HASH_KEY") or None,
            ticket_provider_secrets=_ticket_provider_secrets(),
        )


def _ticket_provider_secrets() -> dict[str, str]:
    raw = os.environ.get("LUMENCUE_TICKET_PROVIDER_SECRETS", "").strip()
    if not raw:
        return {}
    try:
        payload = json.loads(raw)
    except json.JSONDecodeError as error:
        raise ValueError("LUMENCUE_TICKET_PROVIDER_SECRETS는 JSON object여야 합니다.") from error
    if not isinstance(payload, dict) or any(
        not isinstance(provider, str)
        or not provider.strip()
        or not isinstance(secret, str)
        or not secret
        for provider, secret in payload.items()
    ):
        raise ValueError("티켓 provider secret은 비어 있지 않은 string map이어야 합니다.")
    return {provider.strip(): secret for provider, secret in payload.items()}
