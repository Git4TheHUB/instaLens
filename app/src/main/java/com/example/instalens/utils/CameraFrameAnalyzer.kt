package com.example.instalens.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.compose.runtime.State
import androidx.core.content.ContextCompat
import com.example.instalens.domain.manager.objectDetection.ObjectDetectionManager
import com.example.instalens.domain.model.Detection
import javax.inject.Inject

/**
 * This analyzer processes camera frames to detect objects, using a custom implementation that
 * interfaces with an object detection model provided by [ObjectDetectionManager].
 *
 * Frames are sampled to balance responsiveness and performance. The rotation of each frame is
 * corrected so both the detector and the selected-object crop use the same image coordinates.
 *
 * Detection results, which include the objects found and their confidence scores, are passed back
 * through the [onObjectDetectionResults] callback, along with any other relevant metadata.
 *
 * @property onObjectDetectionResults A callback function that processes the list of [Detection]
 * objects returned by the object detection algorithm for each analyzed frame.
 * @property confidenceScoreState A state holding the threshold for the confidence score,
 * used to filter results by the object detection manager.
 *
 * @constructor Creates an instance of the analyzer with injected dependencies necessary
 * for object detection operations.
 */
class CameraFrameAnalyzer @Inject constructor(
    private val context: Context,
    private val objectDetectionManager: ObjectDetectionManager,
    private val onObjectDetectionResults: (List<Detection>, Bitmap) -> Unit,
    private val confidenceScoreState: State<Float>
): ImageAnalysis.Analyzer {
    private var frameSkipCounter = 0

    override fun analyze(image: ImageProxy) {
        try {
            if (frameSkipCounter++ % 12 == 0) {
                val sourceBitmap = image.toBitmap()
                val rotation = image.imageInfo.rotationDegrees
                val rotatedBitmap = if (rotation == 0) {
                    sourceBitmap
                } else {
                    Bitmap.createBitmap(
                        sourceBitmap,
                        0,
                        0,
                        sourceBitmap.width,
                        sourceBitmap.height,
                        Matrix().apply { postRotate(rotation.toFloat()) },
                        true
                    )
                }

                val results = objectDetectionManager.detectObjectsInCurrentFrame(
                    bitmap = rotatedBitmap,
                    rotation = rotation,
                    confidenceThreshold = confidenceScoreState.value
                )
                ContextCompat.getMainExecutor(context).execute {
                    onObjectDetectionResults(results, rotatedBitmap)
                }
            }
        } catch (exception: RuntimeException) {
            Log.e("CameraFrameAnalyzer", "Could not analyze camera frame", exception)
        } finally {
            image.close()
        }
    }
}