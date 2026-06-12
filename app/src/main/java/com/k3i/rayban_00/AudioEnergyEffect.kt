package com.k3i.rayban_00

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun AudioEnergyEffect(onEnergy: (Int) -> Unit) {
    DisposableEffect(Unit) {
        var active = true
        val mainHandler = Handler(Looper.getMainLooper())
        val thread = Thread {
            val sampleRate = 8_000
            val minBuffer = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(sampleRate)
            val recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuffer
            )
            val buffer = ShortArray(minBuffer / 2)
            try {
                recorder.startRecording()
                while (active && recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        val average = buffer.take(read).map { abs(it.toInt()) }.average()
                        val level = ((average / Short.MAX_VALUE) * 420).roundToInt().coerceIn(0, 100)
                        mainHandler.post { onEnergy(level) }
                    }
                }
            } catch (_: SecurityException) {
                mainHandler.post { onEnergy(0) }
            } finally {
                if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    recorder.stop()
                }
                recorder.release()
            }
        }
        thread.start()
        onDispose {
            active = false
            thread.join(300)
            mainHandler.post { onEnergy(0) }
        }
    }
}
