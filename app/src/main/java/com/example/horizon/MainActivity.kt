package com.example.horizon

import android.graphics.Paint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.horizon.ui.theme.HorizonTheme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : ComponentActivity() {

    private val viewModel: SensorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        setContent {
            HorizonTheme {
                val context = LocalContext.current
                DisposableEffect(Unit) {
                    viewModel.registerSensors(context)
                    onDispose {
                        viewModel.unregisterSensors(context)
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(Color(0xFF1A1C20), Color.Black),
                                    radius = 1200f
                                )
                            )
                    )
                    HorizonApp(viewModel)
                }
            }
        }
    }
}

@Composable
fun HorizonApp(viewModel: SensorViewModel) {
    val compassRotation by viewModel.compassRotation.collectAsStateWithLifecycle()
    val levelTilt by viewModel.levelTilt.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        HorizonDisplay(
            rotation = compassRotation,
            tiltX = levelTilt.second,
            tiltY = levelTilt.first
        )
    }
}

@Composable
fun HorizonDisplay(rotation: Float, tiltX: Float, tiltY: Float) {
    val northColor = Color(0xFFE53935)
    val levelColor = Color(0xFF03A9F4)

    val animatedRotation by animateFloatAsState(
        targetValue = -rotation,
        animationSpec = spring(stiffness = Spring.StiffnessLow)
    )

    val maxTilt = 10f
    val tiltAmount = (abs(tiltX) + abs(tiltY)).coerceIn(0f, maxTilt)
    val glowIntensity = 1f - (tiltAmount / maxTilt)
    val isLevel = glowIntensity > 0.98f

    val animatedCoreColor by animateColorAsState(
        targetValue = if (isLevel) levelColor else Color.White,
        animationSpec = tween(500), label = "core_color"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "${(360 + rotation.toInt()) % 360}°",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 72.sp,
            fontWeight = FontWeight.Thin
        )
        Spacer(modifier = Modifier.height(64.dp))

        Box(
            modifier = Modifier
                .size(320.dp)
                .graphicsLayer {
                    rotationX = tiltY * 3f
                    rotationY = -tiltX * 3f
                    cameraDistance = 12f * density
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val radius = size.minDimension / 2
                val center = this.center
                val gridSize = 20.dp.toPx()
                for (i in 0..size.width.toInt() step gridSize.toInt()) {
                    drawLine(Color.DarkGray.copy(alpha = 0.1f), start = Offset(i.toFloat(), 0f), end = Offset(i.toFloat(), size.height))
                }
                for (i in 0..size.height.toInt() step gridSize.toInt()) {
                    drawLine(Color.DarkGray.copy(alpha = 0.1f), start = Offset(0f, i.toFloat()), end = Offset(size.width, i.toFloat()))
                }
                val liquidPath = Path()
                val coreRadius = radius * 0.5f
                val points = 100
                for (i in 0..points) {
                    val angle = (2 * PI * i / points).toFloat()
                    val distortedRadius = coreRadius * (1 + 0.2f * (sin(angle * 3f) * (tiltX/maxTilt) + cos(angle * 2f) * (tiltY/maxTilt)))
                    val x = center.x + distortedRadius * cos(angle)
                    val y = center.y + distortedRadius * sin(angle)
                    if (i == 0) liquidPath.moveTo(x, y) else liquidPath.lineTo(x, y)
                }
                liquidPath.close()

                val coreBrush = Brush.radialGradient(
                    colors = listOf(animatedCoreColor.copy(alpha = 0.3f), Color.Transparent),
                    radius = coreRadius, center = center
                )
                drawPath(path = liquidPath, brush = coreBrush)
                drawPath(path = liquidPath, color = animatedCoreColor, style = Stroke(width = 2.dp.toPx()))
                rotate(degrees = animatedRotation, pivot = center) {
                    drawCircle(color = Color.White.copy(alpha = 0.2f), radius = radius, style = Stroke(width = 1.dp.toPx()))
                    drawArc(
                        brush = Brush.sweepGradient(
                            0.0f to Color.Transparent, 0.45f to northColor, 0.55f to northColor, 1.0f to Color.Transparent
                        ),
                        startAngle = -100f, sweepAngle = 20f, useCenter = false,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                        topLeft = Offset(center.x - radius, center.y - radius), size = size
                    )
                    val textPaint = Paint().apply { color = android.graphics.Color.WHITE; textSize = 40f; textAlign = Paint.Align.CENTER; alpha = (200 * glowIntensity).toInt() }
                    val textRadius = radius * 1.15f
                    drawContext.canvas.nativeCanvas.apply {
                        val textOffset = abs(textPaint.fontMetrics.ascent) / 2
                        drawText("N", center.x, center.y - textRadius + textOffset, textPaint)
                        drawText("S", center.x, center.y + textRadius + textOffset, textPaint)
                        drawText("E", center.x + textRadius, center.y + textOffset, textPaint)
                        drawText("W", center.x - textRadius, center.y + textOffset, textPaint)
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun DefaultPreview() {
    HorizonTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            HorizonDisplay(rotation = 315f, tiltX = 8f, tiltY = -4f)
        }
    }
}

