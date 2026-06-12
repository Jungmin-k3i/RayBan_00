package com.k3i.rayban_00

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConcertExperienceTest {
    @Test
    fun reactionIncreasesEnergyWithoutExceedingOneHundred() {
        val state = ConcertState(fanEnergy = 96).applyReaction(ReactionSignal.Cheer)

        assertEquals(100, state.fanEnergy)
        assertEquals(ReactionSignal.Cheer, state.lastReaction)
        assertEquals(1, state.totalReactions)
        assertEquals(100, state.peakEnergy)
        assertEquals(1, state.trackReactionCounts[state.currentTrack.title])
        assertTrue(state.recap.highlights.any { it.title == ReactionSignal.Cheer.label })
    }

    @Test
    fun tickAdvancesTrackAfterDuration() {
        val state = ConcertState(elapsedSeconds = 149).tick()

        assertEquals(1, state.trackIndex)
        assertEquals(0, state.elapsedSeconds)
    }

    @Test
    fun audioEnergyIsNormalizedAndDoesNotStoreRawAudio() {
        val state = ConcertState(fanEnergy = 40).applyAudioEnergy(180)

        assertEquals(100, state.audioEnergy)
        assertTrue(state.micEnabled)
        assertTrue(state.fanEnergy in 20..100)
    }

    @Test
    fun backgroundPauseStopsAudioEnergy() {
        val state = ConcertState(fanEnergy = 40)
            .applyAudioEnergy(80)
            .pauseForBackground()

        assertEquals(0, state.audioEnergy)
        assertEquals(false, state.micEnabled)
    }

    @Test
    fun audioPanelUiStateExplainsPermissionDeniedAndRetryAction() {
        val uiState = ConcertState().toAudioPanelUiState(
            micPermissionDenied = true,
            appInForeground = true
        )

        assertEquals("시작", uiState.actionLabel)
        assertTrue(uiState.statusMessage.contains("원본 음성").not())
        assertTrue(uiState.warningMessage?.contains("마이크 권한이 거부") == true)
        assertTrue(uiState.warningMessage?.contains("허용") == true)
    }

    @Test
    fun audioPanelUiStateShowsStopActionWhenMicIsActive() {
        val uiState = ConcertState()
            .applyAudioEnergy(60)
            .toAudioPanelUiState(
                micPermissionDenied = false,
                appInForeground = true
        )

        assertEquals("중지", uiState.actionLabel)
        assertTrue(uiState.statusMessage.contains("마이크 입력 실험"))
        assertEquals(null, uiState.warningMessage)
    }

    @Test
    fun audioPanelUiStatePrioritizesPermissionDeniedOverBackgroundWarning() {
        val uiState = ConcertState().toAudioPanelUiState(
            micPermissionDenied = true,
            appInForeground = false
        )

        assertTrue(uiState.warningMessage?.contains("마이크 권한") == true)
        assertEquals(false, uiState.warningMessage?.contains("백그라운드") == true)
    }

    @Test
    fun selectingEventResetsSessionState() {
        val current = ConcertState(fanEnergy = 88, totalReactions = 7, completedTracks = 2)
        val selected = current.selectEvent(sampleConcertEvents.first())

        assertEquals(42, selected.fanEnergy)
        assertEquals(0, selected.totalReactions)
        assertEquals(0, selected.completedTracks)
        assertEquals(0, selected.elapsedSeconds)
    }

    @Test
    fun eventCatalogRestoresMatchingEventWithoutDroppingSessionProgress() {
        val source = sampleConcertEvents.first()
        val replacement = source.copy(subtitle = "loaded from package")
        val state = ConcertState(event = source, trackIndex = 1, elapsedSeconds = 10, fanEnergy = 70)
            .withEventCatalog(listOf(replacement))

        assertEquals("loaded from package", state.event.subtitle)
        assertEquals(1, state.trackIndex)
        assertEquals(10, state.elapsedSeconds)
        assertEquals(70, state.fanEnergy)
    }

    @Test
    fun recapReflectsSessionSummary() {
        val state = ConcertState(fanEnergy = 90, totalReactions = 3, peakEnergy = 94, completedTracks = 1)
            .applyReaction(ReactionSignal.Clap)

        assertEquals("LUMEN LIVE: GLASS HORIZON", state.recap.eventTitle)
        assertEquals(2, state.recap.watchedTracks)
        assertEquals(4, state.recap.totalReactions)
        assertEquals(98, state.recap.peakEnergy)
        assertTrue(state.recap.trackReactionCounts.isNotEmpty())
        assertTrue(state.recap.highlights.isNotEmpty())
    }

    @Test
    fun hudStateCanBecomeRenderInstruction() {
        val instruction = ConcertState().hudState.toRenderInstruction()

        assertEquals(HudPlacement.PeripheralPulse, instruction.placement)
        assertEquals(HudEffect.EdgePulse, instruction.effect)
        assertEquals(HudPriority.Low, instruction.priority)
        assertTrue(instruction.assistiveText.contains("입장 조명"))
        assertEquals(HudArObjectKey.ParticipationWave, instruction.visualScene.arObjectKey)
        assertEquals(HudVisualLayout.EdgeSignal, instruction.visualScene.layout)
        assertEquals(HudVisualAnimation.Wave, instruction.visualScene.animation)
        assertEquals("peripheral-edge", instruction.visualScene.safeAreaHint)
    }

    @Test
    fun liveTranslationDefaultsToPreparedSubtitleFeedForNonCaptionCue() {
        val translation = ConcertState().liveTranslation

        assertEquals(TranslationInputSource.PreparedSubtitleFeed, translation.source)
        assertEquals(TranslationPipelineStage.HudSummary, translation.stage)
        assertEquals("EN", translation.sourceLanguage)
        assertEquals("KO", translation.targetLanguage)
        assertTrue(translation.hudSummary.length <= 34)
        assertTrue(translation.confidencePercent >= 90)
        assertEquals("PreparedSubtitleTranslationEngine", translation.engineName)
        assertEquals(null, translation.fallbackReason)
    }

    @Test
    fun liveTranslationUsesOfficialAudioFeedForCaptionCue() {
        val translation = ConcertState(elapsedSeconds = 72).liveTranslation

        assertEquals(TranslationInputSource.OfficialAudioFeed, translation.source)
        assertEquals(1800, translation.estimatedLatencyMillis)
        assertEquals("OfficialFeedTranslationEngine", translation.engineName)
        assertTrue(translation.fallbackReason?.contains("STT/번역 API") == true)
        assertTrue(translation.policyNote.contains("원본 음성 저장 없이"))
    }

    @Test
    fun liveTranslationUsesPhoneMicExperimentalEngineWhenMicIsActive() {
        val translation = ConcertState()
            .applyAudioEnergy(60)
            .liveTranslation

        assertEquals(TranslationInputSource.PhoneMicExperimental, translation.source)
        assertEquals("PhoneMicExperimentalTranslationEngine", translation.engineName)
        assertTrue(translation.fallbackReason?.contains("실제 STT/번역 API") == true)
        assertTrue(translation.confidencePercent in 35..62)
    }

    @Test
    fun smartphonePreviewRendererAcceptsHudInstruction() {
        val instruction = ConcertState().hudState.toRenderInstruction()
        val result = SmartphonePreviewHudRenderer().render(instruction)

        assertTrue(result.accepted)
        assertEquals(GlassesRendererAvailability.Ready, result.status.availability)
        assertEquals(instruction, result.instruction)
    }

    @Test
    fun metaWearablesToolkitRendererStaysBlockedUntilOfficialSdkIsConnected() {
        val instruction = ConcertState().hudState.toRenderInstruction()
        val result = MetaWearablesToolkitHudRenderer().render(instruction)

        assertEquals(false, result.accepted)
        assertEquals(GlassesRendererAvailability.WaitingForOfficialSdk, result.status.availability)
        assertEquals(instruction, result.instruction)
    }

    @Test
    fun defaultGlassesProfileTargetsRayBanDisplayHardware() {
        val profile = defaultGlassesIntegrationProfile()

        assertEquals(TargetGlassesDevice.RayBanDisplay, profile.targetDevice)
        assertTrue(profile.targetDevice.hasDisplay)
        assertEquals(GlassesRendererAvailability.Ready, profile.primaryRenderer.status.availability)
        assertEquals(GlassesRendererAvailability.Ready, profile.mockDeviceRenderer?.status?.availability)
        assertEquals(GlassesRendererAvailability.Ready, profile.fallbackRenderer.status.availability)
    }

    @Test
    fun metaWearablesMockDeviceRendererAcceptsPayloadForEmulatorReview() {
        val instruction = ConcertState().hudState.toRenderInstruction()
        val renderer = MetaWearablesMockDeviceHudRenderer()
        val result = renderer.render(instruction)
        val document = renderer.prepareDisplayDocument(instruction)

        assertEquals(true, result.accepted)
        assertEquals(GlassesRendererAvailability.Ready, result.status.availability)
        assertTrue(result.status.message.contains("MockDeviceKit"))
        assertEquals(instruction.visualScene.sceneId, document.documentId)
    }

    @Test
    fun hudInstructionCanBecomeToolkitDisplayDocument() {
        val instruction = ConcertState().hudState.toRenderInstruction()
        val document = instruction.toToolkitDisplayDocument()

        assertEquals(TargetGlassesDevice.RayBanDisplay, document.targetDevice)
        assertEquals(instruction.durationMillis, document.durationMillis)
        assertEquals(instruction.priority, document.priority)
        assertEquals(4, document.elements.size)
        assertTrue(document.elements.any { it.id == "visual-scene" && it.type == ToolkitDisplayElementType.List })
        assertTrue(document.elements.any { it.id == "primary-text-ko" && it.value == instruction.textKo })
        assertTrue(document.elements.any { it.metadata["arObjectKey"] == instruction.visualScene.arObjectKey.name })
        assertTrue(document.elements.any { it.metadata["animation"] == instruction.visualScene.animation.name })
    }

    @Test
    fun hudDispatchFallsBackToSmartphonePreviewWhenToolkitIsPending() {
        val instruction = ConcertState().hudState.toRenderInstruction()
        val report = dispatchHudToGlasses(
            instruction = instruction,
            profile = defaultGlassesIntegrationProfile()
        )

        assertEquals(true, report.primaryResult.accepted)
        assertEquals(null, report.fallbackResult)
        assertEquals(GlassesDispatchRoute.MockDevice, report.route)
        assertEquals(1, report.records.size)
        assertEquals("MetaWearablesMockDeviceHudRenderer", report.records.first().rendererName)
        assertEquals(instruction.textKo, report.records.first().textKo)
        assertEquals(false, report.fallbackPlan.active)
    }

    @Test
    fun hudDispatchKeepsRecentRecordsOnly() {
        val instruction = ConcertState().hudState.toRenderInstruction()
        val profile = defaultGlassesIntegrationProfile()
        val records = (1..8).fold(emptyList<GlassesDispatchRecord>()) { currentRecords, _ ->
            dispatchHudToGlasses(
                instruction = instruction,
                profile = profile,
                previousRecords = currentRecords,
                maxRecords = 3
            ).records
        }

        assertEquals(3, records.size)
        assertEquals(6, records.first().sequence)
        assertEquals(8, records.last().sequence)
    }

    @Test
    fun dispatchRecordsCanBeSerializedForLocalAuditLog() {
        val instruction = ConcertState().hudState.toRenderInstruction()
        val records = dispatchHudToGlasses(
            instruction = instruction,
            profile = defaultGlassesIntegrationProfile()
        ).records

        val restored = deserializeGlassesDispatchRecords(serializeGlassesDispatchRecords(records))

        assertEquals(records, restored)
        assertEquals(instruction.textKo, restored.first().textKo)
        assertEquals(GlassesDispatchRoute.MockDevice, restored.first().route)
    }

    @Test
    fun dispatchRecordDeserializerIgnoresCorruptedLines() {
        val record = GlassesDispatchRecord(
            sequence = 7,
            route = GlassesDispatchRoute.FallbackPreview,
            accepted = true,
            rendererName = "renderer",
            availability = GlassesRendererAvailability.Ready,
            documentId = "doc",
            textKo = "문구",
            priority = HudPriority.High
        )
        val raw = "broken-line\n${serializeGlassesDispatchRecords(listOf(record))}\n1\tBAD\ttrue\tbad\tReady\tdoc\ttext\tHigh"

        val restored = deserializeGlassesDispatchRecords(raw)

        assertEquals(listOf(record), restored)
    }

    @Test
    fun highPriorityFallbackPlanIncludesNotificationAndAudioCue() {
        val instruction = ConcertState(elapsedSeconds = 28).hudState.toRenderInstruction()
        val plan = instruction.toFallbackDeliveryPlan(
            MetaWearablesToolkitHudRenderer().render(instruction)
        )

        assertEquals(HudPriority.High, instruction.priority)
        assertTrue(plan.active)
        assertTrue(plan.steps.any { it.channel == FallbackDeliveryChannel.AndroidNotification && it.required })
        assertTrue(plan.steps.any { it.channel == FallbackDeliveryChannel.AudioCue && !it.required })
    }

    @Test
    fun glassesInputCanMoveToNextCueAndRecordMoment() {
        val state = ConcertState(elapsedSeconds = 1).applyGlassesInput(
            GlassesInputAction(GlassesInputIntent.NextCue)
        )

        assertEquals(28, state.elapsedSeconds)
        assertTrue(state.recap.highlights.any { it.title == "다음 큐" })
        assertTrue(defaultGlassesInteractionPolicy.allowedIntents.contains(GlassesInputIntent.NextCue))
    }

    @Test
    fun glassesInputCanMoveToPreviousCue() {
        val state = ConcertState(elapsedSeconds = 80).applyGlassesInput(
            GlassesInputAction(GlassesInputIntent.PreviousCue)
        )

        assertEquals(72, state.elapsedSeconds)
        assertTrue(state.recap.highlights.any { it.title == "이전 큐" })
    }

    @Test
    fun companionCanSeekToSpecificHudCueForPreview() {
        val state = ConcertState().seekToCue(2)

        assertEquals(72, state.elapsedSeconds)
        assertEquals("떼창 가사", state.activeCue.titleKo)
        assertTrue(state.recap.highlights.any { it.title == "HUD 큐 미리보기" })
    }

    @Test
    fun glassesInputReactionUsesSameReactionState() {
        val state = ConcertState().applyGlassesInput(
            GlassesInputAction(
                intent = GlassesInputIntent.SendHeart,
                source = GlassesInputSource.MetaNeuralBand
            )
        )

        assertEquals(ReactionSignal.Heart, state.lastReaction)
        assertEquals(1, state.totalReactions)
        assertTrue(state.recap.highlights.any { it.detail.contains(GlassesInputSource.MetaNeuralBand.label) })
    }

    @Test
    fun toolkitRendererPreparesDocumentBeforeRealSdkIsAvailable() {
        val instruction = ConcertState().hudState.toRenderInstruction()
        val document = MetaWearablesToolkitHudRenderer().prepareDisplayDocument(instruction)

        assertEquals(TargetGlassesDevice.RayBanDisplay, document.targetDevice)
        assertEquals(instruction.visualScene.sceneId, document.documentId)
        assertTrue(document.elements.any { it.id == "visual-scene" })
    }

    @Test
    fun concertPackageParserBuildsEventFromJson() {
        val json = """
            {
              "id": "test-show",
              "title": "Test Show",
              "subtitle": "Package loaded",
              "partnerBrief": {
                "promoter": "Promoter",
                "venue": "Venue",
                "showDate": "2026.09.18",
                "dataStatus": "Confirmed"
              },
              "venueInfo": {
                "name": "Venue",
                "gate": "A1",
                "seat": "B-12",
                "nearestExit": "North",
                "merchBooth": "1F"
              },
              "ticketPolicy": {
                "requiresCheckIn": true,
                "provider": "ticket",
                "ticketId": "T-1",
                "holderName": "Guest",
                "checkedIn": true
              },
              "tracks": [
                {
                  "title": "Track",
                  "artist": "Artist",
                  "durationSeconds": 90,
                  "cues": [
                    {
                      "atSecond": 0,
                      "titleKo": "시작",
                      "titleEn": "Start",
                      "hudMessageKo": "표시",
                      "hudMessageEn": "Display",
                      "placement": "LowerEdge",
                      "effect": "Caption",
                      "durationMillis": 3000
                    }
                  ]
                }
              ],
              "partnerAssets": [
                {
                  "name": "Asset",
                  "detail": "Detail",
                  "status": "Confirmed"
                }
              ],
              "operationsChecklist": [
                {
                  "title": "Check",
                  "owner": "Ops",
                  "status": "Scheduled"
                }
              ],
              "interactionEvents": [
                {
                  "id": "event-chant",
                  "type": "FanChant",
                  "trackIndex": 0,
                  "startSecond": 20,
                  "endSecond": 30,
                  "titleKo": "응원법",
                  "messageKo": "같이 외치기",
                  "titleEn": "Fan chant",
                  "messageEn": "Chant together",
                  "zone": "All",
                  "ctaLabel": "응원",
                  "reactionSignal": "Cheer",
                  "requiresApproval": true
                }
              ]
            }
        """.trimIndent()

        val event = ConcertEventPackageParser.parse(json)

        assertEquals("test-show", event.id)
        assertEquals("T-1", event.ticket.ticketId)
        assertTrue(event.ticket.checkedIn)
        assertEquals(HudPlacement.LowerEdge, event.tracks.first().cues.first().placement)
        assertEquals(1, event.operationsChecklist.size)
        assertEquals(1, event.interactionEvents.size)
        assertEquals(ConcertInteractionEventType.FanChant, event.interactionEvents.first().type)
        assertEquals(ReactionSignal.Cheer, event.interactionEvents.first().reactionSignal)
    }

    @Test
    fun concertStateResolvesActiveAndUpcomingInteractionEvents() {
        val active = ConcertState(elapsedSeconds = 28)
        val upcoming = ConcertState(elapsedSeconds = 90)

        assertTrue(active.activeInteractionEvents.any { it.type == ConcertInteractionEventType.CallAndResponse })
        assertEquals(ConcertInteractionEventType.LightstickWave, active.upcomingInteractionEvents.first().type)
        assertEquals(ConcertInteractionEventType.PhotoCountdown, upcoming.currentFeaturedInteractionEvent?.type)
    }

    @Test
    fun applyingInteractionEventRecordsParticipationAndReaction() {
        val state = ConcertState(elapsedSeconds = 28)
        val event = state.currentFeaturedInteractionEvent!!
        val participated = state.applyInteractionEvent(event)

        assertEquals(event.id, participated.lastInteractionEventId)
        assertEquals(1, participated.interactionEventParticipationCounts[event.id])
        assertEquals(event.reactionSignal, participated.lastReaction)
        assertEquals(1, participated.totalReactions)
        assertTrue(participated.recap.highlights.any { it.title == event.type.label })
    }

    @Test
    fun interactionEventCountsCanBeSerializedForLocalSessionRestore() {
        val counts = mapOf("chant" to 2, "wave" to 1, "ignored" to 0)

        val restored = deserializeInteractionEventCounts(serializeInteractionEventCounts(counts))

        assertEquals(mapOf("chant" to 2, "wave" to 1), restored)
    }

    @Test
    fun concertPackageValidationReportsMissingRequiredKeys() {
        val validation = ConcertEventPackageParser.validate("""{"id":"bad"}""")

        assertEquals(false, validation.valid)
        assertTrue(validation.errors.any { it == "missing:title" })
        assertTrue(validation.errors.any { it == "missing:tracks" })
    }

    @Test
    fun concertPackageValidationRejectsCueOutsideTrackDuration() {
        val validation = ConcertEventPackageParser.validate(
            """
                {
                  "id": "bad-cue",
                  "title": "Bad Cue",
                  "partnerBrief": {
                    "promoter": "Promoter",
                    "venue": "Venue",
                    "showDate": "2026.09.18",
                    "dataStatus": "Confirmed"
                  },
                  "venueInfo": {
                    "name": "Venue",
                    "gate": "A1",
                    "seat": "B-12",
                    "nearestExit": "North",
                    "merchBooth": "1F"
                  },
                  "ticketPolicy": {
                    "requiresCheckIn": true,
                    "provider": "ticket"
                  },
                  "tracks": [
                    {
                      "title": "Short Track",
                      "artist": "Artist",
                      "durationSeconds": 30,
                      "cues": [
                        {
                          "atSecond": 40,
                          "titleKo": "늦은 큐",
                          "titleEn": "Late Cue",
                          "hudMessageKo": "표시",
                          "hudMessageEn": "Display",
                          "placement": "LowerEdge",
                          "effect": "Caption",
                          "durationMillis": 3000
                        }
                      ]
                    }
                  ],
                  "partnerAssets": [
                    {
                      "name": "촬영 정책",
                      "detail": "지정 구간만 촬영 가능",
                      "status": "Confirmed"
                    }
                  ]
                }
            """.trimIndent()
        )

        assertEquals(false, validation.valid)
        assertTrue(validation.errors.any { it == "invalid:tracks[0].cues[0].atSecond" })
    }

    @Test
    fun concertPackageValidationReportsOperationalWarningsWithoutBlockingParse() {
        val validation = ConcertEventPackageParser.validate(
            """
                {
                  "id": "warning-show",
                  "title": "Warning Show",
                  "partnerBrief": {
                    "promoter": "Promoter",
                    "venue": "Venue",
                    "showDate": "2026.09.18",
                    "dataStatus": "Confirmed"
                  },
                  "venueInfo": {
                    "name": "Venue",
                    "gate": "A1",
                    "seat": "B-12",
                    "nearestExit": "North",
                    "merchBooth": "1F"
                  },
                  "ticketPolicy": {
                    "requiresCheckIn": true,
                    "provider": "ticket"
                  },
                  "tracks": [
                    {
                      "title": "Track",
                      "artist": "Artist",
                      "durationSeconds": 90,
                      "cues": [
                        {
                          "atSecond": 0,
                          "titleKo": "시작",
                          "titleEn": "Start",
                          "hudMessageKo": "표시",
                          "hudMessageEn": "Display",
                          "placement": "LowerEdge",
                          "effect": "Caption",
                          "durationMillis": 3000
                        }
                      ]
                    }
                  ],
                  "partnerAssets": [
                    {
                      "name": "Key visual",
                      "detail": "Main art",
                      "status": "Confirmed"
                    }
                  ]
                }
            """.trimIndent()
        )

        assertTrue(validation.valid)
        assertTrue(validation.warnings.any { it == "warning:partnerAssets.capturePolicyMissing" })
        assertTrue(validation.warnings.any { it == "warning:operationsChecklist.missing" })
    }

    @Test
    fun fallbackPackageReportMarksFallbackData() {
        val report = fallbackConcertPackageLoadReport(sampleConcertEvents)

        assertTrue(report.fallbackUsed)
        assertEquals(sampleConcertEvents.size, report.loadedCount)
        assertEquals(0, report.failedCount)
        assertEquals(0, report.warningCount)
        assertEquals(sampleConcertEvents.first().id, report.items.first().eventId)
    }

    @Test
    fun companionAudienceModeHidesOperatorDiagnostics() {
        val audience = CompanionViewMode.AudienceFocus.panelVisibility()
        val operator = CompanionViewMode.OperatorDebug.panelVisibility()

        assertEquals(false, audience.showDispatchControls)
        assertEquals(false, audience.showCueTimeline)
        assertEquals(false, audience.showInputSimulator)
        assertTrue(operator.showDispatchControls)
        assertTrue(operator.showCueTimeline)
        assertTrue(operator.showInputSimulator)
    }

    @Test
    fun consentNoticeIncludesRequiredPrivacyAndVenueClauses() {
        val clauses = defaultConcertConsentNotice

        assertTrue(clauses.all { it.required })
        assertTrue(clauses.any { it.body.contains("원본 음성은 저장하지 않습니다") })
        assertTrue(clauses.any { it.body.contains("공연장과 주최사의 촬영") })
        assertTrue(clauses.any { it.body.contains("얼굴 인식") && it.body.contains("제공하지 않습니다") })
        assertTrue(clauses.any { it.audience == ConsentNoticeAudience.LegalReview })
    }

    @Test
    fun platformIntegrationChannelsKeepDatAndMetaAiAppSeparate() {
        val channels = defaultPlatformIntegrationChannels()

        val androidApp = channels.first { it.type == PlatformIntegrationChannelType.AndroidApp }
        val dat = channels.first { it.type == PlatformIntegrationChannelType.WearablesDat }
        val metaAiApp = channels.first { it.type == PlatformIntegrationChannelType.MetaAiApp }

        assertTrue(androidApp.affectsPhoneUi)
        assertEquals(PlatformIntegrationRole.DeviceAccessSdk, dat.role)
        assertEquals(false, dat.affectsPhoneUi)
        assertTrue(dat.affectsGlassesUi)
        assertEquals(PlatformIntegrationRole.PlatformCompanion, metaAiApp.role)
        assertEquals(false, metaAiApp.affectsGlassesUi)
        assertTrue(metaAiApp.statusLabel.contains("확인 필요"))
        assertTrue(metaAiApp.description.contains("AI 에이전트 기능이 아니라"))
    }

    @Test
    fun backendArchitectureMarksRealProductDependencies() {
        val architecture = defaultBackendProductArchitecture()

        assertTrue(architecture.currentAppMode.contains("로컬 JSON"))
        assertTrue(architecture.targetProductMode.contains("API 서버"))
        assertTrue(architecture.requiredCapabilityCount >= 4)
        assertTrue(
            architecture.capabilities.any {
                it.type == BackendCapabilityType.ConcertPackageApi &&
                    it.requiredForMvp &&
                    it.state == BackendRuntimeState.Planned
            }
        )
        assertTrue(
            architecture.capabilities.any {
                it.type == BackendCapabilityType.TicketVerification &&
                    it.requiredForMvp &&
                    it.state == BackendRuntimeState.Required
            }
        )
    }

    @Test
    fun backendDatabasePlanSeparatesPersonalDataFromOperationalData() {
        val architecture = defaultBackendProductArchitecture()

        assertTrue(architecture.personalDataTableCount >= 2)
        assertTrue(
            architecture.databaseTables.any {
                it.tableName == "tickets" &&
                    it.containsPersonalData &&
                    it.retentionPolicy.contains("법무")
            }
        )
        assertTrue(
            architecture.databaseTables.any {
                it.tableName == "device_dispatch_logs" &&
                    !it.containsPersonalData
            }
        )
    }

    @Test
    fun backendArchitectureTreatsPartnerSystemsAsSourceOfTruth() {
        val architecture = defaultBackendProductArchitecture()

        assertTrue(architecture.requiredPartnerContractCount >= 4)
        assertTrue(
            architecture.partnerDataSources.any {
                it.type == PartnerDataSourceType.PromoterCms &&
                    it.requiredBeforeLaunch &&
                    it.sourceOfTruth.contains("셋리스트") &&
                    it.reconciliationRule.contains("승인 버전")
            }
        )
        assertTrue(
            architecture.partnerDataSources.any {
                it.type == PartnerDataSourceType.TicketingSystem &&
                    it.requiredBeforeLaunch &&
                    it.reconciliationRule.contains("원본을 소유하지 않고")
            }
        )
        assertTrue(
            architecture.partnerDataSources.any {
                it.type == PartnerDataSourceType.ArtistManagement &&
                    it.reconciliationRule.contains("승인된 문구")
            }
        )
    }

    @Test
    fun partnerImportValidationBlocksMissingRequiredSourceFields() {
        val report = validatePartnerImport(
            sourceName = "Promoter CMS",
            sourceVersion = "v1",
            approvedVersion = "v1",
            providedFields = setOf(
                "show.id",
                "show.approved_version",
                "setlist.tracks"
            )
        )

        assertEquals(false, report.canPublish)
        assertTrue(report.blockingIssueCount > 0)
        assertTrue(report.issues.any { it.sourceField == "setlist.time_base" })
        assertTrue(report.issues.any { it.sourceField == "rights.capture_policy" })
        assertTrue(report.issues.any { it.sourceField == "ticket.check_in_status" })
    }

    @Test
    fun partnerImportValidationBlocksVersionMismatch() {
        val report = validatePartnerImport(
            sourceName = "Promoter CMS",
            sourceVersion = "rehearsal-v2",
            approvedVersion = "rehearsal-v1",
            providedFields = defaultPartnerFieldMappings().map { it.sourceField }.toSet()
        )

        assertEquals(false, report.canPublish)
        assertTrue(report.issues.any { it.sourceField == "show.approved_version" })
        assertTrue(report.issues.any { it.message.contains("일치하지 않습니다") })
    }

    @Test
    fun partnerImportValidationAllowsCompleteApprovedMapping() {
        val report = validatePartnerImport(
            sourceName = "Promoter CMS",
            sourceVersion = "rehearsal-v1",
            approvedVersion = "rehearsal-v1",
            providedFields = defaultPartnerFieldMappings().map { it.sourceField }.toSet()
        )

        assertTrue(report.canPublish)
        assertEquals(0, report.blockingIssueCount)
        assertTrue(report.requiredFieldCount >= 9)
        assertTrue(
            report.mappings.any {
                it.sourceOwner == PartnerDataSourceType.TicketingSystem &&
                    it.semanticRule.contains("source of truth")
            }
        )
    }

    @Test
    fun repositoryResultKeepsLocalPackagesBehindStableContract() {
        val packageReport = fallbackConcertPackageLoadReport(sampleConcertEvents)
        val result = ConcertRepositoryResult(
            source = ConcertRepositorySource.LocalAsset,
            packageReport = packageReport,
            importValidationReport = samplePartnerImportValidationReport(),
            message = "local"
        )

        assertEquals(sampleConcertEvents.size, result.events.size)
        assertTrue(result.importValidationReport?.canPublish == true)
        assertTrue(result.isFallback)
    }

    @Test
    fun publishableRepositoryResultFallsBackWhenImportValidationFails() {
        val invalidReport = validatePartnerImport(
            sourceName = "Remote API",
            sourceVersion = "v2",
            approvedVersion = "v1",
            providedFields = defaultPartnerFieldMappings().map { it.sourceField }.toSet()
        )
        val primary = ConcertRepositoryResult(
            source = ConcertRepositorySource.RemoteApi,
            packageReport = ConcertPackageLoadReport(
                events = sampleConcertEvents,
                items = emptyList(),
                fallbackUsed = false
            ),
            importValidationReport = invalidReport,
            message = "remote"
        )

        val result = choosePublishableRepositoryResult(primary, sampleConcertEvents)

        assertEquals(ConcertRepositorySource.FallbackSample, result.source)
        assertTrue(result.isFallback)
        assertTrue(result.importValidationReport?.canPublish == false)
        assertEquals(sampleConcertEvents.size, result.events.size)
    }

    @Test
    fun plannedRemoteRepositoryIsExplicitlyNotImplementedYet() {
        val result = PlannedRemoteConcertRepository().loadConcertPackages()

        assertEquals(ConcertRepositorySource.RemoteApi, result.source)
        assertEquals(0, result.events.size)
        assertTrue(result.importValidationReport?.canPublish == false)
        assertTrue(result.message.contains("아직 구현되지 않았습니다"))
    }

    @Test
    fun repositoryReadinessBlocksEmptyDataAndFailedImport() {
        val empty = PlannedRemoteConcertRepository().loadConcertPackages().toReadinessStatus()
        assertEquals(RepositoryReadinessSeverity.Blocked, empty.severity)

        val invalidReport = validatePartnerImport(
            sourceName = "Remote API",
            sourceVersion = "v2",
            approvedVersion = "v1",
            providedFields = defaultPartnerFieldMappings().map { it.sourceField }.toSet()
        )
        val invalid = ConcertRepositoryResult(
            source = ConcertRepositorySource.RemoteApi,
            packageReport = ConcertPackageLoadReport(
                events = sampleConcertEvents,
                items = emptyList(),
                fallbackUsed = false
            ),
            importValidationReport = invalidReport,
            message = "remote"
        ).toReadinessStatus()

        assertEquals(RepositoryReadinessSeverity.Blocked, invalid.severity)
    }

    @Test
    fun repositoryReadinessWarnsForFallbackAndPassesApprovedLocalData() {
        val fallback = ConcertRepositoryResult(
            source = ConcertRepositorySource.FallbackSample,
            packageReport = fallbackConcertPackageLoadReport(sampleConcertEvents),
            importValidationReport = null,
            message = "fallback"
        ).toReadinessStatus()

        val ready = ConcertRepositoryResult(
            source = ConcertRepositorySource.LocalAsset,
            packageReport = ConcertPackageLoadReport(
                events = sampleConcertEvents,
                items = emptyList(),
                fallbackUsed = false
            ),
            importValidationReport = samplePartnerImportValidationReport(),
            message = "local"
        ).toReadinessStatus()

        assertEquals(RepositoryReadinessSeverity.Warning, fallback.severity)
        assertEquals(RepositoryReadinessSeverity.Ready, ready.severity)
    }
}
