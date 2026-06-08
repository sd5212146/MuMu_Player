package com.example.yyplayer.data.model

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

data class PlayerTheme(
    val id: String,
    val name: String,
    val primaryColor: Color,
    val secondaryColor: Color,
    val backgroundColor: Color,
    val textColor: Color,
    val accentColor: Color,
    val buttonColor: Color
) {
    /** 是否为 Walkman 系列主题（应用金属质感效果） */
    val isWalkmanTheme: Boolean get() = id == "walkman_classic" || id == "walkman_blackgold"

    /**
     * 金属质感：径向高光 Brush
     * 模拟一个定向光源照射在金属表面产生的圆形光斑
     * - 黑金：右上方向下的暖金色聚光灯效果
     * - 经典：正上方向下的冷银色柔光效果
     */
    fun metallicHighlightBrush(size: Size): Brush? {
        if (!isWalkmanTheme) return null
        return if (id == "walkman_blackgold") {
            // 黑金：右上角光源 → 暖金色光斑
            Brush.radialGradient(
                0.00f to Color(0xFFF5E6B8).copy(alpha = 0.50f),  // 光斑中心：亮金色
                0.25f to Color(0xFFD4AF37).copy(alpha = 0.25f),  // 光斑过渡
                0.55f to Color(0xFF8B7530).copy(alpha = 0.08f),  // 光斑边缘
                0.75f to Color.Transparent,
                center = Offset(size.width * 0.75f, size.height * 0.08f),
                radius = size.maxDimension * 1.2f
            )
        } else {
            // 经典：正上方光源 → 亮银色光斑
            Brush.radialGradient(
                0.00f to Color.White.copy(alpha = 0.55f),         // 光斑中心：亮白
                0.30f to Color(0xFFE0E7F0).copy(alpha = 0.30f),  // 光斑过渡：银蓝
                0.55f to Color(0xFFB0BEC5).copy(alpha = 0.10f),  // 光斑边缘
                0.80f to Color.Transparent,
                center = Offset(size.width * 0.50f, size.height * 0.05f),
                radius = size.maxDimension * 1.3f
            )
        }
    }

    /**
     * 金属质感：斜向高光带 Brush
     * 模拟金属表面拉丝纹理产生的定向反光带
     * - 黑金：斜向暖金色光泽
     * - 经典：斜向亮银色光泽
     */
    fun metallicSheenBrush(): Brush? {
        if (!isWalkmanTheme) return null
        return if (id == "walkman_blackgold") {
            // 黑金：右上到左下的一道宽高光带
            Brush.linearGradient(
                0.00f to Color.Transparent,
                0.25f to Color(0xFFC9A84C).copy(alpha = 0.05f),
                0.38f to Color(0xFFE8D5A3).copy(alpha = 0.30f),  // 高光峰
                0.52f to Color(0xFFC9A84C).copy(alpha = 0.08f),
                0.75f to Color.Transparent,
                start = Offset(0f, 0f),
                end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
            )
        } else {
            // 经典：右上到左下的亮银高光带
            Brush.linearGradient(
                0.00f to Color.Transparent,
                0.20f to Color(0xFFCFD8DC).copy(alpha = 0.10f),
                0.35f to Color.White.copy(alpha = 0.40f),         // 高光峰
                0.50f to Color(0xFFCFD8DC).copy(alpha = 0.10f),
                0.72f to Color.Transparent,
                start = Offset(0f, 0f),
                end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
            )
        }
    }

    /**
     * 金属质感：边缘暗角 Brush
     * 模拟金属表面边缘反光不足的自然暗角
     */
    fun metallicVignetteBrush(size: Size): Brush? {
        if (!isWalkmanTheme) return null
        return if (id == "walkman_blackgold") {
            Brush.radialGradient(
                0.00f to Color.Transparent,
                0.50f to Color.Transparent,
                0.75f to Color(0xFF000000).copy(alpha = 0.15f),
                1.00f to Color(0xFF000000).copy(alpha = 0.30f),
                center = Offset(size.width * 0.50f, size.height * 0.50f),
                radius = size.maxDimension * 0.80f
            )
        } else {
            Brush.radialGradient(
                0.00f to Color.Transparent,
                0.50f to Color.Transparent,
                0.75f to Color(0xFF000000).copy(alpha = 0.06f),
                1.00f to Color(0xFF000000).copy(alpha = 0.15f),
                center = Offset(size.width * 0.50f, size.height * 0.50f),
                radius = size.maxDimension * 0.80f
            )
        }
    }
    companion object {
        val PRESETS = listOf(
            // ====== 默认主题：纯净白 ======
            PlayerTheme(
                id = "classic_black",
                name = "经典白",
                primaryColor = Color(0xFF1565C0),
                secondaryColor = Color(0xFFE3F2FD),
                backgroundColor = Color.White,
                textColor = Color(0xFF1A1A1A),
                accentColor = Color(0xFF1976D2),
                buttonColor = Color(0xFF90CAF9)
            ),
            // ====== 复古橙（暖白） ======
            PlayerTheme(
                id = "retro_orange",
                name = "复古橙",
                primaryColor = Color(0xFFE65100),
                secondaryColor = Color(0xFFFFF3E0),
                backgroundColor = Color(0xFFFDF5E6),
                textColor = Color(0xFF3E2723),
                accentColor = Color(0xFFFF6F00),
                buttonColor = Color(0xFFFFCC80)
            ),
            // ====== 赛博蓝（浅蓝） ======
            PlayerTheme(
                id = "cyber_blue",
                name = "赛博蓝",
                primaryColor = Color(0xFF1565C0),
                secondaryColor = Color(0xFFBBDEFB),
                backgroundColor = Color(0xFFE3F2FD),
                textColor = Color(0xFF0D47A1),
                accentColor = Color(0xFF1E88E5),
                buttonColor = Color(0xFF90CAF9)
            ),
            // ====== 森林绿（浅绿） ======
            PlayerTheme(
                id = "forest_green",
                name = "森林绿",
                primaryColor = Color(0xFF2E7D32),
                secondaryColor = Color(0xFFC8E6C9),
                backgroundColor = Color(0xFFE8F5E9),
                textColor = Color(0xFF1B5E20),
                accentColor = Color(0xFF43A047),
                buttonColor = Color(0xFFA5D6A7)
            ),
            // ====== 中国红（浅暖） ======
            PlayerTheme(
                id = "china_red",
                name = "中国红",
                primaryColor = Color(0xFFD32F2F),
                secondaryColor = Color(0xFFFFEBEE),
                backgroundColor = Color(0xFFFFF5F5),
                textColor = Color(0xFFB71C1C),
                accentColor = Color(0xFFE53935),
                buttonColor = Color(0xFFEF9A9A)
            ),
            // ====== Walkman 经典（TPS-L2 蓝银橙） ======
            PlayerTheme(
                id = "walkman_classic",
                name = "Walkman 经典",
                primaryColor = Color(0xFF1A237E),
                secondaryColor = Color(0xFFE3E8F0),
                backgroundColor = Color(0xFFFAFAFA),
                textColor = Color(0xFF1A237E),
                accentColor = Color(0xFFFF6D00),
                buttonColor = Color(0xFF7986CB)
            ),
            // ====== Walkman 黑金（ZX707 黑金砖） ======
            PlayerTheme(
                id = "walkman_blackgold",
                name = "Walkman 黑金",
                primaryColor = Color(0xFFC9A84C),
                secondaryColor = Color(0xFF1A1A1A),
                backgroundColor = Color(0xFF050505),
                textColor = Color(0xFFE8D5A3),
                accentColor = Color(0xFFD4AF37),
                buttonColor = Color(0xFF222222)
            )
        )

        fun getById(id: String): PlayerTheme =
            PRESETS.find { it.id == id } ?: PRESETS.find { it.id == "walkman_blackgold" } ?: PRESETS[0]
    }
}
