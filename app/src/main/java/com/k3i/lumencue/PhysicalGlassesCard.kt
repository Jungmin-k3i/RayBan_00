package com.k3i.lumencue

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PhysicalGlassesCard(
    state: PhysicalGlassesState,
    mockMode: Boolean,
    onMockMode: (Boolean) -> Unit,
    onRegister: () -> Unit,
    onUnregister: () -> Unit,
    onConnect: (PhysicalGlassesDevice) -> Unit,
    onDisconnect: () -> Unit,
    onTest: () -> Unit,
    onFirmwareUpdate: () -> Unit,
    onDatUpdate: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Ray-Ban Display 연결", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("모의 기기 테스트")
                Switch(checked = mockMode, onCheckedChange = onMockMode)
            }
            if (mockMode) {
                Text("모의 기기 모드입니다. 실제 안경을 연결하려면 스위치를 끄세요.")
            } else {
                Text(state.message)
                Text("휴대폰의 Meta AI 앱에서 안경을 페어링하고 개발자 모드를 켜세요.", style = MaterialTheme.typography.bodySmall)
                Button(onClick = onRegister, enabled = !state.registrationBusy && !state.registered) {
                    Text(if (state.registered) "Meta AI 등록 완료" else "Meta AI 앱 등록")
                }
                if (state.registered && state.devices.isEmpty()) Text("연결 가능한 안경을 기다리는 중입니다. 안경 전원과 Meta AI 연결을 확인하세요.")
                state.devices.forEach { entry ->
                    Text("${entry.device.name} · ${entry.device.linkState} · ${entry.device.compatibility}")
                    if (!entry.device.isDisplayCapable()) Text("이 기기는 Display 출력을 지원하지 않습니다.")
                    OutlinedButton(
                        onClick = { onConnect(entry) },
                        enabled = state.registered && entry.canConnect && !state.connecting && !state.ready
                    ) { Text("이 안경 연결") }
                }
                if (state.devices.any { it.device.compatibility == com.meta.wearable.dat.core.types.DeviceCompatibility.DEVICE_UPDATE_REQUIRED }) {
                    OutlinedButton(onClick = onFirmwareUpdate) { Text("안경 펌웨어 업데이트") }
                }
                if (state.datUpdateRequired) OutlinedButton(onClick = onDatUpdate) { Text("안경의 Meta 연동 앱 업데이트") }
                Button(onClick = onTest, enabled = state.ready && !state.sending) { Text("테스트 문구 전송") }
                if (state.ready || state.connecting) OutlinedButton(onClick = onDisconnect) { Text("안경 연결 종료") }
                if (state.registered) TextButton(onClick = onUnregister) { Text("Meta AI 앱 등록 해제") }
                Text("연결 후 AR Live의 HUD 전송 버튼으로 현재 안내를 보낼 수 있습니다.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
