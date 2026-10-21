package ai.storyteller.pdfcraft.ui.ink

import ai.storyteller.pdfcraft.model.InkPoint
import ai.storyteller.pdfcraft.model.InkStroke
import ai.storyteller.pdfcraft.model.ToolMode
import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInteropFilter

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun StylusCanvas(
    toolMode: ToolMode,
    strokeColor: Color,
    baseStrokeWidth: Float,
    strokes: List<InkStroke>,
    onStrokeFinished: (InkStroke) -> Unit,
    modifier: Modifier = Modifier
) {
    if (toolMode == ToolMode.READ) return

    val currentPoints = remember { mutableStateListOf<InkPoint>() }
    var isStylusActive by remember { mutableStateOf(false) }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInteropFilter { event ->
                val toolType = event.getToolType(0)
                val isStylus = toolType == MotionEvent.TOOL_TYPE_STYLUS ||
                               toolType == MotionEvent.TOOL_TYPE_ERASER

                // Hardware Palm Rejection: if a stylus is in use, ignore finger touches
                if (isStylusActive && !isStylus && event.actionMasked != MotionEvent.ACTION_UP) {
                    return@pointerInteropFilter false
                }

                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        isStylusActive = isStylus
                        currentPoints.clear()
                        currentPoints.add(
                            InkPoint(event.x, event.y, event.pressure)
                        )
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        // Ingest historical points for 120Hz smooth pen curves
                        val historySize = event.historySize
                        for (h in 0 until historySize) {
                            currentPoints.add(
                                InkPoint(
                                    event.getHistoricalX(0, h),
                                    event.getHistoricalY(0, h),
                                    event.getHistoricalPressure(0, h)
                                )
                            )
                        }
                        currentPoints.add(
                            InkPoint(event.x, event.y, event.pressure)
                        )
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        currentPoints.add(
                            InkPoint(event.x, event.y, event.pressure)
                        )
                        if (currentPoints.size > 1) {
                            val colorInt = when (toolMode) {
                                ToolMode.HIGHLIGHT -> strokeColor.copy(alpha = 0.35f).value.toInt()
                                else -> strokeColor.value.toInt()
                            }
                            val width = when (toolMode) {
                                ToolMode.HIGHLIGHT -> baseStrokeWidth * 3.5f
                                else -> baseStrokeWidth
                            }
                            onStrokeFinished(
                                InkStroke(
                                    points = currentPoints.toList(),
                                    color = colorInt,
                                    width = width
                                )
                            )
                        }
                        currentPoints.clear()
                        isStylusActive = false
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        currentPoints.clear()
                        isStylusActive = false
                        true
                    }
                    else -> false
                }
            }
    ) {
        // Draw committed strokes
        for (stroke in strokes) {
            if (stroke.points.size < 2) continue
            val path = Path().apply {
                moveTo(stroke.points[0].x, stroke.points[0].y)
                for (i in 1 until stroke.points.size) {
                    val prev = stroke.points[i - 1]
                    val curr = stroke.points[i]
                    quadraticBezierTo(prev.x, prev.y, (prev.x + curr.x) / 2, (prev.y + curr.y) / 2)
                }
            }
            drawPath(
                path = path,
                color = Color(stroke.color),
                style = Stroke(
                    width = stroke.width,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }

        // Draw active in-progress stroke
        if (currentPoints.size > 1) {
            val activePath = Path().apply {
                moveTo(currentPoints[0].x, currentPoints[0].y)
                for (i in 1 until currentPoints.size) {
                    val prev = currentPoints[i - 1]
                    val curr = currentPoints[i]
                    quadraticBezierTo(prev.x, prev.y, (prev.x + curr.x) / 2, (prev.y + curr.y) / 2)
                }
            }
            val activeColor = if (toolMode == ToolMode.HIGHLIGHT) {
                strokeColor.copy(alpha = 0.35f)
            } else {
                strokeColor
            }
            val activeWidth = if (toolMode == ToolMode.HIGHLIGHT) {
                baseStrokeWidth * 3.5f
            } else {
                baseStrokeWidth
            }

            drawPath(
                path = activePath,
                color = activeColor,
                style = Stroke(
                    width = activeWidth,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}
