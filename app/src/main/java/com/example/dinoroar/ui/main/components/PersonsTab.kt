package com.example.dinoroar.ui.main.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dinoroar.data.DataRepository
import com.example.dinoroar.data.local.PersonCategoryEntity
import com.example.dinoroar.data.local.PersonEntity
import com.example.dinoroar.theme.LocalAppColors

@Composable
fun PersonsTab(
    allPersons: List<PersonEntity>,
    allCategories: List<PersonCategoryEntity>,
    repository: DataRepository,
    onNavigateToPersonCategoryManage: () -> Unit,
    innerPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppColors.current
    val neonBlue = appColors.neonBlue
    val neonAmber = appColors.neonAmber
    val textPrimary = appColors.textPrimary
    val textSecondary = appColors.textSecondary
    val cardBg = appColors.cardBg

    val allPersonsWithTemp by produceState<List<PersonEntity>>(initialValue = emptyList(), allPersons) {
        value = repository.getAllPersonsWithTemporary()
    }
    val formalCount = remember(allPersonsWithTemp) { allPersonsWithTemp.filter { !it.isTemporary }.size }
    val tempCount = remember(allPersonsWithTemp) { allPersonsWithTemp.filter { it.isTemporary }.size }
    val categoryCount = allCategories.size

    Box(
        modifier = modifier
            .padding(innerPadding)
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, neonBlue.copy(alpha = 0.4f), RoundedCornerShape(24.dp))
                .clickable { onNavigateToPersonCategoryManage() }
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "🦖 关系人群岛 🌴",
                    color = neonAmber,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(20.dp))
                
                // 统计岛
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
                    modifier = Modifier.fillMaxWidth().border(1.dp, Color.Gray.copy(alpha = 0.1f), RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(text = "📂 人物分类已建：$categoryCount 个", color = textPrimary, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
                        Text(text = "👥 正式关系人物：$formalCount 位", color = textPrimary, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
                        Text(text = "📁 临时路人数量：$tempCount 位", color = textSecondary, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    text = "在这里，你可以给小伙伴们进行分组，调整他们在日记点选时的展示顺序，还可以给每个人设定恐龙高亮标签颜色！",
                    color = textSecondary,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                Spacer(modifier = Modifier.height(28.dp))

                Button(
                    onClick = onNavigateToPersonCategoryManage,
                    colors = ButtonDefaults.buttonColors(containerColor = neonBlue),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text(
                        text = "⚙️ 进入关系与分类管理 ➔",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
