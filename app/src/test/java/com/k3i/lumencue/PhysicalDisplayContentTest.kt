package com.k3i.lumencue

import org.junit.Assert.*
import org.junit.Test

class PhysicalDisplayContentTest {
    @Test fun displayOmitsSceneAndAccessibilityMetadataAndLimitsCaptions() {
        val document = ToolkitDisplayDocument("test", TargetGlassesDevice.RayBanDisplay, 5000, HudPriority.Normal, listOf(
            ToolkitDisplayElement("scene", ToolkitDisplayElementType.List, "internal scene"),
            ToolkitDisplayElement("a11y", ToolkitDisplayElementType.Text, "screen reader", mapOf("accessibility" to "true")),
            ToolkitDisplayElement("ko", ToolkitDisplayElementType.Text, "  안내  "),
            ToolkitDisplayElement("duplicate", ToolkitDisplayElementType.Text, "안내"),
            ToolkitDisplayElement("en", ToolkitDisplayElementType.Text, "x".repeat(200)),
            ToolkitDisplayElement("overflow", ToolkitDisplayElementType.Text, "extra")
        ))
        assertEquals(listOf("안내", "x".repeat(160)), document.physicalDisplayLines())
    }

    @Test fun readyConnectionAloneDoesNotClaimSuccessfulDelivery() {
        val instruction = ConcertState().hudState.toRenderInstruction()
        val profile = physicalProfile()
        val report = dispatchHudToGlasses(instruction, profile)
        assertFalse(report.primaryResult.accepted)
        assertEquals(GlassesDispatchRoute.FallbackPreview, report.route)
    }

    @Test fun onlyConfirmedSdkDeliveryUsesPhysicalRoute() {
        val instruction = ConcertState().hudState.toRenderInstruction()
        val profile = physicalProfile()
        val confirmed = GlassesRenderResult(true, "MetaWearablesToolkitHudRenderer", profile.primaryRenderer.status, instruction)
        val report = dispatchHudToGlasses(instruction, profile, confirmedPrimaryResult = confirmed)
        assertEquals(GlassesDispatchRoute.PrimaryToolkit, report.route)
        assertTrue(report.primaryResult.accepted)
        assertNull(report.fallbackResult)
        assertFalse(report.fallbackPlan.active)
    }

    @Test fun rejectedSdkDeliveryFallsBackWithoutFalsePhysicalSuccess() {
        val instruction = ConcertState().hudState.toRenderInstruction()
        val profile = physicalProfile()
        val failed = GlassesRenderResult(false, "MetaWearablesToolkitHudRenderer", profile.primaryRenderer.status, instruction)
        val report = dispatchHudToGlasses(instruction, profile, confirmedPrimaryResult = failed)
        assertEquals(GlassesDispatchRoute.FallbackPreview, report.route)
        assertFalse(report.primaryResult.accepted)
        assertTrue(report.fallbackPlan.active)
    }

    private fun physicalProfile() = GlassesIntegrationProfile(TargetGlassesDevice.RayBanDisplay, listOf(
        SmartphonePreviewHudRenderer(),
        MetaWearablesToolkitHudRenderer(GlassesRendererStatus(GlassesRendererAvailability.Ready, "connected"))
    ))
}
