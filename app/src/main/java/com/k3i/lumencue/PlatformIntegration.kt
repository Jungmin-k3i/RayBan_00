package com.k3i.lumencue

enum class PlatformIntegrationChannelType {
    AndroidApp,
    WearablesDat,
    MetaAiApp,
    ExternalFallback
}

enum class PlatformIntegrationRole(val label: String) {
    ProductSurface("제품 UI"),
    DeviceAccessSdk("디바이스 접근 SDK"),
    PlatformCompanion("플랫폼 Companion 앱"),
    FallbackChannel("대체 채널")
}

data class PlatformIntegrationChannel(
    val type: PlatformIntegrationChannelType,
    val role: PlatformIntegrationRole,
    val label: String,
    val statusLabel: String,
    val affectsPhoneUi: Boolean,
    val affectsGlassesUi: Boolean,
    val description: String
)

fun defaultPlatformIntegrationChannels(): List<PlatformIntegrationChannel> =
    listOf(
        PlatformIntegrationChannel(
            type = PlatformIntegrationChannelType.AndroidApp,
            role = PlatformIntegrationRole.ProductSurface,
            label = "Android 앱 단독",
            statusLabel = "현재 제품 본체",
            affectsPhoneUi = true,
            affectsGlassesUi = false,
            description = "공연 선택, 티켓, 패키지 검증, AR Live, 게시판을 우리 앱 UI에서 처리합니다."
        ),
        PlatformIntegrationChannel(
            type = PlatformIntegrationChannelType.WearablesDat,
            role = PlatformIntegrationRole.DeviceAccessSdk,
            label = "Meta Wearables DAT",
            statusLabel = "공식 SDK 연결 대기",
            affectsPhoneUi = false,
            affectsGlassesUi = true,
            description = "Ray-Ban Display 같은 웨어러블 기기에 접근하기 위한 SDK 경로입니다."
        ),
        PlatformIntegrationChannel(
            type = PlatformIntegrationChannelType.MetaAiApp,
            role = PlatformIntegrationRole.PlatformCompanion,
            label = "Meta AI 앱",
            statusLabel = "정책/연동 범위 확인 필요",
            affectsPhoneUi = false,
            affectsGlassesUi = false,
            description = "AI 에이전트 기능이 아니라 Ray-Ban 생태계에서 기기 설정, 권한, 공식 앱 흐름과 충돌하지 않는지 확인할 별도 플랫폼 채널입니다."
        ),
        PlatformIntegrationChannel(
            type = PlatformIntegrationChannelType.ExternalFallback,
            role = PlatformIntegrationRole.FallbackChannel,
            label = "알림/오디오/TTS 대체",
            statusLabel = "정책 제한 시 유지",
            affectsPhoneUi = true,
            affectsGlassesUi = false,
            description = "DAT 직접 연동이 제한될 때 스마트폰 화면, 알림, 오디오 안내로 핵심 경험을 유지합니다."
        )
    )
