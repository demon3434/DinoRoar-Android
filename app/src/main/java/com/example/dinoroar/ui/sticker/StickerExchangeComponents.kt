package com.example.dinoroar.ui.sticker

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.dinoroar.data.local.SecurePrefs
import com.example.dinoroar.network.DinoApiService
import com.example.dinoroar.network.StickerExchangeRequest
import com.example.dinoroar.network.StickerSeriesDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun StickerExchangeCartDrawer(
    showCartDrawer: Boolean,
    onDismiss: () -> Unit,
    cart: SnapshotStateMap<Int, Int>,
    seriesList: List<StickerSeriesDto>,
    serverBaseUrl: String,
    eggEnergy: Int,
    cardBg: Color,
    neonBlue: Color,
    neonRed: Color,
    neonAmber: Color,
    textPrimary: Color
) {
    if (!showCartDrawer) return

    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        // 1. 半透明遮罩背景
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable { onDismiss() }
        )

        // 2. 从底部弹起的购物车卡片
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(cardBg)
                .border(1.dp, neonBlue.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .clickable(enabled = false) {}
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🛒 已选贴纸",
                        color = textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "清空",
                        color = neonRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable {
                            cart.clear()
                            onDismiss()
                        }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                val cartItems = cart.entries.toList()
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(cartItems) { _, entry ->
                        val stickerId = entry.key
                        val qty = entry.value
                        val sticker = seriesList.flatMap { it.stickers }.find { it.id == stickerId }
                        if (sticker != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val localRes = com.example.dinoroar.ui.main.getStickerLocalResource(sticker.image_url)
                                val modelData: Any = if (localRes != null) localRes else {
                                    if (sticker.image_url.startsWith("/static/")) serverBaseUrl + sticker.image_url else sticker.image_url
                                }
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(modelData)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = sticker.name,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color.White.copy(alpha = 0.05f))
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                Text(
                                    text = sticker.name,
                                    color = textPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )

                                Text(
                                    text = "🥚 ${sticker.exchange_price * qty}",
                                    color = neonAmber,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clickable {
                                                if (qty > 1) {
                                                    cart[stickerId] = qty - 1
                                                } else {
                                                    cart.remove(stickerId)
                                                    if (cart.isEmpty()) onDismiss()
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = "－", color = neonBlue, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    }

                                    Text(
                                        text = qty.toString(),
                                        color = textPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )

                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clickable {
                                                if (qty < 99) {
                                                    val currentTotalCost = cart.entries.sumOf { (id, q) ->
                                                        val st = seriesList.flatMap { it.stickers }.find { it.id == id }
                                                        (st?.exchange_price ?: 0) * q
                                                    }
                                                    if (currentTotalCost + sticker.exchange_price > eggEnergy) {
                                                        Toast.makeText(context, "蛋能量不足以添加更多哦 🥚", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        cart[stickerId] = qty + 1
                                                    }
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = "＋", color = neonBlue, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StickerExchangeConfirmDialog(
    cart: SnapshotStateMap<Int, Int>,
    seriesList: List<StickerSeriesDto>,
    eggEnergy: Int,
    apiService: DinoApiService,
    securePrefs: SecurePrefs,
    coroutineScope: CoroutineScope,
    onExchangeFinished: (updatedEggEnergy: Int, updatedInventory: String) -> Unit,
    onDismiss: () -> Unit,
    cardBg: Color,
    neonAmber: Color,
    neonRed: Color,
    neonBlue: Color,
    neonGreen: Color,
    textPrimary: Color,
    textSecondary: Color
) {
    val context = LocalContext.current
    var isExchangingMultiple by remember { mutableStateOf(false) }

    val totalCost = cart.entries.sumOf { entry ->
        val st = seriesList.flatMap { it.stickers }.find { it.id == entry.key }
        (st?.exchange_price ?: 0) * entry.value
    }
    val totalQty = cart.values.sum()

    AlertDialog(
        onDismissRequest = { if (!isExchangingMultiple) onDismiss() },
        title = {
            Text(
                text = "🛒 贴纸结算确认",
                color = neonAmber,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        },
        text = {
            Text(
                text = "本次共购买 $totalQty 张贴纸，将消耗 $totalCost 蛋能量。是否确认结算？",
                color = textPrimary,
                fontSize = 14.sp
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    if (eggEnergy < totalCost) {
                        Toast.makeText(context, "蛋能量不足以完成结算哦 🥚", Toast.LENGTH_SHORT).show()
                        onDismiss()
                        return@Button
                    }
                    isExchangingMultiple = true
                    coroutineScope.launch {
                        try {
                            var currentEggEnergy = eggEnergy
                            var currentInventory = securePrefs.stickerInventory
                            for (entry in cart.entries) {
                                val id = entry.key
                                val count = entry.value
                                for (i in 0 until count) {
                                    val syncAsset = apiService.exchangeSticker(StickerExchangeRequest(id))
                                    securePrefs.stickerInventory = syncAsset.sticker_inventory
                                    securePrefs.eggEnergy = syncAsset.egg_energy
                                    securePrefs.lastSyncedInventory = syncAsset.sticker_inventory
                                    currentEggEnergy = syncAsset.egg_energy
                                    currentInventory = syncAsset.sticker_inventory
                                }
                            }
                            Toast.makeText(context, "结算完成，已成功加入你的库存！", Toast.LENGTH_SHORT).show()
                            onExchangeFinished(currentEggEnergy, currentInventory)
                        } catch (e: Exception) {
                            Toast.makeText(context, "部分或全部商品结算失败: ${e.message}", Toast.LENGTH_SHORT).show()
                        } finally {
                            isExchangingMultiple = false
                            onDismiss()
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = neonRed),
                enabled = !isExchangingMultiple
            ) {
                if (isExchangingMultiple) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                } else {
                    Text("确定", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = { onDismiss() },
                enabled = !isExchangingMultiple
            ) {
                Text("取消", color = textSecondary)
            }
        },
        containerColor = cardBg,
        properties = DialogProperties(
            dismissOnBackPress = !isExchangingMultiple,
            dismissOnClickOutside = !isExchangingMultiple
        )
    )
}
