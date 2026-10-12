package com.example.instalens.presentation.home

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.Image
import com.example.instalens.data.manager.objectDetection.ObjectDetectionManagerImpl
import com.example.instalens.domain.model.Detection
import com.example.instalens.presentation.home.components.CameraPreview
import com.example.instalens.presentation.home.components.RequestPermissions
import com.example.instalens.utils.CameraFrameAnalyzer
import com.example.instalens.utils.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

private val HudText = Color.White
private val HudMuted = Color.White.copy(alpha = 0.66f)
private val HudSurface = Color(0x991D2025)

@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val viewModel: HomeViewModel = hiltViewModel()
    RequestPermissions()

    val confidenceScore = remember { mutableFloatStateOf(Constants.INITIAL_CONFIDENCE_SCORE) }
    var detections by remember { mutableStateOf(emptyList<Detection>()) }
    var latestFrame by remember { mutableStateOf<Bitmap?>(null) }
    var selectedDetection by remember { mutableStateOf<Detection?>(null) }
    var onlineDescription by remember { mutableStateOf<String?>(null) }
    var previewSize by remember { mutableStateOf(IntSize.Zero) }

    val analyzer = remember {
        CameraFrameAnalyzer(
            context = context,
            objectDetectionManager = ObjectDetectionManagerImpl(context),
            onObjectDetectionResults = { results, frame ->
                detections = results
                latestFrame = frame
                selectedDetection?.let { previous ->
                    selectedDetection = results
                        .filter { it.detectedObjectName == previous.detectedObjectName }
                        .minByOrNull { candidate ->
                            val dx = candidate.boundingBox.centerX() - previous.boundingBox.centerX()
                            val dy = candidate.boundingBox.centerY() - previous.boundingBox.centerY()
                            dx * dx + dy * dy
                        }
                }
            },
            confidenceScoreState = confidenceScore
        )
    }
    val cameraController = remember {
        viewModel.prepareCameraController(context, analyzer)
    }

    val selectedLabel = selectedDetection?.detectedObjectName
    LaunchedEffect(selectedLabel) {
        onlineDescription = null
        if (selectedLabel != null && !selectedLabel.equals("person", ignoreCase = true)) {
            onlineDescription = withContext(Dispatchers.IO) {
                lookupOnlineObjectInfo(selectedLabel)
            }
        }
    }

    val crop = remember(latestFrame, selectedDetection) {
        val frame = latestFrame
        val target = selectedDetection
        if (frame != null && target != null) cropDetection(frame, target) else null
    }
    val label = selectedLabel.orEmpty()
    val description = onlineDescription ?: localObjectDescription(label)
    val descriptionSource = if (onlineDescription == null) "ON-DEVICE GUIDE" else "REFERENCE"
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val screenWidthPx = with(density) { maxWidth.toPx() }
        val screenHeightPx = with(density) { maxHeight.toPx() }
        val mappedBoxes = detections.map { detection ->
            detection to detectionRectInPreview(detection, previewSize)
        }
        val selectedRect = selectedDetection?.let { target ->
            detectionRectInPreview(target, previewSize)
        }
        val hudWidthPx = with(density) { 300.dp.toPx() }
        val hudHeightPx = with(density) { 148.dp.toPx() }
        val topInsetPx = with(density) { 72.dp.toPx() }
        val bottomInsetPx = with(density) { 112.dp.toPx() }
        val sideInsetPx = with(density) { 12.dp.toPx() }
        val hudPosition = selectedRect?.let { rect ->
            val x = (rect.centerX() - hudWidthPx / 2f)
                .coerceIn(sideInsetPx, (screenWidthPx - hudWidthPx - sideInsetPx).coerceAtLeast(sideInsetPx))
            val above = rect.top - hudHeightPx - with(density) { 12.dp.toPx() }
            val below = rect.bottom + with(density) { 12.dp.toPx() }
            val y = if (above >= topInsetPx) above else below
            IntOffset(
                x.roundToInt(),
                y.coerceIn(
                    topInsetPx,
                    (screenHeightPx - hudHeightPx - bottomInsetPx).coerceAtLeast(topInsetPx)
                ).roundToInt()
            )
        } ?: IntOffset.Zero

        CameraPreview(
            controller = cameraController,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(detections, previewSize, selectedDetection) {
                    detectTapGestures { tap ->
                        val topControls = with(density) { 70.dp.toPx() }
                        val bottomControls = with(density) { 100.dp.toPx() }
                        if (tap.y < topControls || tap.y > size.height - bottomControls) {
                            return@detectTapGestures
                        }
                        val hit = mappedBoxes
                            .filter { (_, rect) -> rect.contains(tap.x, tap.y) }
                            .minByOrNull { (_, rect) -> rect.width() * rect.height() }
                        selectedDetection = hit?.first
                    }
                },
            onPreviewSizeChanged = { previewSize = it }
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            selectedRect?.let { rect ->
                drawRect(
                    color = HudText.copy(alpha = 0.9f),
                    topLeft = androidx.compose.ui.geometry.Offset(rect.left, rect.top),
                    size = androidx.compose.ui.geometry.Size(rect.width(), rect.height()),
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 22.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "INSTA LENS",
                    color = HudText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 2.sp
                )
                Text(
                    text = "LIVE  ·  ON-DEVICE",
                    color = HudMuted,
                    fontSize = 9.sp,
                    letterSpacing = 1.2.sp
                )
            }
            HudButton(
                text = "↻",
                description = "Switch camera",
                onClick = { cameraController.cameraSelector = viewModel.getSelectedCamera(cameraController) }
            )
        }

        AnimatedVisibility(
            visible = selectedDetection != null,
            modifier = Modifier.offset { hudPosition },
            enter = fadeIn() + scaleIn(initialScale = 0.92f),
            exit = fadeOut() + scaleOut(targetScale = 0.96f)
        ) {
            FocusHud(
                label = label,
                confidence = selectedDetection?.confidenceScore ?: 0f,
                crop = crop,
                description = description,
                source = descriptionSource,
                onDismiss = { selectedDetection = null }
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 28.dp)
                .size(72.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.3f))
                .border(1.dp, Color.White.copy(alpha = 0.85f), CircleShape)
                .clickable {
                    viewModel.capturePhoto(
                        context = context,
                        cameraController = cameraController,
                        screenWidth = context.resources.displayMetrics.widthPixels.toFloat(),
                        screenHeight = context.resources.displayMetrics.heightPixels.toFloat(),
                        detections = detections
                    )
                }
                .semantics { contentDescription = "Capture photo" },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }
    }
}

@Composable
private fun FocusHud(
    label: String,
    confidence: Float,
    crop: Bitmap?,
    description: String,
    source: String,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier.width(300.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(width = 112.dp, height = 148.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(HudSurface)
                .border(1.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (crop != null) {
                Image(
                    bitmap = crop.asImageBitmap(),
                    contentDescription = "Cropped view of $label",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(20.dp))
                )
            } else {
                Text("LIVE CROP", color = HudMuted, fontSize = 10.sp, letterSpacing = 1.sp)
            }
            Text(
                text = "LIVE CROP",
                color = Color.White.copy(alpha = 0.88f),
                fontSize = 8.sp,
                letterSpacing = 1.sp,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp)
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .height(148.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(HudSurface)
                .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
                .clickable(onClick = onDismiss)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(source, color = HudMuted, fontSize = 8.sp, letterSpacing = 0.8.sp)
                Text("×", color = HudMuted, fontSize = 17.sp)
            }
            Text(
                text = label.replaceFirstChar { it.uppercase() },
                color = HudText,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = description,
                color = HudMuted,
                fontSize = 10.sp,
                lineHeight = 14.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "MATCH  ${(confidence * 100).toInt()}%",
                color = HudText.copy(alpha = 0.82f),
                fontSize = 8.sp,
                letterSpacing = 0.8.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun HudButton(
    text: String,
    description: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(HudSurface)
            .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = HudText, fontSize = 23.sp)
    }
}

private fun detectionRectInPreview(detection: Detection, previewSize: IntSize): RectF {
    if (previewSize.width <= 0 || previewSize.height <= 0 ||
        detection.tensorImageWidth <= 0 || detection.tensorImageHeight <= 0
    ) {
        return RectF()
    }
    val scale = max(
        previewSize.width.toFloat() / detection.tensorImageWidth,
        previewSize.height.toFloat() / detection.tensorImageHeight
    )
    val left = (previewSize.width - detection.tensorImageWidth * scale) / 2f
    val top = (previewSize.height - detection.tensorImageHeight * scale) / 2f
    return RectF(
        detection.boundingBox.left * scale + left,
        detection.boundingBox.top * scale + top,
        detection.boundingBox.right * scale + left,
        detection.boundingBox.bottom * scale + top
    )
}

private fun cropDetection(bitmap: Bitmap, detection: Detection): Bitmap? {
    val box = detection.boundingBox
    val padding = max(box.width(), box.height()) * 0.12f
    val left = floor((box.left - padding).coerceAtLeast(0f)).toInt()
    val top = floor((box.top - padding).coerceAtLeast(0f)).toInt()
    val right = ceil((box.right + padding).coerceAtMost(bitmap.width.toFloat())).toInt()
    val bottom = ceil((box.bottom + padding).coerceAtMost(bitmap.height.toFloat())).toInt()
    if (right <= left || bottom <= top) return null
    return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
}

private fun localObjectDescription(label: String): String {
    if (label.equals("person", ignoreCase = true)) {
        return "A person is in view. This camera model detects people but does not identify who they are."
    }
    return when (label.lowercase()) {
        "cell phone" -> "A handheld mobile device for calls, messages and apps."
        "laptop" -> "A portable computer with an integrated screen and keyboard."
        "book" -> "A printed or bound collection of pages."
        "cup" -> "A small container commonly used for drinking."
        "bottle" -> "A container designed to hold liquids."
        "chair" -> "A seat designed for one person."
        "dog" -> "A domesticated canine often kept as a companion."
        "cat" -> "A small domesticated feline."
        "car" -> "A road vehicle used to transport people."
        else -> "Detected in the camera view. Online reference text may be available when connected."
    }
}
