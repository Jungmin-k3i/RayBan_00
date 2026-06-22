package com.k3i.lumencue

import android.content.Context

enum class ConcertRepositorySource(val label: String) {
    LocalAsset("로컬 asset"),
    RemoteApi("원격 API"),
    FallbackSample("예비 샘플")
}

data class ConcertRepositoryResult(
    val source: ConcertRepositorySource,
    val packageReport: ConcertPackageLoadReport,
    val importValidationReport: PartnerImportValidationReport? = null,
    val message: String
) {
    val events: List<ConcertEvent>
        get() = packageReport.events

    val isFallback: Boolean
        get() = source == ConcertRepositorySource.FallbackSample || packageReport.fallbackUsed
}

enum class RepositoryReadinessSeverity {
    Ready,
    Warning,
    Blocked
}

data class ConcertRepositoryReadiness(
    val severity: RepositoryReadinessSeverity,
    val message: String
)

interface ConcertRepository {
    fun loadConcertPackages(): ConcertRepositoryResult
}

class LocalAssetConcertRepository(
    context: Context,
    private val fallbackEvents: List<ConcertEvent> = sampleConcertEvents
) : ConcertRepository {
    private val appContext = context.applicationContext

    override fun loadConcertPackages(): ConcertRepositoryResult {
        val assetReport = loadConcertPackageReportFromAssets(appContext)
        return if (assetReport.events.isEmpty()) {
            val fallbackReport = fallbackConcertPackageLoadReport(fallbackEvents)
            ConcertRepositoryResult(
                source = ConcertRepositorySource.FallbackSample,
                packageReport = fallbackReport,
                importValidationReport = null,
                message = "로컬 공연 패키지를 사용할 수 없어 예비 샘플 데이터를 사용합니다."
            )
        } else {
            ConcertRepositoryResult(
                source = ConcertRepositorySource.LocalAsset,
                packageReport = assetReport,
                importValidationReport = samplePartnerImportValidationReport(),
                message = "앱에 포함된 로컬 공연 패키지를 로드했습니다."
            )
        }
    }
}

class PlannedRemoteConcertRepository : ConcertRepository {
    override fun loadConcertPackages(): ConcertRepositoryResult =
        ConcertRepositoryResult(
            source = ConcertRepositorySource.RemoteApi,
            packageReport = fallbackConcertPackageLoadReport(emptyList()),
            importValidationReport = validatePartnerImport(
                sourceName = "Remote API",
                sourceVersion = "",
                approvedVersion = null,
                providedFields = emptySet()
            ),
            message = "원격 API 저장소는 아직 구현되지 않았습니다. Repository 계약만 예약되어 있습니다."
        )
}

fun choosePublishableRepositoryResult(
    primary: ConcertRepositoryResult,
    fallbackEvents: List<ConcertEvent>
): ConcertRepositoryResult {
    val importReport = primary.importValidationReport
    return if (primary.events.isNotEmpty() && importReport?.canPublish != false) {
        primary
    } else {
        ConcertRepositoryResult(
            source = ConcertRepositorySource.FallbackSample,
            packageReport = fallbackConcertPackageLoadReport(fallbackEvents),
            importValidationReport = importReport,
            message = "주 데이터가 비어 있거나 import 검증을 통과하지 못해 예비 샘플 데이터를 사용합니다."
        )
    }
}

fun ConcertRepositoryResult.toReadinessStatus(): ConcertRepositoryReadiness {
    val importReport = importValidationReport
    return when {
        events.isEmpty() -> ConcertRepositoryReadiness(
            severity = RepositoryReadinessSeverity.Blocked,
            message = "공연 데이터가 없어 Companion을 시작할 수 없습니다."
        )

        importReport?.canPublish == false -> ConcertRepositoryReadiness(
            severity = RepositoryReadinessSeverity.Blocked,
            message = "파트너 import 검증에 실패했습니다. 차단 ${importReport.blockingIssueCount}개"
        )

        isFallback -> ConcertRepositoryReadiness(
            severity = RepositoryReadinessSeverity.Warning,
            message = "예비 샘플 또는 fallback 데이터를 사용 중입니다."
        )

        importReport == null -> ConcertRepositoryReadiness(
            severity = RepositoryReadinessSeverity.Warning,
            message = "파트너 import 검증 결과가 없습니다."
        )

        else -> ConcertRepositoryReadiness(
            severity = RepositoryReadinessSeverity.Ready,
            message = "저장소와 파트너 import 검증이 준비되었습니다."
        )
    }
}
