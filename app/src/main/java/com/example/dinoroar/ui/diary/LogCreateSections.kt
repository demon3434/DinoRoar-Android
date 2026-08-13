package com.example.dinoroar.ui.diary

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextAlign
import com.example.dinoroar.data.local.PersonEntity
import com.example.dinoroar.media.AudioRecorder
import com.example.dinoroar.network.DinoApiService
import com.example.dinoroar.ui.main.StickerInfo
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

@Composable
fun SectionHeaderTitle(
    title: String,
    isRequired: Boolean,
    textPrimary: Color,
    modifier: Modifier = Modifier
) {
    Text(
        text = buildAnnotatedString {
            append(title)
            if (isRequired) {
                withStyle(
                    SpanStyle(
                        color = Color.Red,
                        fontWeight = FontWeight.Bold
                    )
                ) {
                    append(" *")
                }
            }
            append(":")
        },
        color = textPrimary,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        modifier = modifier
    )
}

@Composable
fun LogCreateMoodSection(
    selectedMoodDinoId: Int,
    dinos: List<Triple<Int, Int, String>>,
    cardBg: Color,
    textPrimary: Color,
    textSecondary: Color,
    neonBlue: Color,
    neonAmber: Color,
    neonGreen: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    SectionHeaderTitle(
        title = "1. 我的心情",
        isRequired = true,
        textPrimary = textPrimary,
        modifier = modifier.padding(vertical = 8.dp)
    )

    val currentSelectedDino = dinos.find { it.first == selectedMoodDinoId } ?: dinos.first()
    val currentTitle = currentSelectedDino.third.substringBefore("\n")
    val currentSubtitle = currentSelectedDino.third.substringAfter("\n", "")

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .border(1.dp, neonBlue.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(currentSelectedDino.second),
                contentDescription = selectedMoodDinoId.toString(),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(68.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.05f))
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(1f)
            ) {
                val parts = currentTitle.split(" ").filter { it.isNotBlank() }
                val (moodPart, namePart) = parseMoodAndName(parts, currentTitle)

                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = neonGreen, fontWeight = FontWeight.Bold)) {
                            append(moodPart)
                        }
                        if (namePart.isNotEmpty()) {
                            append(" ")
                            withStyle(SpanStyle(color = neonAmber, fontWeight = FontWeight.Bold)) {
                                append(namePart)
                            }
                        }
                    },
                    fontSize = 16.sp,
                    fontFamily = FontFamily.Monospace
                )


                if (currentSubtitle.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = currentSubtitle,
                        color = textSecondary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 16.sp
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "修改 ➔",
                color = neonBlue,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun LogCreatePersonSection(
    selectedPersons: List<PersonEntity>,
    selectedPersonUuids: Set<String>,
    textPrimary: Color,
    textSecondary: Color,
    neonBlue: Color,
    onNavigateToPersonSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        SectionHeaderTitle(
            title = "2. 关联人物",
            isRequired = false,
            textPrimary = textPrimary
        )
        Text(
            text = "点击修改 ➔",
            color = neonBlue,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.clickable { onNavigateToPersonSelect() }
        )
    }
    Spacer(modifier = Modifier.height(8.dp))

    if (selectedPersons.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, neonBlue.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                .clickable { onNavigateToPersonSelect() }
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "🔍 还没有关联关系人，点此添加...", color = textSecondary, fontSize = 13.sp)
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .clickable { onNavigateToPersonSelect() }
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            selectedPersons.forEach { person ->
                val colorPair = remember(person.colorTag, person.isTemporary) {
                    if (person.isTemporary) {
                        com.example.dinoroar.ui.person.DinoColorPalette.getColorByTag("gray")
                    } else {
                        com.example.dinoroar.ui.person.DinoColorPalette.getColorByTag(person.colorTag)
                    }
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(colorPair.bg)
                        .border(1.dp, colorPair.text, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    val label = if (!person.relationship.isNullOrEmpty()) {
                        "${person.name} (${person.relationship})"
                    } else {
                        person.name
                    }
                    Text(
                        text = label,
                        color = colorPair.text,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

private data class VoiceInputLatch(
    val buttonTargetField: SttTargetField,
    val isFocusedTargetField: Boolean,
    val cursorOffset: Int?
)

@Composable
fun LogCreateFormSection(
    title: String,
    onTitleChange: (String) -> Unit,
    content: String,
    onContentChange: (String) -> Unit,
    ownThoughts: String,
    onOwnThoughtsChange: (String) -> Unit,
    audioRecorder: AudioRecorder,
    apiService: DinoApiService,
    onHudStateChange: (active: Boolean, recording: Boolean, transcribing: Boolean, cancel: Boolean) -> Unit,
    textPrimary: Color,
    textSecondary: Color,
    neonBlue: Color,
    modifier: Modifier = Modifier
) {
    val titleFocusRequester = remember { FocusRequester() }
    val contentFocusRequester = remember { FocusRequester() }
    val ownThoughtsFocusRequester = remember { FocusRequester() }

    var focusedField by remember { mutableStateOf<SttTargetField?>(null) }
    var latchedVoiceInput by remember { mutableStateOf<VoiceInputLatch?>(null) }

    var titleState by remember {
        mutableStateOf(TextFieldValue(text = title, selection = TextRange(title.length)))
    }
    var contentState by remember {
        mutableStateOf(TextFieldValue(text = content, selection = TextRange(content.length)))
    }
    var ownThoughtsState by remember {
        mutableStateOf(TextFieldValue(text = ownThoughts, selection = TextRange(ownThoughts.length)))
    }

    LaunchedEffect(title) {
        if (title != titleState.text) {
            titleState = titleState.copy(text = title, selection = TextRange(title.length))
        }
    }
    LaunchedEffect(content) {
        if (content != contentState.text) {
            contentState = contentState.copy(text = content, selection = TextRange(content.length))
        }
    }
    LaunchedEffect(ownThoughts) {
        if (ownThoughts != ownThoughtsState.text) {
            ownThoughtsState = ownThoughtsState.copy(text = ownThoughts, selection = TextRange(ownThoughts.length))
        }
    }

    fun handlePressStart(target: SttTargetField) {
        val isSameFocused = (focusedField == target)
        val offset = if (isSameFocused) {
            when (target) {
                SttTargetField.TITLE -> titleState.selection.min.coerceIn(0, titleState.text.length)
                SttTargetField.CONTENT -> contentState.selection.min.coerceIn(0, contentState.text.length)
                SttTargetField.OWN_THOUGHTS -> ownThoughtsState.selection.min.coerceIn(0, ownThoughtsState.text.length)
            }
        } else null

        latchedVoiceInput = VoiceInputLatch(
            buttonTargetField = target,
            isFocusedTargetField = isSameFocused,
            cursorOffset = offset
        )
    }

    fun handleAppendText(target: SttTargetField, textToAppend: String) {
        val latch = latchedVoiceInput
        val shouldInsertAtCursor = (latch != null && latch.buttonTargetField == target && latch.isFocusedTargetField && latch.cursorOffset != null)

        when (target) {
            SttTargetField.TITLE -> {
                val currentText = titleState.text
                val (newText, newSelection) = if (shouldInsertAtCursor && latch != null && latch.cursorOffset != null) {
                    val offset = latch.cursorOffset
                    val updated = currentText.substring(0, offset) + textToAppend + currentText.substring(offset)
                    Pair(updated, TextRange(offset + textToAppend.length))
                } else {
                    val updated = if (currentText.isBlank()) textToAppend else "$currentText $textToAppend"
                    Pair(updated, TextRange(updated.length))
                }
                titleState = TextFieldValue(text = newText, selection = newSelection)
                onTitleChange(newText)
                try { titleFocusRequester.requestFocus() } catch (_: Exception) {}
            }
            SttTargetField.CONTENT -> {
                val currentText = contentState.text
                val (newText, newSelection) = if (shouldInsertAtCursor && latch != null && latch.cursorOffset != null) {
                    val offset = latch.cursorOffset
                    val updated = currentText.substring(0, offset) + textToAppend + currentText.substring(offset)
                    Pair(updated, TextRange(offset + textToAppend.length))
                } else {
                    val updated = if (currentText.isBlank()) textToAppend else "$currentText\n$textToAppend"
                    Pair(updated, TextRange(updated.length))
                }
                contentState = TextFieldValue(text = newText, selection = newSelection)
                onContentChange(newText)
                try { contentFocusRequester.requestFocus() } catch (_: Exception) {}
            }
            SttTargetField.OWN_THOUGHTS -> {
                val currentText = ownThoughtsState.text
                val (newText, newSelection) = if (shouldInsertAtCursor && latch != null && latch.cursorOffset != null) {
                    val offset = latch.cursorOffset
                    val updated = currentText.substring(0, offset) + textToAppend + currentText.substring(offset)
                    Pair(updated, TextRange(offset + textToAppend.length))
                } else {
                    val updated = if (currentText.isBlank()) textToAppend else "$currentText\n$textToAppend"
                    Pair(updated, TextRange(updated.length))
                }
                ownThoughtsState = TextFieldValue(text = newText, selection = newSelection)
                onOwnThoughtsChange(newText)
                try { ownThoughtsFocusRequester.requestFocus() } catch (_: Exception) {}
            }
        }
        latchedVoiceInput = null
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // 3. 日记标题 (必填)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionHeaderTitle(
                title = "3. 日记标题",
                isRequired = true,
                textPrimary = textPrimary
            )
            VoiceInputButton(
                targetField = SttTargetField.TITLE,
                audioRecorder = audioRecorder,
                apiService = apiService,
                onAppendText = ::handleAppendText,
                onHudStateChange = onHudStateChange,
                onPressStart = ::handlePressStart
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = titleState,
            onValueChange = { newValue ->
                titleState = newValue
                onTitleChange(newValue.text)
            },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = textPrimary,
                unfocusedTextColor = textPrimary,
                focusedBorderColor = neonBlue,
                unfocusedBorderColor = textSecondary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(titleFocusRequester)
                .onFocusChanged { focusState ->
                    if (focusState.isFocused) {
                        focusedField = SttTargetField.TITLE
                    } else if (focusedField == SttTargetField.TITLE) {
                        focusedField = null
                    }
                }
        )
        Spacer(modifier = Modifier.height(16.dp))

        // 4. 事情经过 (必填)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionHeaderTitle(
                title = "4. 事情经过",
                isRequired = true,
                textPrimary = textPrimary
            )
            VoiceInputButton(
                targetField = SttTargetField.CONTENT,
                audioRecorder = audioRecorder,
                apiService = apiService,
                onAppendText = ::handleAppendText,
                onHudStateChange = onHudStateChange,
                onPressStart = ::handlePressStart
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = contentState,
            onValueChange = { newValue ->
                contentState = newValue
                onContentChange(newValue.text)
            },
            minLines = 3,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = textPrimary,
                unfocusedTextColor = textPrimary,
                focusedBorderColor = neonBlue,
                unfocusedBorderColor = textSecondary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(contentFocusRequester)
                .onFocusChanged { focusState ->
                    if (focusState.isFocused) {
                        focusedField = SttTargetField.CONTENT
                    } else if (focusedField == SttTargetField.CONTENT) {
                        focusedField = null
                    }
                }
        )
        Spacer(modifier = Modifier.height(16.dp))

        // 5. 我的感想 (可选)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionHeaderTitle(
                title = "5. 我的感想",
                isRequired = false,
                textPrimary = textPrimary
            )
            VoiceInputButton(
                targetField = SttTargetField.OWN_THOUGHTS,
                audioRecorder = audioRecorder,
                apiService = apiService,
                onAppendText = ::handleAppendText,
                onHudStateChange = onHudStateChange,
                onPressStart = ::handlePressStart
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = ownThoughtsState,
            onValueChange = { newValue ->
                ownThoughtsState = newValue
                onOwnThoughtsChange(newValue.text)
            },
            minLines = 2,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = textPrimary,
                unfocusedTextColor = textPrimary,
                focusedBorderColor = neonBlue,
                unfocusedBorderColor = textSecondary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(ownThoughtsFocusRequester)
                .onFocusChanged { focusState ->
                    if (focusState.isFocused) {
                        focusedField = SttTargetField.OWN_THOUGHTS
                    } else if (focusedField == SttTargetField.OWN_THOUGHTS) {
                        focusedField = null
                    }
                }
        )
    }
}

@Composable
fun LogCreateStickerSection(
    stickers: MutableList<StickerInfo>,
    stickerCacheMap: Map<Int, String>,
    serverBaseUrl: String,
    cardBg: Color,
    neonBlue: Color,
    textPrimary: Color,
    textSecondary: Color,
    canvasInstanceId: Int?,
    canvasAspectRatio: String,
    canvasImageUrl: String?,
    onSelectCanvasClick: () -> Unit,
    onSelectStickerClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedSticker by remember { mutableStateOf<StickerInfo?>(null) }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "🎨 装饰我的手账贴纸:",
            color = textPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(8.dp))

        StickerEditorCanvas(
            stickers = stickers,
            stickerCacheMap = stickerCacheMap,
            serverBaseUrl = serverBaseUrl,
            cardBg = cardBg,
            neonBlue = neonBlue,
            textSecondary = textSecondary,
            canvasInstanceId = canvasInstanceId,
            canvasAspectRatio = canvasAspectRatio,
            canvasImageUrl = canvasImageUrl,
            selectedSticker = selectedSticker,
            onSelectedStickerChange = { selectedSticker = it }
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onSelectCanvasClick,
                colors = ButtonDefaults.buttonColors(containerColor = neonBlue),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f).height(48.dp)
            ) {
                Text("🖼️ 背景画布", color = Color.White, fontWeight = FontWeight.Bold)
            }

            val context = LocalContext.current
            Button(
                onClick = {
                    val connectivityManager = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
                    val activeNetwork = connectivityManager?.activeNetwork
                    val caps = connectivityManager?.getNetworkCapabilities(activeNetwork)
                    val isConnected = caps?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

                    if (isConnected) {
                        onSelectStickerClick()
                    } else {
                        android.widget.Toast.makeText(
                            context,
                            "🌐 当前处于离线状态，无法获取贴纸持有数量，暂不支持添加贴纸",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = neonBlue),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f).height(48.dp)
            ) {
                Text("🎨 装饰贴纸", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun LogCreateMediaSection(
    selectedImageUrisCount: Int,
    selectedVideoUrisCount: Int,
    recordedFilesCount: Int,
    isRecording: Boolean,
    onPickImage: () -> Unit,
    onCaptureImage: () -> Unit,
    onPickVideo: () -> Unit,
    onCaptureVideo: () -> Unit,
    onPickAudio: () -> Unit,
    onToggleRecording: () -> Unit,
    textPrimary: Color,
    textSecondary: Color,
    neonRed: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeaderTitle(
            title = "6. 影音留念",
            isRequired = false,
            textPrimary = textPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Row 1: 图片
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GlassMediaButton(
                    icon = "📷",
                    text = "相册图片 ($selectedImageUrisCount/9)",
                    glowColor = Color(0xFF00E5FF),
                    textColor = textPrimary,
                    secondaryColor = textSecondary,
                    isLight = false,
                    enabled = selectedImageUrisCount < 9,
                    modifier = Modifier.weight(1f).height(52.dp),
                    onClick = onPickImage
                )
                GlassMediaButton(
                    icon = "📸",
                    text = "实时拍照",
                    glowColor = Color(0xFF00B0FF),
                    textColor = Color.White,
                    secondaryColor = textSecondary,
                    isLight = true,
                    enabled = selectedImageUrisCount < 9,
                    modifier = Modifier.weight(1f).height(52.dp),
                    onClick = onCaptureImage
                )
            }

            // Row 2: 视频
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GlassMediaButton(
                    icon = "🎥",
                    text = "文件视频 ($selectedVideoUrisCount/3)",
                    glowColor = Color(0xFFD500F9),
                    textColor = textPrimary,
                    secondaryColor = textSecondary,
                    isLight = false,
                    enabled = selectedVideoUrisCount < 3,
                    modifier = Modifier.weight(1f).height(52.dp),
                    onClick = onPickVideo
                )
                GlassMediaButton(
                    icon = "📹",
                    text = "实时录像",
                    glowColor = Color(0xFFE040FB),
                    textColor = Color.White,
                    secondaryColor = textSecondary,
                    isLight = true,
                    enabled = selectedVideoUrisCount < 3,
                    modifier = Modifier.weight(1f).height(52.dp),
                    onClick = onCaptureVideo
                )
            }

            // Row 3: 音频
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GlassMediaButton(
                    icon = "📁",
                    text = "导入音频 ($recordedFilesCount/5)",
                    glowColor = Color(0xFF00E676),
                    textColor = textPrimary,
                    secondaryColor = textSecondary,
                    isLight = false,
                    enabled = recordedFilesCount < 5,
                    modifier = Modifier.weight(1f).height(52.dp),
                    onClick = onPickAudio
                )
                val isRecordingText = if (isRecording) "停止录音" else "实时录音"
                val recGlowColor = if (isRecording) neonRed else Color(0xFF00E676)
                GlassMediaButton(
                    icon = if (isRecording) "⏹" else "🎤",
                    text = isRecordingText,
                    glowColor = recGlowColor,
                    textColor = if (isRecording) neonRed else Color.White,
                    secondaryColor = textSecondary,
                    isLight = true,
                    enabled = true,
                    modifier = Modifier.weight(1f).height(52.dp),
                    onClick = onToggleRecording
                )
            }
        }
    }
}

@Composable
fun LogCreateBottomBar(
    onSaveToBase: () -> Unit,
    onSaveToTreehole: () -> Unit,
    neonAmber: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = onSaveToTreehole,
            colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
        ) {
            Text("暂存到树洞", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }

        Button(
            onClick = onSaveToBase,
            colors = ButtonDefaults.buttonColors(containerColor = neonAmber),
            modifier = Modifier
                .weight(1.5f)
                .height(48.dp)
        ) {
            Text("存入秘密基地", color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}
