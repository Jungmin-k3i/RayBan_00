package com.k3i.lumencue

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.k3i.lumencue.ui.theme.LumenCueTheme
import org.junit.Rule
import org.junit.Test

class GlassesScreenPreviewTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun freezeKeepsOldFrameAndResumeUsesLatestHud() {
        val initial = ConcertState().hudState.copy(primaryKo = "첫 번째 자막", primaryEn = "First")
        val state = mutableStateOf(initial)
        rule.mainClock.autoAdvance = false
        rule.setContent { LumenCueTheme { GlassesScreenPreview(state.value) } }
        rule.mainClock.advanceTimeBy(32)
        rule.onNodeWithText("화면 고정").performClick()
        rule.mainClock.advanceTimeBy(32)
        rule.runOnIdle { state.value = initial.copy(primaryKo = "새 자막", primaryEn = "Latest") }
        rule.mainClock.advanceTimeBy(32)
        rule.onNodeWithTag("glasses-preview-surface")
            .assertContentDescriptionEquals("HUD 미리보기: 첫 번째 자막. First")
        rule.onNodeWithText("실시간 재개").performClick()
        rule.mainClock.advanceTimeBy(32)
        rule.onNodeWithTag("glasses-preview-surface")
            .assertContentDescriptionEquals("HUD 미리보기: 새 자막. Latest")
    }

    @Test
    fun sampleDoesNotReplaceLiveHudAndFullscreenCloses() {
        val state = ConcertState().hudState.copy(primaryKo = "공연 데이터", primaryEn = "Live data")
        rule.mainClock.autoAdvance = false
        rule.setContent { LumenCueTheme { GlassesScreenPreview(state) } }
        rule.mainClock.advanceTimeBy(32)
        rule.onNodeWithText("자막 예시").performScrollTo().performClick()
        rule.mainClock.advanceTimeBy(32)
        rule.onNodeWithText("예시 재생 · 실제 공연·전송 데이터는 변경하지 않음").assertIsDisplayed()
        rule.onNodeWithTag("glasses-preview-surface")
            .assertContentDescriptionEquals("HUD 미리보기: 지금 이 순간을 함께 기억해요. Let's remember this moment together")
        rule.onNodeWithText("현재 HUD").performScrollTo().performClick()
        rule.mainClock.advanceTimeBy(32)
        rule.onNodeWithTag("glasses-preview-surface")
            .assertContentDescriptionEquals("HUD 미리보기: 공연 데이터. Live data")
        rule.onNodeWithText("전체 화면").performClick()
        rule.mainClock.advanceTimeBy(32)
        rule.onNodeWithText("닫기").assertIsDisplayed().performClick()
        rule.mainClock.advanceTimeBy(32)
        rule.onNodeWithText("닫기").assertDoesNotExist()
        rule.onNodeWithText("전체 화면").assertIsDisplayed()
    }
}
