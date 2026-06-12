package com.k3i.rayban_00

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HomeScreen(
    state: ConcertState,
    glassesProfile: GlassesIntegrationProfile,
    onReaction: (ReactionSignal) -> Unit,
    onInteractionEvent: (ConcertInteractionEvent) -> Unit
) {
    ScreenFrame {
        AudienceHomeHero(
            state = state,
            glassesProfile = glassesProfile
        )
        LiveSyncPanel(
            state = state,
            onReaction = onReaction,
            onInteractionEvent = onInteractionEvent
        )
    }
}

@Composable
private fun AudienceHomeHero(
    state: ConcertState,
    glassesProfile: GlassesIntegrationProfile
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131418)),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(310.dp)
                .background(Color(0xFF111418))
        ) {
            Image(
                painter = painterResource(R.drawable.concert_hero_youth),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0x66050607),
                                Color(0x22050607),
                                Color(0xEE050607)
                            )
                        )
                    )
            )
            LiveStageArtwork(state)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    LivePill("LIVE", Color(0xFFFFD166))
                    LivePill(glassesProfile.targetDevice.displayName, Color(0xFF62D6C4))
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        state.event.title,
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${state.event.venueInfo.name} · ${state.event.venueInfo.seat}",
                        color = Color(0xFFD1D5DB),
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HomeInfoBadge(
                        label = "내 좌석",
                        value = state.event.venueInfo.seat,
                        color = Color(0xFF62D6C4),
                        modifier = Modifier.weight(1f)
                    )
                    HomeInfoBadge(
                        label = "입장 상태",
                        value = if (state.event.ticket.checkedIn) "확인 완료" else "입장 확인 필요",
                        color = Color(0xFFFFD166),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveSyncPanel(
    state: ConcertState,
    onReaction: (ReactionSignal) -> Unit,
    onInteractionEvent: (ConcertInteractionEvent) -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF171A20)), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("지금 공연", color = Color(0xFF62D6C4), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(state.currentTrack.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(state.currentTrack.artist, color = Color(0xFFB8BDC7), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF232733)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("${state.trackIndex + 1}/${state.tracks.size}", color = Color(0xFFFFD166), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
            HomeTrackProgress(state)
            InteractionEventPanel(
                state = state,
                onReaction = onReaction,
                onInteractionEvent = onInteractionEvent
            )
        }
    }
}

@Composable
private fun InteractionEventPanel(
    state: ConcertState,
    onReaction: (ReactionSignal) -> Unit,
    onInteractionEvent: (ConcertInteractionEvent) -> Unit
) {
    val featuredEvent = state.currentFeaturedInteractionEvent
    val upcomingEvents = state.upcomingInteractionEvents.take(3)
    val lightstickColor = featuredEvent?.type?.interactionColor() ?: state.activeCue.effect.interactionColor()
    val featuredParticipationCount = featuredEvent?.let { state.interactionEventParticipationCounts[it.id] ?: 0 } ?: 0
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF111820)), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("라이브 참여 이벤트", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("공연 흐름에 맞춰 지금 참여할 타이밍을 알려줍니다.", color = Color(0xFF9CA3AF), fontSize = 11.sp)
                }
                Text(featuredEvent?.type?.label ?: "대기", color = lightstickColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                LightstickPreview(color = lightstickColor, energy = state.fanEnergy, modifier = Modifier.weight(0.82f))
                Column(modifier = Modifier.weight(1.18f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        featuredEvent?.titleKo ?: "다음 참여 이벤트 대기",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        featuredEvent?.let { "${it.messageKo} · ${it.zone}" }
                            ?: "현재 곡의 승인된 참여 이벤트가 아직 없습니다.",
                        color = Color(0xFFB8BDC7),
                        fontSize = 12.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (featuredEvent != null) {
                        Text(
                            "내 참여 ${featuredParticipationCount}회",
                            color = Color(0xFFFFD166),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { featuredEvent?.let(onInteractionEvent) },
                            enabled = featuredEvent != null,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = lightstickColor,
                                disabledContainerColor = Color(0xFF2B3038),
                                disabledContentColor = Color(0xFF8B949E)
                            )
                        ) {
                            Text(featuredEvent?.ctaLabel ?: "대기", fontSize = 12.sp)
                        }
                        Button(
                            onClick = { onReaction(ReactionSignal.Cheer) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE85D75))
                        ) {
                            Text("응원", fontSize = 12.sp)
                        }
                    }
                }
            }
            if (upcomingEvents.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("다음 이벤트", color = Color(0xFFB8BDC7), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    upcomingEvents.forEach { event ->
                        InteractionEventRow(event = event, currentTrackIndex = state.trackIndex, elapsedSeconds = state.elapsedSeconds)
                    }
                }
            }
        }
    }
}

@Composable
private fun InteractionEventRow(
    event: ConcertInteractionEvent,
    currentTrackIndex: Int,
    elapsedSeconds: Int
) {
    val remaining = if (event.trackIndex == currentTrackIndex) {
        "${formatTime((event.startSecond - elapsedSeconds).coerceAtLeast(0))} 후"
    } else {
        "다음 곡"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1A1F27))
            .padding(horizontal = 10.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(event.type.interactionColor())
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(event.titleKo, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(event.type.label, color = Color(0xFF9CA3AF), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(remaining, color = Color(0xFFFFD166), fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

private fun ConcertInteractionEventType.interactionColor(): Color =
    when (this) {
        ConcertInteractionEventType.CallAndResponse -> Color(0xFFE85D75)
        ConcertInteractionEventType.FanChant -> Color(0xFFB7791F)
        ConcertInteractionEventType.LightstickWave -> Color(0xFF2F80ED)
        ConcertInteractionEventType.Surprise -> Color(0xFFB794F4)
        ConcertInteractionEventType.EncoreGauge -> Color(0xFFFFD166)
        ConcertInteractionEventType.CameraPolicy -> Color(0xFFFF8A65)
        ConcertInteractionEventType.PhotoCountdown -> Color(0xFF62D6C4)
        ConcertInteractionEventType.MerchBooth -> Color(0xFF8AB4F8)
        ConcertInteractionEventType.ExitFlow -> Color(0xFF9AE6B4)
        ConcertInteractionEventType.FanMission -> Color(0xFFE24A68)
        ConcertInteractionEventType.SetlistHint -> Color(0xFF62D6C4)
        ConcertInteractionEventType.Translation -> Color(0xFF8AB4F8)
    }

private fun HudEffect.interactionColor(): Color =
    when (this) {
        HudEffect.Caption -> Color(0xFF62D6C4)
        HudEffect.Countdown -> Color(0xFFFFD166)
        HudEffect.EdgePulse -> Color(0xFFE85D75)
        HudEffect.EnergyMeter -> Color(0xFF8AB4F8)
    }

@Composable
private fun LightstickPreview(
    color: Color,
    energy: Int,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(138.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF1B1F28), Color(0xFF0B0D12)))),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val center = Offset(size.width * 0.5f, size.height * 0.37f)
            val pulse = (energy / 100f).coerceIn(0.2f, 1f)
            drawCircle(color.copy(alpha = 0.14f), radius = 58f + pulse * 18f, center = center)
            drawCircle(color.copy(alpha = 0.32f), radius = 38f + pulse * 10f, center = center)
            drawCircle(color, radius = 24f, center = center)
            drawLine(
                color = Color.White.copy(alpha = 0.62f),
                start = Offset(center.x, center.y + 25f),
                end = Offset(center.x, size.height * 0.82f),
                strokeWidth = 10f,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
            drawLine(
                color = color.copy(alpha = 0.88f),
                start = Offset(center.x, size.height * 0.62f),
                end = Offset(center.x, size.height * 0.84f),
                strokeWidth = 4f,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        }
        Text("SYNC", modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp), color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun HomeTrackProgress(state: ConcertState) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${formatTime(state.elapsedSeconds)} / ${formatTime(state.currentTrack.durationSeconds)}", color = Color(0xFFB8BDC7), fontSize = 12.sp)
            Text("${state.trackIndex + 1}/${state.tracks.size}", color = Color(0xFFFFD166), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(9.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF2E333D))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(state.progress)
                    .height(9.dp)
                    .background(Brush.horizontalGradient(listOf(Color(0xFF62D6C4), Color(0xFFE85D75))))
            )
        }
    }
}

@Composable
private fun LiveStageArtwork(state: ConcertState) {
    Canvas(modifier = Modifier.fillMaxWidth().height(230.dp)) {
        val width = size.width
        val height = size.height
        val energy = state.fanEnergy / 100f

        val beam = Path().apply {
            moveTo(width * 0.18f, 0f)
            lineTo(width * 0.5f, height * 0.8f)
            lineTo(width * 0.82f, 0f)
            close()
        }
        drawPath(beam, Color.White.copy(alpha = 0.08f))

        repeat(18) { index ->
            val x = width * (index + 1) / 19f
            val barHeight = 20f + ((index * 17 + state.fanEnergy) % 75)
            drawLine(
                color = if (index % 3 == 0) Color(0xFFFFD166).copy(alpha = 0.72f) else Color(0xFF62D6C4).copy(alpha = 0.58f),
                start = Offset(x, height - 18f),
                end = Offset(x, height - 18f - barHeight),
                strokeWidth = 7f,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        }

        drawLine(
            color = Color.White.copy(alpha = 0.35f),
            start = Offset(width * 0.18f, height * 0.78f),
            end = Offset(width * 0.82f, height * 0.78f),
            strokeWidth = 3f
        )
        drawCircle(
            color = Color.Transparent,
            radius = 42f + energy * 16f,
            center = Offset(width * 0.5f, height * 0.68f),
            style = Stroke(width = 5f)
        )
    }
}

@Composable
private fun LivePill(label: String, color: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xBB050607))
            .padding(horizontal = 9.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(color))
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun HomeHudCueProgress(progress: Float) {
    val percent = (progress * 100).toInt().coerceIn(0, 100)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("현재 곡 진행", color = Color(0xFFE5E7EB), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text("$percent%", color = Color(0xFFFFD166), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF2E333D))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .height(8.dp)
                    .background(Brush.horizontalGradient(listOf(Color(0xFF62D6C4), Color(0xFF8AB4F8), Color(0xFFFFD166))))
            )
        }
    }
}

@Composable
private fun HomeInfoBadge(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xDD202329))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Text(
            value,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun GlassesIntegrationCard(profile: GlassesIntegrationProfile) {
    val primary = profile.primaryRenderer.status
    val mockDevice = profile.mockDeviceRenderer?.status
    val fallback = profile.fallbackRenderer.status
    Card(colors = darkCard(), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Ray-Ban Display 연동", color = Color.White, fontWeight = FontWeight.Bold)
            InfoRow("대상 기기", profile.targetDevice.displayName)
            InfoRow("디스플레이", if (profile.targetDevice.hasDisplay) "렌즈 HUD 사용 가능 전제" else "렌즈 HUD 없음")
            InfoRow("공식 경로", profile.targetDevice.integrationPath)
            InfoRow("Toolkit 상태", primary.message)
            InfoRow("MockDevice", mockDevice?.message ?: "MockDevice 렌더러 없음")
            InfoRow("현재 대체 출력", fallback.message)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color(0xFF9CA3AF), fontSize = 13.sp)
        Text(
            value,
            color = Color(0xFFE5E7EB),
            fontSize = 13.sp,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
