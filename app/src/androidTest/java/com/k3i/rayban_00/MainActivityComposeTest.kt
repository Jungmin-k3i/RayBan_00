package com.k3i.rayban_00

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class MainActivityComposeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeShowsConcertPackageStatusAndReadinessEntry() {
        composeRule.onNodeWithText("콘서트 AR Companion").assertIsDisplayed()
        composeRule.onNodeWithText("공연 패키지 로드 상태").assertIsDisplayed()
        composeRule.onNodeWithText("준비 점검").assertIsDisplayed()
    }

    @Test
    fun readinessScreenShowsRequiredChecks() {
        composeRule.onNodeWithText("준비 점검").performClick()

        composeRule.onNodeWithText("공연 패키지").assertIsDisplayed()
        composeRule.onNodeWithText("티켓 입장").assertIsDisplayed()
        composeRule.onNodeWithText("안전 확인").assertIsDisplayed()
        composeRule.onNodeWithText("Ray-Ban Display").assertIsDisplayed()
    }

    @Test
    fun detailScreenShowsPlatformAndConsentSections() {
        composeRule.onNodeWithText("공연 정보 열기").performClick()

        composeRule.onNodeWithText("개인정보 및 현장 안전 확인").assertIsDisplayed()
        composeRule.onNodeWithText("공연장 고지문/동의 문안 초안").assertIsDisplayed()
        composeRule.onNodeWithText("촬영/녹음 정책").assertIsDisplayed()
    }
}
