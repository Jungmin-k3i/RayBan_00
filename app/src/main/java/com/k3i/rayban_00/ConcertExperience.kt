package com.k3i.rayban_00

import kotlin.math.roundToInt

enum class ReactionSignal(val label: String, val energyBoost: Int) {
    Heart("하트", 6),
    Clap("박수", 8),
    Cheer("환호", 12),
    SingAlong("떼창", 10)
}

enum class AppScreen {
    Home,
    Readiness,
    Detail,
    Translation,
    Companion,
    Board,
    BoardEventPosts,
    BoardPostDetail,
    Settings,
    SettingsConcert,
    SettingsLanguage,
    SettingsAppearance,
    SettingsOperations,
    SettingsTechnical
}

enum class AppLanguage(
    val code: String,
    val nativeLabel: String,
    val englishLabel: String
) {
    Korean("ko", "한국어", "Korean"),
    English("en", "English", "English")
}

enum class AppThemeMode(
    val koreanLabel: String,
    val englishLabel: String
) {
    System("시스템 설정", "System"),
    Light("라이트", "Light"),
    Dark("다크", "Dark")
}

enum class PartnerAssetStatus(val label: String) {
    Confirmed("확정"),
    NeedsReview("검수 필요"),
    Scheduled("예약됨")
}

enum class HudPlacement(val label: String) {
    LowerEdge("하단"),
    RightCorner("우측"),
    PeripheralPulse("가장자리")
}

enum class HudEffect(val label: String) {
    Caption("자막"),
    Countdown("카운트다운"),
    EdgePulse("가장자리 파동"),
    EnergyMeter("에너지 미터")
}

enum class HudPriority(val label: String) {
    Low("낮음"),
    Normal("보통"),
    High("높음")
}

enum class HudVisualLayout(val label: String) {
    CompactBanner("짧은 배너"),
    CountdownFocus("카운트다운"),
    EdgeSignal("가장자리 신호"),
    EnergyGauge("에너지 게이지")
}

enum class HudVisualAnimation(val label: String) {
    Static("고정"),
    Pulse("점멸"),
    Wave("파동"),
    Countdown("카운트다운"),
    Fill("채우기")
}

enum class HudArObjectKey(
    val label: String,
    val symbol: String,
    val purpose: String
) {
    CaptionLine(
        label = "자막 라인",
        symbol = "TXT",
        purpose = "현재 가사, 멘트 번역, 짧은 안내처럼 모든 공연에 공통으로 쓰는 텍스트 HUD"
    ),
    CountdownRing(
        label = "카운트다운 링",
        symbol = "10",
        purpose = "포토 타임, 떼창 시작, 이벤트 시작 전 남은 시간을 보여주는 공통 HUD"
    ),
    ParticipationWave(
        label = "참여 웨이브",
        symbol = "WAVE",
        purpose = "박수, 떼창, 응원봉 웨이브처럼 관객 참여 타이밍을 알려주는 공통 HUD"
    ),
    EnergyGauge(
        label = "에너지 게이지",
        symbol = "%",
        purpose = "앵콜, 환호, 현장 반응처럼 수치화 가능한 분위기를 보여주는 공통 HUD"
    )
}

enum class TranslationInputSource(
    val label: String,
    val reliabilityLabel: String,
    val note: String,
    val estimatedLatencyMillis: Int
) {
    OfficialAudioFeed(
        label = "공식 오디오 피드",
        reliabilityLabel = "가장 안정적",
        note = "주최사/운영사가 제공하는 믹스 또는 통역용 오디오를 우선 사용합니다.",
        estimatedLatencyMillis = 1800
    ),
    PhoneMicExperimental(
        label = "휴대폰 마이크 실험",
        reliabilityLabel = "소음 영향 큼",
        note = "공식 피드가 없고 사용자가 직접 시작한 경우에만 쓰는 대체 경로입니다.",
        estimatedLatencyMillis = 3400
    ),
    PreparedSubtitleFeed(
        label = "사전 자막 피드",
        reliabilityLabel = "지연 낮음",
        note = "사전에 받은 멘트/곡간 안내가 있을 때 시간 코드에 맞춰 표시합니다.",
        estimatedLatencyMillis = 700
    )
}

enum class TranslationPipelineStage(val label: String) {
    Listening("입력 대기"),
    SpeechToText("음성 인식"),
    Translating("번역"),
    HudSummary("HUD 요약"),
    Ready("표시 준비")
}

data class TranslationInput(
    val source: TranslationInputSource,
    val sourceLanguage: String,
    val targetLanguage: String,
    val originalText: String,
    val preparedTranslationText: String,
    val micLevel: Int
)

data class TranslationResult(
    val source: TranslationInputSource,
    val stage: TranslationPipelineStage,
    val translatedText: String,
    val hudSummary: String,
    val estimatedLatencyMillis: Int,
    val confidencePercent: Int,
    val policyNote: String,
    val engineName: String,
    val fallbackReason: String? = null
)

interface LiveTranslationEngine {
    val name: String
    val supportedSources: Set<TranslationInputSource>

    fun translate(input: TranslationInput): TranslationResult
}

data class LiveTranslationState(
    val source: TranslationInputSource,
    val stage: TranslationPipelineStage,
    val sourceLanguage: String,
    val targetLanguage: String,
    val originalText: String,
    val translatedText: String,
    val hudSummary: String,
    val estimatedLatencyMillis: Int,
    val confidencePercent: Int,
    val policyNote: String,
    val engineName: String,
    val fallbackReason: String? = null
)

enum class TranslationProviderRole(val label: String) {
    Primary("기본"),
    Fallback("대체"),
    Experimental("실험"),
    NotSupported("직접 연동 제외")
}

data class TranslationProviderOption(
    val role: TranslationProviderRole,
    val name: String,
    val executionPath: String,
    val connectivityOwner: String,
    val reason: String,
    val selectedForMvp: Boolean
)

val defaultTranslationProviderOptions = listOf(
    TranslationProviderOption(
        role = TranslationProviderRole.Primary,
        name = "Cloud STT/Translation",
        executionPath = "Android 앱 -> 클라우드 STT/번역 -> Android 앱 -> DAT HUD",
        connectivityOwner = "Android 스마트폰 앱",
        reason = "공식 오디오 피드 기반 실시간 번역 품질, 운영 로그, fallback 제어가 가장 안정적입니다.",
        selectedForMvp = true
    ),
    TranslationProviderOption(
        role = TranslationProviderRole.Fallback,
        name = "Prepared Subtitle Feed",
        executionPath = "공연 패키지 시간 코드 -> Android 앱 -> DAT HUD",
        connectivityOwner = "Android 스마트폰 앱",
        reason = "클라우드 지연/장애 또는 승인된 멘트가 있는 구간에서 저지연으로 표시합니다.",
        selectedForMvp = true
    ),
    TranslationProviderOption(
        role = TranslationProviderRole.Experimental,
        name = "On-device / Phone Mic",
        executionPath = "사용자 시작 마이크 -> 앱 내 처리 후보 -> Android 앱 HUD",
        connectivityOwner = "Android 스마트폰 앱",
        reason = "공식 피드가 없을 때만 검토하며 원본 음성 저장과 백그라운드 수집은 금지합니다.",
        selectedForMvp = false
    ),
    TranslationProviderOption(
        role = TranslationProviderRole.NotSupported,
        name = "Meta AI App internal translation",
        executionPath = "Meta AI 앱 내부 사용자 기능",
        connectivityOwner = "Meta AI 앱",
        reason = "사용자 기능은 존재할 수 있지만 우리 앱이 안정적으로 호출할 공개 provider API로 보지 않습니다.",
        selectedForMvp = false
    )
)

fun selectedTranslationProviderOptions(): List<TranslationProviderOption> =
    defaultTranslationProviderOptions.filter { it.selectedForMvp }

data class HudVisualScene(
    val sceneId: String,
    val arObjectKey: HudArObjectKey,
    val layout: HudVisualLayout,
    val animation: HudVisualAnimation,
    val icon: String,
    val colorToken: String,
    val primaryText: String,
    val secondaryText: String,
    val progressPercent: Int,
    val safeAreaHint: String,
    val durationMillis: Int,
    val priority: HudPriority,
    val accessibleSummary: String
)

enum class ConcertInteractionEventType(val label: String) {
    CallAndResponse("콜앤리스폰스"),
    FanChant("응원법"),
    LightstickWave("응원봉 웨이브"),
    Surprise("서프라이즈"),
    EncoreGauge("앵콜 게이지"),
    CameraPolicy("촬영 구간"),
    PhotoCountdown("포토 타임"),
    MerchBooth("MD/부스"),
    ExitFlow("퇴장 동선"),
    FanMission("팬 미션"),
    SetlistHint("셋리스트 힌트"),
    Translation("멘트 번역")
}

enum class GlassesInputSource(val label: String) {
    SmartphoneSimulator("스마트폰 시뮬레이터"),
    MetaNeuralBand("Meta Neural Band"),
    FrameTouchpad("글래스 터치패드"),
    VoiceCommand("음성 명령")
}

enum class GlassesInputIntent(
    val label: String,
    val gestureHint: String,
    val description: String
) {
    ConfirmHud("확인", "핀치/탭", "현재 HUD 안내를 확인한 것으로 기록"),
    DismissHud("닫기", "아래 스와이프", "현재 HUD 안내를 닫은 것으로 기록"),
    NextCue("다음 큐", "오른쪽 스와이프", "다음 AR 큐 시점으로 미리 이동"),
    PreviousCue("이전 큐", "왼쪽 스와이프", "직전 AR 큐 시점으로 이동"),
    SaveMoment("순간 저장", "길게 누름", "현재 곡과 AR 큐를 리캡 하이라이트에 저장"),
    SendHeart("하트", "짧은 핀치 2회", "하트 반응 전송"),
    SendCheer("환호", "주먹 쥐기", "환호 반응 전송")
}

data class GlassesInputAction(
    val intent: GlassesInputIntent,
    val source: GlassesInputSource = GlassesInputSource.SmartphoneSimulator
)

data class GlassesInteractionPolicy(
    val maxPrimaryActions: Int,
    val allowedIntents: List<GlassesInputIntent>,
    val blockedOnGlasses: List<String>
)

enum class ConsentNoticeAudience(val label: String) {
    Attendee("관객 고지"),
    VenueOperator("공연장 운영"),
    LegalReview("법무 검토")
}

data class ConsentNoticeClause(
    val audience: ConsentNoticeAudience,
    val title: String,
    val body: String,
    val required: Boolean
)

data class AudioPanelUiState(
    val actionLabel: String,
    val statusMessage: String,
    val warningMessage: String?
)

data class PartnerAsset(
    val name: String,
    val detail: String,
    val status: PartnerAssetStatus
)

data class OperationsChecklistItem(
    val title: String,
    val owner: String,
    val status: PartnerAssetStatus
)

data class ConcertCue(
    val atSecond: Int,
    val titleKo: String,
    val titleEn: String,
    val hudMessageKo: String,
    val hudMessageEn: String,
    val placement: HudPlacement,
    val effect: HudEffect,
    val durationMillis: Int = 3_000
)

data class ConcertTrack(
    val title: String,
    val artist: String,
    val durationSeconds: Int,
    val cues: List<ConcertCue>
)

data class ConcertInteractionEvent(
    val id: String,
    val type: ConcertInteractionEventType,
    val trackIndex: Int,
    val startSecond: Int,
    val endSecond: Int,
    val titleKo: String,
    val messageKo: String,
    val titleEn: String,
    val messageEn: String,
    val zone: String = "전체",
    val ctaLabel: String,
    val reactionSignal: ReactionSignal? = null,
    val requiresApproval: Boolean = true
)

data class PartnerBrief(
    val promoter: String,
    val venue: String,
    val showDate: String,
    val dataStatus: String
)

data class VenueInfo(
    val name: String,
    val gate: String,
    val seat: String,
    val nearestExit: String,
    val merchBooth: String
)

data class ConcertTicket(
    val ticketId: String,
    val holderName: String,
    val checkedIn: Boolean
)

data class ConcertEvent(
    val id: String,
    val title: String,
    val subtitle: String,
    val partnerBrief: PartnerBrief,
    val venueInfo: VenueInfo,
    val ticket: ConcertTicket,
    val tracks: List<ConcertTrack>,
    val partnerAssets: List<PartnerAsset>,
    val operationsChecklist: List<OperationsChecklistItem>,
    val interactionEvents: List<ConcertInteractionEvent> = emptyList()
)

enum class BoardAccessDecision(val label: String) {
    Allowed("입장 가능"),
    TicketVerificationRequired("인증 필요")
}

data class BoardAccessPolicy(
    val eventId: String,
    val decision: BoardAccessDecision,
    val reason: String
) {
    val canEnter: Boolean
        get() = decision == BoardAccessDecision.Allowed
}

fun ConcertEvent.boardAccessPolicy(): BoardAccessPolicy =
    if (ticket.checkedIn) {
        BoardAccessPolicy(
            eventId = id,
            decision = BoardAccessDecision.Allowed,
            reason = "티켓/입장 인증이 완료된 공연입니다."
        )
    } else {
        BoardAccessPolicy(
            eventId = id,
            decision = BoardAccessDecision.TicketVerificationRequired,
            reason = "티켓/입장 인증 후 이 공연 게시판을 이용할 수 있습니다."
        )
    }

data class ConcertState(
    val event: ConcertEvent = sampleConcertEvents.first(),
    val trackIndex: Int = 0,
    val elapsedSeconds: Int = 0,
    val fanEnergy: Int = 42,
    val lastReaction: ReactionSignal? = null,
    val audioEnergy: Int = 0,
    val micEnabled: Boolean = false,
    val totalReactions: Int = 0,
    val peakEnergy: Int = 42,
    val completedTracks: Int = 0,
    val trackReactionCounts: Map<String, Int> = emptyMap(),
    val interactionEventParticipationCounts: Map<String, Int> = emptyMap(),
    val lastInteractionEventId: String? = null,
    val recapHighlights: List<RecapHighlight> = emptyList()
) {
    val tracks: List<ConcertTrack>
        get() = event.tracks

    val partnerBrief: PartnerBrief
        get() = event.partnerBrief

    val currentTrack: ConcertTrack
        get() = tracks[trackIndex]

    val activeCue: ConcertCue
        get() = currentTrack.cues.lastOrNull { it.atSecond <= elapsedSeconds }
            ?: currentTrack.cues.first()

    val nextCue: ConcertCue?
        get() = currentTrack.cues.firstOrNull { it.atSecond > elapsedSeconds }

    val progress: Float
        get() = (elapsedSeconds.toFloat() / currentTrack.durationSeconds).coerceIn(0f, 1f)

    val activeInteractionEvents: List<ConcertInteractionEvent>
        get() = event.interactionEvents
            .filter { it.trackIndex == trackIndex && elapsedSeconds in it.startSecond..it.endSecond }
            .sortedBy { it.startSecond }

    val upcomingInteractionEvents: List<ConcertInteractionEvent>
        get() = event.interactionEvents
            .filter { it.trackIndex > trackIndex || (it.trackIndex == trackIndex && it.startSecond > elapsedSeconds) }
            .sortedWith(compareBy<ConcertInteractionEvent> { it.trackIndex }.thenBy { it.startSecond })

    val currentFeaturedInteractionEvent: ConcertInteractionEvent?
        get() = activeInteractionEvents.firstOrNull() ?: upcomingInteractionEvents.firstOrNull()

    val hudState: HudState
        get() = HudState(
            energyPercent = fanEnergy,
            primaryKo = activeCue.hudMessageKo,
            primaryEn = activeCue.hudMessageEn,
            secondary = nextCue?.let { "다음 AR 큐 ${formatTime(it.atSecond - elapsedSeconds)}" } ?: "곡 마무리",
            micLevel = audioEnergy,
            placement = activeCue.placement,
            effect = activeCue.effect,
            durationMillis = activeCue.durationMillis,
            priority = activeCue.priority,
            accessibleSummary = "${activeCue.titleKo}. ${activeCue.hudMessageKo}"
        )

    val liveTranslation: LiveTranslationState
        get() {
            val source = when {
                activeCue.effect == HudEffect.Caption -> TranslationInputSource.OfficialAudioFeed
                micEnabled -> TranslationInputSource.PhoneMicExperimental
                else -> TranslationInputSource.PreparedSubtitleFeed
            }
            val input = TranslationInput(
                source = source,
                sourceLanguage = "EN",
                targetLanguage = "KO",
                originalText = activeCue.hudMessageEn,
                preparedTranslationText = activeCue.hudMessageKo,
                micLevel = audioEnergy
            )
            val result = defaultLiveTranslationEngineChain.translate(input)
            return LiveTranslationState(
                source = result.source,
                stage = result.stage,
                sourceLanguage = input.sourceLanguage,
                targetLanguage = input.targetLanguage,
                originalText = input.originalText,
                translatedText = result.translatedText,
                hudSummary = result.hudSummary,
                estimatedLatencyMillis = result.estimatedLatencyMillis,
                confidencePercent = result.confidencePercent,
                policyNote = result.policyNote,
                engineName = result.engineName,
                fallbackReason = result.fallbackReason
            )
        }

    val recap: ConcertRecap
        get() = ConcertRecap(
            eventTitle = event.title,
            watchedTracks = completedTracks + 1,
            totalReactions = totalReactions,
            peakEnergy = peakEnergy,
            lastCue = activeCue.titleKo,
            trackReactionCounts = trackReactionCounts,
            interactionEventParticipationCounts = interactionEventParticipationCounts,
            highlights = recapHighlights.takeLast(6)
        )
}

private fun String.toHudSummary(maxChars: Int = 34): String =
    if (length <= maxChars) this else take(maxChars - 1).trimEnd() + "…"

private fun TranslationInputSource.translationConfidencePercent(): Int =
    when (this) {
        TranslationInputSource.OfficialAudioFeed -> 86
        TranslationInputSource.PhoneMicExperimental -> 62
        TranslationInputSource.PreparedSubtitleFeed -> 94
    }

private fun TranslationInputSource.translationPolicyNote(): String =
    when (this) {
        TranslationInputSource.OfficialAudioFeed -> "공식 피드 기반 처리는 원본 음성 저장 없이 실시간 변환만 수행합니다."
        TranslationInputSource.PhoneMicExperimental -> "사용자 시작, Android 마이크 권한, 공연장 녹음 정책 확인이 필요합니다."
        TranslationInputSource.PreparedSubtitleFeed -> "사전 제공 문구와 실제 발화가 다르면 스마트폰에서 불일치 안내가 필요합니다."
    }

private val defaultLiveTranslationEngineChain = LiveTranslationEngineChain(
    engines = listOf(
        OfficialFeedTranslationEngine(),
        PreparedSubtitleTranslationEngine(),
        PhoneMicExperimentalTranslationEngine()
    ),
    fallback = DisabledTranslationEngine()
)

class LiveTranslationEngineChain(
    private val engines: List<LiveTranslationEngine>,
    private val fallback: LiveTranslationEngine
) : LiveTranslationEngine {
    override val name: String = "LiveTranslationEngineChain"
    override val supportedSources: Set<TranslationInputSource> =
        engines.flatMap { it.supportedSources }.toSet()

    override fun translate(input: TranslationInput): TranslationResult {
        val engine = engines.firstOrNull { input.source in it.supportedSources }
        return if (engine != null) {
            engine.translate(input)
        } else {
            fallback.translate(input)
        }
    }
}

class OfficialFeedTranslationEngine : LiveTranslationEngine {
    override val name: String = "OfficialFeedTranslationEngine"
    override val supportedSources: Set<TranslationInputSource> = setOf(
        TranslationInputSource.OfficialAudioFeed
    )

    override fun translate(input: TranslationInput): TranslationResult =
        TranslationResult(
            source = input.source,
            stage = TranslationPipelineStage.HudSummary,
            translatedText = input.preparedTranslationText,
            hudSummary = input.preparedTranslationText.toHudSummary(),
            estimatedLatencyMillis = input.source.estimatedLatencyMillis,
            confidencePercent = input.source.translationConfidencePercent(),
            policyNote = input.source.translationPolicyNote(),
            engineName = name,
            fallbackReason = "공식 오디오 STT/번역 API 연결 전까지 승인된 큐/자막 피드로 HUD를 검증합니다."
        )
}

class PreparedSubtitleTranslationEngine : LiveTranslationEngine {
    override val name: String = "PreparedSubtitleTranslationEngine"
    override val supportedSources: Set<TranslationInputSource> = setOf(
        TranslationInputSource.PreparedSubtitleFeed
    )

    override fun translate(input: TranslationInput): TranslationResult =
        TranslationResult(
            source = input.source,
            stage = TranslationPipelineStage.HudSummary,
            translatedText = input.preparedTranslationText,
            hudSummary = input.preparedTranslationText.toHudSummary(),
            estimatedLatencyMillis = input.source.estimatedLatencyMillis,
            confidencePercent = input.source.translationConfidencePercent(),
            policyNote = input.source.translationPolicyNote(),
            engineName = name
        )
}

class PhoneMicExperimentalTranslationEngine : LiveTranslationEngine {
    override val name: String = "PhoneMicExperimentalTranslationEngine"
    override val supportedSources: Set<TranslationInputSource> = setOf(
        TranslationInputSource.PhoneMicExperimental
    )

    override fun translate(input: TranslationInput): TranslationResult {
        val confidencePenalty = if (input.micLevel > 80) 18 else 0
        val confidence = (TranslationInputSource.PhoneMicExperimental.translationConfidencePercent() - confidencePenalty)
            .coerceAtLeast(35)
        return TranslationResult(
            source = input.source,
            stage = TranslationPipelineStage.HudSummary,
            translatedText = input.preparedTranslationText,
            hudSummary = input.preparedTranslationText.toHudSummary(),
            estimatedLatencyMillis = TranslationInputSource.PhoneMicExperimental.estimatedLatencyMillis,
            confidencePercent = confidence,
            policyNote = TranslationInputSource.PhoneMicExperimental.translationPolicyNote(),
            engineName = name,
            fallbackReason = "실제 STT/번역 API 연결 전까지 현재 큐의 번역문으로 HUD 표시를 검증합니다."
        )
    }
}

class DisabledTranslationEngine : LiveTranslationEngine {
    override val name: String = "DisabledTranslationEngine"
    override val supportedSources: Set<TranslationInputSource> = emptySet()

    override fun translate(input: TranslationInput): TranslationResult =
        TranslationResult(
            source = input.source,
            stage = TranslationPipelineStage.Listening,
            translatedText = "",
            hudSummary = "번역을 사용할 수 없습니다.",
            estimatedLatencyMillis = 0,
            confidencePercent = 0,
            policyNote = "지원되는 번역 소스 또는 엔진이 없어 HUD 번역을 비활성화합니다.",
            engineName = name,
            fallbackReason = "No translation engine supports ${input.source.name}"
        )
}

data class HudState(
    val energyPercent: Int,
    val primaryKo: String,
    val primaryEn: String,
    val secondary: String,
    val micLevel: Int,
    val placement: HudPlacement,
    val effect: HudEffect,
    val durationMillis: Int,
    val priority: HudPriority,
    val accessibleSummary: String
)

data class HudRenderInstruction(
    val placement: HudPlacement,
    val effect: HudEffect,
    val priority: HudPriority,
    val durationMillis: Int,
    val textKo: String,
    val textEn: String,
    val assistiveText: String,
    val visualScene: HudVisualScene
)

data class ConcertRecap(
    val eventTitle: String,
    val watchedTracks: Int,
    val totalReactions: Int,
    val peakEnergy: Int,
    val lastCue: String,
    val trackReactionCounts: Map<String, Int>,
    val interactionEventParticipationCounts: Map<String, Int>,
    val highlights: List<RecapHighlight>
)

data class RecapHighlight(
    val timeLabel: String,
    val title: String,
    val detail: String
)

val ConcertCue.priority: HudPriority
    get() = when (effect) {
        HudEffect.Caption -> HudPriority.Normal
        HudEffect.Countdown -> HudPriority.High
        HudEffect.EdgePulse -> HudPriority.Low
        HudEffect.EnergyMeter -> HudPriority.High
    }

val defaultGlassesInteractionPolicy = GlassesInteractionPolicy(
    maxPrimaryActions = 5,
    allowedIntents = listOf(
        GlassesInputIntent.ConfirmHud,
        GlassesInputIntent.DismissHud,
        GlassesInputIntent.NextCue,
        GlassesInputIntent.SaveMoment,
        GlassesInputIntent.SendHeart
    ),
    blockedOnGlasses = listOf(
        "공연 선택",
        "티켓/입장 인증",
        "마이크 권한 요청",
        "촬영/녹음 시작",
        "공연 세션 종료"
    )
)

val defaultConcertConsentNotice = listOf(
    ConsentNoticeClause(
        audience = ConsentNoticeAudience.Attendee,
        title = "실시간 보조 정보 표시",
        body = "공연 중 스마트폰과 Ray-Ban Display에는 주최사가 제공한 셋리스트, 응원 타이밍, 촬영 정책, 공연장 안내가 짧게 표시될 수 있습니다.",
        required = true
    ),
    ConsentNoticeClause(
        audience = ConsentNoticeAudience.Attendee,
        title = "마이크 볼륨 분석",
        body = "실시간 번역 실험에서 마이크 입력을 사용하는 경우 원본 음성은 저장하지 않습니다. 기능은 사용자가 언제든 중지할 수 있습니다.",
        required = true
    ),
    ConsentNoticeClause(
        audience = ConsentNoticeAudience.VenueOperator,
        title = "촬영/녹음 정책 우선",
        body = "공연장과 주최사의 촬영, 녹음, 보안 정책이 앱 기능보다 우선합니다. 금지 구간에서는 앱이 촬영을 유도하지 않습니다.",
        required = true
    ),
    ConsentNoticeClause(
        audience = ConsentNoticeAudience.LegalReview,
        title = "카메라/자동 분석 기능 제한",
        body = "얼굴 인식, 관객 자동 촬영, 상시 녹음, 카메라 기반 자동 분석은 공식 정책과 별도 동의 체계가 확인되기 전까지 제공하지 않습니다.",
        required = true
    )
)

fun ConcertState.toAudioPanelUiState(
    micPermissionDenied: Boolean,
    appInForeground: Boolean
): AudioPanelUiState {
    val warning = when {
        micPermissionDenied ->
            "마이크 권한이 거부되었습니다. 실시간 번역 실험에서 마이크 입력을 사용하려면 Android 설정 또는 권한 요청에서 마이크를 허용해야 합니다."
        !appInForeground ->
            "앱이 백그라운드로 전환되어 Companion 타이머와 마이크 분석을 일시 중지했습니다."
        else -> null
    }
    return AudioPanelUiState(
        actionLabel = if (micEnabled) "중지" else "시작",
        statusMessage = if (micEnabled) {
            "마이크 입력 실험 중입니다. 원본 음성은 저장하지 않습니다."
        } else {
            "공식 오디오 피드가 없을 때만 쓰는 실험 경로입니다."
        },
        warningMessage = warning
    )
}

fun HudState.toRenderInstruction(): HudRenderInstruction =
    HudRenderInstruction(
        placement = placement,
        effect = effect,
        priority = priority,
        durationMillis = durationMillis,
        textKo = primaryKo,
        textEn = primaryEn,
        assistiveText = accessibleSummary,
        visualScene = toVisualScene()
    )

fun HudState.toVisualScene(): HudVisualScene =
    HudVisualScene(
        sceneId = "hud-${placement.name.lowercase()}-${effect.name.lowercase()}",
        arObjectKey = effect.toArObjectKey(),
        layout = effect.toVisualLayout(),
        animation = effect.toVisualAnimation(),
        icon = effect.toArObjectKey().symbol,
        colorToken = effect.toVisualColorToken(),
        primaryText = primaryKo,
        secondaryText = secondary,
        progressPercent = when (effect) {
            HudEffect.EnergyMeter -> energyPercent
            HudEffect.Countdown -> 100
            HudEffect.EdgePulse -> energyPercent.coerceIn(25, 100)
            HudEffect.Caption -> 0
        },
        safeAreaHint = placement.safeAreaHint(),
        durationMillis = durationMillis,
        priority = priority,
        accessibleSummary = accessibleSummary
    )

private fun HudEffect.toArObjectKey(): HudArObjectKey =
    when (this) {
        HudEffect.Caption -> HudArObjectKey.CaptionLine
        HudEffect.Countdown -> HudArObjectKey.CountdownRing
        HudEffect.EdgePulse -> HudArObjectKey.ParticipationWave
        HudEffect.EnergyMeter -> HudArObjectKey.EnergyGauge
    }

private fun HudEffect.toVisualLayout(): HudVisualLayout =
    when (this) {
        HudEffect.Caption -> HudVisualLayout.CompactBanner
        HudEffect.Countdown -> HudVisualLayout.CountdownFocus
        HudEffect.EdgePulse -> HudVisualLayout.EdgeSignal
        HudEffect.EnergyMeter -> HudVisualLayout.EnergyGauge
    }

private fun HudEffect.toVisualAnimation(): HudVisualAnimation =
    when (this) {
        HudEffect.Caption -> HudVisualAnimation.Static
        HudEffect.Countdown -> HudVisualAnimation.Countdown
        HudEffect.EdgePulse -> HudVisualAnimation.Wave
        HudEffect.EnergyMeter -> HudVisualAnimation.Fill
    }

private fun HudEffect.toVisualColorToken(): String =
    when (this) {
        HudEffect.Caption -> "cyan"
        HudEffect.Countdown -> "amber"
        HudEffect.EdgePulse -> "pink"
        HudEffect.EnergyMeter -> "lime"
    }

private fun HudPlacement.safeAreaHint(): String =
    when (this) {
        HudPlacement.LowerEdge -> "lower-third"
        HudPlacement.RightCorner -> "right-corner"
        HudPlacement.PeripheralPulse -> "peripheral-edge"
    }

val sampleConcertEvents = listOf(
    ConcertEvent(
        id = "lumen-2026-seoul",
        title = "LUMEN LIVE: GLASS HORIZON",
        subtitle = "Ray-Ban Display AR Companion 공식 연동 공연",
        partnerBrief = PartnerBrief(
            promoter = "LUMEN Live / 공연 주최사 제공",
            venue = "KSPO Dome",
            showDate = "2026.09.18",
            dataStatus = "셋리스트, 응원법, 번역 문구 사전 연동"
        ),
        venueInfo = VenueInfo(
            name = "KSPO Dome",
            gate = "2-1 게이트",
            seat = "Floor B구역 12열 08번",
            nearestExit = "북2문",
            merchBooth = "1층 동측 MD 부스"
        ),
        ticket = ConcertTicket(
            ticketId = "RB-AR-0918-1208",
            holderName = "관객",
            checkedIn = true
        ),
        tracks = listOf(
            ConcertTrack(
                title = "Neon Signal",
                artist = "LUMEN",
                durationSeconds = 150,
                cues = listOf(
                    ConcertCue(
                        atSecond = 0,
                        titleKo = "입장 조명",
                        titleEn = "Entrance light",
                        hudMessageKo = "시야 하단에 입장 색상 표시",
                        hudMessageEn = "Entrance color appears at the lower edge",
                        placement = HudPlacement.PeripheralPulse,
                        effect = HudEffect.EdgePulse
                    ),
                    ConcertCue(
                        atSecond = 28,
                        titleKo = "응원 콜",
                        titleEn = "Fan chant",
                        hudMessageKo = "LUMEN 콜 3초 전",
                        hudMessageEn = "LUMEN chant in 3 seconds",
                        placement = HudPlacement.RightCorner,
                        effect = HudEffect.Countdown
                    ),
                    ConcertCue(
                        atSecond = 72,
                        titleKo = "떼창 가사",
                        titleEn = "Sing-along lyric",
                        hudMessageKo = "후렴 첫 줄만 짧게 표시",
                        hudMessageEn = "Show only the first chorus line",
                        placement = HudPlacement.LowerEdge,
                        effect = HudEffect.Caption,
                        durationMillis = 4_000
                    ),
                    ConcertCue(
                        atSecond = 118,
                        titleKo = "앵콜 유도",
                        titleEn = "Encore cue",
                        hudMessageKo = "환호 에너지를 모아 앵콜 게이지 상승",
                        hudMessageEn = "Cheer to raise the encore meter",
                        placement = HudPlacement.RightCorner,
                        effect = HudEffect.EnergyMeter
                    )
                )
            ),
            ConcertTrack(
                title = "Glass Horizon",
                artist = "LUMEN",
                durationSeconds = 132,
                cues = listOf(
                    ConcertCue(
                        0,
                        "멘트 번역",
                        "Talk translation",
                        "아티스트 멘트 요약 표시",
                        "Artist talk summary",
                        HudPlacement.LowerEdge,
                        HudEffect.Caption,
                        4_000
                    ),
                    ConcertCue(
                        35,
                        "박수 타이밍",
                        "Clap timing",
                        "비트에 맞춘 가장자리 파동",
                        "Edge pulse on the beat",
                        HudPlacement.PeripheralPulse,
                        HudEffect.EdgePulse
                    ),
                    ConcertCue(
                        84,
                        "하트 신호",
                        "Heart signal",
                        "손목 제스처로 하트 반응",
                        "Send hearts with wrist gesture",
                        HudPlacement.RightCorner,
                        HudEffect.EnergyMeter
                    )
                )
            )
        ),
        partnerAssets = listOf(
            PartnerAsset("셋리스트 타임라인", "곡별 시작 시간과 큐 포인트", PartnerAssetStatus.Confirmed),
            PartnerAsset("응원법 패키지", "콜앤리스폰스, 떼창 구간, 박수 타이밍, 응원봉 웨이브", PartnerAssetStatus.Confirmed),
            PartnerAsset("멘트 번역", "한국어/영어 요약 문구", PartnerAssetStatus.NeedsReview),
            PartnerAsset("참여 이벤트 패키지", "포토 타임, 팬 미션, 앵콜 게이지, 퇴장 동선", PartnerAssetStatus.Confirmed),
            PartnerAsset("공연장 동선", "게이트, 출구, MD 부스 안내", PartnerAssetStatus.Confirmed),
            PartnerAsset("리캡 템플릿", "공연 후 개인 참여 카드", PartnerAssetStatus.Scheduled)
        ),
        operationsChecklist = listOf(
            OperationsChecklistItem("셋리스트 최종본 반영", "공연 기획팀", PartnerAssetStatus.Confirmed),
            OperationsChecklistItem("AR 큐 리허설", "현장 연출팀", PartnerAssetStatus.NeedsReview),
            OperationsChecklistItem("촬영 금지 구간 안내 검수", "보안 운영팀", PartnerAssetStatus.Scheduled),
            OperationsChecklistItem("글래스 HUD 밝기 기준 확인", "제품 운영팀", PartnerAssetStatus.NeedsReview)
        ),
        interactionEvents = defaultLumenInteractionEvents()
    ),
    ConcertEvent(
        id = "nova-2026-preview",
        title = "NOVA FESTIVAL: PREVIEW NIGHT",
        subtitle = "공연 데이터 검수 중인 파일럿 공연",
        partnerBrief = PartnerBrief(
            promoter = "NOVA Stage / 데이터 검수 중",
            venue = "Jamsil Arena",
            showDate = "2026.10.03",
            dataStatus = "티켓 인증 전, 번역 문구 검수 필요"
        ),
        venueInfo = VenueInfo(
            name = "Jamsil Arena",
            gate = "A3 게이트",
            seat = "2층 204구역 05열 11번",
            nearestExit = "서문",
            merchBooth = "외부 광장 MD 텐트"
        ),
        ticket = ConcertTicket(
            ticketId = "NV-PREVIEW-1003-0511",
            holderName = "관객",
            checkedIn = false
        ),
        tracks = listOf(
            ConcertTrack(
                title = "Signal Bloom",
                artist = "NOVA",
                durationSeconds = 120,
                cues = listOf(
                    ConcertCue(
                        0,
                        "입장 안내",
                        "Entry guide",
                        "게이트 입장 확인 후 Companion 시작",
                        "Start Companion after gate check-in",
                        HudPlacement.LowerEdge,
                        HudEffect.Caption
                    ),
                    ConcertCue(
                        45,
                        "촬영 정책",
                        "Capture policy",
                        "이 구간은 촬영 금지",
                        "Recording is not allowed in this section",
                        HudPlacement.RightCorner,
                        HudEffect.Countdown
                    )
                )
            )
        ),
        partnerAssets = listOf(
            PartnerAsset("셋리스트 타임라인", "초안 업로드 완료", PartnerAssetStatus.NeedsReview),
            PartnerAsset("촬영 정책", "공연장 승인 대기", PartnerAssetStatus.Scheduled),
            PartnerAsset("티켓 인증", "예매처 연동 필요", PartnerAssetStatus.NeedsReview)
        ),
        operationsChecklist = listOf(
            OperationsChecklistItem("예매처 체크인 API 확인", "파트너십", PartnerAssetStatus.NeedsReview),
            OperationsChecklistItem("촬영 정책 최종 승인", "공연장 운영", PartnerAssetStatus.Scheduled),
            OperationsChecklistItem("번역 문구 검수", "아티스트 매니지먼트", PartnerAssetStatus.NeedsReview)
        ),
        interactionEvents = defaultNovaInteractionEvents()
    )
)

private fun defaultLumenInteractionEvents(): List<ConcertInteractionEvent> =
    listOf(
        ConcertInteractionEvent(
            id = "lumen-call-response",
            type = ConcertInteractionEventType.CallAndResponse,
            trackIndex = 0,
            startSecond = 24,
            endSecond = 34,
            titleKo = "콜앤리스폰스",
            messageKo = "아티스트 콜 뒤에 'LUMEN!'으로 응답",
            titleEn = "Call and response",
            messageEn = "Answer 'LUMEN!' after the artist call",
            ctaLabel = "응답",
            reactionSignal = ReactionSignal.Cheer
        ),
        ConcertInteractionEvent(
            id = "lumen-fan-chant",
            type = ConcertInteractionEventType.FanChant,
            trackIndex = 0,
            startSecond = 28,
            endSecond = 42,
            titleKo = "응원법 타이밍",
            messageKo = "오른쪽 상단 카운트에 맞춰 응원 콜 시작",
            titleEn = "Fan chant timing",
            messageEn = "Start the chant on the corner countdown",
            ctaLabel = "응원",
            reactionSignal = ReactionSignal.Cheer
        ),
        ConcertInteractionEvent(
            id = "lumen-lightstick-wave",
            type = ConcertInteractionEventType.LightstickWave,
            trackIndex = 0,
            startSecond = 48,
            endSecond = 68,
            titleKo = "응원봉 웨이브",
            messageKo = "Floor B부터 2층까지 파란색 웨이브",
            titleEn = "Lightstick wave",
            messageEn = "Blue wave from Floor B to Level 2",
            zone = "Floor B -> 2층",
            ctaLabel = "싱크",
            reactionSignal = ReactionSignal.Cheer
        ),
        ConcertInteractionEvent(
            id = "lumen-singalong",
            type = ConcertInteractionEventType.FanChant,
            trackIndex = 0,
            startSecond = 70,
            endSecond = 88,
            titleKo = "후렴 떼창",
            messageKo = "첫 줄만 보고 같이 부르기",
            titleEn = "Chorus sing-along",
            messageEn = "Sing together from the first lyric cue",
            ctaLabel = "떼창",
            reactionSignal = ReactionSignal.SingAlong
        ),
        ConcertInteractionEvent(
            id = "lumen-photo-countdown",
            type = ConcertInteractionEventType.PhotoCountdown,
            trackIndex = 0,
            startSecond = 92,
            endSecond = 104,
            titleKo = "포토 타임",
            messageKo = "공식 포토 타임 10초",
            titleEn = "Photo time",
            messageEn = "Official photo time for 10 seconds",
            ctaLabel = "촬영"
        ),
        ConcertInteractionEvent(
            id = "lumen-camera-policy",
            type = ConcertInteractionEventType.CameraPolicy,
            trackIndex = 0,
            startSecond = 105,
            endSecond = 116,
            titleKo = "촬영 종료",
            messageKo = "포토 타임 종료. 이후 촬영 금지",
            titleEn = "Camera policy",
            messageEn = "Photo time ended. Recording is not allowed",
            ctaLabel = "확인"
        ),
        ConcertInteractionEvent(
            id = "lumen-encore-gauge",
            type = ConcertInteractionEventType.EncoreGauge,
            trackIndex = 0,
            startSecond = 118,
            endSecond = 145,
            titleKo = "앵콜 게이지",
            messageKo = "환호와 박수로 앵콜 게이지 채우기",
            titleEn = "Encore gauge",
            messageEn = "Fill the encore gauge with cheers and claps",
            ctaLabel = "환호",
            reactionSignal = ReactionSignal.Cheer
        ),
        ConcertInteractionEvent(
            id = "lumen-translation",
            type = ConcertInteractionEventType.Translation,
            trackIndex = 1,
            startSecond = 0,
            endSecond = 24,
            titleKo = "멘트 번역",
            messageKo = "아티스트 멘트 핵심 요약 표시",
            titleEn = "Talk translation",
            messageEn = "Show the approved artist talk summary",
            ctaLabel = "보기"
        ),
        ConcertInteractionEvent(
            id = "lumen-setlist-hint",
            type = ConcertInteractionEventType.SetlistHint,
            trackIndex = 1,
            startSecond = 25,
            endSecond = 34,
            titleKo = "셋리스트 힌트",
            messageKo = "다음 구간은 블루 응원봉 준비",
            titleEn = "Setlist hint",
            messageEn = "Blue lightsticks for the next section",
            ctaLabel = "준비"
        ),
        ConcertInteractionEvent(
            id = "lumen-fan-mission",
            type = ConcertInteractionEventType.FanMission,
            trackIndex = 1,
            startSecond = 82,
            endSecond = 106,
            titleKo = "팬 미션",
            messageKo = "후렴에서 하트 3번 보내기",
            titleEn = "Fan mission",
            messageEn = "Send 3 hearts during the chorus",
            ctaLabel = "하트",
            reactionSignal = ReactionSignal.Heart
        ),
        ConcertInteractionEvent(
            id = "lumen-merch-booth",
            type = ConcertInteractionEventType.MerchBooth,
            trackIndex = 1,
            startSecond = 108,
            endSecond = 118,
            titleKo = "MD 부스 안내",
            messageKo = "동측 MD 부스 포토카드 줄 분리 운영",
            titleEn = "Merch booth",
            messageEn = "Photo card line is separated at the east booth",
            zone = "1층 동측",
            ctaLabel = "확인"
        ),
        ConcertInteractionEvent(
            id = "lumen-surprise",
            type = ConcertInteractionEventType.Surprise,
            trackIndex = 1,
            startSecond = 119,
            endSecond = 126,
            titleKo = "서프라이즈 이벤트",
            messageKo = "마지막 인사 때 응원봉 화이트",
            titleEn = "Surprise event",
            messageEn = "White lightsticks for the final greeting",
            ctaLabel = "참여",
            reactionSignal = ReactionSignal.Cheer
        ),
        ConcertInteractionEvent(
            id = "lumen-exit-flow",
            type = ConcertInteractionEventType.ExitFlow,
            trackIndex = 1,
            startSecond = 127,
            endSecond = 131,
            titleKo = "퇴장 동선",
            messageKo = "Floor B는 북2문 방향으로 순차 이동",
            titleEn = "Exit flow",
            messageEn = "Floor B exits toward North Gate 2",
            zone = "Floor B",
            ctaLabel = "동선"
        )
    )

private fun defaultNovaInteractionEvents(): List<ConcertInteractionEvent> =
    listOf(
        ConcertInteractionEvent(
            id = "nova-data-review",
            type = ConcertInteractionEventType.SetlistHint,
            trackIndex = 0,
            startSecond = 12,
            endSecond = 30,
            titleKo = "데이터 검수",
            messageKo = "주최사 승인 전 샘플 이벤트",
            titleEn = "Data review",
            messageEn = "Sample event before partner approval",
            ctaLabel = "검수"
        )
    )

fun ConcertState.tick(): ConcertState {
    val nextSecond = elapsedSeconds + 1
    val decayedEnergy = (fanEnergy - 1).coerceAtLeast(20)
    return if (nextSecond >= currentTrack.durationSeconds) {
        copy(
            trackIndex = (trackIndex + 1) % tracks.size,
            elapsedSeconds = 0,
            fanEnergy = decayedEnergy,
            lastReaction = null,
            completedTracks = completedTracks + 1,
            recapHighlights = addRecapHighlight(
                RecapHighlight(
                    timeLabel = formatTime(currentTrack.durationSeconds),
                    title = "곡 완료",
                    detail = currentTrack.title
                )
            )
        )
    } else {
        copy(elapsedSeconds = nextSecond, fanEnergy = decayedEnergy)
    }
}

fun ConcertState.applyReaction(signal: ReactionSignal): ConcertState =
    (fanEnergy + signal.energyBoost).coerceAtMost(100).let { nextEnergy ->
        val trackKey = currentTrack.title
        copy(
            fanEnergy = nextEnergy,
            lastReaction = signal,
            totalReactions = totalReactions + 1,
            peakEnergy = peakEnergy.coerceAtLeast(nextEnergy),
            trackReactionCounts = trackReactionCounts + (trackKey to ((trackReactionCounts[trackKey] ?: 0) + 1)),
            recapHighlights = addRecapHighlight(
                RecapHighlight(
                    timeLabel = formatTime(elapsedSeconds),
                    title = signal.label,
                    detail = "${currentTrack.title} · ${activeCue.titleKo}"
                )
            )
        )
    }

fun ConcertState.applyInteractionEvent(event: ConcertInteractionEvent): ConcertState {
    if (event.id !in this.event.interactionEvents.map { it.id }) return this
    val reacted = event.reactionSignal?.let { applyReaction(it) } ?: this
    val count = (reacted.interactionEventParticipationCounts[event.id] ?: 0) + 1
    return reacted.copy(
        lastInteractionEventId = event.id,
        interactionEventParticipationCounts = reacted.interactionEventParticipationCounts + (event.id to count),
        recapHighlights = reacted.addRecapHighlight(
            RecapHighlight(
                timeLabel = formatTime(reacted.elapsedSeconds),
                title = event.type.label,
                detail = "${event.titleKo} · ${event.zone}"
            )
        )
    )
}

fun ConcertState.applyGlassesInput(action: GlassesInputAction): ConcertState =
    when (action.intent) {
        GlassesInputIntent.ConfirmHud -> recordGlassesInputMoment(action, "HUD 확인", activeCue.titleKo)
        GlassesInputIntent.DismissHud -> recordGlassesInputMoment(action, "HUD 닫기", activeCue.titleKo)
        GlassesInputIntent.NextCue -> {
            val nextSecond = nextCue?.atSecond ?: (currentTrack.durationSeconds - 1)
            copy(elapsedSeconds = nextSecond.coerceIn(0, currentTrack.durationSeconds - 1))
                .recordGlassesInputMoment(action, "다음 큐", activeCue.titleKo)
        }
        GlassesInputIntent.PreviousCue -> {
            val previousSecond = currentTrack.cues
                .filter { it.atSecond < elapsedSeconds }
                .lastOrNull()
                ?.atSecond
                ?: 0
            copy(elapsedSeconds = previousSecond.coerceIn(0, currentTrack.durationSeconds - 1))
                .recordGlassesInputMoment(action, "이전 큐", activeCue.titleKo)
        }
        GlassesInputIntent.SaveMoment -> recordGlassesInputMoment(
            action = action,
            title = "순간 저장",
            detail = "${currentTrack.title} · ${activeCue.titleKo}"
        )
        GlassesInputIntent.SendHeart -> applyReaction(ReactionSignal.Heart)
            .recordGlassesInputMoment(action, "글래스 반응", ReactionSignal.Heart.label)
        GlassesInputIntent.SendCheer -> applyReaction(ReactionSignal.Cheer)
            .recordGlassesInputMoment(action, "글래스 반응", ReactionSignal.Cheer.label)
    }

fun ConcertState.seekToCue(cueIndex: Int): ConcertState {
    val cue = currentTrack.cues.getOrNull(cueIndex) ?: return this
    return copy(
        elapsedSeconds = cue.atSecond.coerceIn(0, currentTrack.durationSeconds - 1)
    ).recordGlassesInputMoment(
        action = GlassesInputAction(GlassesInputIntent.ConfirmHud),
        title = "HUD 큐 미리보기",
        detail = "${currentTrack.title} · ${cue.titleKo}"
    )
}

fun ConcertState.selectEvent(event: ConcertEvent): ConcertState =
    copy(
        event = event,
        trackIndex = 0,
        elapsedSeconds = 0,
        fanEnergy = 42,
        lastReaction = null,
        audioEnergy = 0,
        micEnabled = false,
        totalReactions = 0,
        peakEnergy = 42,
        completedTracks = 0,
        trackReactionCounts = emptyMap(),
        interactionEventParticipationCounts = emptyMap(),
        lastInteractionEventId = null,
        recapHighlights = emptyList()
    )

fun ConcertState.withEventCatalog(events: List<ConcertEvent>): ConcertState {
    if (events.isEmpty()) return this
    val resolvedEvent = events.firstOrNull { it.id == event.id } ?: events.first()
    val resolvedTrackIndex = trackIndex.coerceIn(0, resolvedEvent.tracks.lastIndex.coerceAtLeast(0))
    return copy(
        event = resolvedEvent,
        trackIndex = resolvedTrackIndex,
        elapsedSeconds = elapsedSeconds.coerceAtMost(resolvedEvent.tracks[resolvedTrackIndex].durationSeconds - 1)
    )
}

fun ConcertState.applyAudioEnergy(level: Int): ConcertState {
    val normalizedLevel = level.coerceIn(0, 100)
    val blendedEnergy = ((fanEnergy * 0.82f) + (normalizedLevel * 0.18f)).roundToInt()
    val nextEnergy = blendedEnergy.coerceIn(20, 100)
    return copy(
        audioEnergy = normalizedLevel,
        fanEnergy = nextEnergy,
        micEnabled = true,
        peakEnergy = peakEnergy.coerceAtLeast(nextEnergy),
        recapHighlights = if (nextEnergy > peakEnergy) {
            addRecapHighlight(
                RecapHighlight(
                    timeLabel = formatTime(elapsedSeconds),
                    title = "강한 관객 반응",
                    detail = "${currentTrack.title} · ${nextEnergy}%"
                )
            )
        } else {
            recapHighlights
        }
    )
}

fun ConcertState.stopAudioEnergy(): ConcertState =
    copy(audioEnergy = 0, micEnabled = false)

fun ConcertState.pauseForBackground(): ConcertState =
    stopAudioEnergy()

private fun ConcertState.addRecapHighlight(highlight: RecapHighlight): List<RecapHighlight> =
    (recapHighlights + highlight).takeLast(12)

private fun ConcertState.recordGlassesInputMoment(
    action: GlassesInputAction,
    title: String,
    detail: String
): ConcertState =
    copy(
        recapHighlights = addRecapHighlight(
            RecapHighlight(
                timeLabel = formatTime(elapsedSeconds),
                title = title,
                detail = "${action.source.label} · $detail"
            )
        )
    )

fun formatTime(totalSeconds: Int): String {
    val clamped = totalSeconds.coerceAtLeast(0)
    val minutes = clamped / 60
    val seconds = clamped % 60
    return "%d:%02d".format(minutes, seconds)
}
