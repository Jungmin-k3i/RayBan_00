package com.k3i.rayban_00

enum class FieldRequirement {
    Required,
    Optional
}

enum class ImportIssueSeverity(val label: String) {
    Info("정보"),
    Warning("경고"),
    Blocking("배포 차단")
}

data class PartnerFieldMapping(
    val sourceOwner: PartnerDataSourceType,
    val sourceField: String,
    val appField: String,
    val requirement: FieldRequirement,
    val blockingWhenMissing: Boolean,
    val semanticRule: String
)

data class PartnerImportIssue(
    val sourceField: String,
    val severity: ImportIssueSeverity,
    val message: String
)

data class PartnerImportValidationReport(
    val sourceName: String,
    val sourceVersion: String,
    val approvedVersion: String?,
    val mappings: List<PartnerFieldMapping>,
    val issues: List<PartnerImportIssue>
) {
    val canPublish: Boolean
        get() = issues.none { it.severity == ImportIssueSeverity.Blocking }

    val blockingIssueCount: Int
        get() = issues.count { it.severity == ImportIssueSeverity.Blocking }

    val warningIssueCount: Int
        get() = issues.count { it.severity == ImportIssueSeverity.Warning }

    val requiredFieldCount: Int
        get() = mappings.count { it.requirement == FieldRequirement.Required }
}

fun defaultPartnerFieldMappings(): List<PartnerFieldMapping> =
    listOf(
        PartnerFieldMapping(
            sourceOwner = PartnerDataSourceType.PromoterCms,
            sourceField = "show.id",
            appField = "ConcertEvent.id",
            requirement = FieldRequirement.Required,
            blockingWhenMissing = true,
            semanticRule = "공연별 고유 ID입니다. 앱 내부 이벤트 ID와 1:1로 매핑되어야 합니다."
        ),
        PartnerFieldMapping(
            sourceOwner = PartnerDataSourceType.PromoterCms,
            sourceField = "show.approved_version",
            appField = "ConcertPackage.version",
            requirement = FieldRequirement.Required,
            blockingWhenMissing = true,
            semanticRule = "주최사가 승인한 버전만 배포할 수 있습니다."
        ),
        PartnerFieldMapping(
            sourceOwner = PartnerDataSourceType.PromoterCms,
            sourceField = "setlist.tracks",
            appField = "ConcertEvent.tracks",
            requirement = FieldRequirement.Required,
            blockingWhenMissing = true,
            semanticRule = "곡 순서와 곡 수가 리허설 승인본과 일치해야 합니다."
        ),
        PartnerFieldMapping(
            sourceOwner = PartnerDataSourceType.PromoterCms,
            sourceField = "setlist.time_base",
            appField = "ConcertCue.atSecond",
            requirement = FieldRequirement.Required,
            blockingWhenMissing = true,
            semanticRule = "큐 시간 기준이 공연 시작 후 초 단위인지, 영상 타임코드인지 명확해야 합니다."
        ),
        PartnerFieldMapping(
            sourceOwner = PartnerDataSourceType.PromoterCms,
            sourceField = "ar_cues",
            appField = "ConcertCue",
            requirement = FieldRequirement.Required,
            blockingWhenMissing = true,
            semanticRule = "HUD 문구, 위치, 효과, 표시 시간이 앱 스키마로 변환 가능해야 합니다."
        ),
        PartnerFieldMapping(
            sourceOwner = PartnerDataSourceType.VenueOperations,
            sourceField = "venue.map_version",
            appField = "VenueInfo.version",
            requirement = FieldRequirement.Required,
            blockingWhenMissing = true,
            semanticRule = "동선 안내는 공연장 운영자가 승인한 지도 버전과 일치해야 합니다."
        ),
        PartnerFieldMapping(
            sourceOwner = PartnerDataSourceType.VenueOperations,
            sourceField = "venue.safety_notices",
            appField = "OperationsChecklistItem",
            requirement = FieldRequirement.Optional,
            blockingWhenMissing = false,
            semanticRule = "긴급 공지는 별도 push 경로가 없으면 앱 패키지에 포함된 안내만 표시합니다."
        ),
        PartnerFieldMapping(
            sourceOwner = PartnerDataSourceType.ArtistManagement,
            sourceField = "artist.approved_messages",
            appField = "ConcertCue.hudMessageKo",
            requirement = FieldRequirement.Required,
            blockingWhenMissing = true,
            semanticRule = "자동 생성 문구가 아니라 아티스트 측 승인 문구만 production에 포함합니다."
        ),
        PartnerFieldMapping(
            sourceOwner = PartnerDataSourceType.ArtistManagement,
            sourceField = "rights.capture_policy",
            appField = "PartnerAsset.capturePolicy",
            requirement = FieldRequirement.Required,
            blockingWhenMissing = true,
            semanticRule = "촬영 가능/불가 구간은 공연장 정책과 아티스트 권리 정책을 동시에 만족해야 합니다."
        ),
        PartnerFieldMapping(
            sourceOwner = PartnerDataSourceType.TicketingSystem,
            sourceField = "ticket.provider_ticket_id",
            appField = "ConcertTicket.ticketId",
            requirement = FieldRequirement.Required,
            blockingWhenMissing = true,
            semanticRule = "앱은 티켓 원본을 소유하지 않고 provider id와 검증 결과만 저장합니다."
        ),
        PartnerFieldMapping(
            sourceOwner = PartnerDataSourceType.TicketingSystem,
            sourceField = "ticket.check_in_status",
            appField = "ConcertTicket.checkedIn",
            requirement = FieldRequirement.Required,
            blockingWhenMissing = true,
            semanticRule = "입장 권한은 티켓/입장 시스템 결과를 source of truth로 사용합니다."
        )
    )

fun validatePartnerImport(
    sourceName: String,
    sourceVersion: String,
    approvedVersion: String?,
    providedFields: Set<String>,
    mappings: List<PartnerFieldMapping> = defaultPartnerFieldMappings()
): PartnerImportValidationReport {
    val issues = buildList {
        if (sourceVersion.isBlank()) {
            add(
                PartnerImportIssue(
                    sourceField = "source.version",
                    severity = ImportIssueSeverity.Blocking,
                    message = "원본 데이터 버전이 없어 앱 배포 패키지를 고정할 수 없습니다."
                )
            )
        }
        if (approvedVersion.isNullOrBlank()) {
            add(
                PartnerImportIssue(
                    sourceField = "show.approved_version",
                    severity = ImportIssueSeverity.Blocking,
                    message = "주최사 승인 버전이 없어 production 배포가 불가능합니다."
                )
            )
        } else if (sourceVersion.isNotBlank() && sourceVersion != approvedVersion) {
            add(
                PartnerImportIssue(
                    sourceField = "show.approved_version",
                    severity = ImportIssueSeverity.Blocking,
                    message = "원본 버전 $sourceVersion 과 승인 버전 $approvedVersion 이 일치하지 않습니다."
                )
            )
        }

        mappings.forEach { mapping ->
            if (mapping.sourceField !in providedFields) {
                val severity = if (mapping.blockingWhenMissing) {
                    ImportIssueSeverity.Blocking
                } else {
                    ImportIssueSeverity.Warning
                }
                add(
                    PartnerImportIssue(
                        sourceField = mapping.sourceField,
                        severity = severity,
                        message = "${mapping.sourceField} 필드가 없어 ${mapping.appField} 매핑을 완료할 수 없습니다."
                    )
                )
            }
        }
    }

    return PartnerImportValidationReport(
        sourceName = sourceName,
        sourceVersion = sourceVersion,
        approvedVersion = approvedVersion,
        mappings = mappings,
        issues = issues
    )
}

fun samplePartnerImportValidationReport(): PartnerImportValidationReport =
    validatePartnerImport(
        sourceName = "Local sample package",
        sourceVersion = "rehearsal-v1",
        approvedVersion = "rehearsal-v1",
        providedFields = setOf(
            "show.id",
            "show.approved_version",
            "setlist.tracks",
            "setlist.time_base",
            "ar_cues",
            "venue.map_version",
            "artist.approved_messages",
            "rights.capture_policy",
            "ticket.provider_ticket_id",
            "ticket.check_in_status"
        )
    )
