package com.example.focusgate

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

@Composable
fun SmoothCollapsibleContent(
    visible: Boolean,
    contentSpacing: Dp,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(
            animationSpec = tween(FocusGateMotion.EXPAND_MS, easing = FastOutSlowInEasing),
            expandFrom = Alignment.Top
        ) + fadeIn(tween(FocusGateMotion.FADE_IN_MS)),
        exit = shrinkVertically(
            animationSpec = tween(FocusGateMotion.COLLAPSE_MS, easing = FastOutSlowInEasing),
            shrinkTowards = Alignment.Top
        ) + fadeOut(tween(FocusGateMotion.FADE_OUT_MS))
    ) {
        Column {
            Spacer(Modifier.height(contentSpacing))
            Column(verticalArrangement = Arrangement.spacedBy(contentSpacing)) {
                content()
            }
        }
    }
}
