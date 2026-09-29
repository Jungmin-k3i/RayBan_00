package com.k3i.lumencue

import android.app.Activity
import android.content.Context
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.selectors.SpecificDeviceSelector
import com.meta.wearable.dat.core.session.DeviceSession
import com.meta.wearable.dat.core.session.DeviceSessionState
import com.meta.wearable.dat.core.types.*
import com.meta.wearable.dat.display.Display
import com.meta.wearable.dat.display.addDisplay
import com.meta.wearable.dat.display.removeDisplay
import com.meta.wearable.dat.display.types.DisplayState
import com.meta.wearable.dat.display.views.TextStyle
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class PhysicalGlassesDevice(val id: DeviceIdentifier, val device: Device) {
    val canConnect: Boolean
        get() = device.linkState == LinkState.CONNECTED && device.isDisplayCapable() &&
            device.compatibility == DeviceCompatibility.COMPATIBLE
}

data class PhysicalGlassesState(
    val initialized: Boolean = false,
    val registered: Boolean = false,
    val registrationBusy: Boolean = false,
    val devices: List<PhysicalGlassesDevice> = emptyList(),
    val connecting: Boolean = false,
    val ready: Boolean = false,
    val sending: Boolean = false,
    val datUpdateRequired: Boolean = false,
    val message: String = "Meta AI 앱에서 안경 페어링과 개발자 모드를 설정한 뒤 앱을 등록하세요."
) {
    val rendererStatus: GlassesRendererStatus
        get() = GlassesRendererStatus(
            if (ready) GlassesRendererAvailability.Ready else GlassesRendererAvailability.WaitingForOfficialSdk,
            message
        )
}

/** Owns only real device sessions. MockDeviceKit must be stopped before initialize(). */
class MetaWearablesDisplayGateway(private val context: Context) {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow(PhysicalGlassesState())
    val state: StateFlow<PhysicalGlassesState> = mutableState.asStateFlow()
    private val metadataJobs = mutableMapOf<DeviceIdentifier, Job>()
    private val sessionJobs = mutableListOf<Job>()
    private var session: DeviceSession? = null
    private var display: Display? = null
    private var selected: DeviceIdentifier? = null
    private var clearJob: Job? = null
    private val sendMutex = Mutex()

    fun showMessage(message: String) { mutableState.update { it.copy(message = message) } }

    fun initialize(): Boolean {
        if (state.value.initialized) return true
        val result = Wearables.initialize(context.applicationContext)
        if (result.isFailure && result.errorOrNull() != WearablesError.ALREADY_INITIALIZED) {
            showMessage("Meta 초기화 실패: ${result.errorOrNull()?.description}")
            return false
        }
        mutableState.update { it.copy(initialized = true) }
        scope.launch {
            Wearables.registrationState.collect { registration ->
                val registered = registration == RegistrationState.REGISTERED
                if (!registered && session != null) disconnect()
                mutableState.update { it.copy(
                    registered = registered,
                    registrationBusy = registration == RegistrationState.REGISTERING || registration == RegistrationState.UNREGISTERING,
                    message = when (registration) {
                        RegistrationState.REGISTERED -> "Meta AI 앱 등록 완료 · 목록에서 안경을 선택하세요"
                        RegistrationState.REGISTERING -> "Meta AI 앱에서 연결을 승인하세요"
                        RegistrationState.UNREGISTERING -> "앱 등록 해제 중"
                        RegistrationState.UNAVAILABLE -> "Meta AI 앱 설치 및 안경 페어링을 확인하세요"
                        else -> "Meta AI 앱 등록이 필요합니다"
                    }
                ) }
            }
        }
        scope.launch {
            Wearables.registrationErrorStream.collect { showMessage("앱 등록 오류: ${it.description}") }
        }
        scope.launch {
            Wearables.devices.collect { ids ->
                (metadataJobs.keys - ids).forEach { id -> metadataJobs.remove(id)?.cancel() }
                mutableState.update { it.copy(devices = it.devices.filter { device -> device.id in ids }) }
                if (selected != null && selected !in ids) disconnect("안경 연결이 끊어졌습니다. 다시 연결하세요.")
                ids.forEach { id ->
                    if (metadataJobs[id] == null) {
                        Wearables.devicesMetadata[id]?.let { metadata ->
                            metadataJobs[id] = scope.launch {
                                metadata.collect { device ->
                                    val entry = PhysicalGlassesDevice(id, device)
                                    mutableState.update { it.copy(devices = it.devices.filterNot { d -> d.id == id } + entry) }
                                    if (id == selected && !entry.canConnect) disconnect("안경 연결 상태가 변경됐습니다. 업데이트 또는 연결 상태를 확인하세요.")
                                }
                            }
                        }
                    }
                }
            }
        }
        return true
    }

    fun register(activity: Activity) {
        if (!initialize()) return
        if (state.value.registered) { showMessage("등록되어 있습니다. 목록에서 안경을 선택하세요."); return }
        runCatching { Wearables.startRegistration(activity) }
            .onFailure { showMessage("Meta AI 앱을 열 수 없습니다: ${it.message}") }
    }

    fun unregister(activity: Activity) {
        disconnect()
        runCatching { Wearables.startUnregistration(activity) }
            .onFailure { showMessage("등록 해제 실패: ${it.message}") }
    }

    fun connect(device: PhysicalGlassesDevice) {
        if (!state.value.registered || !device.canConnect) {
            showMessage("앱 등록과 안경 연결·호환 상태를 먼저 확인하세요."); return
        }
        disconnect()
        selected = device.id
        mutableState.update { it.copy(connecting = true, datUpdateRequired = false, message = "안경 세션을 시작합니다") }
        val result = Wearables.createSession(SpecificDeviceSelector(device.id))
        result.onFailure { error, _ -> failSession(error) }
        result.onSuccess { active ->
            session = active
            sessionJobs += scope.launch {
                active.errors.collect { error -> if (session === active) failSession(error) }
            }
            active.start()
            sessionJobs += scope.launch {
                var observedActiveSession = false
                active.state.collect { phase ->
                    if (session !== active) return@collect
                    if (phase != DeviceSessionState.STOPPED) observedActiveSession = true
                    when (phase) {
                        DeviceSessionState.STARTED -> if (display == null) attachDisplay(active)
                        DeviceSessionState.STOPPED -> if (observedActiveSession) disconnect("안경 세션이 종료됐습니다. 다시 연결하세요.")
                        else -> Unit
                    }
                }
            }
            sessionJobs += scope.launch {
                delay(30_000)
                if (session === active && !state.value.ready) disconnect("연결 시간이 초과됐습니다. 안경 착용·전원·Meta AI 연결을 확인하세요.")
            }
        }
    }

    private fun failSession(error: DeviceSessionError) {
        disconnect("안경 세션 오류: ${error.description}")
        mutableState.update { it.copy(datUpdateRequired = error == DeviceSessionError.DAT_APP_ON_THE_GLASSES_UPDATE_REQUIRED) }
    }

    private fun attachDisplay(active: DeviceSession) {
        active.addDisplay().onFailure { error, _ -> disconnect("화면 연결 실패: ${error.description}") }
            .onSuccess { connectedDisplay ->
                display = connectedDisplay
                sessionJobs += scope.launch {
                    connectedDisplay.state.collect { phase ->
                        if (display !== connectedDisplay) return@collect
                        when (phase) {
                            DisplayState.STARTED -> mutableState.update { it.copy(ready = true, connecting = false, message = "안경 화면 연결 완료 · 테스트 문구를 전송하세요") }
                            DisplayState.CLOSED -> disconnect("안경 화면 연결이 종료됐습니다")
                            DisplayState.STOPPED -> if (state.value.ready) disconnect("안경 화면 연결이 종료됐습니다")
                            else -> mutableState.update { it.copy(ready = false, message = "안경 화면 준비 중") }
                        }
                    }
                }
            }
    }

    suspend fun send(instruction: HudRenderInstruction): GlassesRenderResult {
        val accepted = sendDocument(instruction.toToolkitDisplayDocument())
        return GlassesRenderResult(accepted, "MetaWearablesToolkitHudRenderer", state.value.rendererStatus, instruction)
    }

    suspend fun sendDocument(document: ToolkitDisplayDocument): Boolean = sendMutex.withLock {
        val active = display
        if (active == null || !state.value.ready) { showMessage("안경 화면을 먼저 연결하세요"); return@withLock false }
        clearJob?.cancel()
        mutableState.update { it.copy(sending = true) }
        try {
            val lines = document.physicalDisplayLines()
            val result = withTimeoutOrNull(10_000) {
                active.sendContent {
                    flexBox(gap = 8, padding = 16) {
                        lines.forEach { line -> text(line, style = TextStyle.BODY) }
                    }
                }
            }
            val accepted = result?.getOrNull() == true && display === active && state.value.ready
            showMessage(if (accepted) "SDK 수신 확인 · 안경의 실제 표시를 확인하세요" else "안경 전송 실패: ${result?.errorOrNull()?.description ?: "시간 초과 또는 연결 종료"}")
            if (accepted) {
                clearJob = scope.launch {
                    delay(document.durationMillis.coerceIn(1_000, 30_000).toLong())
                    sendMutex.withLock {
                        if (display === active) active.clearDisplay().onFailure { error, _ -> showMessage("화면 지우기 실패: ${error.description}") }
                    }
                }
            }
            accepted
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            showMessage("안경 전송 실패: ${error.message}")
            false
        } finally {
            mutableState.update { it.copy(sending = false) }
        }
    }

    fun updateFirmware(activity: Activity) {
        Wearables.openFirmwareUpdate(activity).onFailure { error, _ -> showMessage(error.description) }
    }
    fun updateDat(activity: Activity) {
        Wearables.openDATGlassesAppUpdate(activity).onFailure { error, _ -> showMessage(error.description) }
    }

    fun disconnect(message: String = "안경 연결을 종료했습니다") {
        clearJob?.cancel()
        sessionJobs.forEach { it.cancel() }
        sessionJobs.clear()
        val previous = session
        session = null
        display = null
        selected = null
        runCatching { previous?.removeDisplay() }
        runCatching { previous?.stop() }
        mutableState.update { it.copy(connecting = false, ready = false, sending = false, message = message) }
    }

    fun close() { disconnect(); job.cancel() }
}
