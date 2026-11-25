package com.cyron.manager.presentation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The whole app sits on one very soft light field instead of a flat fill: a vertical
 * background ramp plus two out-of-focus colour pools. It is drawn once behind the
 * Scaffold, so it costs nothing while lists scroll.
 */
@Composable
fun AmbientBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val light = scheme.background.luminance() > .5f
    val warm = scheme.primary.copy(alpha = if (light) .08f else .17f)
    val cool = scheme.secondary.copy(alpha = if (light) .06f else .11f)
    Box(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(scheme.background, scheme.background, scheme.surface)))
            .drawBehind {
                val topRadius = size.width * .95f
                val topCenter = Offset(size.width * .84f, size.height * .03f)
                drawCircle(Brush.radialGradient(listOf(warm, Color.Transparent), center = topCenter, radius = topRadius), radius = topRadius, center = topCenter)
                val midRadius = size.width * .85f
                val midCenter = Offset(size.width * .06f, size.height * .45f)
                drawCircle(Brush.radialGradient(listOf(cool, Color.Transparent), center = midCenter, radius = midRadius), radius = midRadius, center = midCenter)
            },
        content = content
    )
}

/**
 * The single card surface used across Cyron. A vertical tonal ramp, a hairline border and
 * an optional coloured halo keep the four screens visually consistent.
 */
@Composable
fun CyronCard(
    modifier: Modifier = Modifier,
    glow: Color? = null,
    shape: Shape = RoundedCornerShape(18.dp),
    contentPadding: PaddingValues = PaddingValues(15.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Card(
        modifier,
        shape = shape,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent, contentColor = scheme.onSurface),
        border = BorderStroke(.8.dp, scheme.outline.copy(alpha = .55f))
    ) {
        Column(
            Modifier
                .background(Brush.verticalGradient(listOf(scheme.surfaceVariant.copy(alpha = .94f), scheme.surface.copy(alpha = .97f))))
                .then(if (glow != null) Modifier.background(Brush.radialGradient(listOf(glow.copy(alpha = .13f), Color.Transparent), radius = 560f)) else Modifier)
                .padding(contentPadding),
            content = content
        )
    }
}

/**
 * Signature control: one block per byte-range connection. A download started with eight
 * parallel connections shows eight cells filling independently, which is the most honest
 * progress visual this app can have.
 */
@Composable
fun SegmentProgressBar(
    fraction: Float,
    segments: Int,
    color: Color,
    modifier: Modifier = Modifier,
    trackColor: Color = MaterialTheme.colorScheme.outline.copy(alpha = .38f),
    height: Dp = 7.dp,
    contentDescription: String = "Download progress"
) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(620, easing = FastOutSlowInEasing), label = "segmentProgress")
    val count = segments.coerceIn(1, 24)
    val language = LocalAppLanguage.current
    Canvas(modifier.fillMaxWidth().height(height).semantics {
        this.contentDescription = localizeUiText(contentDescription, language)
        progressBarRangeInfo = ProgressBarRangeInfo(animated, 0f..1f)
    }) {
        val gap = if (count > 1) 2.dp.toPx() else 0f
        val cell = (size.width - gap * (count - 1)) / count
        val radius = CornerRadius(size.height / 2f)
        val filled = animated * count
        for (i in 0 until count) {
            val left = i * (cell + gap)
            drawRoundRect(trackColor, topLeft = Offset(left, 0f), size = Size(cell, size.height), cornerRadius = radius)
            val local = (filled - i).coerceIn(0f, 1f)
            if (local > 0f) {
                drawRoundRect(
                    Brush.horizontalGradient(listOf(color.copy(alpha = .72f), color)),
                    topLeft = Offset(left, 0f),
                    size = Size(cell * local, size.height),
                    cornerRadius = radius
                )
            }
        }
    }
}

/** Circular gauge used for the single-file detail header. */
@Composable
fun GaugeRing(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    stroke: Dp = 9.dp,
    contentDescription: String = "Download progress",
    content: @Composable BoxScope.() -> Unit
) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(700, easing = FastOutSlowInEasing), label = "gauge")
    val track = MaterialTheme.colorScheme.outline.copy(alpha = .4f)
    val language = LocalAppLanguage.current
    Box(modifier.semantics {
        this.contentDescription = localizeUiText(contentDescription, language)
        progressBarRangeInfo = ProgressBarRangeInfo(animated, 0f..1f)
    }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val width = stroke.toPx()
            val inset = width / 2f
            val arcSize = Size(size.width - width, size.height - width)
            drawArc(track, startAngle = -90f, sweepAngle = 360f, useCenter = false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(width, cap = StrokeCap.Round))
            if (animated > 0f) {
                drawArc(
                    Brush.sweepGradient(listOf(color.copy(alpha = .45f), color, color.copy(alpha = .85f))),
                    startAngle = -90f,
                    sweepAngle = 360f * animated,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width, cap = StrokeCap.Round)
                )
            }
        }
        content()
    }
}

/** Breathing indicator for live transfers. */
@Composable
fun PulseDot(color: Color, size: Dp = 6.dp) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val spread by transition.animateFloat(
        initialValue = .7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1150, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulseSpread"
    )
    Box(Modifier.size(size * 2.2f), contentAlignment = Alignment.Center) {
        Box(Modifier.size(size * 1.9f * spread).clip(CircleShape).background(color.copy(alpha = .16f * spread)))
        Box(Modifier.size(size).clip(CircleShape).background(color))
    }
}

/**
 * Floating pill navigation. The selected tab expands into a filled capsule with its label,
 * the others stay as quiet icons, which frees the vertical space the stock bar wasted.
 */
@Composable
fun CyronNavBar(
    items: List<Pair<String, ImageVector>>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.verticalGradient(listOf(scheme.surface.copy(alpha = .97f), scheme.surfaceVariant.copy(alpha = .92f))))
            .border(.8.dp, scheme.outline.copy(alpha = .6f), RoundedCornerShape(24.dp))
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEachIndexed { index, (label, icon) ->
            val selected = index == selectedIndex
            val tint by animateColorAsState(if (selected) scheme.onPrimary else scheme.onSurfaceVariant, tween(230), label = "navTint")
            val fill by animateColorAsState(if (selected) scheme.primary else Color.Transparent, tween(230), label = "navFill")
            Row(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(fill)
                    .semantics(mergeDescendants = true) {
                        contentDescription = label
                        this.selected = selected
                        role = Role.Tab
                    }
                    .clickable { onSelect(index) }
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(19.dp), tint = tint)
                if (selected) UiText("  $label", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = tint, letterSpacing = .2.sp)
            }
        }
    }
}
