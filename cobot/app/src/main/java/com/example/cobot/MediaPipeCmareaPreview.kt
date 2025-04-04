//package com.example.cobot
//
//import MediaPipePoseHelper
//import android.content.Context
//import android.util.Log
//import androidx.camera.core.CameraSelector
//import androidx.camera.core.ImageAnalysis
//import androidx.camera.core.Preview
//import androidx.camera.lifecycle.ProcessCameraProvider
//import androidx.camera.view.PreviewView
//import androidx.compose.foundation.layout.fillMaxSize
//import androidx.compose.runtime.Composable
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.viewinterop.AndroidView
//import androidx.core.content.ContextCompat
//import androidx.lifecycle.LifecycleOwner
//import java.util.concurrent.ExecutorService
//
//@Composable
//fun MediaPipeCameraPreview(
//    context: Context,
//    lifecycleOwner: LifecycleOwner,
//    cameraExecutor: ExecutorService,
//    mediaPipeHelper: MediaPipePoseHelper
//) {
//    AndroidView(
//        factory = { ctx ->
//            val previewView = PreviewView(ctx).apply {
//                var implementationMode = PreviewView.ImplementationMode.COMPATIBLE
//            }
//
//            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
//            cameraProviderFuture.addListener({
//                val cameraProvider = cameraProviderFuture.get()
//
//                val preview = Preview.Builder().build().also {
//                    it.setSurfaceProvider(previewView.surfaceProvider)
//                }
//
//                val imageAnalysis = ImageAnalysis.Builder()
//                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
//                    .build()
//
//                imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
//                    try {
//                        val bitmap = imageProxyToBitmap2(imageProxy)
//                        mediaPipeHelper.detect(bitmap)
//                    } catch (e: Exception) {
//                        Log.e("MediaPipe", "Frame error", e)
//                    } finally {
//                        imageProxy.close()
//                    }
//                }
//
//                cameraProvider.unbindAll()
//                cameraProvider.bindToLifecycle(
//                    lifecycleOwner,
//                    CameraSelector.DEFAULT_FRONT_CAMERA,
//                    preview,
//                    imageAnalysis
//                )
//            }, ContextCompat.getMainExecutor(ctx))
//
//            previewView
//        },
//        modifier = Modifier.fillMaxSize()
//    )
//}
