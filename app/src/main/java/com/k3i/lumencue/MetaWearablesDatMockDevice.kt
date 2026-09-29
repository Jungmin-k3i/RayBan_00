package com.k3i.lumencue

import android.content.Context
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.selectors.AutoDeviceSelector
import com.meta.wearable.dat.core.session.DeviceSession
import com.meta.wearable.dat.core.session.DeviceSessionState
import com.meta.wearable.dat.core.types.Device
import com.meta.wearable.dat.core.types.LinkState
import com.meta.wearable.dat.core.types.WearablesError
import com.meta.wearable.dat.mockdevice.MockDeviceKit
import com.meta.wearable.dat.mockdevice.api.GlassesModel
import com.meta.wearable.dat.mockdevice.api.MockDeviceKitConfig
import com.meta.wearable.dat.mockdevice.api.MockDeviceKitInterface
import com.meta.wearable.dat.mockdevice.api.MockGlasses
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

enum class MetaWearablesMockDevicePhase {
    NotInitialized,
    Initializing,
    SessionStarted,
    DisplayUnsupported,
    Failed
}

data class MetaWearablesMockDeviceRuntimeState(
    val phase: MetaWearablesMockDevicePhase,
    val sdkVersion: String = "0.9.0",
    val kitEnabled: Boolean = false,
    val devicePaired: Boolean = false,
    val sessionStarted: Boolean = false,
    val displayCapable: Boolean = false,
    val deviceName: String? = null,
    val message: String
) {
    companion object {
        fun notInitialized() = MetaWearablesMockDeviceRuntimeState(
            phase = MetaWearablesMockDevicePhase.NotInitialized,
            message = "DAT MockDeviceKit 초기화 전"
        )

        fun initializing() = MetaWearablesMockDeviceRuntimeState(
            phase = MetaWearablesMockDevicePhase.Initializing,
            message = "DAT MockDeviceKit 초기화 및 세션 연결 중"
        )
    }
}

/**
 * Boots the real Meta DAT MockDeviceKit and opens a real SDK device session.
 *
 * DAT 0.9.0 does not expose a META_RAYBAN_DISPLAY value through GlassesModel,
 * so the current mock can validate SDK initialization, pairing, connectivity,
 * and session lifecycle but cannot accept Display content. That limitation is
 * surfaced to the renderer instead of reporting a synthetic success.
 */
class MetaWearablesDatMockDeviceGateway(context: Context) {
    private val appContext = context.applicationContext
    private var mockDeviceKit: MockDeviceKitInterface? = null
    private var deviceSession: DeviceSession? = null

    suspend fun connect(): MetaWearablesMockDeviceRuntimeState {
        val initialization = Wearables.initialize(appContext)
        val initializationError = initialization.errorOrNull()
        if (initialization.isFailure && initializationError != WearablesError.ALREADY_INITIALIZED) {
            return failedState(
                message = "DAT SDK 초기화 실패: ${initializationError?.description ?: "알 수 없는 오류"}"
            )
        }

        return runCatching {
            val kit = MockDeviceKit.getInstance(appContext).also { mockDeviceKit = it }
            if (!kit.isEnabled) {
                kit.enable(
                    MockDeviceKitConfig(
                        initiallyRegistered = true,
                        initialPermissionsGranted = true
                    )
                )
            }

            val glasses = kit.pairedDevices
                .filterIsInstance<MockGlasses>()
                .firstOrNull()
                ?: kit.pairGlasses(GlassesModel.META_GLASSES).getOrThrow()

            glasses.powerOn()
            glasses.unfold()
            glasses.don()

            val metadata = awaitConnectedDevice(glasses)
            val session = Wearables.createSession(AutoDeviceSelector()).getOrThrow()
                .also { deviceSession = it }
            session.start()

            val sessionState = withTimeoutOrNull(8_000) {
                session.state.first { state ->
                    state == DeviceSessionState.STARTED || state == DeviceSessionState.STOPPED
                }
            }
            val sessionStarted = sessionState == DeviceSessionState.STARTED
            val displayCapable = metadata?.isDisplayCapable() == true
            val deviceName = metadata?.name ?: GlassesModel.META_GLASSES.displayName

            when {
                !sessionStarted -> failedState(
                    kitEnabled = kit.isEnabled,
                    devicePaired = true,
                    deviceName = deviceName,
                    message = "DAT MockDevice 세션 시작 실패: ${sessionState?.name ?: "시간 초과"}"
                )

                !displayCapable -> MetaWearablesMockDeviceRuntimeState(
                    phase = MetaWearablesMockDevicePhase.DisplayUnsupported,
                    kitEnabled = kit.isEnabled,
                    devicePaired = true,
                    sessionStarted = true,
                    displayCapable = false,
                    deviceName = deviceName,
                    message = "DAT MockDevice 세션 연결 성공 · SDK 0.9.0에는 Ray-Ban Display 모의 모델이 없어 HUD는 스마트폰 미리보기로 대체"
                )

                else -> MetaWearablesMockDeviceRuntimeState(
                    phase = MetaWearablesMockDevicePhase.SessionStarted,
                    kitEnabled = kit.isEnabled,
                    devicePaired = true,
                    sessionStarted = true,
                    displayCapable = true,
                    deviceName = deviceName,
                    message = "DAT MockDevice 디스플레이 세션 준비 완료"
                )
            }
        }.getOrElse { error ->
            failedState(
                kitEnabled = mockDeviceKit?.isEnabled == true,
                devicePaired = mockDeviceKit?.pairedDevices?.isNotEmpty() == true,
                message = "DAT MockDevice 연결 실패: ${error.message ?: error::class.java.simpleName}"
            )
        }
    }

    fun close() {
        runCatching { deviceSession?.stop() }
        runCatching { mockDeviceKit?.disable() }
        deviceSession = null
        mockDeviceKit = null
    }

    private suspend fun awaitConnectedDevice(glasses: MockGlasses): Device? {
        repeat(50) {
            val metadata = Wearables.devicesMetadata[glasses.deviceIdentifier]?.value
            if (metadata?.linkState == LinkState.CONNECTED) return metadata
            delay(100)
        }
        return Wearables.devicesMetadata[glasses.deviceIdentifier]?.value
    }

    private fun failedState(
        kitEnabled: Boolean = false,
        devicePaired: Boolean = false,
        deviceName: String? = null,
        message: String
    ) = MetaWearablesMockDeviceRuntimeState(
        phase = MetaWearablesMockDevicePhase.Failed,
        kitEnabled = kitEnabled,
        devicePaired = devicePaired,
        sessionStarted = false,
        displayCapable = false,
        deviceName = deviceName,
        message = message
    )
}
