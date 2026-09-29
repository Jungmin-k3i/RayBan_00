from __future__ import annotations

from dataclasses import asdict, dataclass
from typing import Any


PLACEMENTS = {"LowerEdge", "RightCorner", "PeripheralPulse"}
EFFECTS = {"Caption", "Countdown", "EdgePulse", "EnergyMeter"}


@dataclass(frozen=True)
class ValidationIssue:
    path: str
    message: str

    def to_dict(self) -> dict[str, str]:
        return asdict(self)


def validate_concert_package(payload: Any) -> list[ValidationIssue]:
    issues: list[ValidationIssue] = []
    if not isinstance(payload, dict):
        return [ValidationIssue("$", "공연 패키지는 JSON object여야 합니다.")]

    for field in ("id", "title"):
        _required_string(payload, field, issues)

    partner = _required_object(payload, "partnerBrief", issues)
    for field in ("promoter", "venue", "showDate", "dataStatus"):
        _required_string(partner, field, issues, "partnerBrief")

    venue = _required_object(payload, "venueInfo", issues)
    for field in ("name", "gate", "seat", "nearestExit", "merchBooth"):
        _required_string(venue, field, issues, "venueInfo")

    ticket = _required_object(payload, "ticketPolicy", issues)
    _required_string(ticket, "provider", issues, "ticketPolicy")
    if not isinstance(ticket.get("requiresCheckIn"), bool):
        issues.append(ValidationIssue("ticketPolicy.requiresCheckIn", "boolean 값이 필요합니다."))

    partner_assets = payload.get("partnerAssets")
    if not isinstance(partner_assets, list):
        issues.append(ValidationIssue("partnerAssets", "array가 필요합니다."))

    tracks = payload.get("tracks")
    if not isinstance(tracks, list) or not tracks:
        issues.append(ValidationIssue("tracks", "한 개 이상의 track이 필요합니다."))
        tracks = []

    for track_index, track in enumerate(tracks):
        path = f"tracks[{track_index}]"
        if not isinstance(track, dict):
            issues.append(ValidationIssue(path, "track은 object여야 합니다."))
            continue
        _required_string(track, "title", issues, path)
        _required_string(track, "artist", issues, path)
        duration = track.get("durationSeconds")
        if not isinstance(duration, int) or isinstance(duration, bool) or duration < 1:
            issues.append(ValidationIssue(f"{path}.durationSeconds", "1 이상의 integer가 필요합니다."))
            duration = 0
        cues = track.get("cues")
        if not isinstance(cues, list):
            issues.append(ValidationIssue(f"{path}.cues", "array가 필요합니다."))
            continue
        for cue_index, cue in enumerate(cues):
            cue_path = f"{path}.cues[{cue_index}]"
            _validate_cue(cue, cue_path, duration, issues)

    interaction_ids: set[str] = set()
    interactions = payload.get("interactionEvents", [])
    if not isinstance(interactions, list):
        issues.append(ValidationIssue("interactionEvents", "array가 필요합니다."))
        interactions = []
    for index, interaction in enumerate(interactions):
        path = f"interactionEvents[{index}]"
        if not isinstance(interaction, dict):
            issues.append(ValidationIssue(path, "interaction event는 object여야 합니다."))
            continue
        event_id = interaction.get("id")
        if not isinstance(event_id, str) or not event_id.strip():
            issues.append(ValidationIssue(f"{path}.id", "비어 있지 않은 string이 필요합니다."))
        elif event_id in interaction_ids:
            issues.append(ValidationIssue(f"{path}.id", "interaction event id가 중복되었습니다."))
        else:
            interaction_ids.add(event_id)
        track_index = interaction.get("trackIndex")
        if not isinstance(track_index, int) or isinstance(track_index, bool) or not 0 <= track_index < len(tracks):
            issues.append(ValidationIssue(f"{path}.trackIndex", "존재하는 track index가 필요합니다."))
            continue
        start = interaction.get("startSecond")
        end = interaction.get("endSecond")
        duration = tracks[track_index].get("durationSeconds", 0) if isinstance(tracks[track_index], dict) else 0
        if not isinstance(start, int) or isinstance(start, bool) or start < 0:
            issues.append(ValidationIssue(f"{path}.startSecond", "0 이상의 integer가 필요합니다."))
        if not isinstance(end, int) or isinstance(end, bool) or not isinstance(start, int) or end <= start:
            issues.append(ValidationIssue(f"{path}.endSecond", "startSecond보다 큰 integer가 필요합니다."))
        elif isinstance(duration, int) and end > duration:
            issues.append(ValidationIssue(f"{path}.endSecond", "track duration을 초과할 수 없습니다."))

    return issues


def _validate_cue(cue: Any, path: str, track_duration: int, issues: list[ValidationIssue]) -> None:
    if not isinstance(cue, dict):
        issues.append(ValidationIssue(path, "cue는 object여야 합니다."))
        return
    for field in ("titleKo", "titleEn", "hudMessageKo", "hudMessageEn"):
        _required_string(cue, field, issues, path)
    at_second = cue.get("atSecond")
    if not isinstance(at_second, int) or isinstance(at_second, bool) or at_second < 0:
        issues.append(ValidationIssue(f"{path}.atSecond", "0 이상의 integer가 필요합니다."))
    elif track_duration and at_second >= track_duration:
        issues.append(ValidationIssue(f"{path}.atSecond", "track duration보다 작아야 합니다."))
    if cue.get("placement") not in PLACEMENTS:
        issues.append(ValidationIssue(f"{path}.placement", "지원하지 않는 HUD placement입니다."))
    if cue.get("effect") not in EFFECTS:
        issues.append(ValidationIssue(f"{path}.effect", "지원하지 않는 HUD effect입니다."))
    duration = cue.get("durationMillis", 3000)
    if not isinstance(duration, int) or isinstance(duration, bool) or not 1000 <= duration <= 10000:
        issues.append(ValidationIssue(f"{path}.durationMillis", "1000~10000 범위의 integer가 필요합니다."))


def _required_object(
    parent: dict[str, Any], field: str, issues: list[ValidationIssue]
) -> dict[str, Any]:
    value = parent.get(field)
    if not isinstance(value, dict):
        issues.append(ValidationIssue(field, "object가 필요합니다."))
        return {}
    return value


def _required_string(
    parent: dict[str, Any],
    field: str,
    issues: list[ValidationIssue],
    prefix: str = "",
) -> None:
    value = parent.get(field)
    path = f"{prefix}.{field}" if prefix else field
    if not isinstance(value, str) or not value.strip():
        issues.append(ValidationIssue(path, "비어 있지 않은 string이 필요합니다."))

