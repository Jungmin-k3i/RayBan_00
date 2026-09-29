package com.k3i.lumencue

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.ceil
import kotlin.math.sin

private enum class PreviewSource(val label: String, val effect: HudEffect? = null) {
    Live("현재 HUD"),
    Caption("자막 예시", HudEffect.Caption),
    Countdown("카운트다운 예시", HudEffect.Countdown),
    Wave("웨이브 예시", HudEffect.EdgePulse),
    Energy("에너지 예시", HudEffect.EnergyMeter)
}

/** A local rendering of the outgoing instruction, never a stream from physical glasses. */
@Composable
fun GlassesScreenPreview(hudState: HudState) {
    var source by rememberSaveable { mutableStateOf(PreviewSource.Live) }
    var frozenInstruction by remember { mutableStateOf<HudRenderInstruction?>(null) }
    var showGuides by rememberSaveable { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val liveInstruction = hudState.toRenderInstruction()
    val instruction = frozenInstruction ?: when (source) {
        PreviewSource.Live -> liveInstruction
        else -> previewSample(hudState, source)
    }
    val owner = LocalLifecycleOwner.current
    var foreground by remember(owner) {
        mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, _ ->
            foreground = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    // Energy updates must not restart the clock. A new cue/text starts a new cycle.
    var elapsedMillis by remember(source, instruction.effect, instruction.placement,
        instruction.textKo, instruction.textEn, instruction.durationMillis) { mutableFloatStateOf(0f) }
    val running = frozenInstruction == null && foreground
    LaunchedEffect(running, source, instruction.effect, instruction.placement,
        instruction.textKo, instruction.textEn, instruction.durationMillis) {
        if (!running) return@LaunchedEffect
        var previous = withFrameMillis { it }
        while (true) {
            withFrameMillis { now ->
                elapsedMillis = (elapsedMillis + (now - previous).coerceAtMost(100)) %
                    instruction.durationMillis.coerceAtLeast(1)
                previous = now
            }
        }
    }

    val toggleFreeze = {
        frozenInstruction = if (frozenInstruction == null) instruction else null
    }
    val selectSource: (PreviewSource) -> Unit = { source = it; frozenInstruction = null }
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF151E24)),
        shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("글래스 화면 미리보기", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("전송할 HUD를 앱에서 재현합니다 · 실제 기기 화면 수신 아님",
                color = Color(0xFFB8BDC7), fontSize = 12.sp)
            PreviewSources(source, selectSource)
            PreviewStatus(source, frozenInstruction != null)
            GlassesPreviewSurface(instruction, elapsedMillis, showGuides, Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = toggleFreeze, modifier = Modifier.weight(1f)) {
                    Text(if (frozenInstruction == null) "화면 고정" else "실시간 재개")
                }
                OutlinedButton(onClick = { expanded = true }, modifier = Modifier.weight(1f)) {
                    Text("전체 화면")
                }
            }
            FilterChip(selected = showGuides, onClick = { showGuides = !showGuides },
                label = { Text("배치 가이드") })
            Text("${instruction.effect.label} · ${instruction.placement.label} · " +
                "${instruction.durationMillis / 1000f}초 반복 미리보기",
                color = Color(0xFF9CA3AF), fontSize = 11.sp)
        }
    }
    if (expanded) {
        Dialog(onDismissRequest = { expanded = false }, properties = DialogProperties(
            usePlatformDefaultWidth = false, decorFitsSystemWindows = false
        )) {
            Column(Modifier.fillMaxSize().background(Color(0xFF080D12)).safeDrawingPadding().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("글래스 화면 미리보기", color = Color.White, fontWeight = FontWeight.Bold)
                        Text("앱 내 재현 · 실제 기기 화면 수신 아님", color = Color(0xFFB8BDC7), fontSize = 11.sp)
                    }
                    OutlinedButton(onClick = { expanded = false }) { Text("닫기") }
                }
                PreviewSources(source, selectSource)
                PreviewStatus(source, frozenInstruction != null)
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    // Keep one logical viewport and letterbox, including in landscape.
                    val viewportWidth = minOf(maxWidth, maxHeight * (16f / 9f))
                    GlassesPreviewSurface(instruction, elapsedMillis, showGuides, Modifier.width(viewportWidth))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = toggleFreeze) {
                        Text(if (frozenInstruction == null) "화면 고정" else "실시간 재개")
                    }
                    FilterChip(selected = showGuides, onClick = { showGuides = !showGuides },
                        label = { Text("배치 가이드") })
                }
                Text("내용·배치·애니메이션 검수용이며 실제 렌즈의 광학 특성을 재현하지 않습니다.",
                    color = Color(0xFF9CA3AF), fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun PreviewSources(selected: PreviewSource, onSelect: (PreviewSource) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PreviewSource.entries.forEach { item ->
            FilterChip(selected = item == selected, onClick = { onSelect(item) }, label = { Text(item.label) })
        }
    }
}

@Composable
private fun PreviewStatus(source: PreviewSource, frozen: Boolean) {
    Text(when {
        frozen -> "고정된 화면 · 새 HUD 반영 일시 중지"
        source == PreviewSource.Live -> "LIVE · 현재 앱 HUD와 동기화"
        else -> "예시 재생 · 실제 공연·전송 데이터는 변경하지 않음"
    }, color = Color(0xFF62D6C4), fontSize = 12.sp)
}

private fun previewSample(state: HudState, source: PreviewSource): HudRenderInstruction = state.copy(
    effect = requireNotNull(source.effect),
    placement = when (source) {
        PreviewSource.Countdown -> HudPlacement.RightCorner
        PreviewSource.Wave -> HudPlacement.PeripheralPulse
        else -> HudPlacement.LowerEdge
    },
    primaryKo = when (source) {
        PreviewSource.Caption -> "지금 이 순간을 함께 기억해요"
        PreviewSource.Countdown -> "곧 포토 타임이 시작돼요"
        PreviewSource.Wave -> "음악에 맞춰 응원봉을 흔들어요"
        else -> "우리의 응원 에너지"
    },
    primaryEn = when (source) {
        PreviewSource.Caption -> "Let's remember this moment together"
        PreviewSource.Countdown -> "Get ready for photo time"
        PreviewSource.Wave -> "Wave your light stick with the music"
        else -> "Together, we light up the stage"
    },
    secondary = "미리보기 예시", accessibleSummary = "글래스 HUD 시각 검수 예시",
    energyPercent = 72, durationMillis = 6000
).toRenderInstruction()

@Composable
private fun GlassesPreviewSurface(
    instruction: HudRenderInstruction,
    elapsedMillis: Float,
    showGuides: Boolean,
    modifier: Modifier = Modifier
) {
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    Canvas(modifier.aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).background(Color.Black)
        .testTag("glasses-preview-surface")
        .semantics { contentDescription = "HUD 미리보기: ${instruction.textKo}. ${instruction.textEn}" }) {
        val canvas = drawContext.canvas.nativeCanvas
        val saved = canvas.save()
        // Logical preview coordinates, not a claim about the physical lens resolution.
        canvas.scale(size.width / 640f, size.height / 360f)
        val scene = instruction.visualScene
        val accent = when (scene.colorToken) {
            "amber" -> 0xFFFFD166.toInt()
            "pink" -> 0xFFE85D9E.toInt()
            "lime" -> 0xFFA8E66B.toInt()
            else -> 0xFF62D6C4.toInt()
        }
        val right = instruction.placement == HudPlacement.RightCorner
        val left = if (right) 344f else 28f
        val top = if (right) 120f else 230f
        val width = if (right) 268f else 584f
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f
        if (showGuides) {
            paint.color = 0x8052AEAA.toInt()
            canvas.drawRoundRect(16f, 16f, 624f, 344f, 16f, 16f, paint)
            canvas.drawRoundRect(left, top, left + width, top + 110f, 10f, 10f, paint)
        }
        if (instruction.effect == HudEffect.EdgePulse) {
            val pulse = (0.5 + 0.5 * sin(elapsedMillis / 450.0)).toFloat()
            paint.color = accent
            paint.alpha = (75 + 170 * pulse).toInt()
            paint.strokeWidth = 3f + 5f * pulse
            canvas.drawRoundRect(10f, 10f, 630f, 350f, 22f, 22f, paint)
        }
        paint.alpha = 255
        when (instruction.effect) {
            HudEffect.Countdown -> {
                val progress = 1f - elapsedMillis / instruction.durationMillis.coerceAtLeast(1)
                val centerX = if (right) 478f else 320f
                val centerY = if (right) 64f else 147f
                paint.color = 0xFF263038.toInt()
                paint.strokeWidth = 5f
                canvas.drawCircle(centerX, centerY, 38f, paint)
                paint.color = accent
                canvas.drawArc(centerX - 38f, centerY - 38f, centerX + 38f, centerY + 38f,
                    -90f, 360f * progress.coerceIn(0f, 1f), false, paint)
                paint.style = Paint.Style.FILL
                paint.textAlign = Paint.Align.CENTER
                paint.textSize = 32f
                paint.typeface = Typeface.DEFAULT_BOLD
                canvas.drawText(ceil((instruction.durationMillis - elapsedMillis) / 1000f).toInt().coerceAtLeast(1).toString(),
                    centerX, centerY + 11f, paint)
            }
            HudEffect.EnergyMeter -> {
                val energy = scene.progressPercent.coerceIn(0, 100)
                val barTop = top - 36f
                paint.style = Paint.Style.FILL
                paint.color = 0xFF263038.toInt()
                canvas.drawRoundRect(left, barTop, left + width - 70f, barTop + 12f, 6f, 6f, paint)
                paint.color = accent
                canvas.drawRoundRect(left, barTop, left + (width - 70f) * energy / 100f, barTop + 12f, 6f, 6f, paint)
                paint.textSize = 22f
                paint.textAlign = Paint.Align.RIGHT
                paint.typeface = Typeface.DEFAULT_BOLD
                canvas.drawText("$energy%", left + width, barTop + 14f, paint)
            }
            else -> Unit
        }
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.LEFT
        paint.color = 0xD9121B22.toInt()
        canvas.drawRoundRect(left, top, left + width, top + 110f, 12f, 12f, paint)
        paint.color = accent
        canvas.drawRoundRect(left, top + 12f, left + 3f, top + 98f, 1.5f, 1.5f, paint)
        paint.color = android.graphics.Color.WHITE
        paint.textSize = if (right) 22f else 27f
        paint.typeface = Typeface.DEFAULT_BOLD
        val textWidth = width - 28f
        drawPreviewText(canvas, paint, instruction.textKo, left + 14f, top + 31f, textWidth, 2, 29f)
        paint.color = accent
        paint.textSize = if (right) 15f else 18f
        paint.typeface = Typeface.DEFAULT
        drawPreviewText(canvas, paint, instruction.textEn, left + 14f, top + 90f, textWidth, 1, 20f)
        canvas.restoreToCount(saved)
    }
}

private fun drawPreviewText(canvas: android.graphics.Canvas, paint: Paint, text: String,
    x: Float, baseline: Float, width: Float, maxLines: Int, lineHeight: Float) {
    var remaining = text.replace('\n', ' ').trim()
    repeat(maxLines) { index ->
        if (remaining.isEmpty()) return
        val fits = paint.breakText(remaining, true, width, null).coerceAtLeast(1)
        val last = index == maxLines - 1
        val line = if (last && fits < remaining.length) {
            val count = paint.breakText(remaining, true, width - paint.measureText("…"), null)
            remaining.take(count) + "…"
        } else remaining.take(fits)
        canvas.drawText(line, x, baseline + index * lineHeight, paint)
        remaining = remaining.drop(fits).trimStart()
    }
}
