package com.example.util

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.media.ImageReader
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.example.data.api.GeminiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.OutputStream

object CameraController {

    private const val TAG = "CameraController"

    fun processCameraCommand(
        context: Context,
        voiceAssistant: VoiceAssistantManager,
        rawCommand: String
    ): Boolean {
        val cmd = rawCommand.lowercase().trim()

        // 1. SCENE ANALYSIS ("saamne kya hai", "yeh kya hai batao", "dekh ke batao")
        if (cmd.contains("saamne kya hai") || cmd.contains("yeh kya hai") ||
            cmd.contains("dekh ke batao") || cmd.contains("kya dikh raha hai") ||
            cmd.contains("scene analyze") || cmd.contains("samne kya hai")
        ) {
            analyzeScene(context, voiceAssistant)
            return true
        }

        // 2. SELFIE / PHOTO ("selfie lo", "photo lo", "photo khincho", "peeche wali se photo lo")
        if (cmd.contains("selfie") || cmd.contains("photo") || cmd.contains("pic lo")) {
            val isFront = cmd.contains("selfie") || cmd.contains("front") || cmd.contains("aage")
            takePhoto(context, isFront, voiceAssistant)
            return true
        }

        return false
    }

    fun takePhoto(
        context: Context,
        isFrontCamera: Boolean,
        voiceAssistant: VoiceAssistantManager
    ) {
        if (!hasCameraPermission(context)) {
            postToast(context, "⚠️ Camera permission missing!")
            voiceAssistant.speak("Pehle Camera permission enable karein.")
            return
        }

        postToast(context, "📷 Photo khinch raha hoon...")

        captureFrame(context, isFrontCamera) { bitmap ->
            if (bitmap != null) {
                GlobalScope.launch(Dispatchers.IO) {
                    val savedUri = saveBitmapToGallery(context, bitmap)
                    withContext(Dispatchers.Main) {
                        if (savedUri != null) {
                            postToast(context, "✅ Photo saved in DCIM/Max Gallery")
                            voiceAssistant.speak("Photo le li gayi hai aur gallery me save ho gayi hai.")
                        } else {
                            postToast(context, "❌ Photo save karne me error aaya")
                            voiceAssistant.speak("Photo toh khinch li par gallery me save nahi ho saki.")
                        }
                    }
                }
            } else {
                Handler(Looper.getMainLooper()).post {
                    postToast(context, "❌ Camera capture error")
                    voiceAssistant.speak("Camera se photo lene me samasya aayi.")
                }
            }
        }
    }

    fun analyzeScene(
        context: Context,
        voiceAssistant: VoiceAssistantManager
    ) {
        if (!hasCameraPermission(context)) {
            postToast(context, "⚠️ Camera permission missing!")
            voiceAssistant.speak("Pehle Camera permission enable karein.")
            return
        }

        postToast(context, "👁️ Saamne ki cheez analyze kar raha hoon...")
        voiceAssistant.speak("Ek second, main camera se dekh raha hoon...")

        captureFrame(context, isFrontCamera = false) { bitmap ->
            if (bitmap != null) {
                GlobalScope.launch(Dispatchers.IO) {
                    val description = GeminiClient.analyzeSceneImage(bitmap)
                    withContext(Dispatchers.Main) {
                        postToast(context, "💡 Scene: $description")
                        voiceAssistant.speak(description)
                    }
                }
            } else {
                Handler(Looper.getMainLooper()).post {
                    postToast(context, "❌ Camera frame capture failed")
                    voiceAssistant.speak("Saamne ki photo lene me samasya aayi.")
                }
            }
        }
    }

    /**
     * Silent Background Photo Capture (for anti-theft or background guard mode)
     */
    fun captureSilentPhotoInBackground(
        context: Context,
        isFrontCamera: Boolean,
        onPhotoCaptured: (Bitmap?) -> Unit
    ) {
        if (!hasCameraPermission(context)) {
            Log.w(TAG, "Silent photo capture failed: Camera permission missing.")
            onPhotoCaptured(null)
            return
        }
        captureFrame(context, isFrontCamera, onPhotoCaptured)
    }

    @SuppressLint("MissingPermission")
    fun captureFrame(
        context: Context,
        isFrontCamera: Boolean,
        onBitmapReady: (Bitmap?) -> Unit
    ) {
        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraIdList = cameraManager.cameraIdList

            var targetCameraId: String? = null
            val targetLensFacing = if (isFrontCamera) CameraCharacteristics.LENS_FACING_FRONT else CameraCharacteristics.LENS_FACING_BACK

            for (id in cameraIdList) {
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (facing == targetLensFacing) {
                    targetCameraId = id
                    break
                }
            }

            if (targetCameraId == null && cameraIdList.isNotEmpty()) {
                targetCameraId = cameraIdList[0]
            }

            if (targetCameraId == null) {
                Log.e(TAG, "No suitable camera ID found.")
                onBitmapReady(null)
                return
            }

            val thread = HandlerThread("CameraBackgroundThread").apply { start() }
            val bgHandler = Handler(thread.looper)

            val imageReader = ImageReader.newInstance(1280, 720, ImageFormat.JPEG, 1)

            imageReader.setOnImageAvailableListener({ reader ->
                val image = reader.acquireNextImage() ?: return@setOnImageAvailableListener
                val buffer = image.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                image.close()

                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

                thread.quitSafely()
                onBitmapReady(bitmap)
            }, bgHandler)

            cameraManager.openCamera(targetCameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    try {
                        val captureBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                            addTarget(imageReader.surface)
                        }

                        camera.createCaptureSession(
                            listOf(imageReader.surface),
                            object : android.hardware.camera2.CameraCaptureSession.StateCallback() {
                                override fun onConfigured(session: android.hardware.camera2.CameraCaptureSession) {
                                    try {
                                        session.capture(captureBuilder.build(), object : android.hardware.camera2.CameraCaptureSession.CaptureCallback() {
                                            override fun onCaptureCompleted(
                                                session: android.hardware.camera2.CameraCaptureSession,
                                                request: android.hardware.camera2.CaptureRequest,
                                                result: android.hardware.camera2.TotalCaptureResult
                                            ) {
                                                super.onCaptureCompleted(session, request, result)
                                                camera.close()
                                            }
                                        }, bgHandler)
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Session capture error", e)
                                        camera.close()
                                        thread.quitSafely()
                                        onBitmapReady(null)
                                    }
                                }

                                override fun onConfigureFailed(session: android.hardware.camera2.CameraCaptureSession) {
                                    camera.close()
                                    thread.quitSafely()
                                    onBitmapReady(null)
                                }
                            },
                            bgHandler
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Error creating capture session", e)
                        camera.close()
                        thread.quitSafely()
                        onBitmapReady(null)
                    }
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    thread.quitSafely()
                    onBitmapReady(null)
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    Log.e(TAG, "Camera open error: $error")
                    camera.close()
                    thread.quitSafely()
                    onBitmapReady(null)
                }
            }, bgHandler)

        } catch (e: Exception) {
            Log.e(TAG, "Error in captureFrame", e)
            onBitmapReady(null)
        }
    }

    fun saveBitmapToGallery(context: Context, bitmap: Bitmap): Uri? {
        val filename = "Max_Photo_${System.currentTimeMillis()}.jpg"
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/Max")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

        return try {
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                }
            }
            uri
        } catch (e: Exception) {
            Log.e(TAG, "Error saving bitmap to gallery", e)
            null
        }
    }

    fun hasCameraPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun postToast(context: Context, message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}
