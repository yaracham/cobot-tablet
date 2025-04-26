package com.example.cobot.automated_driving

import android.graphics.*
import android.media.Image
import java.io.ByteArrayOutputStream

fun mediaImageToBitmap(mediaImage: Image): Bitmap {
    val yBuffer = mediaImage.planes[0].buffer
    val uBuffer = mediaImage.planes[1].buffer
    val vBuffer = mediaImage.planes[2].buffer

    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()

    val nv21 = ByteArray(ySize + uSize + vSize)
    yBuffer.get(nv21, 0, ySize)
    vBuffer.get(nv21, ySize, vSize)
    uBuffer.get(nv21, ySize + vSize, uSize)

    val yuvImage = YuvImage(nv21, ImageFormat.NV21, mediaImage.width, mediaImage.height, null)
    val out = ByteArrayOutputStream()
    yuvImage.compressToJpeg(Rect(0, 0, mediaImage.width, mediaImage.height), 100, out)
    val imageBytes = out.toByteArray()
    return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
}

fun rotateBitmap(bitmap: Bitmap, rotation: Float): Bitmap {
    val matrix = Matrix()
    matrix.postRotate(rotation)
    matrix.postScale(-1f, 1f) // Mirror for front camera
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}