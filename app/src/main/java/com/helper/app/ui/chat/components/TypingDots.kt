package com.helper.app.ui.chat.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Анимированные «три точки» — индикатор того, что Саша печатает. */
@Composable
fun TypingDots(
    modifier: Modifier = Modifier,
    dotColor: Color,
) {
    val transition = rememberInfiniteTransition(label = "typing")
    val scales = (0..2).map { index ->
        transition.animateFloat(
            initialValue = 0.6f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 500, delayMillis = index * 150),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "dot-$index",
        ).value
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        scales.forEach { s ->
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .scale(s)
                    .clip(CircleShape)
                    .background(dotColor),
            )
        }
    }
}
