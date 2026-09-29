package com.k3i.lumencue

enum class BackendRuntimeState(val label: String) {
    LocalOnly("로컬 MVP"),
    Planned("제품화 필요"),
    Required("필수 연동"),
    Deferred("후순위")
}

enum class BackendCapabilityType {
    ConcertPackageApi,
    PartnerAdminPortal,
    TicketVerification,
    AudienceSessionSync,
    RecapStorage,
    DeviceIntegrationAudit,
    PushNotification
}

enum class PartnerDataSourceType {
    PromoterCms,
    TicketingSystem,
    VenueOperations,
    ArtistManagement,
    MetaWearablesPlatform
}

data class BackendCapabilityStatus(
    val type: BackendCapabilityType,
    val title: String,
    val state: BackendRuntimeState,
    val requiredForMvp: Boolean,
    val currentImplementation: String,
    val productRequirement: String
)

data class PartnerDataSourceContract(
    val type: PartnerDataSourceType,
    val sourceName: String,
    val sourceOfTruth: String,
    val syncMode: String,
    val requiredBeforeLaunch: Boolean,
    val mismatchRisk: String,
    val reconciliationRule: String
)

data class DatabaseTableSpec(
    val tableName: String,
    val owner: String,
    val purpose: String,
    val containsPersonalData: Boolean,
    val retentionPolicy: String
)

data class BackendProductArchitecture(
    val currentAppMode: String,
    val targetProductMode: String,
    val partnerDataSources: List<PartnerDataSourceContract>,
    val capabilities: List<BackendCapabilityStatus>,
    val databaseTables: List<DatabaseTableSpec>
) {
    val requiredCapabilityCount: Int
        get() = capabilities.count { it.requiredForMvp }

    val implementedCapabilityCount: Int
        get() = capabilities.count { it.state == BackendRuntimeState.LocalOnly }

    val personalDataTableCount: Int
        get() = databaseTables.count { it.containsPersonalData }

    val requiredPartnerContractCount: Int
        get() = partnerDataSources.count { it.requiredBeforeLaunch }
}

fun defaultBackendProductArchitecture(): BackendProductArchitecture =
    BackendProductArchitecture(
        currentAppMode = "Android 앱은 기본적으로 로컬 JSON/SharedPreferences로 동작하며, 설정 시 로컬 백엔드의 배포 패키지를 안전한 fallback과 함께 읽습니다.",
        targetProductMode = "실제 제품은 주최사 원본 데이터와 일치하는 API 서버, 원격 DB, 티켓/입장 연동, 운영 감사 로그가 필요합니다.",
        partnerDataSources = listOf(
            PartnerDataSourceContract(
                type = PartnerDataSourceType.PromoterCms,
                sourceName = "주최사/기획사 CMS",
                sourceOfTruth = "공연명, 일정, 셋리스트, AR 큐, 촬영 정책",
                syncMode = "공연 전 패키지 업로드 또는 API import",
                requiredBeforeLaunch = true,
                mismatchRisk = "곡 순서, 큐 시간, 촬영 가능 구간이 실제 공연 운영안과 달라질 수 있습니다.",
                reconciliationRule = "주최사 승인 버전 번호를 기준으로 앱 배포 패키지를 고정하고, 리허설 변경분은 새 버전으로만 반영합니다."
            ),
            PartnerDataSourceContract(
                type = PartnerDataSourceType.TicketingSystem,
                sourceName = "예매처/입장 인증 시스템",
                sourceOfTruth = "티켓 ID, 예매자 권한, 입장 확인 상태",
                syncMode = "HMAC 서명 체크인 webhook, 관리자 수동 import 폴백",
                requiredBeforeLaunch = true,
                mismatchRisk = "앱의 티켓 상태가 실제 입장 권한과 다르면 무단 접근 또는 정상 사용자 차단이 발생합니다.",
                reconciliationRule = "앱 DB는 티켓 원본을 소유하지 않고 provider event id로 재시도를 중복 제거하며 ticket id와 검증 결과만 30일간 최소 저장합니다."
            ),
            PartnerDataSourceContract(
                type = PartnerDataSourceType.VenueOperations,
                sourceName = "공연장 운영 시스템",
                sourceOfTruth = "게이트, 좌석 구역, 출구, MD 부스, 안전 공지",
                syncMode = "공연 전 운영 파일 import, 긴급 공지는 운영자 push",
                requiredBeforeLaunch = true,
                mismatchRisk = "동선 안내가 현장 운영과 다르면 관객 혼잡과 안전 문제가 생길 수 있습니다.",
                reconciliationRule = "공연장 운영자가 최종 승인한 venue map version을 저장하고 앱에 표시합니다."
            ),
            PartnerDataSourceContract(
                type = PartnerDataSourceType.ArtistManagement,
                sourceName = "아티스트 매니지먼트",
                sourceOfTruth = "멘트 번역, 응원 문구, 저작권/초상권 제한",
                syncMode = "공연 전 검수 문구 import",
                requiredBeforeLaunch = true,
                mismatchRisk = "미승인 번역/문구가 노출되면 브랜드, 저작권, 공연 연출 문제가 발생합니다.",
                reconciliationRule = "자동 생성 문구는 기본값으로 쓰지 않고 승인된 문구만 production package에 포함합니다."
            ),
            PartnerDataSourceContract(
                type = PartnerDataSourceType.MetaWearablesPlatform,
                sourceName = "Meta Wearables DAT/Developer Preview",
                sourceOfTruth = "글래스 출력 가능 범위, 입력 이벤트, 배포 정책",
                syncMode = "SDK/정책 버전 확인",
                requiredBeforeLaunch = false,
                mismatchRisk = "플랫폼 정책과 다르게 설계하면 Ray-Ban Display 기능이 배포 단계에서 막힐 수 있습니다.",
                reconciliationRule = "중립 `HudRenderInstruction`을 유지하고, 실제 DAT adapter는 승인된 API 범위 안에서만 구현합니다."
            )
        ),
        capabilities = listOf(
            BackendCapabilityStatus(
                type = BackendCapabilityType.ConcertPackageApi,
                title = "공연 패키지 API",
                state = BackendRuntimeState.LocalOnly,
                requiredForMvp = true,
                currentImplementation = "SQLite 기반 draft/검수/승인/기준 버전 충돌 검사/배포 API와 Android 원격 조회/fallback 경로를 구현했습니다.",
                productRequirement = "주최사가 검수 완료한 공연 패키지를 API로 배포하고 버전/상태를 관리해야 합니다."
            ),
            BackendCapabilityStatus(
                type = BackendCapabilityType.PartnerAdminPortal,
                title = "주최사 백오피스",
                state = BackendRuntimeState.LocalOnly,
                requiredForMvp = true,
                currentImplementation = "로컬 백엔드 /admin에서 패키지 등록·승인·배포, 긴급 공지, 티켓 체크인, HUD 감사 로그를 관리합니다.",
                productRequirement = "기획사/공연장 운영자가 셋리스트, 큐, 촬영 정책, 동선을 업로드하고 검수해야 합니다."
            ),
            BackendCapabilityStatus(
                type = BackendCapabilityType.TicketVerification,
                title = "티켓/입장 인증",
                state = BackendRuntimeState.LocalOnly,
                requiredForMvp = true,
                currentImplementation = "서명된 provider webhook, 수동 import 폴백, 체크인 검증, 12시간 audience token 발급을 구현했습니다.",
                productRequirement = "예매처 또는 입장 시스템과 연동해 실제 관객의 공연 접근 권한을 확인해야 합니다."
            ),
            BackendCapabilityStatus(
                type = BackendCapabilityType.AudienceSessionSync,
                title = "관객 세션 동기화",
                state = BackendRuntimeState.Deferred,
                requiredForMvp = false,
                currentImplementation = "개별 기기에서 세션을 로컬 저장합니다.",
                productRequirement = "여러 기기 간 반응 집계, 공연 중 공지, 장애 복구가 필요하면 서버 동기화가 필요합니다."
            ),
            BackendCapabilityStatus(
                type = BackendCapabilityType.RecapStorage,
                title = "리캡 저장",
                state = BackendRuntimeState.Deferred,
                requiredForMvp = false,
                currentImplementation = "공연 후 리캡은 앱 세션 상태에서 계산합니다.",
                productRequirement = "사용자 계정 기반 리캡 보관, 공유, 재방문 기능에는 원격 저장소가 필요합니다."
            ),
            BackendCapabilityStatus(
                type = BackendCapabilityType.DeviceIntegrationAudit,
                title = "글래스 연동 감사 로그",
                state = BackendRuntimeState.LocalOnly,
                requiredForMvp = true,
                currentImplementation = "앱 로컬 기록을 티켓 audience token으로 서버 감사 로그 API에 자동 동기화하며 재시도 중복을 제거합니다.",
                productRequirement = "DAT/글래스 출력 성공, fallback, 오류를 운영자가 추적할 수 있게 서버 로그가 필요합니다."
            ),
            BackendCapabilityStatus(
                type = BackendCapabilityType.PushNotification,
                title = "긴급 공지/푸시",
                state = BackendRuntimeState.Deferred,
                requiredForMvp = false,
                currentImplementation = "우선순위 기반 긴급 공지 API와 Android HUD override를 구현했습니다. 실시간 push 전달은 후속 작업입니다.",
                productRequirement = "입장 지연, 안전 공지, 글래스 출력 제한 시 Android 푸시/TTS 대체 경로가 필요합니다."
            )
        ),
        databaseTables = listOf(
            DatabaseTableSpec(
                tableName = "concert_events",
                owner = "공연 운영",
                purpose = "공연 기본 정보, venue, 일정, 상태",
                containsPersonalData = false,
                retentionPolicy = "공연 종료 후 운영 계약 기간에 맞춰 보관"
            ),
            DatabaseTableSpec(
                tableName = "concert_packages",
                owner = "주최사/콘텐츠 운영",
                purpose = "셋리스트, AR 큐, 번역 문구, 촬영 정책, 동선",
                containsPersonalData = false,
                retentionPolicy = "버전 이력 포함 보관"
            ),
            DatabaseTableSpec(
                tableName = "tickets",
                owner = "티켓/입장 연동",
                purpose = "사용자 공연 접근 권한과 입장 확인 상태",
                containsPersonalData = true,
                retentionPolicy = "마지막 상태 갱신 후 30일, 관객 삭제 요청 시 즉시 삭제"
            ),
            DatabaseTableSpec(
                tableName = "audience_sessions",
                owner = "앱 서비스",
                purpose = "선택 공연, 진행 상태, 리캡 생성에 필요한 최소 세션",
                containsPersonalData = true,
                retentionPolicy = "사용 가능 12시간, 만료 후 30일, 관객 삭제 요청 시 즉시 삭제"
            ),
            DatabaseTableSpec(
                tableName = "device_dispatch_logs",
                owner = "글래스 연동 운영",
                purpose = "DAT 출력, fallback, renderer 오류, payload id 추적",
                containsPersonalData = false,
                retentionPolicy = "연결된 관객 세션 만료 후 30일"
            ),
            DatabaseTableSpec(
                tableName = "ticket_provider_events",
                owner = "티켓/입장 연동",
                purpose = "서명 webhook 재시도 중복 제거와 처리 감사",
                containsPersonalData = false,
                retentionPolicy = "수신 후 30일"
            ),
            DatabaseTableSpec(
                tableName = "data_deletion_receipts",
                owner = "개인정보 운영",
                purpose = "개인 식별자 없는 삭제 처리 증빙",
                containsPersonalData = false,
                retentionPolicy = "완료 후 365일"
            )
        )
    )
