package com.example.bookstoreapp.ui.screens.search

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.example.bookstoreapp.ui.viewmodels.BookViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ImageSearchScreen(navController: NavController, bookViewModel: BookViewModel) {
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    LaunchedEffect(Unit) {
        if (!cameraPermissionState.status.isGranted) {
            cameraPermissionState.launchPermissionRequest()
        }
    }

    if (cameraPermissionState.status.isGranted) {
        CameraInterface(navController, bookViewModel)
    } else {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Cần quyền Camera để tìm kiếm bằng hình ảnh", color = Color.White)
                Button(onClick = { cameraPermissionState.launchPermissionRequest() }) {
                    Text("Cấp quyền")
                }
            }
        }
    }
}

@Composable
fun CameraInterface(navController: NavController, bookViewModel: BookViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
    
    var flashEnabled by remember { mutableStateOf(false) }
    var camera: Camera? by remember { mutableStateOf(null) }
    var isSearching by remember { mutableStateOf(false) }
    
    val previewView = remember { PreviewView(context) }
    val imageCapture: ImageCapture = remember { ImageCapture.Builder().build() }
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? -> 
        uri?.let {
            val bitmap = context.contentResolver.openInputStream(it)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
            if (bitmap != null) {
                isSearching = true
                searchWithBitmap(bitmap, bookViewModel, {
                    isSearching = false
                    navController.popBackStack() 
                })
            }
        }
    }

    LaunchedEffect(flashEnabled) {
        camera?.cameraControl?.enableTorch(flashEnabled)
    }

    LaunchedEffect(Unit) {
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner, cameraSelector, preview, imageCapture
                )
            } catch (exc: Exception) {
                Log.e("ImageSearch", "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp).statusBarsPadding(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Trở về", tint = Color.White)
            }
            IconButton(onClick = { flashEnabled = !flashEnabled }) {
                Icon(
                    if (flashEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                    contentDescription = "Flash",
                    tint = Color.White
                )
            }
        }

        // Bottom controls
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 48.dp).align(Alignment.BottomCenter),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { galleryLauncher.launch("image/*") }) {
                Icon(Icons.Default.PhotoLibrary, contentDescription = "Thư viện", tint = Color.White, modifier = Modifier.size(32.dp))
            }

            Surface(
                modifier = Modifier.size(80.dp).clickable {
                    if (!isSearching) {
                        isSearching = true
                        captureAndSearch(imageCapture, cameraExecutor, bookViewModel) {
                            isSearching = false
                            navController.popBackStack()
                        }
                    }
                },
                shape = CircleShape,
                color = Color.White,
                border = BorderStroke(4.dp, Color.LightGray)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Box(modifier = Modifier.size(60.dp).background(Color.White, CircleShape).border(2.dp, Color.Black, CircleShape))
                }
            }
            
            Box(modifier = Modifier.size(48.dp))
        }

        if (isSearching) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Đang nhận diện bìa sách...", color = Color.White)
                }
            }
        }
    }
}

private fun captureAndSearch(
    imageCapture: ImageCapture,
    executor: ExecutorService,
    viewModel: BookViewModel,
    onDone: () -> Unit
) {
    imageCapture.takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {
        override fun onCaptureSuccess(image: ImageProxy) {
            val bitmap = image.toBitmap()
            searchWithBitmap(bitmap, viewModel, onDone)
            image.close()
        }
        override fun onError(exception: ImageCaptureException) {
            Log.e("ImageSearch", "Capture failed: ${exception.message}", exception)
            onDone()
        }
    })
}

private fun searchWithBitmap(
    bitmap: Bitmap,
    viewModel: BookViewModel,
    onDone: () -> Unit
) {
    val stream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
    val byteArray = stream.toByteArray()
    val requestFile = byteArray.toRequestBody("image/jpeg".toMediaTypeOrNull(), 0, byteArray.size)
    val body = MultipartBody.Part.createFormData("image", "search.jpg", requestFile)
    viewModel.searchByImage(body, onDone)
}

private fun ImageProxy.toBitmap(): Bitmap {
    val buffer = planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}
