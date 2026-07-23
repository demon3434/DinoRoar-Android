package com.example.dinoroar.ui.main.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dinoroar.data.local.LogEntity
import com.example.dinoroar.data.local.LogPersonCrossRef
import com.example.dinoroar.data.local.PersonEntity
import com.example.dinoroar.theme.LocalAppColors

@Composable
fun BondHandbookDialog(
    person: PersonEntity,
    logs: List<LogEntity>,
    allCrossRefs: List<LogPersonCrossRef>,
    onDismissRequest: () -> Unit,
    onFilterPerson: (String) -> Unit,
    onNavigateToCreate: (String?) -> Unit
) {
    val appColors = LocalAppColors.current

    val relatedLogUuids = remember(person.uuid, allCrossRefs) {
        allCrossRefs.filter { it.personUuid == person.uuid }.map { it.logUuid }.toSet()
    }
    val relatedLogs = remember(relatedLogUuids, logs) {
        logs.filter { relatedLogUuids.contains(it.uuid) }
    }
    val happyMoodIds = setOf(1, 2, 3, 4)
    val sadMoodIds = setOf(7, 8, 9, 10, 11)

    val happyCount = relatedLogs.count {
        it.moodDinoId in happyMoodIds
    }
    val sadCount = relatedLogs.count {
        it.moodDinoId in sadMoodIds
    }
    val totalCount = relatedLogs.size
    val starCount = if (happyCount > 0) minOf(5, (happyCount + 1) / 2) else 0
    val fireCount = if (totalCount > 0) minOf(5, (totalCount + 3) / 4) else 0
    val eggCount = if (sadCount > 0) minOf(5, (sadCount + 1) / 2) else 0

    val bendingText = when {
        totalCount >= 8 -> "挚友知己 🏆"
        totalCount >= 6 -> "黄金搭档 ✨"
        totalCount >= 4 -> "深厚友情 🌸"
        totalCount >= 2 -> "玩耍伙伴 🌿"
        else -> "初识萌芽 🌱"
    }

    val moodReport = when {
        eggCount >= 3 -> "最近和 ta 在一起的时光似乎有些多云转雷雨 🌧️。记录里包含了不少别扭或委屈（共 ${sadCount} 次）。要不要主动送 ta 一个暖暖的拥抱来和好呢？"
        starCount >= 3 && eggCount <= 1 -> "ta 绝对是你的超级晴天星！每次提到 ta，日记本里都洒满了金灿灿的太阳光 ✨（共记录了 ${happyCount} 次晴天陪伴）！"
        starCount >= 2 && eggCount >= 2 -> "你们真是欢喜冤家！相爱相杀，在一起时快乐大笑不断（${happyCount}次），小摩擦也不少（${sadCount}次），但拌嘴之后羁绊反而更火热了！"
        totalCount > 0 -> "陪伴细水长流，你们在日记本里温馨而平稳地度过。共同面朝晴天 ${happyCount} 次，面朝雷雨 ${sadCount} 次。"
        else -> "你们刚开始种下友谊的种子，多和 ta 聊天写写日记吧！🌱"
    }

    val happyPercent = if (totalCount > 0) (happyCount * 100) / totalCount else 100
    val sadPercent = if (totalCount > 0) 100 - happyPercent else 0

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = when {
                        eggCount >= 3 -> "🌧️"
                        starCount >= 3 -> "⭐"
                        starCount >= 2 && eggCount >= 2 -> "🔥"
                        totalCount > 0 -> "✨"
                        else -> "🌱"
                    },
                    fontSize = 32.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${person.name} 的羁绊手账",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    color = appColors.textPrimary
                )
                Text(
                    text = "秘密基地关系星座小岛",
                    fontSize = 11.sp,
                    color = appColors.textSecondary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Gray.copy(alpha = 0.08f))
                    .border(1.dp, Color.Gray.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(text = "羁绊等级：", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = appColors.textPrimary)
                    Text(text = bendingText, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFFF59E0B))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(text = "陪伴热度：", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = appColors.textPrimary)
                    Text(text = "共同记录过 ${totalCount} 次", fontSize = 13.sp, color = appColors.textPrimary)
                }

                Spacer(modifier = Modifier.height(2.dp))
                HorizontalDivider(color = Color.Gray.copy(alpha = 0.15f))
                Spacer(modifier = Modifier.height(2.dp))

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "🌟 晴天小星星：", fontSize = 12.sp, color = appColors.textSecondary)
                        Text(
                            text = if (starCount > 0) "⭐".repeat(starCount) else "无",
                            fontSize = 12.sp,
                            color = if (starCount > 0) Color.Unspecified else appColors.textSecondary.copy(alpha = 0.5f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "💥 友情小火苗：", fontSize = 12.sp, color = appColors.textSecondary)
                        Text(
                            text = if (fireCount > 0) "🔥".repeat(fireCount) else "无",
                            fontSize = 12.sp,
                            color = if (fireCount > 0) Color.Unspecified else appColors.textSecondary.copy(alpha = 0.5f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "🌧️ 雷雨臭鸡蛋：", fontSize = 12.sp, color = appColors.textSecondary)
                        Text(
                            text = if (eggCount > 0) "🥚".repeat(eggCount) else "无",
                            fontSize = 12.sp,
                            color = if (eggCount > 0) Color.Unspecified else appColors.textSecondary.copy(alpha = 0.5f)
                        )
                    }
                }

                if (totalCount > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    HorizontalDivider(color = Color.Gray.copy(alpha = 0.15f))
                    Spacer(modifier = Modifier.height(4.dp))

                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "☀️ 晴天陪伴: ${happyCount}次 (${happyPercent}%)",
                                fontSize = 10.sp,
                                color = appColors.textSecondary
                            )
                            Text(
                                text = "🌧️ 雷雨共面: ${sadCount}次 (${sadPercent}%)",
                                fontSize = 10.sp,
                                color = appColors.textSecondary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color(0xFFEF4444))
                        ) {
                            if (happyPercent > 0) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .weight(happyPercent.toFloat())
                                        .background(Color(0xFF22C55E))
                                )
                            }
                            if (sadPercent > 0) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .weight(sadPercent.toFloat())
                                        .background(Color(0xFFEF4444))
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                HorizontalDivider(color = Color.Gray.copy(alpha = 0.15f))
                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = moodReport,
                    fontSize = 12.sp,
                    color = appColors.textPrimary,
                    fontStyle = FontStyle.Italic,
                    lineHeight = 16.sp
                )
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                TextButton(onClick = onDismissRequest) {
                    Text("关闭", color = appColors.textSecondary)
                }
                TextButton(
                    onClick = {
                        onDismissRequest()
                        onFilterPerson(person.uuid)
                    }
                ) {
                    Text("翻看日记", color = appColors.neonBlue, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = {
                        onDismissRequest()
                        onNavigateToCreate(person.uuid)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = appColors.neonBlue)
                ) {
                    Text("去写日记", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        },
        containerColor = appColors.cardBg
    )
}

@Composable
fun ScoreGuideDialog(
    onDismissRequest: () -> Unit
) {
    val appColors = LocalAppColors.current
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                text = "📊 关系人评价及打分说明 🌈",
                fontWeight = FontWeight.Bold,
                color = appColors.textPrimary
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "点击基地好友头像或榜单，即可查看专属 ta 的“羁绊手账”画像评价：",
                    color = appColors.textPrimary,
                    fontSize = 13.sp
                )
                HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f))
                Text(
                    text = "🌟 晴天小星星：",
                    color = appColors.neonGreen,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Text(
                    text = "依据记录 ta 时日记的情绪得分。若为“好心情”（如三角龙、冲天翼手龙、霸王龙、雷龙）则获得晴天评星，最多5星。",
                    color = appColors.textSecondary,
                    fontSize = 12.sp
                )
                Text(
                    text = "💥 友情小火苗：",
                    color = Color(0xFFF59E0B),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Text(
                    text = "依据你与 ta 共同记录的日记总篇数（相处热度）。多写日记能提升友情评星，最多5星。",
                    color = appColors.textSecondary,
                    fontSize = 12.sp
                )
                Text(
                    text = "🌧️ 雷雨臭鸡蛋：",
                    color = appColors.neonRed,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Text(
                    text = "依据记录 ta 时日记为“雷雨心情”（如甲龙、叹气翼手龙、副栉龙、喷火霸王龙）的次数评星。若有小摩擦则提示鸡蛋警告，代表需要你给 ta 温暖拥抱！",
                    color = appColors.textSecondary,
                    fontSize = 12.sp
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text("我都懂啦！", color = appColors.neonBlue)
            }
        },
        containerColor = appColors.cardBg
    )
}
