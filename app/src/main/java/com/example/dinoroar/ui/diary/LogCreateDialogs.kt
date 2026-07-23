package com.example.dinoroar.ui.diary

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.dinoroar.ui.diary.rememberUriImage

@Composable
fun LogCreateImagePreviewDialog(
    previewImageUri: Uri,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val fullBitmap = rememberUriImage(previewImageUri, context, maxW = null)
        var scale by remember { mutableStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.95f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            if (fullBitmap != null) {
                Image(
                    bitmap = fullBitmap,
                    contentDescription = "Zoomable full preview",
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 5f)
                                if (scale > 1f) {
                                    offset += pan * scale
                                } else {
                                    offset = Offset.Zero
                                }
                            }
                        }
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        )
                        .clickable(enabled = false) { }
                )
            }

            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "双指捏合可缩放图片",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.6f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun LogCreateCompressingOverlay(
    isCompressing: Boolean,
    cardBg: Color,
    neonBlue: Color,
    textPrimary: Color,
    textSecondary: Color
) {
    if (isCompressing) {
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .padding(16.dp)
                    .border(2.dp, neonBlue, RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(color = neonBlue)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "🔐 安全加密并压缩中...",
                        color = textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "正在为你的秘密日记与多媒体留念进行高强度压缩与加密，请耐心稍候...",
                        color = textSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
fun LogCreateExitConfirmDialog(
    onDismiss: () -> Unit,
    onSaveToBase: () -> Unit,
    onSaveToTreehole: () -> Unit,
    onDiscard: () -> Unit,
    cardBg: Color,
    neonAmber: Color,
    neonBlue: Color,
    neonRed: Color,
    textPrimary: Color,
    textSecondary: Color
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🦖 要离开这里吗？",
                    fontWeight = FontWeight.Bold,
                    color = neonAmber,
                    fontFamily = FontFamily.Monospace
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(24.dp)
                ) {
                    Text("×", color = textSecondary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
        },
        text = {
            Text(
                text = "你写下的秘密还没有被妥善保护起来哦。要是现在离开，今天的内容就会随风飘散了。\n\n建议你先【暂存到树洞】把日记存放在设备里，或者赶紧【存入秘密基地】妥妥同步到秘密云端防丢哦！",
                color = textPrimary,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onSaveToBase,
                    colors = ButtonDefaults.buttonColors(containerColor = neonAmber),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("存入秘密基地", color = Color.Black, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onSaveToTreehole,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("暂存到树洞", color = Color.White, fontWeight = FontWeight.Bold)
                }
                TextButton(
                    onClick = onDiscard,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text("狠心丢弃秘密离开 ➔", color = neonRed, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        },
        containerColor = cardBg,
        modifier = Modifier.border(1.dp, neonBlue.copy(alpha = 0.3f), RoundedCornerShape(28.dp))
    )
}

@Composable
fun LogCreatePersonDialog(
    onDismiss: () -> Unit,
    neonAmber: Color,
    onCreatePerson: (String, String, String) -> Unit
) {
    var newPersonName by remember { mutableStateOf("") }
    var newPersonAbbrev by remember { mutableStateOf("") }
    var newPersonRelation by remember { mutableStateOf("") }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建关系人物", color = neonAmber, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = newPersonName,
                    onValueChange = { newPersonName = it },
                    label = { Text("姓名") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = newPersonAbbrev,
                    onValueChange = { newPersonAbbrev = it },
                    label = { Text("首字母缩写 (例如: XM)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = newPersonRelation,
                    onValueChange = { newPersonRelation = it },
                    label = { Text("关系 (例如: 爸爸, 老师, 同桌)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (newPersonName.isNotBlank() && newPersonRelation.isNotBlank()) {
                        onCreatePerson(newPersonName, newPersonAbbrev, newPersonRelation)
                    } else {
                        Toast.makeText(context, "请填入姓名与关系！", Toast.LENGTH_SHORT).show()
                    }
                }
            ) {
                Text("确认")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
