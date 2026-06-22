package com.k3i.lumencue

enum class GlassesRendererAvailability {
    Ready,
    WaitingForOfficialSdk,
    Unsupported
}

enum class TargetGlassesDevice(
    val displayName: String,
    val hasDisplay: Boolean,
    val integrationPath: String
) {
    RayBanDisplay(
        displayName = "Meta Ray-Ban Display",
        hasDisplay = true,
        integrationPath = "Meta Wearables Device Access Toolkit"
    )
}

data class GlassesRendererStatus(
    val availability: GlassesRendererAvailability,
    val message: String
)

data class GlassesIntegrationProfile(
    val targetDevice: TargetGlassesDevice,
    val renderers: List<GlassesHudRenderer>
) {
    val primaryRenderer: GlassesHudRenderer
        get() = mockDeviceRenderer
            ?.takeIf { it.status.availability == GlassesRendererAvailability.Ready }
            ?: renderers.first { it is MetaWearablesToolkitHudRenderer }

    val mockDeviceRenderer: GlassesHudRenderer?
        get() = renderers.firstOrNull { it is MetaWearablesMockDeviceHudRenderer }

    val fallbackRenderer: GlassesHudRenderer
        get() = renderers.first { it is SmartphonePreviewHudRenderer }
}

interface GlassesHudRenderer {
    val status: GlassesRendererStatus

    fun render(instruction: HudRenderInstruction): GlassesRenderResult
}

data class GlassesRenderResult(
    val accepted: Boolean,
    val rendererName: String,
    val status: GlassesRendererStatus,
    val instruction: HudRenderInstruction?
)

enum class GlassesDispatchRoute(val label: String) {
    PrimaryToolkit("DAT Toolkit"),
    MockDevice("DAT MockDevice"),
    FallbackPreview("스마트폰 미리보기")
}

enum class FallbackDeliveryChannel(val label: String) {
    PhoneHudPreview("스마트폰 HUD"),
    AndroidNotification("Android 알림"),
    AudioCue("오디오/TTS")
}

data class FallbackDeliveryStep(
    val channel: FallbackDeliveryChannel,
    val title: String,
    val message: String,
    val required: Boolean
)

data class FallbackDeliveryPlan(
    val active: Boolean,
    val reason: String,
    val steps: List<FallbackDeliveryStep>
)

data class GlassesDispatchRecord(
    val sequence: Int,
    val route: GlassesDispatchRoute,
    val accepted: Boolean,
    val rendererName: String,
    val availability: GlassesRendererAvailability,
    val documentId: String,
    val textKo: String,
    val priority: HudPriority
)

data class GlassesDispatchReport(
    val primaryResult: GlassesRenderResult,
    val fallbackResult: GlassesRenderResult?,
    val selectedResult: GlassesRenderResult,
    val route: GlassesDispatchRoute,
    val document: ToolkitDisplayDocument,
    val fallbackPlan: FallbackDeliveryPlan,
    val records: List<GlassesDispatchRecord>
)

enum class ToolkitDisplayElementType {
    Text,
    Image,
    List,
    Button,
    Video
}

data class ToolkitDisplayElement(
    val id: String,
    val type: ToolkitDisplayElementType,
    val value: String,
    val metadata: Map<String, String> = emptyMap()
)

data class ToolkitDisplayDocument(
    val documentId: String,
    val targetDevice: TargetGlassesDevice,
    val durationMillis: Int,
    val priority: HudPriority,
    val elements: List<ToolkitDisplayElement>
)

fun HudRenderInstruction.toToolkitDisplayDocument(
    targetDevice: TargetGlassesDevice = TargetGlassesDevice.RayBanDisplay
): ToolkitDisplayDocument =
    ToolkitDisplayDocument(
        documentId = visualScene.sceneId,
        targetDevice = targetDevice,
        durationMillis = durationMillis,
        priority = priority,
        elements = listOf(
            ToolkitDisplayElement(
                id = "visual-scene",
                type = ToolkitDisplayElementType.List,
                value = visualScene.layout.name,
                metadata = mapOf(
                    "arObjectKey" to visualScene.arObjectKey.name,
                    "arObjectLabel" to visualScene.arObjectKey.label,
                    "animation" to visualScene.animation.name,
                    "icon" to visualScene.icon,
                    "colorToken" to visualScene.colorToken,
                    "progressPercent" to visualScene.progressPercent.toString(),
                    "safeAreaHint" to visualScene.safeAreaHint
                )
            ),
            ToolkitDisplayElement(
                id = "primary-text-ko",
                type = ToolkitDisplayElementType.Text,
                value = visualScene.primaryText,
                metadata = mapOf(
                    "placement" to placement.name,
                    "effect" to effect.name,
                    "language" to "ko"
                )
            ),
            ToolkitDisplayElement(
                id = "secondary-text-en",
                type = ToolkitDisplayElementType.Text,
                value = textEn,
                metadata = mapOf(
                    "placement" to placement.name,
                    "effect" to effect.name,
                    "language" to "en"
                )
            ),
            ToolkitDisplayElement(
                id = "assistive-summary",
                type = ToolkitDisplayElementType.Text,
                value = visualScene.accessibleSummary,
                metadata = mapOf("accessibility" to "true")
            )
        )
    )

class SmartphonePreviewHudRenderer : GlassesHudRenderer {
    override val status: GlassesRendererStatus = GlassesRendererStatus(
        availability = GlassesRendererAvailability.Ready,
        message = "스마트폰 HUD 미리보기 사용 가능"
    )

    override fun render(instruction: HudRenderInstruction): GlassesRenderResult =
        GlassesRenderResult(
            accepted = true,
            rendererName = "SmartphonePreviewHudRenderer",
            status = status,
            instruction = instruction
        )
}

class MetaWearablesToolkitHudRenderer : GlassesHudRenderer {
    override val status: GlassesRendererStatus = GlassesRendererStatus(
        availability = GlassesRendererAvailability.WaitingForOfficialSdk,
        message = "Meta Wearables Device Access Toolkit SDK 접근 및 승인 대기"
    )

    override fun render(instruction: HudRenderInstruction): GlassesRenderResult =
        GlassesRenderResult(
            accepted = false,
            rendererName = "MetaWearablesToolkitHudRenderer",
            status = status,
            instruction = instruction
        )

    fun prepareDisplayDocument(instruction: HudRenderInstruction): ToolkitDisplayDocument =
        instruction.toToolkitDisplayDocument(TargetGlassesDevice.RayBanDisplay)
}

class MetaWearablesMockDeviceHudRenderer(
    private val sdkArtifactDeclared: Boolean = true,
    private val packageTokenConfigured: Boolean = true
) : GlassesHudRenderer {
    override val status: GlassesRendererStatus = GlassesRendererStatus(
        availability = if (sdkArtifactDeclared && packageTokenConfigured) {
            GlassesRendererAvailability.Ready
        } else {
            GlassesRendererAvailability.WaitingForOfficialSdk
        },
        message = when {
            sdkArtifactDeclared && packageTokenConfigured ->
                "DAT MockDeviceKit으로 에뮬레이터 payload 검증 준비 완료"

            sdkArtifactDeclared ->
                "mwdat-mockdevice artifact 선언 완료, GitHub Packages 토큰 설정 필요"

            else ->
                "mwdat-mockdevice artifact 선언 필요"
        }
    )

    override fun render(instruction: HudRenderInstruction): GlassesRenderResult =
        GlassesRenderResult(
            accepted = status.availability == GlassesRendererAvailability.Ready,
            rendererName = "MetaWearablesMockDeviceHudRenderer",
            status = status,
            instruction = instruction
        )

    fun prepareDisplayDocument(instruction: HudRenderInstruction): ToolkitDisplayDocument =
        instruction.toToolkitDisplayDocument(TargetGlassesDevice.RayBanDisplay)
}

fun dispatchHudToGlasses(
    instruction: HudRenderInstruction,
    profile: GlassesIntegrationProfile,
    previousRecords: List<GlassesDispatchRecord> = emptyList(),
    maxRecords: Int = 6
): GlassesDispatchReport {
    val primaryResult = profile.primaryRenderer.render(instruction)
    val fallbackResult = if (primaryResult.accepted) {
        null
    } else {
        profile.fallbackRenderer.render(instruction)
    }
    val selectedResult = fallbackResult ?: primaryResult
    val route = if (primaryResult.accepted) {
        if (primaryResult.rendererName == "MetaWearablesMockDeviceHudRenderer") {
            GlassesDispatchRoute.MockDevice
        } else {
            GlassesDispatchRoute.PrimaryToolkit
        }
    } else {
        GlassesDispatchRoute.FallbackPreview
    }
    val document = instruction.toToolkitDisplayDocument(profile.targetDevice)
    val record = GlassesDispatchRecord(
        sequence = (previousRecords.maxOfOrNull { it.sequence } ?: 0) + 1,
        route = route,
        accepted = selectedResult.accepted,
        rendererName = selectedResult.rendererName,
        availability = selectedResult.status.availability,
        documentId = document.documentId,
        textKo = instruction.textKo,
        priority = instruction.priority
    )

    return GlassesDispatchReport(
        primaryResult = primaryResult,
        fallbackResult = fallbackResult,
        selectedResult = selectedResult,
        route = route,
        document = document,
        fallbackPlan = instruction.toFallbackDeliveryPlan(primaryResult),
        records = (previousRecords + record).takeLast(maxRecords)
    )
}

fun HudRenderInstruction.toFallbackDeliveryPlan(
    primaryResult: GlassesRenderResult? = null
): FallbackDeliveryPlan {
    val directGlassesAvailable = primaryResult?.accepted == true
    if (directGlassesAvailable) {
        return FallbackDeliveryPlan(
            active = false,
            reason = "Ray-Ban Display 직접 출력이 가능하므로 대체 경로를 사용하지 않습니다.",
            steps = emptyList()
        )
    }

    val steps = buildList {
        add(
            FallbackDeliveryStep(
                channel = FallbackDeliveryChannel.PhoneHudPreview,
                title = "스마트폰 HUD 유지",
                message = textKo,
                required = true
            )
        )
        if (priority == HudPriority.High) {
            add(
                FallbackDeliveryStep(
                    channel = FallbackDeliveryChannel.AndroidNotification,
                    title = "중요 공연 안내",
                    message = textKo,
                    required = true
                )
            )
            add(
                FallbackDeliveryStep(
                    channel = FallbackDeliveryChannel.AudioCue,
                    title = "짧은 오디오 안내",
                    message = assistiveText,
                    required = false
                )
            )
        }
    }

    return FallbackDeliveryPlan(
        active = true,
        reason = primaryResult?.status?.message ?: "글래스 직접 출력 가능 여부가 확인되지 않았습니다.",
        steps = steps
    )
}

fun defaultGlassesRenderers(): List<GlassesHudRenderer> =
    listOf(
        SmartphonePreviewHudRenderer(),
        MetaWearablesToolkitHudRenderer(),
        MetaWearablesMockDeviceHudRenderer()
    )

fun defaultGlassesIntegrationProfile(): GlassesIntegrationProfile =
    GlassesIntegrationProfile(
        targetDevice = TargetGlassesDevice.RayBanDisplay,
        renderers = defaultGlassesRenderers()
    )
