package com.k3i.lumencue

import org.json.JSONArray
import org.json.JSONObject

data class ConcertEventPackageValidation(
    val valid: Boolean,
    val errors: List<String>,
    val warnings: List<String> = emptyList()
)

object ConcertEventPackageParser {
    fun validate(json: String): ConcertEventPackageValidation {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        try {
            val root = JSONObject(json)
            root.requireString("id", errors)
            root.requireString("title", errors)

            val partnerBrief = root.requireObject("partnerBrief", errors)
            partnerBrief?.requireStrings(
                "partnerBrief",
                listOf("promoter", "venue", "showDate", "dataStatus"),
                errors
            )

            val venueInfo = root.requireObject("venueInfo", errors)
            venueInfo?.requireStrings(
                "venueInfo",
                listOf("name", "gate", "seat", "nearestExit", "merchBooth"),
                errors
            )

            val ticketPolicy = root.requireObject("ticketPolicy", errors)
            ticketPolicy?.requireStrings("ticketPolicy", listOf("provider"), errors)

            val tracks = root.requireArray("tracks", errors)
            if (tracks != null) {
                if (tracks.length() == 0) errors += "empty:tracks"
                tracks.validateTracks(errors)
            }

            val partnerAssets = root.requireArray("partnerAssets", errors)
            if (partnerAssets != null) {
                if (partnerAssets.length() == 0) {
                    errors += "empty:partnerAssets"
                } else {
                    partnerAssets.validatePartnerAssets(errors, warnings)
                }
            }

            val operationsChecklist = root.optJSONArray("operationsChecklist")
            if (operationsChecklist == null) {
                warnings += "warning:operationsChecklist.missing"
            } else if (operationsChecklist.length() == 0) {
                warnings += "warning:operationsChecklist.empty"
            } else {
                operationsChecklist.validateOperationsChecklist(errors)
            }

            root.optJSONArray("interactionEvents")?.validateInteractionEvents(errors)
        } catch (exception: Exception) {
            errors += "invalid-json:${exception.message ?: "unknown"}"
        }
        return ConcertEventPackageValidation(
            valid = errors.isEmpty(),
            errors = errors,
            warnings = warnings
        )
    }

    fun parse(json: String): ConcertEvent {
        val validation = validate(json)
        require(validation.valid) {
            "Invalid concert package: ${validation.errors.joinToString(",")}"
        }

        val root = JSONObject(json)
        val partnerBrief = root.getJSONObject("partnerBrief")
        val venueInfo = root.getJSONObject("venueInfo")
        val ticketPolicy = root.getJSONObject("ticketPolicy")

        return ConcertEvent(
            id = root.getString("id"),
            title = root.getString("title"),
            subtitle = root.optString("subtitle", ""),
            partnerBrief = PartnerBrief(
                promoter = partnerBrief.getString("promoter"),
                venue = partnerBrief.getString("venue"),
                showDate = partnerBrief.getString("showDate"),
                dataStatus = partnerBrief.getString("dataStatus")
            ),
            venueInfo = VenueInfo(
                name = venueInfo.getString("name"),
                gate = venueInfo.getString("gate"),
                seat = venueInfo.getString("seat"),
                nearestExit = venueInfo.getString("nearestExit"),
                merchBooth = venueInfo.getString("merchBooth")
            ),
            ticket = ConcertTicket(
                ticketId = ticketPolicy.optString("ticketId", "${root.getString("id")}-ticket"),
                holderName = ticketPolicy.optString("holderName", "관객"),
                checkedIn = ticketPolicy.optBoolean("checkedIn", !ticketPolicy.optBoolean("requiresCheckIn", true))
            ),
            tracks = root.getJSONArray("tracks").toTracks(),
            partnerAssets = root.getJSONArray("partnerAssets").toPartnerAssets(),
            operationsChecklist = root.optJSONArray("operationsChecklist")?.toOperationsChecklist()
                ?: emptyList(),
            interactionEvents = root.optJSONArray("interactionEvents")?.toInteractionEvents()
                ?: emptyList()
        )
    }

    fun parseList(jsonItems: List<String>): List<ConcertEvent> =
        jsonItems.map { parse(it) }
}

private fun JSONObject.requireObject(key: String, errors: MutableList<String>): JSONObject? {
    if (!has(key) || isNull(key)) {
        errors += "missing:$key"
        return null
    }
    return optJSONObject(key) ?: run {
        errors += "invalid:$key"
        null
    }
}

private fun JSONObject.requireArray(key: String, errors: MutableList<String>): JSONArray? {
    if (!has(key) || isNull(key)) {
        errors += "missing:$key"
        return null
    }
    return optJSONArray(key) ?: run {
        errors += "invalid:$key"
        null
    }
}

private fun JSONObject.requireString(key: String, errors: MutableList<String>, prefix: String? = null) {
    val path = listOfNotNull(prefix, key).joinToString(".")
    if (!has(key) || isNull(key) || optString(key).isBlank()) {
        errors += "missing:$path"
    }
}

private fun JSONObject.requireStrings(prefix: String, keys: List<String>, errors: MutableList<String>) {
    keys.forEach { key -> requireString(key, errors, prefix) }
}

private fun JSONArray.validateTracks(errors: MutableList<String>) {
    for (trackIndex in 0 until length()) {
        val track = optJSONObject(trackIndex)
        val trackPath = "tracks[$trackIndex]"
        if (track == null) {
            errors += "invalid:$trackPath"
            continue
        }

        track.requireString("title", errors, trackPath)
        track.requireString("artist", errors, trackPath)
        if (!track.has("durationSeconds") || track.optInt("durationSeconds", -1) <= 0) {
            errors += "invalid:$trackPath.durationSeconds"
        }

        val durationSeconds = track.optInt("durationSeconds", -1)
        val cues = track.requireArray("cues", errors)
        if (cues != null) {
            if (cues.length() == 0) errors += "empty:$trackPath.cues"
            cues.validateCues(trackPath, durationSeconds, errors)
        }
    }
}

private fun JSONArray.validateCues(trackPath: String, trackDurationSeconds: Int, errors: MutableList<String>) {
    for (cueIndex in 0 until length()) {
        val cue = optJSONObject(cueIndex)
        val cuePath = "$trackPath.cues[$cueIndex]"
        if (cue == null) {
            errors += "invalid:$cuePath"
            continue
        }

        listOf("titleKo", "titleEn", "hudMessageKo", "hudMessageEn")
            .forEach { key -> cue.requireString(key, errors, cuePath) }

        if (!cue.has("atSecond") || cue.optInt("atSecond", -1) < 0) {
            errors += "invalid:$cuePath.atSecond"
        } else if (trackDurationSeconds >= 0 && cue.optInt("atSecond") > trackDurationSeconds) {
            errors += "invalid:$cuePath.atSecond"
        }

        val durationMillis = cue.optInt("durationMillis", 3_000)
        if (durationMillis !in 1_000..10_000) errors += "invalid:$cuePath.durationMillis"

        cue.validateEnum("placement", HudPlacement.entries.map { it.name }, cuePath, errors)
        cue.validateEnum("effect", HudEffect.entries.map { it.name }, cuePath, errors)
    }
}

private fun JSONArray.validatePartnerAssets(
    errors: MutableList<String>,
    warnings: MutableList<String>
) {
    var hasCapturePolicy = false
    for (assetIndex in 0 until length()) {
        val asset = optJSONObject(assetIndex)
        val assetPath = "partnerAssets[$assetIndex]"
        if (asset == null) {
            errors += "invalid:$assetPath"
            continue
        }

        asset.requireString("name", errors, assetPath)
        asset.requireString("detail", errors, assetPath)
        asset.validateEnum("status", PartnerAssetStatus.entries.map { it.name }, assetPath, errors)

        val text = "${asset.optString("name")} ${asset.optString("detail")}"
        if (text.contains("촬영") || text.contains("capture", ignoreCase = true)) {
            hasCapturePolicy = true
        }
    }
    if (!hasCapturePolicy) warnings += "warning:partnerAssets.capturePolicyMissing"
}

private fun JSONArray.validateOperationsChecklist(errors: MutableList<String>) {
    for (itemIndex in 0 until length()) {
        val item = optJSONObject(itemIndex)
        val itemPath = "operationsChecklist[$itemIndex]"
        if (item == null) {
            errors += "invalid:$itemPath"
            continue
        }

        item.requireString("title", errors, itemPath)
        item.requireString("owner", errors, itemPath)
        item.validateEnum("status", PartnerAssetStatus.entries.map { it.name }, itemPath, errors)
    }
}

private fun JSONArray.validateInteractionEvents(errors: MutableList<String>) {
    for (itemIndex in 0 until length()) {
        val item = optJSONObject(itemIndex)
        val itemPath = "interactionEvents[$itemIndex]"
        if (item == null) {
            errors += "invalid:$itemPath"
            continue
        }

        item.requireString("id", errors, itemPath)
        item.requireString("titleKo", errors, itemPath)
        item.requireString("titleEn", errors, itemPath)
        item.requireString("messageKo", errors, itemPath)
        item.requireString("messageEn", errors, itemPath)
        item.requireString("ctaLabel", errors, itemPath)
        item.validateEnum("type", ConcertInteractionEventType.entries.map { it.name }, itemPath, errors)
        item.optString("reactionSignal").takeIf { it.isNotBlank() }?.let { signal ->
            if (signal !in ReactionSignal.entries.map { it.name }) {
                errors += "invalid:$itemPath.reactionSignal"
            }
        }

        if (!item.has("trackIndex") || item.optInt("trackIndex", -1) < 0) {
            errors += "invalid:$itemPath.trackIndex"
        }
        if (!item.has("startSecond") || item.optInt("startSecond", -1) < 0) {
            errors += "invalid:$itemPath.startSecond"
        }
        if (!item.has("endSecond") || item.optInt("endSecond", -1) < 0) {
            errors += "invalid:$itemPath.endSecond"
        }
        if (item.optInt("endSecond", 0) < item.optInt("startSecond", 0)) {
            errors += "invalid:$itemPath.timeRange"
        }
    }
}

private fun JSONObject.validateEnum(
    key: String,
    allowedValues: List<String>,
    prefix: String,
    errors: MutableList<String>
) {
    val value = optString(key)
    if (!has(key) || isNull(key) || value !in allowedValues) {
        errors += "invalid:$prefix.$key"
    }
}

private fun JSONArray.toTracks(): List<ConcertTrack> =
    buildList {
        for (index in 0 until length()) {
            val item = getJSONObject(index)
            add(
                ConcertTrack(
                    title = item.getString("title"),
                    artist = item.getString("artist"),
                    durationSeconds = item.getInt("durationSeconds"),
                    cues = item.getJSONArray("cues").toCues()
                )
            )
        }
    }

private fun JSONArray.toCues(): List<ConcertCue> =
    buildList {
        for (index in 0 until length()) {
            val item = getJSONObject(index)
            add(
                ConcertCue(
                    atSecond = item.getInt("atSecond"),
                    titleKo = item.getString("titleKo"),
                    titleEn = item.getString("titleEn"),
                    hudMessageKo = item.getString("hudMessageKo"),
                    hudMessageEn = item.getString("hudMessageEn"),
                    placement = enumValueOf(item.getString("placement")),
                    effect = enumValueOf(item.getString("effect")),
                    durationMillis = item.optInt("durationMillis", 3_000)
                )
            )
        }
    }

private fun JSONArray.toPartnerAssets(): List<PartnerAsset> =
    buildList {
        for (index in 0 until length()) {
            val item = getJSONObject(index)
            add(
                PartnerAsset(
                    name = item.getString("name"),
                    detail = item.getString("detail"),
                    status = enumValueOf(item.getString("status"))
                )
            )
        }
    }

private fun JSONArray.toOperationsChecklist(): List<OperationsChecklistItem> =
    buildList {
        for (index in 0 until length()) {
            val item = getJSONObject(index)
            add(
                OperationsChecklistItem(
                    title = item.getString("title"),
                    owner = item.getString("owner"),
                    status = enumValueOf(item.getString("status"))
                )
            )
        }
    }

private fun JSONArray.toInteractionEvents(): List<ConcertInteractionEvent> =
    buildList {
        for (index in 0 until length()) {
            val item = getJSONObject(index)
            add(
                ConcertInteractionEvent(
                    id = item.getString("id"),
                    type = enumValueOf(item.getString("type")),
                    trackIndex = item.getInt("trackIndex"),
                    startSecond = item.getInt("startSecond"),
                    endSecond = item.getInt("endSecond"),
                    titleKo = item.getString("titleKo"),
                    messageKo = item.getString("messageKo"),
                    titleEn = item.getString("titleEn"),
                    messageEn = item.getString("messageEn"),
                    zone = item.optString("zone", "전체"),
                    ctaLabel = item.getString("ctaLabel"),
                    reactionSignal = item.optString("reactionSignal")
                        .takeIf { it.isNotBlank() }
                        ?.let { enumValueOf<ReactionSignal>(it) },
                    requiresApproval = item.optBoolean("requiresApproval", true)
                )
            )
        }
    }
