package com.k3i.lumencue

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
    onInteractionEvent: (ConcertInteractionEvent) -> Unit
) {
    ScreenFrame {
        AudienceHomeHero(state = state, glassesProfile = glassesProfile)
        NowPlayingCard(state = state)
        SpotlightActionCard(state = state, onInteractionEvent = onInteractionEvent)
    }
}

@Composable
private fun AudienceHomeHero(
    state: ConcertState,
    glassesProfile: GlassesIntegrationProfile
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(330.dp)
            .clip(RoundedCornerShape(8.dp))
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
                            Color(0x33050607),
                            Color(0x22050607),
                            Color(0xF2050607)
                        )
                    )
                )
        )
        LiveStageArtwork(state)
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LivePill("LIVE", Color(0xFFFFD166))
            LivePill(glassesProfile.targetDevice.displayName, Color(0xFF62D6C4))
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    state.event.title,
                    color = Color.White,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 33.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    state.event.venueInfo.name,
                    color = Color(0xFFD1D5DB),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HeroMetric(label = "SEAT", value = state.event.venueInfo.seat, color = Color(0xFF62D6C4), modifier = Modifier.weight(1f))
                HeroMetric(
                    label = "CHECK",
                    value = if (state.event.ticket.checkedIn) "입장 완료" else "확인 필요",
                    color = if (state.event.ticket.checkedIn) Color(0xFF9AE6B4) else Color(0xFFFFD166),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun NowPlayingCard(state: ConcertState) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF15181F)), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("NOW", color = Color(0xFF62D6C4), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(state.currentTrack.title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(state.activeCue.titleKo, color = Color(0xFFB8BDC7), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                EnergyRing(energy = state.fanEnergy)
            }
            HomeTrackProgress(state)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CompactCueTile("현재", state.activeCue.hudMessageKo, Color(0xFFFFD166), Modifier.weight(1f))
                CompactCueTile("다음", state.nextCue?.titleKo ?: "마지막 큐", Color(0xFF8AB4F8), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SpotlightActionCard(
    state: ConcertState,
    onInteractionEvent: (ConcertInteractionEvent) -> Unit
) {
    val featuredEvent = state.currentFeaturedInteractionEvent
    val eventColor = featuredEvent?.type?.interactionColor() ?: state.activeCue.effect.interactionColor()
    val participation = featuredEvent?.let { state.interactionEventParticipationCounts[it.id] ?: 0 } ?: 0

    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF10161D)), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                LightstickPreview(color = eventColor, energy = state.fanEnergy, modifier = Modifier.weight(0.72f))
                Column(modifier = Modifier.weight(1.28f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    LivePill(featuredEvent?.type?.label ?: "대기", eventColor)
                    Text(
                        featuredEvent?.titleKo ?: "지금은 무대 집중",
                        color = Color.White,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        featuredEvent?.messageKo ?: "다음 참여 타이밍이 오면 바로 띄워줄게요.",
                        color = Color(0xFFB8BDC7),
                        fontSize = 13.sp,
                        lineHeight = 17.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (featuredEvent != null) {
                        Text("내 참여 ${participation}회", color = Color(0xFFFFD166), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Button(
                onClick = { featuredEvent?.let(onInteractionEvent) },
                enabled = featuredEvent != null,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = eventColor,
                    disabledContainerColor = Color(0xFF242933),
                    disabledContentColor = Color(0xFF8B949E)
                )
            ) {
                Text(featuredEvent?.ctaLabel ?: "대기 중", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            UpcomingStrip(state)
        }
    }
}

@Composable
private fun UpcomingStrip(state: ConcertState) {
    val upcomingEvents = state.upcomingInteractionEvents.take(2)
    if (upcomingEvents.isEmpty()) return

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        upcomingEvents.forEach { event ->
            val remaining = if (event.trackIndex == state.trackIndex) {
                "${formatTime((event.startSecond - state.elapsedSeconds).coerceAtLeast(0))} 후"
            } else {
                "다음 곡"
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1A1F27))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(remaining, color = event.type.interactionColor(), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(event.titleKo, color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private fun ConcertInteractionEventType.interactionColor(): Color =
    when (this) {
        ConcertInteractionEventType.CallAndResponse -> Color(0xFFE85D75)
        ConcertInteractionEventType.FanChant -> Color(0xFFFFD166)
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
            .height(152.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF1B1F28), Color(0xFF0B0D12)))),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val center = Offset(size.width * 0.5f, size.height * 0.35f)
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
            Text("${(state.progress * 100).toInt().coerceIn(0, 100)}%", color = Color(0xFFFFD166), fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                    .background(Brush.horizontalGradient(listOf(Color(0xFF62D6C4), Color(0xFFE85D75), Color(0xFFFFD166))))
            )
        }
    }
}

@Composable
private fun LiveStageArtwork(state: ConcertState) {
    Canvas(modifier = Modifier.fillMaxWidth().height(240.dp)) {
        val width = size.width
        val height = size.height
        val energy = state.fanEnergy / 100f

        repeat(18) { index ->
            val x = width * (index + 1) / 19f
            val barHeight = 18f + ((index * 17 + state.fanEnergy) % 80)
            drawLine(
                color = when (index % 4) {
                    0 -> Color(0xFFFFD166).copy(alpha = 0.66f)
                    1 -> Color(0xFF62D6C4).copy(alpha = 0.58f)
                    2 -> Color(0xFFE85D75).copy(alpha = 0.54f)
                    else -> Color(0xFF8AB4F8).copy(alpha = 0.52f)
                },
                start = Offset(x, height - 22f),
                end = Offset(x, height - 22f - barHeight),
                strokeWidth = 7f,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        }
        drawCircle(
            color = Color(0x33FFD166),
            radius = 42f + energy * 54f,
            center = Offset(width * 0.5f, height * 0.62f),
            style = Stroke(width = 5f)
        )
    }
}

@Composable
private fun LivePill(label: String, color: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xC7050607))
            .padding(horizontal = 9.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(color))
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun HeroMetric(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xE31B1F27))
            .padding(11.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(label, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun CompactCueTile(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF20252E))
            .padding(11.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text(value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun EnergyRing(energy: Int) {
    Box(modifier = Modifier.size(66.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.matchParentSize()) {
            drawCircle(Color(0xFF2A303A), style = Stroke(width = 8f))
            drawArc(
                color = Color(0xFFFFD166),
                startAngle = -90f,
                sweepAngle = 360f * (energy / 100f).coerceIn(0f, 1f),
                useCenter = false,
                style = Stroke(width = 8f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(energy.toString(), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("HYPE", color = Color(0xFFFFD166), fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
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
