package com.example.bookstoreapp.ui.screens.search

import android.Manifest
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.bookstoreapp.ui.viewmodels.BookViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import kotlin.random.Random

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun VoiceSearchDialog(
    onDismiss: () -> Unit,
    bookViewModel: BookViewModel,
    onResult: () -> Unit
) {
    val context = LocalContext.current
    val recordAudioPermissionState = rememberPermissionState(Manifest.permission.RECORD_AUDIO)
    
    var isRecording by remember { mutableStateOf(false) }
    var recordingTime by remember { mutableStateOf(0) }
    var audioFile by remember { mutableStateOf<File?>(null) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var statusText by remember { mutableStateOf("Đang lắng nghe...") }

    // Hiệu ứng nhịp đập cho nút
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    // Hiệu ứng Sóng âm (Visualizer)
    val waveAnimations = List(8) { 
        infiniteTransition.animateFloat(
            initialValue = 0.2f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(Random.nextInt(400, 800), easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "wave_$it"
        )
    }

    fun startRecording() {
        if (isRecording) return
        try {
            val file = File(context.cacheDir, "voice_search_${System.currentTimeMillis()}.m4a")
            audioFile = file
            
            val newRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            
            newRecorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            recorder = newRecorder
            isRecording = true
            statusText = "Đang lắng nghe..."
            Log.d("VoiceSearch", "Bắt đầu ghi âm: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e("VoiceSearch", "Lỗi khởi tạo", e)
            statusText = "Lỗi âm thanh"
        }
    }

    fun sendAudioAndClose() {
        val file = audioFile
        if (file != null && file.exists()) {
            val requestFile = file.asRequestBody("audio/m4a".toMediaTypeOrNull())
            val body = MultipartBody.Part.createFormData("audio", file.name, requestFile)
            
            bookViewModel.searchByVoice(body) {
                if (file.exists()) file.delete()
                onResult()
                onDismiss()
            }
        }
    }

    fun stopAndFinish() {
        try {
            recorder?.apply {
                stop()
                release()
            }
            recorder = null
            isRecording = false
            Log.d("VoiceSearch", "Dừng và gửi đi ngay")
            sendAudioAndClose()
        } catch (e: Exception) {
            Log.e("VoiceSearch", "Lỗi dừng ghi", e)
            isRecording = false
            onDismiss()
        }
    }

    // Tự động khởi động khi mở Pop-up
    LaunchedEffect(recordAudioPermissionState.status.isGranted) {
        if (recordAudioPermissionState.status.isGranted) {
            startRecording()
        }
    }

    // Đếm thời gian
    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingTime = 0
            while (isRecording) {
                kotlinx.coroutines.delay(1000)
                recordingTime++
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            recorder?.apply {
                try { stop() } catch (_: Exception) {}
                release()
            }
        }
    }

    Dialog(onDismissRequest = { if (!isRecording) onDismiss() }) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Column(
                modifier = Modifier.padding(24.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Tìm kiếm bằng giọng nói", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Visualizer Sóng âm
                if (isRecording) {
                    Canvas(modifier = Modifier.fillMaxWidth().height(60.dp)) {
                        val barWidth = 8.dp.toPx()
                        val spacing = 6.dp.toPx()
                        val totalWidth = (barWidth + spacing) * waveAnimations.size - spacing
                        val startX = (size.width - totalWidth) / 2
                        
                        waveAnimations.forEachIndexed { index, animValue ->
                            val currentBarHeight = (size.height * 0.8f) * animValue.value
                            val x = startX + index * (barWidth + spacing)
                            val y = (size.height - currentBarHeight) / 2
                            
                            drawRoundRect(
                                color = Color.Red,
                                topLeft = Offset(x, y),
                                size = Size(barWidth, currentBarHeight),
                                cornerRadius = CornerRadius(barWidth/2, barWidth/2)
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(60.dp))
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (!recordAudioPermissionState.status.isGranted) {
                    Button(onClick = { recordAudioPermissionState.launchPermissionRequest() }) {
                        Text("Cấp quyền Micro")
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(90.dp)
                            .scale(if (isRecording) pulseScale else 1f)
                            .background(if (isRecording) Color.Red else Color.Gray, CircleShape)
                            .clickable {
                                if (isRecording) stopAndFinish() else startRecording()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = if (isRecording) String.format("%02d:%02d", recordingTime / 60, recordingTime % 60) else statusText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isRecording) Color.Red else Color.Black,
                    fontWeight = FontWeight.Medium
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                TextButton(
                    onClick = {
                        isRecording = false
                        onDismiss()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.Gray)
                ) {
                    Text("Hủy tìm kiếm")
                }
            }
        }
    }
}
