package com.example.dinoroar.ui.lock.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dinoroar.ui.lock.CamouflageGameViewModel

@Composable
fun GameControls(
    viewModel: CamouflageGameViewModel,
    dinoSize: Float,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        // 左大拇指下蹲键 (蹲) - 垂直居中靠左
        ErgonomicButton(
            text = "蹲",
            onPress = { viewModel.onDuckPressed() },
            onRelease = { viewModel.onDuckReleased() },
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = 40.dp, y = 0.dp)
        )

        // 右大拇指跳起键 (跳) - 垂直居中靠右
        ErgonomicButton(
            text = "跳",
            onPress = { viewModel.onJumpPressed(dinoSize) },
            onRelease = { viewModel.onJumpReleased() },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = (-40).dp, y = 0.dp)
        )
    }
}

@Composable
fun ErgonomicButton(
    text: String,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1.0f,
        label = "button_scale"
    )
    val bgColor = if (isPressed) Color.Black.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.15f)
    val borderColor = if (isPressed) Color(0xFF795548).copy(alpha = 0.5f) else Color(0xFFB8CBB8).copy(alpha = 0.3f)

    Box(
        modifier = modifier
            .size(110.dp)
            .scale(scale)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        onPress()
                        val press = androidx.compose.foundation.interaction.PressInteraction.Press(it)
                        interactionSource.tryEmit(press)
                        try {
                            awaitRelease()
                        } finally {
                            onRelease()
                            interactionSource.tryEmit(androidx.compose.foundation.interaction.PressInteraction.Release(press))
                        }
                    }
                )
            }
            .background(bgColor, RoundedCornerShape(55.dp))
            .border(2.dp, borderColor, RoundedCornerShape(55.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (isPressed) Color.White else Color(0xFF795548).copy(alpha = 0.8f),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            lineHeight = 28.sp
        )
    }
}
