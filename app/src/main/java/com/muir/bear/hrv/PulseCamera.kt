package com.muir.bear.hrv

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Range
import com.muir.bear.ErrorLog
import java.util.concurrent.Executor

/**
 * Streams the average fingertip colour from the back camera with the torch on. Each frame gives
 * one sample: (seconds, red brightness, finger-covered?). No image is stored. Uses the platform
 * camera API, so no extra library is needed.
 */
class PulseCamera(
    private val context: Context,
    private val onSample: (t: Double, red: Double, covered: Boolean) -> Unit,
    private val onError: (String) -> Unit,
) {
    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var reader: ImageReader? = null
    @Volatile private var stopped = false

    /** Caller must hold the CAMERA permission. */
    @SuppressLint("MissingPermission")
    fun start() {
        try {
            val cm = context.getSystemService(CameraManager::class.java)
            val id = cm.cameraIdList.firstOrNull {
                val c = cm.getCameraCharacteristics(it)
                c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK &&
                    c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return onError("This phone has no back camera with a flash.")
            val chars = cm.getCameraCharacteristics(id)
            val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                ?: return onError("Camera not available.")
            // A small frame is plenty: we only need the average colour.
            val size = map.getOutputSizes(ImageFormat.YUV_420_888)
                .filter { it.width >= 160 }
                .minByOrNull { it.width * it.height }
                ?: return onError("Camera not available.")
            // Prefer a steady 30 fps (exposure can't stretch frame times).
            val fps = chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
                ?.filter { it.upper == 30 }?.maxByOrNull { it.lower }

            val t = HandlerThread("bear-pulse").also { it.start() }
            thread = t
            val h = Handler(t.looper)
            handler = h
            val r = ImageReader.newInstance(size.width, size.height, ImageFormat.YUV_420_888, 4)
            r.setOnImageAvailableListener({ rd ->
                try {
                    val img = rd.acquireLatestImage() ?: return@setOnImageAvailableListener
                    try { if (!stopped) process(img) } finally { img.close() }
                } catch (_: IllegalStateException) {
                    // Reader closed while a frame was arriving; harmless when stopping.
                }
            }, h)
            reader = r

            cm.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(d: CameraDevice) {
                    if (stopped) { d.close(); return }
                    device = d
                    startSession(d, r, fps, h)
                }
                override fun onDisconnected(d: CameraDevice) { d.close() }
                override fun onError(d: CameraDevice, error: Int) {
                    d.close()
                    onError("The camera stopped (error $error). Close other camera apps and try again.")
                }
            }, h)
        } catch (e: Exception) {
            ErrorLog.log("HRV", "Camera start failed", e)
            onError("Couldn't start the camera.")
        }
    }

    private fun startSession(d: CameraDevice, r: ImageReader, fps: Range<Int>?, h: Handler) {
        val request = d.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
            addTarget(r.surface)
            set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
            fps?.let { set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, it) }
        }.build()
        val executor = Executor { h.post(it) }
        val config = SessionConfiguration(
            SessionConfiguration.SESSION_REGULAR, listOf(OutputConfiguration(r.surface)), executor,
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(s: CameraCaptureSession) {
                    if (stopped) { s.close(); return }
                    session = s
                    try { s.setRepeatingRequest(request, null, h) } catch (e: Exception) {
                        ErrorLog.log("HRV", "Repeating request failed", e)
                        onError("Couldn't start the camera.")
                    }
                }
                override fun onConfigureFailed(s: CameraCaptureSession) { onError("Couldn't start the camera.") }
            },
        )
        d.createCaptureSession(config)
    }

    /** Mean of the centre of the Y and V (red-difference) planes; red ≈ Y + 1.402 (V − 128). */
    private fun process(img: Image) {
        val y = img.planes[0]
        val v = img.planes[2]
        val w = img.width
        val hgt = img.height
        var ySum = 0L; var yN = 0
        val yBuf = y.buffer
        for (row in hgt / 4 until hgt * 3 / 4 step 4) {
            for (col in w / 4 until w * 3 / 4 step 4) {
                ySum += yBuf.get(row * y.rowStride + col * y.pixelStride).toInt() and 0xFF
                yN++
            }
        }
        var vSum = 0L; var vN = 0
        val vBuf = v.buffer
        for (row in hgt / 8 until hgt * 3 / 8 step 2) {
            for (col in w / 8 until w * 3 / 8 step 2) {
                val idx = row * v.rowStride + col * v.pixelStride
                if (idx < vBuf.limit()) { vSum += vBuf.get(idx).toInt() and 0xFF; vN++ }
            }
        }
        if (yN == 0 || vN == 0) return
        val yMean = ySum.toDouble() / yN
        val vMean = vSum.toDouble() / vN
        val red = yMean + 1.402 * (vMean - 128)
        // A lit fingertip fills the frame with deep red: strong red difference, not black.
        val covered = vMean > 150 && yMean > 20
        onSample(img.timestamp / 1e9, red, covered)
    }

    fun stop() {
        stopped = true
        try { session?.close() } catch (_: Exception) {}
        try { device?.close() } catch (_: Exception) {}
        try { reader?.close() } catch (_: Exception) {}
        thread?.quitSafely()
        session = null; device = null; reader = null; thread = null; handler = null
    }
}
