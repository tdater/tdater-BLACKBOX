package com.tdater.blackbox.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import com.tdater.blackbox.ui.theme.BbColors
import com.tdater.blackbox.ui.theme.LocalPalette

/** App-wide backdrop: theme gradient, two soft glows and (flight-recorder look only) a faint blueprint grid. */
@Composable
fun Backdrop(content: @Composable BoxScope.() -> Unit) {
    val p = LocalPalette.current
    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(p.bgTop, p.bgMid, p.bgBottom)))
            .drawBehind {
                if (p.grid.alpha > 0f) {
                    val step = 28.dp.toPx()
                    var x = 0f
                    while (x < size.width) { drawLine(p.grid, Offset(x, 0f), Offset(x, size.height)); x += step }
                    var y = 0f
                    while (y < size.height) { drawLine(p.grid, Offset(0f, y), Offset(size.width, y)); y += step }
                }
                drawCircle(Brush.radialGradient(listOf(p.glowA, Color.Transparent),
                    center = Offset(size.width * 0.95f, 0f), radius = size.minDimension * 0.9f),
                    radius = size.minDimension * 0.9f, center = Offset(size.width * 0.95f, 0f))
                drawCircle(Brush.radialGradient(listOf(p.glowB, Color.Transparent),
                    center = Offset(0f, size.height), radius = size.minDimension),
                    radius = size.minDimension, center = Offset(0f, size.height))
            },
        content = content,
    )
}

/** Accent → secondary → nothing. Used for top-bar underlines and section rules. */
val SignalBrush: Brush @Composable get() = Brush.horizontalGradient(listOf(BbColors.Orange, BbColors.Cyan.copy(alpha = 0.7f), Color.Transparent))

/** Diagonal black/orange hazard stripes, like the tape on a flight recorder. */
@Composable
fun HazardStripe(modifier: Modifier = Modifier, height: Dp = 6.dp, color: Color = BbColors.Orange) {
    Canvas(modifier.fillMaxWidth().height(height).clipToBounds()) {
        drawRect(Color(0xFF111111))
        val w = size.height * 1.6f
        var x = -size.height
        clipRect {
            while (x < size.width + size.height) {
                val p = Path().apply {
                    moveTo(x, size.height); lineTo(x + size.height, 0f); lineTo(x + size.height + w / 2, 0f); lineTo(x + w / 2, size.height); close()
                }
                drawPath(p, color)
                x += w
            }
        }
    }
}

/**
 * The BLACKBOX mark: a closed box with a heartbeat trace. When [animated], a bright pulse sweeps
 * along the trace like a recorder that is still listening.
 */
@Composable
fun TraceGlyph(modifier: Modifier = Modifier, size: Dp = 72.dp, animated: Boolean = true) {
    val anim by rememberInfiniteTransition(label = "trace").animateFloat(
        0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart), label = "sweep")
    val orange = BbColors.Orange
    val hot = BbColors.OrangeHot
    val ink = MaterialTheme.colorScheme.onBackground
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        val stroke = s * 0.045f
        val box = Size(s * 0.78f, s * 0.6f)
        val tl = Offset((s - box.width) / 2, (s - box.height) / 2)
        drawRoundRect(Brush.linearGradient(listOf(orange.copy(alpha = 0.25f), Color.Transparent), tl,
            Offset(tl.x + box.width, tl.y + box.height)), tl, box, androidx.compose.ui.geometry.CornerRadius(s * 0.08f))
        drawRoundRect(ink, tl, box, androidx.compose.ui.geometry.CornerRadius(s * 0.08f), style = Stroke(stroke))
        val pts = listOf(0.12f to 0.55f, 0.32f to 0.55f, 0.42f to 0.28f, 0.54f to 0.8f, 0.64f to 0.45f, 0.88f to 0.45f)
            .map { (px, py) -> Offset(tl.x + box.width * px, tl.y + box.height * py) }
        val path = Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) } }
        drawPath(path, orange.copy(alpha = 0.45f), style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        // Bright head of the sweep.
        val sweep = if (animated) anim else 1f
        val head = tl.x + box.width * (0.08f + 0.84f * sweep)
        clipRect(left = head - box.width * 0.22f, right = head) {
            drawPath(path, Brush.horizontalGradient(listOf(Color.Transparent, hot), head - box.width * 0.22f, head),
                style = Stroke(stroke * 1.4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
